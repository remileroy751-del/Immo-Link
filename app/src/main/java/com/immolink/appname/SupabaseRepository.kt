package com.immolink.appname

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

object SupabaseConfig {
    const val URL = "https://hraiykeenouojxrcwrlx.supabase.co"
    const val KEY = "sb_publishable_ghGIS3uEaT3YBPsXavBo1Q_Mi2ytaXi"
}

class SupabaseRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("immolink_supabase", Context.MODE_PRIVATE)

    private fun accessToken(): String = prefs.getString("access_token", "") ?: ""
    fun currentUid(): String = prefs.getString("user_id", "") ?: ""
    fun isSignedIn(): Boolean = accessToken().isNotBlank() && currentUid().isNotBlank()

    private suspend fun request(method: String, path: String, body: String? = null, auth: Boolean = true, extra: Map<String,String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val conn = (URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 30000
            doInput = true
            setRequestProperty("apikey", SupabaseConfig.KEY)
            setRequestProperty("Accept", "application/json")
            if (auth && accessToken().isNotBlank()) setRequestProperty("Authorization", "Bearer ${accessToken()}")
            extra.forEach { (k,v) -> setRequestProperty(k,v) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
        conn.disconnect()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error_description") } }.getOrDefault(text)
            throw IllegalStateException("Supabase $code: ${msg.ifBlank { text.take(240) }}")
        }
        text
    }

    suspend fun signUp(email: String, password: String, metadata: Map<String, String>) {
        val json = JSONObject().put("email", email).put("password", password).put("data", JSONObject(metadata))
        val response = request("POST", "/auth/v1/signup", json.toString(), auth = false)
        saveAuth(response)
        if (!isSignedIn()) throw IllegalStateException("SUPABASE_EMAIL_CONFIRMATION_REQUIRED")
    }

    suspend fun signIn(email: String, password: String) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val response = request("POST", "/auth/v1/token?grant_type=password", body, auth = false)
        saveAuth(response)
        if (!isSignedIn()) throw IllegalStateException("Connexion Supabase impossible")
    }

    private fun saveAuth(response: String) {
        val j = JSONObject(response)
        val session = j.optJSONObject("session") ?: j
        val token = session.optString("access_token")
        val refresh = session.optString("refresh_token")
        val user = j.optJSONObject("user") ?: session.optJSONObject("user")
        val uid = user?.optString("id").orEmpty()
        prefs.edit().apply {
            if (token.isNotBlank()) putString("access_token", token)
            if (refresh.isNotBlank()) putString("refresh_token", refresh)
            if (uid.isNotBlank()) putString("user_id", uid)
        }.apply()
    }

    fun signOut() { prefs.edit().clear().apply() }

    suspend fun getProfile(uid: String): UserProfile {
        val rows = request("GET", "/rest/v1/profiles?id=eq.${enc(uid)}&select=*")
        val j = JSONArray(rows).optJSONObject(0) ?: throw IllegalStateException("Profil introuvable")
        return profile(j)
    }

    suspend fun getAgency(uid: String): Agency? {
        val rows = request("GET", "/rest/v1/agencies?owner_id=eq.${enc(uid)}&select=*")
        return JSONArray(rows).optJSONObject(0)?.let(::agency)
    }

    suspend fun createAgency(data: Map<String, Any?>) {
        val body = JSONObject(data.filterValues { it != null }).toString()
        val rows = JSONArray(request("POST", "/rest/v1/agencies", body, extra = mapOf("Prefer" to "return=representation")))
        val agencyId = rows.optJSONObject(0)?.optString("id").orEmpty()
        if (agencyId.isBlank()) throw IllegalStateException("Agence non créée")
        request("PATCH", "/rest/v1/profiles?id=eq.${enc(currentUid())}", JSONObject(mapOf("agency" to true, "agency_id" to agencyId)).toString(), extra = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun publishListing(data: Map<String, Any?>): Listing {
        val body = JSONObject(data.filterValues { it != null }).toString()
        val row = JSONArray(request("POST", "/rest/v1/rpc/publish_listing", body)).optJSONObject(0)
            ?: throw IllegalStateException("Annonce non créée")
        return listing(row)
    }

    suspend fun relist(id: String) { request("POST", "/rest/v1/rpc/relist_listing", JSONObject().put("p_listing_id", id).toString()) }
    suspend fun deleteListing(id: String) { request("POST", "/rest/v1/rpc/delete_listing", JSONObject().put("p_listing_id", id).toString()) }
    suspend fun deleteAccount() { request("POST", "/rest/v1/rpc/delete_my_account", "{}") }
    suspend fun incrementViews(id: String) { request("POST", "/rest/v1/rpc/increment_listing_views", JSONObject().put("p_listing_id", id).toString()) }

    suspend fun uploadUris(uid: String, bucket: String, uris: List<Uri>, max: Int): List<String> = withContext(Dispatchers.IO) {
        require(uris.size <= max)
        uris.take(max).map { uri ->
            val name = "$uid/${UUID.randomUUID()}.jpg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Image illisible")
            val conn = (URL("${SupabaseConfig.URL}/storage/v1/object/$bucket/$name").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 20000; readTimeout = 60000
                setRequestProperty("apikey", SupabaseConfig.KEY); setRequestProperty("Authorization", "Bearer ${accessToken()}")
                setRequestProperty("Content-Type", "image/jpeg"); setRequestProperty("x-upsert", "false")
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("Upload impossible (${conn.errorStream?.readBytes()?.toString(Charsets.UTF_8)?.take(200)})")
            conn.disconnect()
            if (bucket == "listing-photos" || bucket == "agency-logos") "${SupabaseConfig.URL}/storage/v1/object/public/$bucket/$name" else name
        }
    }

    suspend fun submitCertification(frontPath: String, backPath: String) {
        val agencyId = getAgency(currentUid())?.id ?: throw IllegalStateException("Agence introuvable")
        request("POST", "/rest/v1/agency_certifications", JSONObject(mapOf("agency_id" to agencyId, "owner_id" to currentUid(), "front_path" to frontPath, "back_path" to backPath)).toString(), extra = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun createPayment(kind: String): String {
        throw IllegalStateException("Le paiement CinetPay doit être configuré côté Supabase Edge Functions avant utilisation.")
    }

    suspend fun reportListing(listingId: String, reason: String) {
        val p = getProfile(currentUid())
        request("POST", "/rest/v1/reports", JSONObject(mapOf("listing_id" to listingId, "reporter_id" to currentUid(), "country" to p.country, "reason" to reason)).toString(), extra = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun saveSearch(criteria: SearchCriteria, country: String, uid: String) {
        val body = JSONObject(mapOf("user_id" to uid, "country" to country, "mode" to criteria.mode, "property_type" to criteria.propertyType, "city" to criteria.city, "min_price" to criteria.minPrice, "max_price" to criteria.maxPrice, "active" to true)).toString()
        request("POST", "/rest/v1/saved_searches", body, extra = mapOf("Prefer" to "return=minimal"))
    }

    suspend fun toggleFavorite(uid: String, listingId: String, country: String) {
        request("POST", "/rest/v1/rpc/toggle_favorite", JSONObject().put("p_listing_id", listingId).toString())
    }
    suspend fun isFavorite(uid: String, listingId: String): Boolean = JSONArray(request("GET", "/rest/v1/favorites?user_id=eq.${enc(uid)}&listing_id=eq.${enc(listingId)}&select=listing_id")).length() > 0

    suspend fun searchListings(uid: String, criteria: SearchCriteria): List<Listing> {
        val p = getProfile(uid)
        val q = StringBuilder("/rest/v1/listings?select=*&country=eq.${enc(p.country)}&active=eq.true&mode=eq.${enc(criteria.mode)}&order=created_at.desc")
        val rows = JSONArray(request("GET", q.toString()))
        return (0 until rows.length()).map { listing(rows.getJSONObject(it)) }.filter {
            (criteria.city.isBlank() || it.city == criteria.city) &&
            (criteria.propertyType.isBlank() || it.propertyType == criteria.propertyType) &&
            (criteria.minPrice <= 0 || it.price >= criteria.minPrice) &&
            (criteria.maxPrice == null || it.price <= criteria.maxPrice!!)
        }
    }

    suspend fun getFeatured(uid: String, mode: String): List<Listing> = searchListings(uid, SearchCriteria(mode = mode)).take(30)
    suspend fun getMyListings(uid: String): List<Listing> = JSONArray(request("GET", "/rest/v1/listings?owner_id=eq.${enc(uid)}&select=*&order=created_at.desc")).let { a -> (0 until a.length()).map { listing(a.getJSONObject(it)) } }
    suspend fun getListing(id: String): Listing? = JSONArray(request("GET", "/rest/v1/listings?id=eq.${enc(id)}&select=*")).optJSONObject(0)?.let(::listing)
    suspend fun getOwner(uid: String): UserProfile = getProfile(uid)
    suspend fun getOwnerAgency(uid: String): Agency? = getAgency(uid)

    suspend fun getChats(uid: String): List<ChatSummary> {
        val rows = JSONArray(request("GET", "/rest/v1/chats?select=*&participants=cs.{${enc(uid)}}&order=updated_at.desc"))
        return (0 until rows.length()).map { chat(rows.getJSONObject(it)) }
    }
    suspend fun getMessages(chatId: String): List<ChatMessage> {
        val rows = JSONArray(request("GET", "/rest/v1/chat_messages?chat_id=eq.${enc(chatId)}&select=*&order=created_at.asc"))
        return (0 until rows.length()).map { message(rows.getJSONObject(it)) }
    }
    suspend fun sendMessage(chatId: String, listingId: String, country: String, participants: List<String>, senderId: String, text: String) {
        val existing = JSONArray(request("GET", "/rest/v1/chats?id=eq.${enc(chatId)}&select=id"))
        if (existing.length() == 0) request("POST", "/rest/v1/chats", JSONObject(mapOf("id" to chatId, "listing_id" to listingId, "country" to country, "participants" to JSONArray(participants), "last_text" to text)).toString(), extra = mapOf("Prefer" to "return=minimal"))
        else request("PATCH", "/rest/v1/chats?id=eq.${enc(chatId)}", JSONObject(mapOf("last_text" to text)).toString(), extra = mapOf("Prefer" to "return=minimal"))
        request("POST", "/rest/v1/chat_messages", JSONObject(mapOf("chat_id" to chatId, "sender_id" to senderId, "text" to text)).toString(), extra = mapOf("Prefer" to "return=minimal"))
    }

    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
    private fun str(j: JSONObject, key: String) = j.optString(key).takeIf { it.isNotBlank() }
    private fun arr(j: JSONObject, key: String): List<String> { val a=j.optJSONArray(key) ?: return emptyList(); return (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }
    private fun profile(j: JSONObject) = UserProfile(j.optString("id"), j.optString("first_name"), j.optString("email"), j.optString("phone"), j.optString("country"), j.optString("country_code"), j.optString("city"), j.optBoolean("agency"), j.optString("agency_id"), str(j,"created_at"))
    private fun agency(j: JSONObject) = Agency(j.optString("id"), j.optString("owner_id"), j.optString("name"), j.optString("logo_url"), j.optString("address"), j.optString("email"), j.optBoolean("certified"), str(j,"certification_expires_at"), j.optString("certification_status"), j.optBoolean("extra_slots_paid"), j.optInt("extra_slots"), str(j,"created_at"))
    private fun listing(j: JSONObject) = Listing(j.optString("id"), j.optString("owner_id"), j.optString("owner_name"), j.optString("owner_phone"), j.optString("owner_country_code"), j.optString("country"), j.optString("city"), j.optString("mode"), j.optString("property_type"), arr(j,"photo_urls"), j.optLong("price"), j.optLong("deposit_months"), j.optString("description"), j.optString("relationship"), str(j,"created_at"), str(j,"expires_at"), j.optBoolean("active",true), j.optLong("views"), j.optString("agency_id"), j.optString("owner_agency_name"), j.optBoolean("agency_certified"))
    private fun chat(j: JSONObject) = ChatSummary(j.optString("id"), j.optString("listing_id"), arr(j,"participants"), j.optString("last_text"), str(j,"updated_at"))
    private fun message(j: JSONObject) = ChatMessage(j.optString("id"), j.optString("sender_id"), j.optString("text"), str(j,"created_at"))
}

package com.immolink.appname

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    suspend fun getProfile(uid: String): UserProfile {
        val d = db.collection("users").document(uid).get().await()
        if (!d.exists()) throw IllegalStateException("Profil introuvable")
        return d.toObject(UserProfile::class.java)?.copy(uid = uid) ?: throw IllegalStateException("Profil invalide")
    }

    suspend fun getAgency(uid: String): Agency? = db.collection("agencies").document(uid).get().await().toObject(Agency::class.java)

    suspend fun uploadUris(uid: String, folder: String, uris: List<Uri>, max: Int): List<String> {
        require(uris.size <= max)
        return uris.take(max).map { uri ->
            val ref = storage.reference.child("$folder/$uid/${UUID.randomUUID()}.jpg")
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        }
    }

    suspend fun publishListing(data: Map<String, Any?>): Map<String, Any?> = functions.getHttpsCallable("publishListing").call(data).await().data as Map<String, Any?>
    suspend fun relist(id: String) = functions.getHttpsCallable("relistListing").call(mapOf("listingId" to id)).await()
    suspend fun deleteListing(id: String) = functions.getHttpsCallable("deleteListing").call(mapOf("listingId" to id)).await()
    suspend fun createAgency(data: Map<String, Any?>) = functions.getHttpsCallable("createAgency").call(data).await()
    suspend fun createPayment(kind: String): String {
        val r = functions.getHttpsCallable("createPaymentLink").call(mapOf("kind" to kind)).await().data as Map<*, *>
        return r["paymentUrl"] as String
    }
    suspend fun deleteAccount() = functions.getHttpsCallable("deleteAccount").call().await()
    suspend fun submitCertification(data: Map<String, Any?>) = functions.getHttpsCallable("submitAgencyCertification").call(data).await()
    suspend fun reportListing(listingId: String, reason: String) = functions.getHttpsCallable("reportListing").call(mapOf("listingId" to listingId, "reason" to reason)).await()

    suspend fun saveSearch(criteria: SearchCriteria, country: String, uid: String) {
        db.collection("savedSearches").add(mapOf("userId" to uid, "country" to country, "mode" to criteria.mode, "propertyType" to criteria.propertyType, "city" to criteria.city, "minPrice" to criteria.minPrice, "maxPrice" to criteria.maxPrice, "active" to true, "createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun toggleFavorite(uid: String, listingId: String, country: String) {
        val ref = db.collection("users").document(uid).collection("favorites").document(listingId)
        if (ref.get().await().exists()) ref.delete().await() else ref.set(mapOf("listingId" to listingId, "country" to country, "createdAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun isFavorite(uid: String, listingId: String): Boolean = db.collection("users").document(uid).collection("favorites").document(listingId).get().await().exists()

    suspend fun searchListings(uid: String, criteria: SearchCriteria): List<Listing> {
        val profile = getProfile(uid)
        // Keep the Firestore query deliberately simple. Optional filters are applied locally,
        // which avoids fragile composite-index combinations and keeps country isolation explicit.
        val base = db.collection("listings")
            .whereEqualTo("country", profile.country)
            .whereEqualTo("active", true)
            .whereEqualTo("mode", criteria.mode)
            .get().await()
        return base.documents.mapNotNull { it.toObject(Listing::class.java)?.copy(id = it.id) }
            .filter { criteria.city.isBlank() || it.city == criteria.city }
            .filter { criteria.propertyType.isBlank() || it.propertyType == criteria.propertyType }
            .filter { criteria.minPrice <= 0 || it.price >= criteria.minPrice }
            .filter { criteria.maxPrice == null || it.price <= criteria.maxPrice!! }
            .sortedByDescending { it.createdAt?.seconds ?: 0 }
    }

    suspend fun getFeatured(uid: String, mode: String): List<Listing> = searchListings(uid, SearchCriteria(mode = mode)).take(30)
    suspend fun getMyListings(uid: String): List<Listing> = db.collection("listings").whereEqualTo("ownerId", uid).get().await().documents.mapNotNull { it.toObject(Listing::class.java)?.copy(id = it.id) }.sortedByDescending { it.createdAt?.seconds ?: 0 }
    suspend fun getListing(id: String): Listing? = db.collection("listings").document(id).get().await().toObject(Listing::class.java)?.copy(id = id)
    suspend fun incrementViews(id: String) { db.collection("listings").document(id).update("views", FieldValue.increment(1)).await() }
    suspend fun getOwner(uid: String): UserProfile {
        val d = db.collection("publicProfiles").document(uid).get().await()
        return d.toObject(UserProfile::class.java) ?: UserProfile(uid = uid)
    }
    suspend fun getOwnerAgency(uid: String): Agency? = getAgency(uid)

    suspend fun getChats(uid: String): List<ChatSummary> = db.collection("chats").whereArrayContains("participants", uid).get().await().documents.mapNotNull { it.toObject(ChatSummary::class.java)?.copy(id = it.id) }.sortedByDescending { it.updatedAt?.seconds ?: 0 }
    suspend fun getMessages(chatId: String): List<ChatMessage> = db.collection("chats").document(chatId).collection("messages").orderBy("createdAt").get().await().documents.mapNotNull { it.toObject(ChatMessage::class.java)?.copy(id = it.id) }
    suspend fun sendMessage(chatId: String, listingId: String, country: String, participants: List<String>, senderId: String, text: String) {
        val chat = db.collection("chats").document(chatId)
        chat.set(mapOf("participants" to participants, "listingId" to listingId, "country" to country, "lastText" to text, "updatedAt" to FieldValue.serverTimestamp()), com.google.firebase.firestore.SetOptions.merge()).await()
        chat.collection("messages").add(mapOf("senderId" to senderId, "text" to text, "createdAt" to FieldValue.serverTimestamp())).await()
    }
}

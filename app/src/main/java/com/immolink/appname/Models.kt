package com.immolink.appname

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserProfile(
    val uid: String = "", val firstName: String = "", val email: String = "", val phone: String = "", val country: String = "",
    val countryCode: String = "", val city: String = "", val agency: Boolean = false, val agencyId: String = "",
    val createdAt: String? = null
)

data class Agency(
    val id: String = "", val ownerId: String = "", val name: String = "", val logoUrl: String = "", val address: String = "", val email: String = "",
    val certified: Boolean = false, val certificationExpiresAt: String? = null, val certificationStatus: String = "none",
    val extraSlotsPaid: Boolean = false, val extraSlots: Int = 0, val createdAt: String? = null
) {
    fun quota(): Int = if (extraSlotsPaid) 25 else 15
    fun badgeActive(): Boolean {
        if (!certified || certificationExpiresAt.isNullOrBlank()) return false
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US)
            (fmt.parse(certificationExpiresAt)?.time ?: 0L) > System.currentTimeMillis()
        }.getOrDefault(false)
    }
}

data class Listing(
    val id: String = "", val ownerId: String = "", val ownerName: String = "", val ownerPhone: String = "", val ownerCountryCode: String = "",
    val country: String = "", val city: String = "", val mode: String = "", val propertyType: String = "", val photoUrls: List<String> = emptyList(),
    val price: Long = 0, val depositMonths: Long = 0, val description: String = "", val relationship: String = "", val createdAt: String? = null,
    val expiresAt: String? = null, val active: Boolean = true, val views: Long = 0, val agencyId: String = "", val ownerAgencyName: String = "", val agencyCertified: Boolean = false
)

data class SearchCriteria(
    val mode: String = "rent", val propertyType: String = "", val city: String = "", val minPrice: Long = 0, val maxPrice: Long? = null
)

data class BudgetRange(val label: String, val min: Long = 0, val max: Long? = null)
data class ChatMessage(val id: String = "", val senderId: String = "", val text: String = "", val createdAt: String? = null)
data class ChatSummary(val id: String = "", val listingId: String = "", val participants: List<String> = emptyList(), val lastText: String = "", val updatedAt: String? = null)

package com.immolink.appname

import com.google.firebase.Timestamp

data class UserProfile(
    val uid: String = "", val firstName: String = "", val phone: String = "", val country: String = "",
    val countryCode: String = "", val city: String = "", val agency: Boolean = false, val agencyId: String = "",
    val fcmToken: String = "", val createdAt: Timestamp? = null
)

data class Agency(
    val ownerId: String = "", val name: String = "", val logoUrl: String = "", val address: String = "", val email: String = "",
    val certified: Boolean = false, val certificationExpiresAt: Timestamp? = null, val certificationStatus: String = "none",
    val extraSlotsPaid: Boolean = false, val extraSlots: Int = 0, val createdAt: Timestamp? = null
) {
    fun quota(): Int = if (extraSlotsPaid) 25 else 15
    fun badgeActive(now: Timestamp = Timestamp.now()): Boolean = certified && certificationExpiresAt != null && certificationExpiresAt > now
}

data class Listing(
    val id: String = "", val ownerId: String = "", val ownerName: String = "", val ownerPhone: String = "", val ownerCountryCode: String = "",
    val country: String = "", val city: String = "", val mode: String = "", val propertyType: String = "", val photoUrls: List<String> = emptyList(),
    val price: Long = 0, val depositMonths: Long = 0, val description: String = "", val relationship: String = "", val createdAt: Timestamp? = null,
    val expiresAt: Timestamp? = null, val active: Boolean = true, val views: Long = 0, val agencyId: String = "", val ownerAgencyName: String = "", val agencyCertified: Boolean = false
)

data class SearchCriteria(
    val mode: String = "rent", val propertyType: String = "", val city: String = "", val minPrice: Long = 0, val maxPrice: Long? = null
)

data class BudgetRange(val label: String, val min: Long = 0, val max: Long? = null)

data class ChatMessage(val id: String = "", val senderId: String = "", val text: String = "", val createdAt: Timestamp? = null)

data class ChatSummary(val id: String = "", val listingId: String = "", val participants: List<String> = emptyList(), val lastText: String = "", val updatedAt: Timestamp? = null)

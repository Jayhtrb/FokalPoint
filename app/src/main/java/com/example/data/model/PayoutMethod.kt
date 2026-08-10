package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payout_methods")
data class PayoutMethod(
    @PrimaryKey val id: String,
    val userId: String,
    val type: PayoutMethodType,
    val accountHolderName: String,
    val accountNumber: String?,
    val bankName: String?,
    val ifscCode: String?,
    val upiId: String?,
    val isDefault: Boolean,
    val status: PayoutMethodStatus,
    val createdAt: String
)

enum class PayoutMethodType {
    BANK_ACCOUNT,
    UPI
}

enum class PayoutMethodStatus {
    ACTIVE,
    PENDING_VERIFICATION,
    REJECTED
}

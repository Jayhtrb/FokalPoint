package com.example.data.database

import androidx.room.TypeConverter
import com.example.data.model.PayoutMethodStatus
import com.example.data.model.PayoutMethodType

class Converters {
    @TypeConverter
    fun fromPayoutMethodType(value: PayoutMethodType): String {
        return value.name
    }

    @TypeConverter
    fun toPayoutMethodType(value: String): PayoutMethodType {
        return PayoutMethodType.valueOf(value)
    }

    @TypeConverter
    fun fromPayoutMethodStatus(value: PayoutMethodStatus): String {
        return value.name
    }

    @TypeConverter
    fun toPayoutMethodStatus(value: String): PayoutMethodStatus {
        return PayoutMethodStatus.valueOf(value)
    }
}

package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.dao.*
import com.example.data.model.*

@Database(
    entities = [
        User::class,
        Creator::class,
        Portfolio::class,
        Booking::class,
        Review::class,
        Message::class,
        Favorite::class,
        ClientLead::class,
        PayoutMethod::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun creatorDao(): CreatorDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun bookingDao(): BookingDao
    abstract fun reviewDao(): ReviewDao
    abstract fun messageDao(): MessageDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun clientLeadDao(): ClientLeadDao
    abstract fun payoutMethodDao(): PayoutMethodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fokalpoint_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.fokalpoint.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fokalpoint.app.data.dao.*
import com.fokalpoint.app.data.model.*

/**
 * On-device cache. With a live backend the server is the source of truth, so schema
 * changes can safely rebuild the cache; in demo mode it reseeds the sample catalogue.
 */
@Database(
    entities = [
        User::class,
        Creator::class,
        Portfolio::class,
        Booking::class,
        Review::class,
        Message::class,
        Favorite::class,
        ClientLead::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun creatorDao(): CreatorDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun bookingDao(): BookingDao
    abstract fun reviewDao(): ReviewDao
    abstract fun messageDao(): MessageDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun clientLeadDao(): ClientLeadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fokalpoint_cache")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

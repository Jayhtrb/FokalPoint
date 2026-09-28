package com.example.data.supabase

import android.content.Context
import com.example.data.repository.SessionManager

/** App-wide Supabase entry point: `SupabaseClient.get(context).auth` / `.rest`. */
class SupabaseClient private constructor(context: Context) {
    val sessions = SessionManager(context)
    val auth = SupabaseAuth(sessions)
    val rest = SupabaseRest(auth)

    companion object {
        @Volatile private var instance: SupabaseClient? = null

        fun get(context: Context): SupabaseClient =
            instance ?: synchronized(this) {
                instance ?: SupabaseClient(context.applicationContext).also { instance = it }
            }
    }
}

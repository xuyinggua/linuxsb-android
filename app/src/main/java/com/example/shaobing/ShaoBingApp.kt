package com.example.shaobing

import android.app.Application
import android.content.Context
import com.example.shaobing.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ShaoBingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set
        val applicationScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
        val db: AppDatabase by lazy { AppDatabase.get(appContext) }
    }
}

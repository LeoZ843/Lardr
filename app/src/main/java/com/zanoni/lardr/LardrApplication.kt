package com.zanoni.lardr

import android.app.Application
import com.zanoni.lardr.data.local.PendingWritesManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LardrApplication : Application() {

    @Inject
    lateinit var pendingWritesManager: PendingWritesManager

    override fun onCreate() {
        super.onCreate()
        pendingWritesManager.replayPendingWrites()
    }
}
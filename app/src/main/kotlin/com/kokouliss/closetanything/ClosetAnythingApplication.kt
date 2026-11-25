package com.kokouliss.closetanything

import android.app.Application
import com.kokouliss.closetanything.feature.auth.restoreNightMode
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ClosetAnythingApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Before any screen exists, so the first one already has the chosen light or dark look.
        restoreNightMode(this)
    }
}

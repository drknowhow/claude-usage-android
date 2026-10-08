package com.yepgent.usage

import android.app.Application
import com.yepgent.usage.data.Store
import com.yepgent.usage.surfaces.Surfaces
import com.yepgent.usage.work.Scheduler

class UsageApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Surfaces.createChannels(this)
        Scheduler.schedule(this, Store(this).intervalMinutes)
    }
}

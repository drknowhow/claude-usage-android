package com.yepgent.usage.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yepgent.usage.data.UsageRepository
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UsageRepository.refresh(applicationContext, manual = inputData.getBoolean(KEY_MANUAL, false))
        return Result.success()
    }

    companion object {
        const val KEY_MANUAL = "manual"
    }
}

/** The shade card's refresh button. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Scheduler.refreshNow(context)
}

/** Boot and app updates drop the status notification; repost it instead of waiting for the next poll. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Scheduler.refreshNow(context, manual = false)
        }
    }
}

object Scheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Replaces the periodic poll, e.g. after the interval setting changes. */
    fun schedule(context: Context, intervalMinutes: Int, replace: Boolean = false) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "refresh",
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun refreshNow(context: Context, manual: Boolean = true) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(workDataOf(RefreshWorker.KEY_MANUAL to manual))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("refresh-now", ExistingWorkPolicy.REPLACE, request)
    }
}

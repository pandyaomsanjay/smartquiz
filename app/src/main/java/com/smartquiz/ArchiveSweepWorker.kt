package com.smartquiz

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ArchiveSweepWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ExpiredQuizArchiver.sweep(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "archive_sweep"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ArchiveSweepWorker>(
                6, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
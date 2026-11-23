package app.pwhs.blockads.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pwhs.blockads.data.datastore.AppPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object FilterUpdateScheduler {

    suspend fun scheduleFilterUpdate(context: Context, appPreferences: AppPreferences) {
        val enabled = appPreferences.autoUpdateEnabled.first()

        if (!enabled) {
            cancelFilterUpdate(context)
            return
        }

        val frequency = appPreferences.autoUpdateFrequency.first()

        // Don't schedule if set to manual
        if (frequency == AppPreferences.UPDATE_FREQUENCY_MANUAL) {
            cancelFilterUpdate(context)
            return
        }

        val intervalHours = when (frequency) {
            AppPreferences.UPDATE_FREQUENCY_6H -> 6L
            AppPreferences.UPDATE_FREQUENCY_12H -> 12L
            AppPreferences.UPDATE_FREQUENCY_24H -> 24L
            AppPreferences.UPDATE_FREQUENCY_48H -> 48L
            else -> 24L
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Flex = period / 4: the update stays once-per-cycle but becomes
        // eligible across the whole last quarter of the period instead of a
        // single instant, so Doze maintenance windows and intermittent
        // battery-saver lifts can serve it. Without flex, a missed pickup
        // instant slips the run a full period. Flex is expressed against
        // TimeUnit.MINUTES so the 6h preset gets a 90-minute window rather
        // than an hour-on-the-floor from integer hour division.
        val periodMinutes = intervalHours * 60
        val workRequest = PeriodicWorkRequestBuilder<FilterUpdateWorker>(
            periodMinutes,
            TimeUnit.MINUTES,
            periodMinutes / 4,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            FilterUpdateWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun cancelFilterUpdate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(FilterUpdateWorker.WORK_NAME)
    }
}

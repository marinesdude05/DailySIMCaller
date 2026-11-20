package com.example.dailysimcaller

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Quiet, opt-out preparation after boot. Android determines when the job runs. */
object BootPreparation {
    const val RESULT = "last_boot_preparation"
    private const val ENABLED = "background_boot_preparation_enabled"
    private const val JOB_ID = 31706

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).apply()
        if (enabled) schedule(context) else
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, BootPreparationService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        val scheduled = context.getSystemService(JobScheduler::class.java).schedule(job)
        val prefs = context.getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
        if (scheduled == JobScheduler.RESULT_FAILURE) {
            prefs.edit().putString(RESULT, "Android could not schedule background preparation").apply()
        } else if (prefs.getString(RESULT, "Not yet run") == "Not yet run") {
            prefs.edit().putString(RESULT, "Scheduled; waiting for Android and network").apply()
        }
    }
}

/** Fetches only the authorized CSV. Does NOT call numbers or start a phone-call service. */
class BootPreparationService : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (!BootPreparation.isEnabled(this)) return false
        running = CoroutineScope(Dispatchers.IO).launch {
            val prefs = getSharedPreferences(DailyAlarm.PREFS, Context.MODE_PRIVATE)
            val now = SimpleDateFormat("MMM d, h:mm a", Locale.US).format(Date())
            val result = try {
                val endpoint = getString(R.string.csv_endpoint).trim()
                if (endpoint.isBlank()) {
                    "Boot preparation at $now: no central CSV configured"
                } else {
                    val fetched = RemoteCsv.fetch(endpoint)
                    prefs.edit()
                        .putString("numbers", fetched.numbers.joinToString("\n"))
                        .putString("last_csv_refresh", now)
                        .putString("last_csv_revision", fetched.sha256Prefix)
                        .apply()
                    "Prepared ${fetched.numbers.size} numbers at $now; no calls placed"
                }
            } catch (_: CancellationException) {
                return@launch  // Android paused this job; onStopJob requests rescheduling.
            } catch (e: Exception) {
                "Background refresh failed at $now: ${e.message ?: "unknown error"}"
            }
            prefs.edit().putString(BootPreparation.RESULT, result).apply()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true  // Android may retry the preparation under better conditions.
    }
}

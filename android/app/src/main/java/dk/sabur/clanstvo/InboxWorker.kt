package dk.sabur.clanstvo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Pozadinska provjera novih obavijesti (podsjetnik za članarinu) — bez Firebase-a.
 * Pokreće se svaka 3 sata (kad ima interneta), pri otvaranju aplikacije i odmah nakon "Probna obavijest".
 */
class InboxWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val token = Prefs.token(applicationContext)
        if (token.isEmpty()) return@withContext Result.success()
        try {
            val r = Api.inbox(token, Prefs.lastId(applicationContext))
            when (r.code) {
                200 -> {
                    r.messages.forEach { Notifier.show(applicationContext, it) }
                    Prefs.setLastId(applicationContext, r.lastId)
                    Result.success()
                }
                401 -> { Prefs.clear(applicationContext); Result.success() }   // odjavljen / obrisan
                else -> Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(c: Context) {
            val req = PeriodicWorkRequestBuilder<InboxWorker>(3, TimeUnit.HOURS).setConstraints(net).build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("sabur-inbox", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(c: Context) {
            val req = OneTimeWorkRequestBuilder<InboxWorker>().setConstraints(net).setInitialDelay(3, TimeUnit.SECONDS).build()
            WorkManager.getInstance(c).enqueueUniqueWork("sabur-inbox-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}

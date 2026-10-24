package dk.sabur.clanstvo

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationManagerCompat

/**
 * JavaScript most: window.SaburApp u stranici (koristi ga assets/push.js na serveru).
 * Stranica se učitava samo s dzemat.sabur.dk/clanstvo (ostalo se otvara izvan aplikacije).
 */
class Bridge(private val act: MainActivity) {

    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME
    @JavascriptInterface fun deviceId(): String = Prefs.deviceId(act)
    @JavascriptInterface fun model(): String = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
    @JavascriptInterface fun getMember(): String = Prefs.memberId(act)

    /** Server je izdao token za ovog člana → od sada se u pozadini preuzimaju njegove obavijesti */
    @JavascriptInterface fun setToken(token: String, memberId: String, lastId: String) {
        Prefs.save(act, token, memberId, lastId.toLongOrNull() ?: 0L)
        Fcm.sync(act)
        InboxWorker.runNow(act)
    }

    /** Odjava člana → više nema obavijesti na ovom uređaju */
    @JavascriptInterface fun logout() {
        val tok = Prefs.token(act)
        Prefs.clear(act)
        if (tok.isNotEmpty()) Thread { Api.unregister(tok) }.start()
    }

    @JavascriptInterface fun checkNow() = InboxWorker.runNow(act)

    @JavascriptInterface fun notificationsEnabled(): Boolean = NotificationManagerCompat.from(act).areNotificationsEnabled()

    @JavascriptInterface fun openNotificationSettings() {
        act.runOnUiThread {
            val i = if (Build.VERSION.SDK_INT >= 26)
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, act.packageName)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + act.packageName))
            try { act.startActivity(i) } catch (e: Exception) { }
        }
    }

    @JavascriptInterface fun print() = act.runOnUiThread { act.printPage() }

    @JavascriptInterface fun checkUpdate() = act.runOnUiThread { Updater.check(act, silent = false) }
}

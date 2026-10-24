package dk.sabur.clanstvo

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Automatska nadogradnja s GitHub Releases (isto kao "Moje Kamere"):
 * version.json = {"versionCode":123,"versionName":"2.0.23","apk":"https://…/sabur-clanstvo.apk","notes":"…"}
 */
object Updater {

    fun check(act: MainActivity, silent: Boolean) {
        val now = System.currentTimeMillis()
        if (silent && now - Prefs.lastUpdateCheck(act) < 3600_000L) return   // najviše jednom na sat
        Prefs.setLastUpdateCheck(act, now)
        Thread {
            try {
                val c = URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = true
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                c.disconnect()
                val code = j.optInt("versionCode", 0)
                val name = j.optString("versionName", "")
                val apk = j.optString("apk", "")
                val notes = j.optString("notes", "")
                act.runOnUiThread {
                    if (code > BuildConfig.VERSION_CODE && apk.startsWith("https://")) ask(act, name, apk, notes)
                    else if (!silent) Toast.makeText(act, R.string.update_none, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!silent) act.runOnUiThread { Toast.makeText(act, R.string.update_failed, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun ask(act: MainActivity, name: String, apk: String, notes: String) {
        if (act.isFinishing) return
        AlertDialog.Builder(act)
            .setTitle(R.string.update_title)
            .setMessage(act.getString(R.string.update_text, name) + if (notes.isNotBlank()) "\n\n$notes" else "")
            .setPositiveButton(R.string.update_yes) { _, _ -> download(act, apk) }
            .setNegativeButton(R.string.update_no, null)
            .show()
    }

    private fun download(act: MainActivity, apk: String) {
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(act, R.string.install_permission, Toast.LENGTH_LONG).show()
            try { act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName))) } catch (e: Exception) { }
            return
        }
        Toast.makeText(act, R.string.update_downloading, Toast.LENGTH_LONG).show()
        Thread {
            try {
                val dir = File(act.cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, "sabur-clanstvo.apk")
                var url = URL(apk)
                var c = openNoRedirect(url)
                var hops = 0
                while (c.responseCode in 300..399 && hops < 5) {      // GitHub preusmjerava na drugi host
                    url = URL(url, c.getHeaderField("Location"))
                    c.disconnect()
                    c = openNoRedirect(url)
                    hops++
                }
                c.inputStream.use { inp -> f.outputStream().use { inp.copyTo(it) } }
                c.disconnect()
                act.runOnUiThread { install(act, f) }
            } catch (e: Exception) {
                act.runOnUiThread { Toast.makeText(act, R.string.update_failed, Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun openNoRedirect(u: URL): HttpURLConnection =
        (u.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 20000; readTimeout = 60000 }

    private fun install(act: MainActivity, f: File) {
        val uri = FileProvider.getUriForFile(act, act.packageName + ".fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { act.startActivity(i) } catch (e: Exception) { Toast.makeText(act, R.string.update_failed, Toast.LENGTH_LONG).show() }
    }
}

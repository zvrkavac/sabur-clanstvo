package dk.sabur.clanstvo

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging: server (push_lib.php → fcm_send) šalje poruku, Google je dostavi
 * odmah — i kad je aplikacija ugašena. Ako Firebase nije podešen, radi stara provjera (InboxWorker).
 */
class SaburMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Fcm.send(applicationContext, token)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        val d = msg.data
        val id = d["id"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)
        if (d["id"] != null && id <= Prefs.lastId(applicationContext)) return   // već prikazano (preuzeto iz sanduča)
        val title = d["title"] ?: msg.notification?.title ?: return
        val body = d["body"] ?: msg.notification?.body ?: ""
        Notifier.show(applicationContext, Api.Msg(id, title, body, d["url"] ?: "member_area.php"))
        // ista poruka stiže i preko sanduča (InboxWorker) — ne prikazuj je dvaput
        if (id > Prefs.lastId(applicationContext)) Prefs.setLastId(applicationContext, id)
    }
}

object Fcm {
    /** Pošalji FCM token serveru (samo kad je član povezan s uređajem) */
    fun sync(c: Context) {
        if (Prefs.token(c).isEmpty()) return
        try {
            if (FirebaseApp.getApps(c).isEmpty()) return               // Firebase nije podešen (nema google-services.json)
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t -> send(c, t) }
        } catch (e: Exception) { }
    }

    fun send(c: Context, fcmToken: String) {
        val dev = Prefs.token(c)
        if (dev.isEmpty() || fcmToken.isEmpty()) return
        Thread { Api.setFcm(dev, fcmToken) }.start()
    }
}

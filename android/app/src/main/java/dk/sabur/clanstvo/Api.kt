package dk.sabur.clanstvo

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Pozivi prema app_api.php (bez kolačića — samo token uređaja) */
object Api {
    class Msg(val id: Long, val title: String, val body: String, val url: String)
    class Inbox(val code: Int, val lastId: Long, val messages: List<Msg>)

    private fun open(url: String, token: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SaburApp/${BuildConfig.VERSION_NAME} (Android)")
        }

    fun inbox(token: String, since: Long): Inbox {
        // token i u URL-u: neki serveri (PHP-FPM) ne prosljeđuju Authorization header
        val c = open(BuildConfig.BASE_URL + "app_api.php?act=inbox&since=$since&token=$token", token)
        try {
            val code = c.responseCode
            if (code != 200) return Inbox(code, since, emptyList())
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val arr = j.optJSONArray("messages")
            val list = mutableListOf<Msg>()
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Msg(o.optLong("id"), o.optString("title"), o.optString("body"), o.optString("url", "member_area.php")))
            }
            return Inbox(200, j.optLong("last_id", since), list)
        } finally { c.disconnect() }
    }

    /** FCM token uređaja → server (za trenutne obavijesti) */
    fun setFcm(token: String, fcm: String) {
        try {
            val c = open(BuildConfig.BASE_URL + "app_api.php?act=fcm&token=$token", token, "POST")
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(("fcm=" + java.net.URLEncoder.encode(fcm, "UTF-8")).toByteArray()) }
            c.responseCode
            c.disconnect()
        } catch (e: Exception) { }
    }

    fun unregister(token: String) {
        try {
            val c = open(BuildConfig.BASE_URL + "app_api.php?act=unregister&token=$token", token, "POST")
            c.doOutput = true
            c.outputStream.use { it.write(ByteArray(0)) }
            c.responseCode
            c.disconnect()
        } catch (e: Exception) { }
    }
}

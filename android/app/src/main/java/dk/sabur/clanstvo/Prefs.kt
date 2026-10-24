package dk.sabur.clanstvo

import android.content.Context
import java.util.UUID

/** Lokalno pamćenje: ID uređaja, token člana, zadnja preuzeta poruka */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("sabur", Context.MODE_PRIVATE)

    fun deviceId(c: Context): String {
        var id = sp(c).getString("device_id", null)
        if (id == null) { id = UUID.randomUUID().toString(); sp(c).edit().putString("device_id", id).apply() }
        return id
    }
    fun token(c: Context): String = sp(c).getString("token", "") ?: ""
    fun memberId(c: Context): String = sp(c).getString("member_id", "") ?: ""
    fun lastId(c: Context): Long = sp(c).getLong("last_id", 0L)
    fun setLastId(c: Context, v: Long) = sp(c).edit().putLong("last_id", v).apply()

    fun save(c: Context, token: String, memberId: String, lastId: Long) {
        sp(c).edit().putString("token", token).putString("member_id", memberId).putLong("last_id", lastId).apply()
    }
    fun clear(c: Context) = sp(c).edit().remove("token").remove("member_id").remove("last_id").apply()

    fun lastUpdateCheck(c: Context): Long = sp(c).getLong("upd_check", 0L)
    fun setLastUpdateCheck(c: Context, t: Long) = sp(c).edit().putLong("upd_check", t).apply()
}

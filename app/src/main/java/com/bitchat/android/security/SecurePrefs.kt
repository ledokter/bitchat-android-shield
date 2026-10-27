package com.bitchat.android.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * SharedPreferences encrypted with a key held by Android Keystore, so a copy of the app's data
 * folder (forensic extraction, backup) reveals neither the names nor the values. The plaintext file
 * of earlier versions is migrated once, then deleted. If the Keystore is broken on a device, the
 * encrypted file is recreated; as a last resort the plain file is used so the app still opens.
 */
object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private const val SUFFIX = "_enc"
    private val cache = HashMap<String, SharedPreferences>()

    @Synchronized
    fun open(context: Context, name: String): SharedPreferences {
        cache[name]?.let { return it }
        val app = context.applicationContext
        val prefs = runCatching { create(app, name) }
            .recoverCatching {
                Log.w(TAG, "Encrypted prefs unreadable, recreating: ${it.message}")
                app.deleteSharedPreferences(name + SUFFIX)
                create(app, name)
            }
            .getOrElse {
                Log.e(TAG, "Encrypted prefs unavailable, using plain storage: ${it.message}")
                return app.getSharedPreferences(name, Context.MODE_PRIVATE)
            }
        migrate(app, name, prefs)
        cache[name] = prefs
        return prefs
    }

    private fun create(context: Context, name: String): SharedPreferences {
        val key = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            name + SUFFIX,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrate(context: Context, name: String, target: SharedPreferences) {
        val plainFile = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
        if (!plainFile.exists()) return
        val plain = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = target.edit()
        @Suppress("UNCHECKED_CAST")
        plain.all.forEach { (k, v) ->
            when (v) {
                is String -> editor.putString(k, v)
                is Int -> editor.putInt(k, v)
                is Long -> editor.putLong(k, v)
                is Float -> editor.putFloat(k, v)
                is Boolean -> editor.putBoolean(k, v)
                is Set<*> -> editor.putStringSet(k, v as Set<String>)
            }
        }
        if (editor.commit()) {
            plain.edit().clear().commit()
            context.deleteSharedPreferences(name)
        }
    }
}

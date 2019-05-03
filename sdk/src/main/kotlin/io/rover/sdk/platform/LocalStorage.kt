package io.rover.sdk.platform

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences

/**
 * Very simple hash-like storage of keys and values.
 */
interface KeyValueStorage {
    /**
     * Get the current value of the given key, or null if unset.
     */
    operator fun get(key: String): String?

    /**
     * Set the value of the given key.  If [value] is null, unsets the key.
     */
    operator fun set(key: String, value: String?)

    /**
     * Clear and remove a given key.
     */
    fun unset(key: String)

    val keys: Set<String>
}

/**
 *  Obtain a persistent key-value named persistent storage area using Android's [SharedPreferences].
 */
open class LocalStorage(
    private val androidContext: Context,
    private val baseContextName: String = "io.rover.localstorage"
)  {
    open fun getKeyValueStorageFor(namedContext: String): KeyValueStorage {
        val prefs = androidContext.getSharedPreferences("$baseContextName.$namedContext", MODE_PRIVATE)
        return object : KeyValueStorage {
            override fun get(key: String): String? = prefs.getString(key, null)

            override fun set(key: String, value: String?) {
                prefs.edit().putString(key, value).apply()
            }

            override fun unset(key: String) {
                prefs.edit().remove(key).apply()
            }

            override val keys: Set<String>
                get() = prefs.all.keys
                    .toSet()
        }
    }
}
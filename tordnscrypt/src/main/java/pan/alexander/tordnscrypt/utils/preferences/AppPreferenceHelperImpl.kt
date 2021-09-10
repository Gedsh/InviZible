package pan.alexander.tordnscrypt.utils.preferences
/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.APP_PREFERENCES_NAME
import pan.alexander.tordnscrypt.domain.preferences.PreferenceType
import java.lang.UnsupportedOperationException
import java.util.concurrent.ConcurrentSkipListSet
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val COROUTINE_NAME = "Preferences coroutine"
private const val CHANNEL_BUFFER_CAPACITY = 10

@Singleton
class AppPreferenceHelperImpl @Inject constructor(
    @Named(APP_PREFERENCES_NAME)
    private val appSharedPreferences: SharedPreferences,
    @Named(CoroutinesModule.SUPERVISOR_JOB_MAIN_DISPATCHER_SCOPE)
    private val _coroutineScope: CoroutineScope
) : AppPreferenceHelper {

    private val coroutineScope = _coroutineScope + CoroutineName(COROUTINE_NAME)
    private val editor = appSharedPreferences.edit()
    private val keysToSaveSet by lazy { ConcurrentSkipListSet<String>() }

    @ExperimentalCoroutinesApi
    @ObsoleteCoroutinesApi
    private val channel by lazy {
        coroutineScope.actor<Pair<String, Any>>(capacity = CHANNEL_BUFFER_CAPACITY) {

            for (message in channel) {
                val key = message.first
                keysToSaveSet.add(key)
                when (val value = message.second) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, null).putString(key, value)
                    is Set<*> -> {
                        if (value.all { it is String }) {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(key, null).putStringSet(key, value as Set<String>)
                        } else {
                            throw UnsupportedOperationException("AppPreferenceHelper Only String Set is allowed")
                        }
                    }
                    else -> throw UnsupportedOperationException(
                        "AppPreferenceHelper Type ${value.javaClass.canonicalName} is not implemented"
                    )
                }
                if (channel.isEmpty) {
                    editor.apply()
                    keysToSaveSet.clear()
                }
            }
        }
    }

    @ObsoleteCoroutinesApi
    @ExperimentalCoroutinesApi
    override fun setPreference(key: String, value: Any) = coroutineScope.launch {
        channel.send(Pair(key, value))
    }

    override fun getPreference(@PreferenceType type: Int, key: String): Any {

        if (keysToSaveSet.contains(key)) {
            editor.apply()
            keysToSaveSet.clear()
        }

        return when (type) {
            PreferenceType.BOOL_PREFERENCE -> appSharedPreferences.getBoolean(key, false)
            PreferenceType.INT_PREFERENCE -> appSharedPreferences.getInt(key, 0)
            PreferenceType.FLOAT_PREFERENCE -> appSharedPreferences.getFloat(key, 0f)
            PreferenceType.STRING_PREFERENCE -> appSharedPreferences.getString(key, "") as Any
            PreferenceType.STRING_SET_PREFERENCE -> appSharedPreferences.getStringSet(
                key,
                emptySet()
            ) as Any
            else -> throw UnsupportedOperationException(
                "AppPreferenceHelper Preference Type $type is not implemented"
            )
        }
    }
}

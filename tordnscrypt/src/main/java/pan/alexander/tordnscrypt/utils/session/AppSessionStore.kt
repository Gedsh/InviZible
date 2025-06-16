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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.session

import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.emptyMap

@Singleton
class AppSessionStore @Inject constructor() {
    private val keyToValue = ConcurrentHashMap<String, Any?>()

    fun <T> save(key: String, value: T?) {
        keyToValue[key] = value
    }

    fun <T> save(key: String, value: MutableSet<T>) {
        keyToValue[key] = value
    }

    fun <T, V> save(key: String, value: HashMap<T, V>) {
        keyToValue[key] = value
    }

    fun <T> restore(key: String): T? = try {
        keyToValue[key] as? T
    } catch (e: Exception) {
        loge("AppSessionStore restore", e)
        null
    }

    fun <T> restoreSet(key: String): Set<T> = try {
        if (keyToValue.get(key) != null) {
            keyToValue[key] as Set<T>
        } else {
            emptySet<T>()
        }
    } catch (e: Exception) {
        loge("AppSessionStore restoreSet", e)
        emptySet<T>()
    }

    fun clearSet(key: String) = try {
        (keyToValue[key] as? MutableSet<*>)?.clear()
    } catch (e: Exception) {
        loge("AppSessionStore clearSet", e)
    }

    fun <T, V> restoreMap(key: String): Map<T, V> = try {
        if (keyToValue.get(key) != null) {
            keyToValue[key] as Map<T, V>
        } else {
            emptyMap<T, V>()
        }
    } catch (e: Exception) {
        loge("AppSessionStore restoreMap", e)
        emptyMap<T, V>()
    }

    fun clearMap(key: String) = try {
        (keyToValue[key] as? MutableMap<*, *>)?.clear()
    } catch (e: Exception) {
        loge("AppSessionStore clearMap", e)
    }
}

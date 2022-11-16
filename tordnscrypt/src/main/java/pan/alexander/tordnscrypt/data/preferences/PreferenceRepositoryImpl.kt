package pan.alexander.tordnscrypt.data.preferences
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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.domain.preferences.PreferenceType.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceRepositoryImpl @Inject constructor(
    private val preferenceDataSource: PreferenceDataSource
) : PreferenceRepository {
    override fun getBoolPreference(key: String): Boolean {
        return preferenceDataSource.getPreference(BOOL_PREFERENCE, key) as Boolean
    }

    override fun setBoolPreference(key: String, value: Boolean) {
        preferenceDataSource.setPreference(key, value)
    }

    override fun getIntPreference(key: String): Int {
        return preferenceDataSource.getPreference(INT_PREFERENCE, key) as Int
    }

    override fun setIntPreference(key: String, value: Int) {
        preferenceDataSource.setPreference(key, value)
    }

    override fun getFloatPreference(key: String): Float {
        return preferenceDataSource.getPreference(FLOAT_PREFERENCE, key) as Float
    }

    override fun setFloatPreference(key: String, value: Float) {
        preferenceDataSource.setPreference(key, value)
    }

    override fun getStringPreference(key: String): String {
        return preferenceDataSource.getPreference(STRING_PREFERENCE, key) as String
    }

    override fun setStringPreference(key: String, value: String) {
        preferenceDataSource.setPreference(key, value)
    }

    @Synchronized
    override fun getStringSetPreference(key: String): HashSet<String> {
        return HashSet(
            preferenceDataSource.getPreference(STRING_SET_PREFERENCE, key) as Set<String>
        )
    }

    override fun setStringSetPreference(key: String, value: Set<String>) {
        preferenceDataSource.setPreference(key, value)
    }
}

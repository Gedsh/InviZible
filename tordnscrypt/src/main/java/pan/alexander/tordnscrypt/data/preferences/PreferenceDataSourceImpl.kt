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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.data.preferences

import pan.alexander.tordnscrypt.domain.preferences.PreferenceType
import pan.alexander.tordnscrypt.utils.preferences.AppPreferenceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceDataSourceImpl @Inject constructor(
    private val appPreferenceHelper: AppPreferenceHelper
) : PreferenceDataSource {
    override fun getPreference(@PreferenceType type: Int, key: String): Any {
        return appPreferenceHelper.getPreference(type, key)
    }

    override fun setPreference(key: String, value: Any) {
        appPreferenceHelper.setPreference(key, value)
    }

}

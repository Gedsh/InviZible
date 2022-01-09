package pan.alexander.tordnscrypt.domain.preferences
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

interface PreferenceRepository {
    fun getBoolPreference(key: String): Boolean
    fun setBoolPreference(key: String, value: Boolean)
    fun getIntPreference(key: String): Int
    fun setIntPreference(key: String, value: Int)
    fun getFloatPreference(key: String): Float
    fun setFloatPreference(key: String, value: Float)
    fun getStringPreference(key: String): String
    fun setStringPreference(key: String, value: String)
    fun getStringSetPreference(key: String): HashSet<String>
    fun setStringSetPreference(key: String, value: Set<String>)
}

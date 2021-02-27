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

package pan.alexander.tordnscrypt.data

import pan.alexander.tordnscrypt.ApplicationBase
import pan.alexander.tordnscrypt.domain.CheckInternetConnectionRepository

class CheckInternetConnectionRepositoryImpl: CheckInternetConnectionRepository {
    private val applicationContext = ApplicationBase.instance?.applicationContext

    override fun checkInternetAvailable(site: String, withTor: Boolean): Boolean {
        val internetChecker = InternetChecker(site, withTor)
        return internetChecker.checkConnectionAvailability(applicationContext)
    }
}
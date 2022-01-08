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

package pan.alexander.tordnscrypt.data.connection_checker

import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerRepository
import javax.inject.Inject

class ConnectionCheckerRepositoryImpl @Inject constructor(
    private val connectionCheckerDataSource: ConnectionCheckerDataSource,
) : ConnectionCheckerRepository {

    override fun checkInternetAvailableOverHttp(
        site: String,
        proxyAddress: String,
        proxyPort: Int
    ): Boolean {
        return connectionCheckerDataSource.checkInternetAvailableOverHttp(
            site,
            proxyAddress,
            proxyPort
        )
    }

    override fun checkInternetAvailableOverSocks(
        ip: String,
        port: Int,
        proxyAddress: String,
        proxyPort: Int
    ): Boolean {
        return connectionCheckerDataSource.checkInternetAvailableOverSocks(
            ip,
            port,
            proxyAddress,
            proxyPort
        )
    }

    override fun checkNetworkAvailable(): Boolean {
        return connectionCheckerDataSource.checkNetworkAvailable()
    }
}

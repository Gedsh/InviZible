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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.data.bridges

import pan.alexander.tordnscrypt.utils.Constants.ONIONOO_SITE_ADDRESS
import pan.alexander.tordnscrypt.utils.web.HttpsConnectionManager
import javax.inject.Inject

private const val READ_TIMEOUT_SEC = 30
private const val CONNECT_TIMEOUT_SEC = 30

class DefaultVanillaBridgeDataSourceImpl @Inject constructor(
    private val httpsConnectionManager: HttpsConnectionManager
) : DefaultVanillaBridgeDataSource {

    override suspend fun getRelaysWithFingerprintAndAddress(): List<String> = httpsConnectionManager.apply {
        readTimeoutSec = READ_TIMEOUT_SEC
        connectTimeoutSec = CONNECT_TIMEOUT_SEC
    }.get(
        "${ONIONOO_SITE_ADDRESS}details",
        linkedMapOf<String, String>().apply {
            put("type", "relay")
            put("running", "true")
            put("fields", "fingerprint,or_addresses")
        }
    )

}

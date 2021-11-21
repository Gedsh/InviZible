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

package pan.alexander.tordnscrypt.data.log_reader

import android.content.Context
import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository
import pan.alexander.tordnscrypt.settings.PathVars
import javax.inject.Inject

class ModulesLogRepositoryImpl @Inject constructor(
    val applicationContext: Context,
    pathVars: PathVars
) : ModulesLogRepository {

    private val appDataDir = pathVars.appDataDir
    private var dnsCryptLogFileReader: OwnFileReader? = null
    private var torLogFileReader: OwnFileReader? = null
    private var itpdLogFileReader: OwnFileReader? = null
    private var itpdHtmlFileReader: HtmlReader? = null

    override fun getDNSCryptLog(): List<String> {
        dnsCryptLogFileReader = dnsCryptLogFileReader ?: OwnFileReader(
            applicationContext,
            "$appDataDir/logs/DnsCrypt.log"
        )
        return dnsCryptLogFileReader?.readLastLines() ?: emptyList()
    }

    override fun getTorLog(): List<String> {
        torLogFileReader = torLogFileReader ?: OwnFileReader(
            applicationContext,
            "$appDataDir/logs/Tor.log"
        )
        return torLogFileReader?.readLastLines() ?: emptyList()
    }

    override fun getITPDLog(): List<String> {
        itpdLogFileReader = itpdLogFileReader ?: OwnFileReader(
            applicationContext,
            "$appDataDir/logs/i2pd.log"
        )
        return itpdLogFileReader?.readLastLines() ?: emptyList()
    }

    override fun getITPDHtmlData(): List<String> {
        itpdHtmlFileReader = itpdHtmlFileReader ?: HtmlReader(7070)
        return itpdHtmlFileReader?.readLines() ?: emptyList()
    }
}

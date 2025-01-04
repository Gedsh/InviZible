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

package pan.alexander.tordnscrypt.data.dns_rules

import android.content.Context
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.filemanager.FileManager
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject

class DnsRulesDataSourceImpl @Inject constructor(
    private val context: Context,
    private val pathVars: PathVars
) : DnsRulesDataSource {

    override fun getBlacklistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptBlackListPath)
    }

    override fun getSingleBlacklistRulesStream(): InputStreamReader? {
        val file = File(pathVars.dnsCryptSingleBlackListPath)
        if (file.isFile) {
            return file.reader()
        }
        return null
    }

    override fun saveSingleBlacklistRules(rules: List<String>) {
        FileManager.writeTextFileSynchronous(context, pathVars.dnsCryptSingleBlackListPath, rules)
    }

    override fun getRemoteBlacklistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptRemoteBlackListPath)
    }

    override fun getRemoteBlacklistRulesFileSize(): Long {
        return File(pathVars.dnsCryptRemoteBlackListPath).length()
    }

    override fun getRemoteBlacklistRulesFileDate(): Long {
        return File(pathVars.dnsCryptRemoteBlackListPath).lastModified()
    }

    override fun clearRemoteBlacklistRules() {
        File(pathVars.dnsCryptRemoteBlackListPath).printWriter().use {
            println()
        }
    }

    override fun getLocalBlacklistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptLocalBlackListPath)
    }

    override fun clearLocalBlacklistRules() {
        File(pathVars.dnsCryptLocalBlackListPath).printWriter().use {
            println()
        }
    }

    override fun getLocalBlacklistRulesFileSize(): Long {
        return File(pathVars.dnsCryptLocalBlackListPath).length()
    }

    override fun getLocalBlacklistRulesFileDate(): Long {
        return File(pathVars.dnsCryptLocalBlackListPath).lastModified()
    }

    override fun getWhitelistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptWhiteListPath)
    }

    override fun getSingleWhitelistRulesStream(): InputStreamReader? {
        val file = File(pathVars.dnsCryptSingleWhiteListPath)
        if (file.isFile) {
            return file.reader()
        }
        return null
    }

    override fun saveSingleWhitelistRules(rules: List<String>) {
        FileManager.writeTextFileSynchronous(context, pathVars.dnsCryptSingleWhiteListPath, rules)
    }

    override fun getRemoteWhitelistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptRemoteWhiteListPath)
    }

    override fun getRemoteWhitelistRulesFileSize(): Long {
        return File(pathVars.dnsCryptRemoteWhiteListPath).length()
    }

    override fun getRemoteWhitelistRulesFileDate(): Long {
        return File(pathVars.dnsCryptRemoteWhiteListPath).lastModified()
    }

    override fun clearRemoteWhitelistRules() {
        File(pathVars.dnsCryptRemoteWhiteListPath).printWriter().use {
            println()
        }
    }

    override fun getLocalWhitelistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptLocalWhiteListPath)
    }

    override fun getLocaleWhitelistRulesFileSize(): Long {
        return File(pathVars.dnsCryptLocalWhiteListPath).length()
    }

    override fun getLocalWhitelistRulesFileDate(): Long {
        return File(pathVars.dnsCryptLocalWhiteListPath).lastModified()
    }

    override fun clearLocalWhitelistRules() {
        File(pathVars.dnsCryptLocalWhiteListPath).printWriter().use {
            println()
        }
    }

    override fun getIpBlacklistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptIPBlackListPath)
    }

    override fun getSingleIpBlacklistRulesStream(): InputStreamReader? {
        val file = File(pathVars.dnsCryptSingleIPBlackListPath)
        if (file.isFile) {
            return file.reader()
        }
        return null
    }

    override fun saveSingleIpBlacklistRules(rules: List<String>) {
        FileManager.writeTextFileSynchronous(context, pathVars.dnsCryptSingleIPBlackListPath, rules)
    }

    override fun getRemoteIpBlacklistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptRemoteIPBlackListPath)
    }

    override fun getRemoteIpBlacklistRulesFileSize(): Long {
        return File(pathVars.dnsCryptRemoteIPBlackListPath).length()
    }

    override fun getRemoteIpBlacklistRulesFileDate(): Long {
        return File(pathVars.dnsCryptRemoteIPBlackListPath).lastModified()
    }

    override fun clearRemoteIpBlacklistRules() {
        File(pathVars.dnsCryptRemoteIPBlackListPath).printWriter().use {
            println()
        }
    }

    override fun getLocalIpBlacklistRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptLocalIPBlackListPath)
    }

    override fun getLocalIpBlacklistRulesFileSize(): Long {
        return File(pathVars.dnsCryptLocalIPBlackListPath).length()
    }

    override fun getLocalIpBlacklistRulesFileDate(): Long {
        return File(pathVars.dnsCryptLocalIPBlackListPath).lastModified()
    }

    override fun clearLocalIpBlacklistRules() {
        File(pathVars.dnsCryptLocalIPBlackListPath).printWriter().use {
            println()
        }
    }

    override fun getForwardingRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptForwardingRulesPath)
    }

    override fun getSingleForwardingRulesStream(): InputStreamReader? {
        val file = File(pathVars.dnsCryptSingleForwardingRulesPath)
        if (file.isFile) {
            return file.reader()
        }
        return null
    }

    override fun saveSingleForwardingRules(rules: List<String>) {
        FileManager.writeTextFileSynchronous(
            context,
            pathVars.dnsCryptSingleForwardingRulesPath,
            rules
        )
    }

    override fun getRemoteForwardingRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptRemoteForwardingRulesPath)
    }

    override fun getRemoteForwardingRulesFileSize(): Long {
        return File(pathVars.dnsCryptRemoteForwardingRulesPath).length()
    }

    override fun getRemoteForwardingRulesFileDate(): Long {
        return File(pathVars.dnsCryptRemoteForwardingRulesPath).lastModified()
    }

    override fun clearRemoteForwardingRules() {
        File(pathVars.dnsCryptRemoteForwardingRulesPath).printWriter().use {
            println()
        }
    }

    override fun getLocalForwardingRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptLocalForwardingRulesPath)
    }

    override fun getLocalForwardingRulesFileSize(): Long {
        return File(pathVars.dnsCryptLocalForwardingRulesPath).length()
    }

    override fun getLocalForwardingRulesFileDate(): Long {
        return File(pathVars.dnsCryptLocalForwardingRulesPath).lastModified()
    }

    override fun clearLocalForwardingRules() {
        File(pathVars.dnsCryptLocalForwardingRulesPath).printWriter().use {
            println()
        }
    }

    override fun getCloakingRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptCloakingRulesPath)
    }

    override fun getSingleCloakingRulesStream(): InputStreamReader? {
        val file = File(pathVars.dnsCryptSingleCloakingRulesPath)
        if (file.isFile) {
            return file.reader()
        }
        return null
    }

    override fun saveSingleCloakingRules(rules: List<String>) {
        FileManager.writeTextFileSynchronous(
            context,
            pathVars.dnsCryptSingleCloakingRulesPath,
            rules
        )
    }

    override fun getRemoteCloakingRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptRemoteCloakingRulesPath)
    }

    override fun getRemoteCloakingRulesFileSize(): Long {
        return File(pathVars.dnsCryptRemoteCloakingRulesPath).length()
    }

    override fun getRemoteCloakingRulesFileDate(): Long {
        return File(pathVars.dnsCryptRemoteCloakingRulesPath).lastModified()
    }

    override fun clearRemoteCloakingRules() {
        File(pathVars.dnsCryptRemoteCloakingRulesPath).printWriter().use {
            println()
        }
    }

    override fun getLocalCloakingRulesStream(): InputStreamReader {
        return getInputStreamFromFile(pathVars.dnsCryptLocalCloakingRulesPath)
    }

    override fun getLocalCloakingRulesFileSize(): Long {
        return File(pathVars.dnsCryptLocalCloakingRulesPath).length()
    }

    override fun getLocalCloakingRulesFileDate(): Long {
        return File(pathVars.dnsCryptLocalCloakingRulesPath).lastModified()
    }

    override fun clearLocalCloakingRules() {
        File(pathVars.dnsCryptLocalCloakingRulesPath).printWriter().use {
            println()
        }
    }

    private fun getInputStreamFromFile(path: String): InputStreamReader {
        val file = File(path)
        if (!file.isFile) {
            file.createNewFile()
        }
        return file.reader()
    }
}

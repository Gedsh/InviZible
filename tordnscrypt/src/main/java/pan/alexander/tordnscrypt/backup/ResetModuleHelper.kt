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

package pan.alexander.tordnscrypt.backup

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.WorkerThread
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.installer.ChmodCommand
import pan.alexander.tordnscrypt.installer.DNSCryptExtractCommand
import pan.alexander.tordnscrypt.installer.ITPDExtractCommand
import pan.alexander.tordnscrypt.installer.InstallerHelper
import pan.alexander.tordnscrypt.installer.TorExtractCommand
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.enums.ModuleName
import pan.alexander.tordnscrypt.utils.filemanager.FileManager
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import javax.inject.Inject

class ResetModuleHelper @Inject constructor(
    private val context: Context,
    pathVars: PathVars,
    private val installerHelper: InstallerHelper
) {

    private val dataDir = pathVars.appDataDir

    @WorkerThread
    fun resetModuleSettings(moduleName: ModuleName) = try {
        logw("Resetting ${moduleName.moduleName} settings")

        cleanModuleFolder(moduleName)
        extractModuleData(moduleName)
        correctAppDir(moduleName)

        logw("Reset ${moduleName.moduleName} settings success")
    } catch (e: Exception) {
        loge("Reset ${moduleName.moduleName} settings error", e)
    }

    private fun cleanModuleFolder(moduleName: ModuleName) {
        when (moduleName) {
            ModuleName.DNSCRYPT_MODULE -> {
                FileManager.deleteDirSynchronous(
                    context,
                    "$dataDir/app_data/dnscrypt-proxy"
                )
            }

            ModuleName.TOR_MODULE -> {
                FileManager.deleteDirSynchronous(
                    context,
                    "$dataDir/tor_data"
                )
                FileManager.deleteDirSynchronous(
                    context,
                    "$dataDir/app_data/tor"
                )
            }

            ModuleName.ITPD_MODULE -> {
                FileManager.deleteDirSynchronous(
                    context,
                    "$dataDir/i2pd_data"
                )
                FileManager.deleteDirSynchronous(
                    context,
                    "$dataDir/app_data/i2pd"
                )
            }
        }
    }

    private fun extractModuleData(moduleName: ModuleName) {
        when (moduleName) {
            ModuleName.DNSCRYPT_MODULE -> {
                DNSCryptExtractCommand(context, dataDir).execute()
                ChmodCommand.dirChmod("$dataDir/app_data/dnscrypt-proxy", false)
            }

            ModuleName.TOR_MODULE -> {
                TorExtractCommand(context, dataDir).execute()
                ChmodCommand.dirChmod("$dataDir/app_data/tor", false)
            }

            ModuleName.ITPD_MODULE -> {
                ITPDExtractCommand(context, dataDir).execute()
                ChmodCommand.dirChmod("$dataDir/app_data/i2pd", false)
            }
        }


    }

    private fun correctAppDir(moduleName: ModuleName) {
        val path = when (moduleName) {
            ModuleName.DNSCRYPT_MODULE -> "$dataDir/app_data/dnscrypt-proxy/dnscrypt-proxy.toml"
            ModuleName.TOR_MODULE -> "$dataDir/app_data/tor/tor.conf"
            ModuleName.ITPD_MODULE -> "$dataDir/app_data/i2pd/i2pd.conf"

        }
        updateAppDir(path)
    }

    @SuppressLint("SdCardPath")
    private fun updateAppDir(path: String) {
        var lines = FileManager.readTextFileSynchronous(context, path)
        var line: String
        for (i in lines.indices) {
            line = lines[i]
            if (line.contains("/data/user/0/pan.alexander.tordnscrypt")) {
                line = line.replace(
                    "/data/user/0/pan.alexander.tordnscrypt.*?/".toRegex(),
                    "$dataDir/"
                )
                lines[i] = line
            }
        }

        if (context.isGpVersion() && path.contains("dnscrypt-proxy.toml")) {
            lines = installerHelper.prepareDNSCryptForGP(lines)
        }

        FileManager.writeTextFileSynchronous(context, path, lines)
    }

    private fun Context.isGpVersion() = getString(R.string.package_name).contains(".gp")
}

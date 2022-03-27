package pan.alexander.tordnscrypt.utils.apps

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

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserManager
import androidx.core.content.ContextCompat
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_SHOWS_ALL_APPS
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MULTI_USER_SUPPORT
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named

private const val ON_APP_ADDED_REFRESH_PERIOD_MSEC = 250
private val pattern: Pattern = Pattern.compile("UserHandle\\{(\\d+)\\}")
private val reentrantLock = ReentrantLock()

class InstalledApplicationsManager private constructor(
    private var onAppAddListener: OnAppAddListener?,
    private val activeApps: Set<String>,
    private val showSpecialApps: Boolean,
    private var showAllApps: Boolean?
) {

    @Inject
    lateinit var context: Context

    @Inject
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    lateinit var defaultPreferences: SharedPreferences

    @Inject
    lateinit var pathVars: PathVars

    init {
        App.instance.daggerComponent.inject(this)

        showAllApps = showAllApps ?: defaultPreferences.getBoolean(FIREWALL_SHOWS_ALL_APPS, false)
    }

    private val ownUID = pathVars.appUid
    private var multiUserSupport = defaultPreferences.getBoolean(MULTI_USER_SUPPORT, false)
    private var savedTime = 0L

    fun getInstalledApps(): List<ApplicationData> {

        try {

            reentrantLock.lockInterruptibly()

            val uids = arrayListOf<Int>()
            val packageManager: PackageManager = context.packageManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && multiUserSupport) {
                val userService = context.getSystemService(Context.USER_SERVICE) as UserManager
                val list = userService.userProfiles

                for (user in list) {
                    user?.let {
                        val m = pattern.matcher(user.toString())
                        if (m.find()) {
                            val id = m.group(1)?.toInt()
                            if (id != null) {
                                uids.add(id)
                            }
                        }
                    }
                }

                logi("Devise Users: ${uids.joinToString()}")
            }

            var pkgManagerFlags = PackageManager.GET_META_DATA

            if (multiUserSupport) {
                pkgManagerFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    pkgManagerFlags or PackageManager.MATCH_UNINSTALLED_PACKAGES
                } else {
                    @Suppress("DEPRECATION")
                    pkgManagerFlags or PackageManager.GET_UNINSTALLED_PACKAGES
                }
            }

            val installedApps = packageManager.getInstalledApplications(pkgManagerFlags)
            val userAppsMap = hashMapOf<Int, ApplicationData>()
            val multiUserAppsMap = hashMapOf<Int, ApplicationData>()
            var application: ApplicationData?

            installedApps.forEach { applicationInfo ->
                application = userAppsMap[applicationInfo.uid]

                val name =
                    packageManager.getApplicationLabel(applicationInfo)?.toString() ?: "Undefined"
                val icon = packageManager.getApplicationIcon(applicationInfo)

                if (application == null) {
                    val uid = applicationInfo.uid

                    if (uid == ownUID) {
                        return@forEach
                    }

                    val system = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    if (!system && showAllApps == false) {
                        val useInternet = isAppUseInternet(packageManager, applicationInfo)

                        if (!useInternet) {
                            return@forEach
                        }
                    }

                    val packageName = applicationInfo.packageName

                    application = ApplicationData(
                        name,
                        packageName,
                        uid,
                        icon,
                        system,
                        activeApps.contains(uid.toString())
                    )

                    if ((applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0) {
                        application?.let {
                            userAppsMap[uid] = it
                            val time = System.currentTimeMillis()
                            if (time - savedTime > ON_APP_ADDED_REFRESH_PERIOD_MSEC) {
                                onAppAddListener?.onAppAdded(it)
                                savedTime = time
                            }
                        }
                    }
                } else {
                    application?.addName(name)
                }

                if (uids.size > 1 || uids.getOrElse(0) { 0 } != 0) {
                    val tempMultiUserAppsMap: Map<Int, ApplicationData> = checkPartOfMultiUser(
                        applicationInfo,
                        name,
                        icon,
                        uids,
                        packageManager,
                        multiUserAppsMap
                    )
                    tempMultiUserAppsMap.forEach { (uid, applicationData) ->
                        if (multiUserAppsMap.containsKey(uid)) {
                            multiUserAppsMap[uid]?.addAllNames(applicationData.names)
                        } else {
                            multiUserAppsMap[uid] = applicationData
                        }
                    }

                }

            }

            if (multiUserAppsMap.isNotEmpty()) {
                multiUserAppsMap.forEach { (uid, applicationData) ->
                    userAppsMap[uid] = applicationData
                }
            }

            val applications = userAppsMap.values.toMutableList()

            getKnownApplications().forEach { knownApp ->
                if (!applications.contains(knownApp)) {
                    applications.add(knownApp)
                }
            }

            return applications.sorted()
        } catch (e: Exception) {
            loge("InstalledApplications getInstalledApps")
        } finally {
            onAppAddListener = null
            if (reentrantLock.isLocked && reentrantLock.isHeldByCurrentThread) {
                reentrantLock.unlock()
            }
        }
        return emptyList()
    }

    private fun isAppUseInternet(
        packageManager: PackageManager,
        applicationInfo: ApplicationInfo
    ): Boolean {
        var useInternet = false
        try {
            val pInfo: PackageInfo = packageManager.getPackageInfo(
                applicationInfo.packageName,
                PackageManager.GET_PERMISSIONS
            )
            if (pInfo.requestedPermissions != null) {
                for (permInfo in pInfo.requestedPermissions) {
                    if (permInfo == Manifest.permission.INTERNET) {
                        useInternet = true
                        break
                    }
                }
            }
        } catch (e: Exception) {
            useInternet = true
            logw("InstalledApplications isAppUseInternet", e)
        }
        return useInternet
    }

    private fun checkPartOfMultiUser(
        applicationInfo: ApplicationInfo,
        name: String,
        icon: Drawable, uids: List<Int>,
        packageManager: PackageManager,
        multiUserAppsMap: Map<Int, ApplicationData>
    ): Map<Int, ApplicationData> {

        val tempMultiUserAppsMap = hashMapOf<Int, ApplicationData>()

        uids.forEach { uid ->
            if (uid != 0) {
                try {
                    val applicationUID = "$uid${applicationInfo.uid}".toInt()

                    val packages = packageManager.getPackagesForUid(applicationUID)

                    packages?.let {
                        val system = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val packageName = it.joinToString()
                        val application = ApplicationData(
                            "$name(M)", packageName, applicationUID,
                            icon, system, activeApps.contains(applicationUID.toString())
                        )

                        tempMultiUserAppsMap[applicationUID] = application

                        val time = System.currentTimeMillis()

                        if (!multiUserAppsMap.containsKey(applicationUID)
                            && time - savedTime > ON_APP_ADDED_REFRESH_PERIOD_MSEC
                        ) {
                            onAppAddListener?.onAppAdded(application)
                            savedTime = time
                        }
                    }
                } catch (e: Exception) {
                    loge("InstalledApplications checkPartOfMultiUser")
                }
            }
        }

        return tempMultiUserAppsMap
    }

    private fun getKnownApplications(): ArrayList<ApplicationData> {
        val defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
        val userId = ownUID / 100_000
        val adb = getUidForName("adb", 1011 + userId * 100_000)
        val media = getUidForName("media", 1013 + userId * 100_000)
        val vpn = getUidForName("vpn", 1016 + userId * 100_000)
        val drm = getUidForName("drm", 1019 + userId * 100_000)
        val mdns = getUidForName("mdns", 1020 + userId * 100_000)
        val gps = getUidForName("gps", 1021 + userId * 100_000)
        val dns = getUidForName("dns", 1051 + userId * 100_000)
        val shell = getUidForName("shell", 2000 + userId * 100_000)
        val clat = getUidForName("clat", 1029 + userId * 100_000)
        val specialDataApps = arrayListOf(
            ApplicationData("Kernel", "UID -1", -1, defaultIcon, true, activeApps.contains("-1")),
            ApplicationData("Root", "root", 0, defaultIcon, true, activeApps.contains("0")),
            ApplicationData(
                "Android Debug Bridge",
                "adb",
                adb,
                defaultIcon,
                true,
                activeApps.contains(adb.toString())
            ),
            ApplicationData(
                "Media server",
                "media",
                media,
                defaultIcon,
                true,
                activeApps.contains(media.toString())
            ),
            ApplicationData(
                "VPN",
                "vpn",
                vpn,
                defaultIcon,
                true,
                activeApps.contains(vpn.toString())
            ),
            ApplicationData(
                "Digital Rights Management",
                "drm",
                drm,
                defaultIcon,
                true,
                activeApps.contains(drm.toString())
            ),
            ApplicationData(
                "Multicast DNS",
                "mDNS",
                mdns,
                defaultIcon,
                true,
                activeApps.contains(mdns.toString())
            ),
            ApplicationData(
                "GPS",
                "gps",
                gps,
                defaultIcon,
                true,
                activeApps.contains(gps.toString())
            ),
            ApplicationData(
                "DNS",
                "dns",
                dns,
                defaultIcon,
                true,
                activeApps.contains(dns.toString())
            ),
            ApplicationData(
                "Linux shell",
                "shell",
                shell,
                defaultIcon,
                true,
                activeApps.contains(shell.toString())
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specialDataApps.add(
                ApplicationData(
                    "Clat",
                    "clat",
                    clat,
                    defaultIcon,
                    true,
                    activeApps.contains(clat.toString())
                )
            )
        }

        if (showSpecialApps) {
            specialDataApps.add(
                ApplicationData(
                    "Internet time servers",
                    "ntp",
                    ApplicationData.SPECIAL_UID_NTP,
                    defaultIcon,
                    true,
                    activeApps.contains(ApplicationData.SPECIAL_UID_NTP.toString())
                )
            )
            specialDataApps.add(
                ApplicationData(
                    "A-GPS",
                    "agps",
                    ApplicationData.SPECIAL_UID_AGPS,
                    defaultIcon,
                    true,
                    activeApps.contains(ApplicationData.SPECIAL_UID_AGPS.toString())
                )
            )
        }

        return specialDataApps
    }

    private fun getUidForName(name: String, defaultValue: Int): Int {
        var uid = defaultValue
        try {
            val result = Process.getUidForName(name)
            if (result > 0) {
                uid = result
            } else {
                logw("No uid for $name, using default value $defaultValue")
            }
        } catch (e: Exception) {
            logw("No uid for $name, using default value $defaultValue")
        }
        return uid
    }

    interface OnAppAddListener {
        fun onAppAdded(application: ApplicationData)
    }

    class Builder {

        private var onAppAddListener: OnAppAddListener? = null
        private var activeApps = setOf<String>()
        private var showSpecialApps = false
        private var showAllApps: Boolean? = null

        fun setOnAppAddListener(onAppAddListener: OnAppAddListener): Builder {
            this.onAppAddListener = onAppAddListener
            return this
        }

        fun activeApps(activeApps: Set<String>): Builder {
            this.activeApps = activeApps
            return this
        }

        fun showSpecialApps(show: Boolean): Builder {
            this.showSpecialApps = show
            return this
        }

        fun showAllApps(show: Boolean?): Builder {
            this.showAllApps = show
            return this
        }

        fun build(): InstalledApplicationsManager =
            InstalledApplicationsManager(
                onAppAddListener,
                activeApps,
                showSpecialApps,
                showAllApps
            )
    }
}

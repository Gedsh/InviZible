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

package pan.alexander.tordnscrypt.utils.apps

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import androidx.core.content.ContextCompat
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.Utils.allowInteractAcrossUsersPermissionIfRequired
import pan.alexander.tordnscrypt.utils.Utils.getUidForName
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
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
    private var iconIsRequired: Boolean
) {

    @Inject
    lateinit var context: Context

    @Inject
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    lateinit var defaultPreferences: SharedPreferences

    @Inject
    lateinit var pathVars: PathVars

    @Inject
    lateinit var installedAppNamesStorage: InstalledAppNamesStorage

    init {
        App.instance.daggerComponent.inject(this)
    }

    private val ownUID = pathVars.appUid
    private var multiUserSupport = defaultPreferences.getBoolean(MULTI_USER_SUPPORT, true)
    private var savedTime = 0L

    @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    fun getInstalledApps(): List<ApplicationData> {

        try {

            if (multiUserSupport) {
                allowInteractAcrossUsersPermissionIfRequired(context, pathVars)
            }

            reentrantLock.lockInterruptibly()

            val userUids = arrayListOf<Int>()
            val packageManager: PackageManager = context.packageManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && multiUserSupport) {
                val userService = context.getSystemService(Context.USER_SERVICE) as UserManager
                val list = userService.userProfiles

                for (user in list) {
                    user?.let {
                        val m = pattern.matcher(user.toString())
                        if (m.find()) {
                            val id = m.group(1)?.toLong()
                            if (id != null && id <= Int.MAX_VALUE) {
                                userUids.add(id.toInt())
                            }
                        }
                    }
                }

                logi("Devise Users: ${userUids.joinToString()}")
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

            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(pkgManagerFlags.toLong())
                )
            } else {
                packageManager.getInstalledApplications(pkgManagerFlags)
            }

            val userAppsMap = hashMapOf<Int, ApplicationData>()
            val multiUserAppsMap = hashMapOf<Int, ApplicationData>()

            installedApps.forEach { applicationInfo ->
                var appDataSaved = userAppsMap[applicationInfo.uid]

                //val name = packageManager.getApplicationLabel(applicationInfo)?.toString() ?: "Undefined"
                val name = try {
                    applicationInfo.loadLabel(packageManager)?.toString()
                        ?: applicationInfo.packageName
                } catch (e: Exception) {
                    logw("InstalledApplications get name", e)
                    applicationInfo.packageName
                }
                val icon = if (iconIsRequired) {
                    //packageManager.getApplicationIcon(applicationInfo)
                    try {
                        applicationInfo.loadIcon(packageManager)
                    } catch (e: Exception) {
                        logw("InstalledApplications get icon", e)
                        null
                    }
                } else {
                    null
                }

                val uid = applicationInfo.uid
                val packageName = applicationInfo.packageName

                if (appDataSaved == null) {

                    if (uid == ownUID) {
                        return@forEach
                    }

                    val system = isAppSystem(applicationInfo)
                    val useInternet = isAppUseInternet(packageManager, applicationInfo)

                    appDataSaved = ApplicationData(
                        name,
                        packageName,
                        uid,
                        icon,
                        system,
                        useInternet,
                        activeApps.contains(uid.toString())
                    )

                    if (isAppInstalled(applicationInfo)) {
                        appDataSaved.let {
                            userAppsMap[uid] = it
                            updateDisplayedList(it)
                        }
                    }
                } else {

                    val system = isAppSystem(applicationInfo) || appDataSaved.system
                    val useInternet = isAppUseInternet(packageManager, applicationInfo)
                            || appDataSaved.hasInternetPermission
                    val pack = if (packageName.length < appDataSaved.pack.length) {
                        packageName
                    } else {
                        appDataSaved.pack
                    }

                    if (system != appDataSaved.system
                        || useInternet != appDataSaved.hasInternetPermission
                        || pack != appDataSaved.pack
                    ) {

                        val namesSaved = appDataSaved.names
                        val iconSaved = appDataSaved.icon

                        appDataSaved = ApplicationData(
                            name,
                            pack,
                            uid,
                            icon ?: iconSaved,
                            system,
                            useInternet,
                            activeApps.contains(uid.toString())
                        )

                        appDataSaved.addAllNames(namesSaved)

                        userAppsMap[uid] = appDataSaved
                    } else {
                        appDataSaved.addName(name)
                    }
                }

                if (userUids.size > 1 || userUids.getOrElse(0) { 0 } != 0) {
                    val singleAppMultiUserAppsMap: Map<Int, ApplicationData> = checkPartOfMultiUser(
                        applicationInfo,
                        name,
                        icon,
                        userUids,
                        packageManager,
                        multiUserAppsMap
                    )
                    singleAppMultiUserAppsMap.forEach { (uid, applicationData) ->
                        val applicationDataSaved = multiUserAppsMap[uid]

                        if (applicationDataSaved != null) {
                            val system = applicationDataSaved.system || applicationData.system
                            val useInternet = applicationDataSaved.hasInternetPermission
                                    || applicationData.hasInternetPermission
                            val pack =
                                if (applicationData.pack.length < applicationDataSaved.pack.length) {
                                    applicationData.pack
                                } else {
                                    applicationDataSaved.pack
                                }

                            if (system != applicationDataSaved.system
                                || useInternet != applicationDataSaved.hasInternetPermission
                                || pack != applicationDataSaved.pack
                            ) {
                                val appData = ApplicationData(
                                    applicationData.names.firstOrNull() ?: name,
                                    pack,
                                    applicationData.uid,
                                    applicationData.icon ?: applicationDataSaved.icon,
                                    system,
                                    useInternet,
                                    activeApps.contains(uid.toString())
                                )

                                appData.addAllNames(applicationData.names)
                                appData.addAllNames(applicationDataSaved.names)

                                multiUserAppsMap[uid] = appData
                            } else {
                                multiUserAppsMap[uid]?.addAllNames(applicationData.names)
                            }
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

            installedAppNamesStorage.updateAppUidToNames(applications)

            return applications.sorted()
        } catch (e: Exception) {
            loge("InstalledApplications getInstalledApps", e)
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

            val pInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    applicationInfo.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                packageManager.getPackageInfo(
                    applicationInfo.packageName,
                    PackageManager.GET_PERMISSIONS
                )
            }

            if (pInfo.requestedPermissions != null) {
                for (permInfo in pInfo.requestedPermissions) {
                    if (permInfo == Manifest.permission.INTERNET) {
                        useInternet = true
                        break
                    }
                }
            }
        } catch (ignored: PackageManager.NameNotFoundException) {
            useInternet = true
        } catch (e: Exception) {
            useInternet = true
            logw("InstalledApplications isAppUseInternet", e)
        }
        return useInternet
    }

    private fun isAppSystem(applicationInfo: ApplicationInfo) =
        (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

    private fun isAppInstalled(applicationInfo: ApplicationInfo) =
        applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED != 0

    private fun checkPartOfMultiUser(
        applicationInfo: ApplicationInfo,
        name: String,
        icon: Drawable?,
        userUids: List<Int>,
        packageManager: PackageManager,
        multiUserAppsMap: Map<Int, ApplicationData>
    ): Map<Int, ApplicationData> {

        val singleAppMultiUserAppsMap = hashMapOf<Int, ApplicationData>()

        for (userUid in userUids) {

            if (userUid == 0) {
                continue
            }

            try {
                tryCheckApp(
                    appInfo = applicationInfo,
                    name = name,
                    icon = icon,
                    userUid = userUid,
                    packageManager = packageManager,
                    multiUserAppsMap = multiUserAppsMap,
                    singleAppMultiUserAppsMap = singleAppMultiUserAppsMap
                )
            } catch (ignored: SecurityException) {
            } catch (e: Exception) {
                loge("InstalledApplications checkPartOfMultiUser", e)
            }
        }

        return singleAppMultiUserAppsMap
    }

    private fun tryCheckApp(
        appInfo: ApplicationInfo,
        name: String,
        icon: Drawable?,
        userUid: Int,
        packageManager: PackageManager,
        multiUserAppsMap: Map<Int, ApplicationData>,
        singleAppMultiUserAppsMap: HashMap<Int, ApplicationData>
    ) {
        val applicationUIDLong = "$userUid${appInfo.uid}".toLong()
        if (applicationUIDLong > Int.MAX_VALUE) return
        val applicationUID = applicationUIDLong.toInt()

        val packages = packageManager.getPackagesForUid(applicationUID) ?: return

        val system = isAppSystem(appInfo)
        val useInternet = isAppUseInternet(packageManager, appInfo)
        val pack = packages.minByOrNull { it.length } ?: ""
        val appData = ApplicationData(
            "$name(M)",
            pack,
            applicationUID,
            icon,
            system,
            useInternet,
            activeApps.contains(applicationUID.toString())
        )
        singleAppMultiUserAppsMap[applicationUID] = appData
        if (!multiUserAppsMap.containsKey(applicationUID)) {
            updateDisplayedList(appData)
        }
    }

    private fun getKnownApplications(): ArrayList<ApplicationData> {
        val defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
        val userId = ownUID / 100_000
        val adb = getUidForName("adb", 1011 + userId * 100_000)
        val media = getUidForName("media", 1013 + userId * 100_000)
        val vpn = getUidForName("vpn", 1016 + userId * 100_000)
        val drm = getUidForName("drm", 1019 + userId * 100_000)
        val mdns = getUidForName("mdnsr", 1020 + userId * 100_000)
        val gps = getUidForName("gps", 1021 + userId * 100_000)
        val dns = getUidForName("dns", 1051 + userId * 100_000)
        val dnsTether = getUidForName("dns_tether", 1052 + userId * 100_000)
        val shell = getUidForName("shell", 2000 + userId * 100_000)
        val clat = getUidForName("clat", 1029 + userId * 100_000)
        val specialDataApps = arrayListOf(
            ApplicationData(
                "Kernel",
                "uid -1",
                -1,
                defaultIcon,
                system = true,
                true,
                activeApps.contains("-1")
            ),
            ApplicationData(
                "Root",
                "root",
                0,
                defaultIcon,
                system = true,
                true,
                activeApps.contains("0")
            ),
            ApplicationData(
                "Android Debug Bridge",
                "adb",
                adb,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(adb.toString())
            ),
            ApplicationData(
                "Media server",
                "media",
                media,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(media.toString())
            ),
            ApplicationData(
                "VPN",
                "vpn",
                vpn,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(vpn.toString())
            ),
            ApplicationData(
                "Digital Rights Management",
                "drm",
                drm,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(drm.toString())
            ),
            ApplicationData(
                "Multicast DNS",
                "mdnsr",
                mdns,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(mdns.toString())
            ),
            ApplicationData(
                "GPS",
                "gps",
                gps,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(gps.toString())
            ),
            ApplicationData(
                "DNS",
                "dns",
                dns,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(dns.toString())
            ),
            ApplicationData(
                "DNS Tether",
                "dns.tether",
                dnsTether,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(dnsTether.toString())
            ),
            ApplicationData(
                "Linux shell",
                "shell",
                shell,
                defaultIcon,
                system = true,
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
                    system = true,
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
                    system = true,
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
                    system = true,
                    true,
                    activeApps.contains(ApplicationData.SPECIAL_UID_AGPS.toString())
                )
            )
            specialDataApps.add(
                ApplicationData(
                    context.getString(R.string.connectivity_check),
                    "connectivitycheck.gstatic.com",
                    ApplicationData.SPECIAL_UID_CONNECTIVITY_CHECK,
                    defaultIcon,
                    system = true,
                    true,
                    activeApps.contains(ApplicationData.SPECIAL_UID_CONNECTIVITY_CHECK.toString())
                )
            )
        }

        return specialDataApps
    }

    interface OnAppAddListener {
        fun onAppAdded(application: ApplicationData)
    }

    private fun updateDisplayedList(applicationData: ApplicationData) {
        val time = System.currentTimeMillis()
        if (time - savedTime > ON_APP_ADDED_REFRESH_PERIOD_MSEC) {
            onAppAddListener?.onAppAdded(applicationData)
            savedTime = time
        }
    }

    class Builder {

        private var onAppAddListener: OnAppAddListener? = null
        private var activeApps = setOf<String>()
        private var showSpecialApps = false
        private var iconIsRequired = false

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

        fun setIconRequired(): Builder {
            this.iconIsRequired = true
            return this
        }

        fun build(): InstalledApplicationsManager =
            InstalledApplicationsManager(
                onAppAddListener,
                activeApps,
                showSpecialApps,
                iconIsRequired
            )
    }
}

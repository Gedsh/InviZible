package pan.alexander.tordnscrypt.utils

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern


class InstalledApplications(private val context: Context, private val activeApps: Set<String>) {

    private companion object{
        private val pattern: Pattern = Pattern.compile("UserHandle\\{(.*)\\}")
        private val reentrantLock = ReentrantLock()
    }

    private var onAppAddListener: OnAppAddListener? = null

    fun setOnAppAddListener(onAppAddListener: OnAppAddListener?) {
        this.onAppAddListener = onAppAddListener
    }

    interface OnAppAddListener {
        fun onAppAdded(application: ApplicationData)
    }

    private val ownUID = Process.myUid()
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val multiUserSupport = sharedPreferences.getBoolean("pref_common_multi_user", false)
    private var showSpecials = false
    private var savedTime = 0L
    private val onAppAddedRefreshPeriod = 250

    fun getInstalledApps(showSpecials: Boolean = false): List<ApplicationData> {

        this.showSpecials = showSpecials

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

                Log.i(LOG_TAG, "Devise Users: ${uids.joinToString()}")
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

                val name = packageManager.getApplicationLabel(applicationInfo).toString()
                val icon = packageManager.getApplicationIcon(applicationInfo)

                if (application == null) {
                    val system = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    var useInternet = false
                    val uid = applicationInfo.uid

                    try {
                        val pInfo: PackageInfo = packageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_PERMISSIONS)
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
                        Log.e(LOG_TAG, "InstalledApplications getApp exception  ${e.message}\n${e.cause}")
                    }

                    if (!useInternet && !system || uid == ownUID) {
                        return@forEach
                    }

                    val packageName = applicationInfo.packageName

                    application = ApplicationData(name, packageName, uid, icon, system, activeApps.contains(uid.toString()))

                    if ((applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0) {
                        application?.let {
                            userAppsMap[uid] = it
                            val time = System.currentTimeMillis()
                            if (time - savedTime > onAppAddedRefreshPeriod) {
                                onAppAddListener?.onAppAdded(it)
                                savedTime = time
                            }
                        }
                    }
                } else {
                    application?.addName(name)
                }

                if (uids.size > 1 || uids.getOrElse(0) { 0 } != 0) {
                    val tempMultiUserAppsMap: Map<Int, ApplicationData> = checkPartOfMultiUser(applicationInfo, name, icon, uids, packageManager, multiUserAppsMap)
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
            Log.e(LOG_TAG, "InstalledApplications getInstalledApps exception ${e.message}\n${e.cause}\n${e.stackTrace}")
        } finally {
            if (reentrantLock.isLocked && reentrantLock.isHeldByCurrentThread) {
                reentrantLock.unlock()
            }
        }
        return emptyList()
    }

    private fun checkPartOfMultiUser(applicationInfo: ApplicationInfo, name: String, icon: Drawable, uids: List<Int>,
                                     packageManager: PackageManager,
                                     multiUserAppsMap: Map<Int, ApplicationData>): Map<Int, ApplicationData> {

        val tempMultiUserAppsMap = hashMapOf<Int, ApplicationData>()

        uids.forEach { uid ->
            if (uid != 0) {
                try {
                    val applicationUID = "$uid${applicationInfo.uid}".toInt()

                    val packages = packageManager.getPackagesForUid(applicationUID)

                    packages?.let {
                        val system = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val packageName = it.joinToString()
                        val application = ApplicationData("$name(M)", packageName, applicationUID,
                                icon, system, activeApps.contains(applicationUID.toString()))

                        tempMultiUserAppsMap[applicationUID] = application

                        val time = System.currentTimeMillis()

                        if (!multiUserAppsMap.containsKey(applicationUID)
                                && time - savedTime > onAppAddedRefreshPeriod) {
                            onAppAddListener?.onAppAdded(application)
                            savedTime = time
                        }
                    }
                } catch (e: java.lang.Exception) {
                    Log.e(LOG_TAG, "checkPartOfMultiUser exception ${e.message} ${e.cause}")
                }
            }
        }

        return tempMultiUserAppsMap
    }

    private fun getKnownApplications(): ArrayList<ApplicationData> {
        val defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
        val userId = Process.myUid() / 100_000
        val adb = getUidForName("adb", 1011 + userId * 100_000)
        val media = getUidForName("media", 1013 + userId * 100_000)
        val vpn = getUidForName("vpn", 1016 + userId * 100_000)
        val drm = getUidForName("drm", 1019 + userId * 100_000)
        val mdns = 1020 + userId * 100_000
        val gps = getUidForName("gps", 1021 + userId * 100_000)
        val dns = 1051 + userId * 100_000
        val shell = getUidForName("shell", 2000 + userId * 100_000)
        val specialDataApps = arrayListOf(
                ApplicationData("Kernel", "UID -1", -1, defaultIcon, true, activeApps.contains("-1")),
                ApplicationData("Root", "root", 0, defaultIcon, true, activeApps.contains("0")),
                ApplicationData("Android Debug Bridge", "adb", adb, defaultIcon, true, activeApps.contains(adb.toString())),
                ApplicationData("Media server", "media", media, defaultIcon, true, activeApps.contains(media.toString())),
                ApplicationData("VPN", "vpn", vpn, defaultIcon, true, activeApps.contains(vpn.toString())),
                ApplicationData("Digital Rights Management", "drm", drm, defaultIcon, true, activeApps.contains(drm.toString())),
                ApplicationData("Multicast DNS", "mDNS", mdns, defaultIcon, true, activeApps.contains(mdns.toString())),
                ApplicationData("GPS", "gps", gps, defaultIcon, true, activeApps.contains(gps.toString())),
                ApplicationData("DNS", "dns", dns, defaultIcon, true, activeApps.contains(dns.toString())),
                ApplicationData("Linux shell", "shell", shell, defaultIcon, true, activeApps.contains(shell.toString()))
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specialDataApps.add(ApplicationData("Clat", "clat", 1029, defaultIcon, system = true, active = false))
        }

        if (showSpecials) {
            specialDataApps.add(ApplicationData("Internet time servers", "ntp", ApplicationData.SPECIAL_UID_NTP, defaultIcon,
                    true, activeApps.contains(ApplicationData.SPECIAL_UID_NTP.toString())))
            specialDataApps.add(ApplicationData("A-GPS", "agps", ApplicationData.SPECIAL_UID_AGPS, defaultIcon,
                    true, activeApps.contains(ApplicationData.SPECIAL_UID_AGPS.toString())))
        }

        return specialDataApps
    }

    private fun getUidForName(name: String, defaultValue: Int): Int {
        var result = defaultValue
        try {
            result = Process.getUidForName(name)
        } catch (ignored: Exception) {
        }
        return result
    }
}
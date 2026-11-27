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

package pan.alexander.tordnscrypt.utils.apps

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.os.UserHandle
import androidx.core.content.ContextCompat
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.Utils.allowInteractAcrossUsersPermissionIfRequired
import pan.alexander.tordnscrypt.utils.Utils.getUidForName
import pan.alexander.tordnscrypt.utils.Utils.isInteractAcrossUsersPermissionGranted
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MULTI_USER_SUPPORT
import pan.alexander.tordnscrypt.utils.session.AppSessionStore
import pan.alexander.tordnscrypt.utils.session.SessionKeys.MULTIPLE_USERS_EXISTS
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named

private const val ON_APP_ADDED_REFRESH_PERIOD_MSEC = 250
private const val UIDS_PER_USER = 100_000
// System query flag, unavailable in the public SDK. Requires INTERACT_ACROSS_USERS.
private const val MATCH_ANY_USER = 0x00400000
private val pattern: Pattern = Pattern.compile("UserHandle\\{(\\d+)\\}")
private val captivePortalPattern = Pattern.compile("^com\\.(\\s+\\.)?android.captiveportallogin$")
private val reentrantLock = ReentrantLock()

class InstalledApplicationsManager private constructor(
    private var onAppAddListener: OnAppAddListener?,
    private val activeApps: Set<String>,
    private val showSpecialApps: Boolean,
    private val iconIsRequired: Boolean
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

    @Inject
    lateinit var sessionStore: AppSessionStore

    init {
        App.instance.daggerComponent.inject(this)
    }

    private val ownUID = pathVars.appUid
    private val multiUserSupport = defaultPreferences.getBoolean(MULTI_USER_SUPPORT, true)
    private var savedTime = 0L
    var isInventoryComplete = false
        private set
    private var crossUserQueriesAllowed = false

    @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
    fun getInstalledApps(): List<ApplicationData> {

        isInventoryComplete = false
        try {

            val permissionRequested = multiUserSupport
                    && allowInteractAcrossUsersPermissionIfRequired(context, pathVars)

            reentrantLock.lockInterruptibly()
            crossUserQueriesAllowed = multiUserSupport && isInteractAcrossUsersPermissionGranted(context)

            val userProfiles = linkedMapOf<Int, UserHandle>()
            val packageManager: PackageManager = context.packageManager

            if (multiUserSupport) {
                val userService = context.getSystemService(Context.USER_SERVICE) as UserManager
                val list = userService.userProfiles

                for (user in list) {
                    user?.let {
                        val m = pattern.matcher(user.toString())
                        if (m.find()) {
                            val id = m.group(1)?.toLong()
                            if (id != null && id <= Int.MAX_VALUE) {
                                userProfiles[id.toInt()] = user
                            }
                        }
                    }
                }

                sessionStore.save(MULTIPLE_USERS_EXISTS, userProfiles.size > 1)

                logi("Device users: ${userProfiles.keys.joinToString()}")
            }

            val userIds = userProfiles.keys.toList()
            val hasOtherProfiles = userIds.any { it != ownUID / UIDS_PER_USER }
            val installedApps = loadApplications(packageManager, crossUserQueriesAllowed && hasOtherProfiles)
            // RootCommands is asynchronous: a pre-grant snapshot is not a complete cross-user scan.
            if (hasOtherProfiles && permissionRequested && !crossUserQueriesAllowed) {
                isInventoryComplete = false
            }

            val userAppsMap = hashMapOf<Int, ApplicationData>()
            val multiUserAppsMap = hashMapOf<Int, ApplicationData>()

            installedApps.forEach { applicationInfo ->
                if (applicationInfo.uid == ownUID) return@forEach
                // Uninstalled local candidates may still identify an installed app in another profile.
                if (!isAppIncluded(applicationInfo) && !hasOtherProfiles) return@forEach

                val name = loadAppName(applicationInfo, packageManager)
                val icon = loadAppIcon(applicationInfo, packageManager)

                if (applicationInfo.uid / UIDS_PER_USER == ownUID / UIDS_PER_USER) {
                    createApplicationData(applicationInfo, packageManager, name, icon)?.let { app ->
                        val first = !userAppsMap.containsKey(app.uid)
                        mergeApplication(userAppsMap, app)
                        if (first) updateDisplayedList(app)
                    }
                }
                if (hasOtherProfiles) {
                    checkPartOfMultiUser(applicationInfo, name, icon, userIds, packageManager,
                        multiUserAppsMap).values.forEach { mergeApplication(multiUserAppsMap, it) }
                }
            }

            if (hasOtherProfiles) {
                addProfileApplications(userProfiles, packageManager, multiUserAppsMap)
            }
            multiUserAppsMap.values.forEach { mergeApplication(userAppsMap, it) }

            val applications = userAppsMap.values.toMutableList()

            getKnownApplications().forEach { knownApp ->
                if (!applications.contains(knownApp)) {
                    applications.add(knownApp)
                }
            }

            captivePortalUidSearchingFinished = true

            installedAppNamesStorage.updateAppUidToNames(applications)

            return applications.sorted()
        } catch (e: Exception) {
            isInventoryComplete = false
            loge("InstalledApplications getInstalledApps", e)
        } finally {
            onAppAddListener = null
            if (reentrantLock.isLocked && reentrantLock.isHeldByCurrentThread) {
                reentrantLock.unlock()
            }
        }
        return emptyList()
    }

    private fun loadApplications(
        packageManager: PackageManager,
        acrossProfiles: Boolean
    ): List<ApplicationInfo> {
        var flags = PackageManager.GET_META_DATA
        flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags or PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        } else {
            @Suppress("DEPRECATION")
            flags or PackageManager.GET_UNINSTALLED_PACKAGES
        }
        isInventoryComplete = true
        if (acrossProfiles) {
            try {
                return queryApplications(packageManager, flags or MATCH_ANY_USER)
            } catch (e: SecurityException) {
                // The permission may have changed, or the device may impose additional restrictions.
                isInventoryComplete = false
                logw("InstalledApplications cross-profile query", e)
            }
        }
        return queryApplications(packageManager, flags)
    }

    private fun queryApplications(packageManager: PackageManager, flags: Int): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getInstalledApplications(flags)
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
        } catch (_: PackageManager.NameNotFoundException) {
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

    private fun isAppArchived(applicationInfo: ApplicationInfo) =
        Build.VERSION.SDK_INT >= 35 && applicationInfo.isArchived

    private fun isAppIncluded(applicationInfo: ApplicationInfo) =
        isAppInstalled(applicationInfo) || isAppArchived(applicationInfo)

    private fun loadAppName(info: ApplicationInfo, packageManager: PackageManager): String = try {
        //val name = packageManager.getApplicationLabel(info)?.toString() ?: "Undefined"
        info.loadLabel(packageManager)?.toString() ?: info.packageName
    } catch (e: Exception) {
        logw("InstalledApplications get name", e)
        info.packageName
    }

    private fun loadAppIcon(info: ApplicationInfo, packageManager: PackageManager): Drawable? =
        if (iconIsRequired) {
            //packageManager.getApplicationIcon(info)
            try {
                info.loadIcon(packageManager)
            } catch (e: Exception) {
                logw("InstalledApplications get icon", e)
                null
            }
        } else {
            null
        }

    private fun queryProfileApplications(profile: UserHandle): List<ApplicationInfo> {
        val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return emptyList()
        return launcher.getActivityList(null, profile).map { it.applicationInfo }
    }

    private fun addProfileApplications(
        profiles: Map<Int, UserHandle>,
        packageManager: PackageManager,
        apps: MutableMap<Int, ApplicationData>,
        query: (UserHandle) -> List<ApplicationInfo> = ::queryProfileApplications
    ) {
        for ((userId, profile) in profiles) {
            if (userId == ownUID / UIDS_PER_USER) continue
            try {
                // Enumerate independently of main-profile candidates; one app can have several activities.
                val packages = hashSetOf<String>()
                for (info in query(profile)) {
                    if (info.uid < 0
                        || info.uid / UIDS_PER_USER != userId
                        || !isAppIncluded(info)
                        || !packages.add(info.packageName)) continue
                    val app = createApplicationData(
                        info,
                        packageManager,
                        loadAppName(info, packageManager),
                        loadAppIcon(info, packageManager)) ?: continue
                    val first = !apps.containsKey(app.uid)
                    mergeApplication(apps, app)
                    if (first) updateDisplayedList(app)
                }
            } catch (_: SecurityException) {
                // Launcher visibility is optional; keep results from the existing ownership queries.
            } catch (e: Exception) {
                isInventoryComplete = false
                logw("InstalledApplications profile enumeration", e)
            }
        }
    }

    private fun createApplicationData(
        info: ApplicationInfo,
        packageManager: PackageManager,
        name: String,
        icon: Drawable?
    ): ApplicationData? {
        if (!isAppIncluded(info)) return null
        val internet = isAppUseInternet(packageManager, info)
        if (isAppContainsResourcesOnly(info) && !internet) return null
        if (!captivePortalUidSearchingFinished
            && captivePortalPattern.matcher(info.packageName.lowercase()).matches()) {
            portalUids.add(info.uid)
        }
        val archived = isAppArchived(info)
        return ApplicationData(
            name = name, pack = info.packageName, uid = info.uid, icon = icon,
            system = isAppSystem(info), hasInternetPermission = internet,
            active = activeApps.contains(info.uid.toString()), archived = archived,
            user = info.uid / UIDS_PER_USER, disabled = !archived && !info.enabled
        )
    }

    private fun mergeApplication(apps: MutableMap<Int, ApplicationData>, app: ApplicationData) {
        val previous = apps[app.uid]
        if (previous == null) {
            apps[app.uid] = app
            return
        }
        // A UID is archived only when every member is archived; an enabled member wins.
        val archived = previous.archived && app.archived
        val disabled = !archived && (previous.archived || previous.disabled) && (app.archived || app.disabled)
        val pack = if (app.pack.length < previous.pack.length) app.pack else previous.pack
        val system = previous.system || app.system
        val internet = previous.hasInternetPermission || app.hasInternetPermission
        if (pack == previous.pack && system == previous.system
            && internet == previous.hasInternetPermission && archived == previous.archived
            && disabled == previous.disabled) {
            previous.addAllNames(app.names)
            return
        }
        val merged = app.copy(
            pack = pack,
            icon = app.icon ?: previous.icon,
            system = system,
            hasInternetPermission = internet,
            archived = archived, disabled = disabled
        )
        merged.addAllNames(previous.names)
        merged.addAllNames(app.names)
        apps[app.uid] = merged
    }

    private fun isAppContainsResourcesOnly(applicationInfo: ApplicationInfo) =
        applicationInfo.flags and ApplicationInfo.FLAG_HAS_CODE == 0

    private fun checkPartOfMultiUser(
        applicationInfo: ApplicationInfo,
        name: String,
        icon: Drawable?,
        userIds: List<Int>,
        packageManager: PackageManager,
        multiUserAppsMap: Map<Int, ApplicationData>
    ): Map<Int, ApplicationData> {

        val singleAppMultiUserAppsMap = hashMapOf<Int, ApplicationData>()

        for (userId in userIds) {

            if (userId == ownUID / UIDS_PER_USER) {
                continue
            }

            try {
                tryCheckApp(
                    appInfo = applicationInfo,
                    name = name,
                    icon = icon,
                    userId = userId,
                    packageManager = packageManager,
                    multiUserAppsMap = multiUserAppsMap,
                    singleAppMultiUserAppsMap = singleAppMultiUserAppsMap
                )
            } catch (_: SecurityException) {
                if (crossUserQueriesAllowed) isInventoryComplete = false
            } catch (e: Exception) {
                isInventoryComplete = false
                loge("InstalledApplications checkPartOfMultiUser", e)
            }
        }

        return singleAppMultiUserAppsMap
    }

    private fun tryCheckApp(
        appInfo: ApplicationInfo,
        name: String,
        icon: Drawable?,
        userId: Int,
        packageManager: PackageManager,
        multiUserAppsMap: Map<Int, ApplicationData>,
        singleAppMultiUserAppsMap: HashMap<Int, ApplicationData>
    ) {
        val applicationUIDLong = userId.toLong() * UIDS_PER_USER + appInfo.uid % UIDS_PER_USER
        if (applicationUIDLong < 0 || applicationUIDLong > Int.MAX_VALUE) {
            isInventoryComplete = false
            return
        }
        val applicationUID = applicationUIDLong.toInt()

        val profileInfo = loadProfileApplication(packageManager, appInfo, applicationUID) ?: return
        val appData = createApplicationData(profileInfo, packageManager, name, icon) ?: return
        singleAppMultiUserAppsMap[applicationUID] = appData
        if (!multiUserAppsMap.containsKey(applicationUID)) {
            updateDisplayedList(appData)
        }
    }

    private fun loadProfileApplication(
        packageManager: PackageManager, source: ApplicationInfo, uid: Int
    ): ApplicationInfo? {
        if (source.uid == uid) return source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                // LauncherApps has only int flags; UNINSTALLED also includes archived candidates.
                val info = launcher?.getApplicationInfo(source.packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS,
                    UserHandle.getUserHandleForUid(uid))
                if (info != null) return if (info.uid == uid) info else null
            } catch (_: PackageManager.NameNotFoundException) {
                // Some devices expose UID ownership but restrict profile metadata.
            } catch (_: SecurityException) {
                // Metadata is optional; the owner query below determines profile membership.
            }
        }
        val owners = packageManager.getPackagesForUid(uid) ?: return null
        // Never borrow a shared-UID sibling's identity or another profile's state flags.
        if (!owners.contains(source.packageName)) return null
        return ApplicationInfo(source).apply {
            this.uid = uid
            flags = flags or ApplicationInfo.FLAG_INSTALLED
            enabled = true // State is unknown; do not label it disabled based on another profile.
            if (Build.VERSION.SDK_INT >= 35) isArchived = false
        }
    }

    private fun getKnownApplications(): ArrayList<ApplicationData> {
        val defaultIcon = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
        val userId = ownUID / UIDS_PER_USER
        val adb = getUidForName("adb", 1011 + userId * UIDS_PER_USER)
        val media = getUidForName("media", 1013 + userId * UIDS_PER_USER)
        val vpn = getUidForName("vpn", 1016 + userId * UIDS_PER_USER)
        val drm = getUidForName("drm", 1019 + userId * UIDS_PER_USER)
        val mdns = getUidForName("mdnsr", 1020 + userId * UIDS_PER_USER)
        val gps = getUidForName("gps", 1021 + userId * UIDS_PER_USER)
        val dns = getUidForName("dns", 1051 + userId * UIDS_PER_USER)
        val dnsTether = getUidForName("dns_tether", 1052 + userId * UIDS_PER_USER)
        val shell = getUidForName("shell", 2000 + userId * UIDS_PER_USER)
        val clat = getUidForName("clat", 1029 + userId * UIDS_PER_USER)
        val nobody = getUidForName("nobody", 9999 + userId * UIDS_PER_USER)

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
            ),
            ApplicationData(
                "Nobody",
                "nobody",
                nobody,
                defaultIcon,
                system = true,
                true,
                activeApps.contains(nobody.toString())
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

    companion object {
        private var captivePortalUidSearchingFinished = false
        private val portalUids = hashSetOf<Int>()
        fun getCaptivePortalUids(): Set<Int> {
            if (!captivePortalUidSearchingFinished
                && portalUids.isEmpty()
                && !reentrantLock.isLocked) {
                Builder().build().getInstalledApps()
            }
            return portalUids
        }
    }
}

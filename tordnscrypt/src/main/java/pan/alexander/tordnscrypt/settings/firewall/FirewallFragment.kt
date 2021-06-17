package pan.alexander.tordnscrypt.settings.firewall

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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.databinding.FragmentFirewallBinding
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.CachedExecutor.getExecutorService
import pan.alexander.tordnscrypt.utils.InstalledApplications
import pan.alexander.tordnscrypt.utils.PrefManager
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.Comparator


const val APPS_ALLOW_LAN_PREF = "appsAllowLan"
const val APPS_ALLOW_WIFI_PREF = "appsAllowWifi"
const val APPS_ALLOW_GSM_PREF = "appsAllowGsm"
const val APPS_ALLOW_ROAMING = "appsAllowRoaming"
const val APPS_ALLOW_VPN = "appsAllowVpn"
const val APPS_NEWLY_INSTALLED = "appsNewlyInstalled"

@SuppressLint("NotifyDataSetChanged")
class FirewallFragment : Fragment(), InstalledApplications.OnAppAddListener, View.OnClickListener,
        SearchView.OnQueryTextListener, ChipGroup.OnCheckedChangeListener, CompoundButton.OnCheckedChangeListener {

    private var _binding: FragmentFirewallBinding? = null
    private val binding get() = _binding!!

    private val modulesStatus = ModulesStatus.getInstance()
    private var handler: Handler? = null
    private var futureTask: Future<*>? = null
    private var firewallAdapter: RecyclerView.Adapter<FirewallAdapter.FirewallViewHolder>? = null
    private var lastVisibleAdapterPosition: Int = 0

    var firewallSwitch: SwitchCompat? = null

    @Volatile
    var appsList = CopyOnWriteArrayList<AppFirewall>()
    var savedAppsListWhenSearch: CopyOnWriteArrayList<AppFirewall>? = null

    private val reentrantLock = ReentrantLock()

    private var appsAllowLan = mutableSetOf<Int>()
    private var appsAllowWifi = mutableSetOf<Int>()
    private var appsAllowGsm = mutableSetOf<Int>()
    private var appsAllowRoaming = mutableSetOf<Int>()
    private var appsAllowVpn = mutableSetOf<Int>()

    @Volatile
    var allowLanForAll = false

    @Volatile
    var allowWifiForAll = false

    @Volatile
    var allowGsmForAll = false

    @Volatile
    var allowRoamingForAll = false

    @Volatile
    var allowVPNForAll = false

    @Volatile
    var appsListComplete = false

    private var searchText: String? = null

    var firewallEnabled = false

    private var comparatorWithName: Comparator<AppFirewall>? = null

    private var comparatorWithUID: Comparator<AppFirewall>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true

        val looper = Looper.getMainLooper()
        if (looper != null) {
            handler = Handler(looper)
        }

        firewallEnabled = PrefManager(context).getBoolPref("FirewallEnabled")

        initComparators()
    }

    @SuppressLint("ResourceType")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val context = activity ?: return null

        _binding = FragmentFirewallBinding.inflate(inflater, container, false)

        if (firewallEnabled) {
            enableFirewall(context)
        } else {
            disableFirewall()
        }

        binding.btnTopLanFirewall.setOnClickListener(this)
        binding.btnTopWifiFirewall.setOnClickListener(this)
        binding.btnTopGsmFirewall.setOnClickListener(this)
        binding.btnTopRoamingFirewall.setOnClickListener(this)

        if (modulesStatus.mode == OperationMode.VPN_MODE) {
            binding.btnTopVpnFirewall.visibility = View.GONE
        } else {
            binding.btnTopVpnFirewall.setOnClickListener(this)
        }

        binding.btnTopCheckAllFirewall.setOnClickListener(this)
        binding.btnTopUnCheckAllFirewall.setOnClickListener(this)

        binding.chipGroupFirewall.setOnCheckedChangeListener(this)

        binding.chipGroupFirewallSort.setOnCheckedChangeListener(this)

        searchText = null

        when {
            binding.chipFirewallSystem.isChecked -> chipSelectSystemApps(context)
            binding.chipFirewallUser.isChecked -> chipSelectUserApps(context)
            binding.chipFirewallAll.isChecked -> chipSelectAllApps(context)
            else -> updateTopIcons(context)
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        val context = activity ?: return

        val manager = LinearLayoutManager(context)
        firewallAdapter = FirewallAdapter(this)
        firewallAdapter?.setHasStableIds(true)

        binding.rvFirewallApps.apply {
            layoutManager = manager
            adapter = firewallAdapter
        }

        if (appsListComplete || !firewallEnabled) {
            binding.pbFirewallApp.isIndeterminate = false
            binding.pbFirewallApp.visibility = View.GONE
        } else {
            binding.pbFirewallApp.isIndeterminate = true
            binding.pbFirewallApp.visibility = View.VISIBLE
        }

        if (searchText != null && appsListComplete && !binding.rvFirewallApps.isComputingLayout) {
            updateTopIconsData()
            updateTopIcons(context)
            firewallAdapter?.notifyDataSetChanged()
        }

        if (lastVisibleAdapterPosition > 0 && appsListComplete) {
            (binding.rvFirewallApps.layoutManager as LinearLayoutManager).scrollToPosition(lastVisibleAdapterPosition)
        }
    }

    override fun onPause() {
        super.onPause()

        if (appsList.isNotEmpty() && appsListComplete) {

            binding.pbFirewallApp.isIndeterminate = true
            binding.pbFirewallApp.visibility = View.VISIBLE

            lastVisibleAdapterPosition =
                (binding.rvFirewallApps.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()

        futureTask?.cancel(true)
        handler?.removeCallbacksAndMessages(null)
    }

    private fun initComparators() {

        val rootMode = modulesStatus.mode == OperationMode.ROOT_MODE

        comparatorWithName = compareBy({!(it.allowLan
                || it.allowWifi
                || it.allowGsm
                || it.allowRoaming
                || it.allowVPN && rootMode)},
            { it.applicationData.names.first() })

        comparatorWithUID = compareBy({!(it.allowLan
                || it.allowWifi
                || it.allowGsm
                || it.allowRoaming
                || it.allowVPN && rootMode)},
            { it.applicationData.uid })
    }

    private fun getDeviceApps(context: Context) {

        if (appsList.isNotEmpty()) {
            return
        }

        appsListComplete = false

        futureTask = getExecutorService().submit {

            try {

                reentrantLock.lockInterruptibly()

                if (appsList.isEmpty()) {

                    handler?.post {
                        if (_binding != null) {
                            binding.pbFirewallApp.isIndeterminate = true
                            binding.pbFirewallApp.visibility = View.VISIBLE
                        }
                    }

                    val firewallFirstStart = !PrefManager(context).getBoolPref("FirewallWasStarted")
                    if (firewallFirstStart) {
                        PrefManager(context).setBoolPref("FirewallWasStarted", true)
                    }

                    appsAllowLan.addAll(stringSetToIntSet(PrefManager(context).getSetStrPref(APPS_ALLOW_LAN_PREF)))
                    appsAllowWifi.addAll(stringSetToIntSet(PrefManager(context).getSetStrPref(APPS_ALLOW_WIFI_PREF)))
                    appsAllowGsm.addAll(stringSetToIntSet(PrefManager(context).getSetStrPref(APPS_ALLOW_GSM_PREF)))
                    appsAllowRoaming.addAll(stringSetToIntSet(PrefManager(context).getSetStrPref(APPS_ALLOW_ROAMING)))
                    appsAllowVpn.addAll(stringSetToIntSet(PrefManager(context).getSetStrPref(APPS_ALLOW_VPN)))

                    val appsNewlyInstalled = stringSetToIntSet(PrefManager(context).getSetStrPref(APPS_NEWLY_INSTALLED))

                    val installedApplications = InstalledApplications(context, setOf())
                    installedApplications.setOnAppAddListener(this@FirewallFragment)
                    val installedApps = installedApplications.getInstalledApps(true)

                    while (_binding?.rvFirewallApps?.isComputingLayout == true) {
                        TimeUnit.MILLISECONDS.sleep(100)
                    }

                    appsListComplete = true

                    appsList.clear()

                    for (applicationData: ApplicationData in installedApps) {
                        val uid = applicationData.uid

                        if (appsNewlyInstalled.contains(uid)) {
                            appsList.add(0, AppFirewall(
                                    applicationData,
                                    appsAllowLan.contains(uid),
                                    appsAllowWifi.contains(uid),
                                    appsAllowGsm.contains(uid),
                                    appsAllowRoaming.contains(uid),
                                    appsAllowVpn.contains(uid)))
                        } else {
                            appsList.add(AppFirewall(
                                    applicationData,
                                    appsAllowLan.contains(uid),
                                    appsAllowWifi.contains(uid),
                                    appsAllowGsm.contains(uid),
                                    appsAllowRoaming.contains(uid),
                                    appsAllowVpn.contains(uid)))
                        }
                    }

                    PrefManager(context).setSetStrPref(APPS_NEWLY_INSTALLED, setOf())

                    val appListSize = appsList.size
                    allowLanForAll = appsAllowLan.size == appListSize
                    allowWifiForAll = appsAllowWifi.size == appListSize
                    allowGsmForAll = appsAllowGsm.size == appListSize
                    allowRoamingForAll = appsAllowRoaming.size == appListSize
                    allowVPNForAll = appsAllowVpn.size == appListSize

                    while (_binding?.rvFirewallApps?.isComputingLayout == true) {
                        TimeUnit.MILLISECONDS.sleep(100)
                    }

                    handler?.post {

                        if (_binding != null) {
                            binding.pbFirewallApp.isIndeterminate = false
                            binding.pbFirewallApp.visibility = View.GONE

                            if (firewallFirstStart) {
                                activateAllFirsStart(context)
                            }

                            if (binding.chipFirewallSortUid.isChecked) {
                                sortByUid()
                            } else {
                                sortByName()
                            }

                            if (binding.chipFirewallSystem.isChecked) {
                                chipSelectSystemApps(context)
                            } else if (binding.chipFirewallUser.isChecked) {
                                chipSelectUserApps(context)
                            }

                            if (allowLanForAll || allowWifiForAll || allowGsmForAll
                                    || allowRoamingForAll || allowVPNForAll) {
                                updateTopIcons(context)
                            }

                            firewallAdapter?.notifyDataSetChanged()
                        }
                    }
                }

            } catch (e: Exception) {
                appsListComplete = true
                Log.e(LOG_TAG, "FirewallFragment getDeviceApps exception ${e.message} ${e.cause} ${Arrays.toString(e.stackTrace)}")
            } finally {
                if (reentrantLock.isLocked) {
                    reentrantLock.unlock()
                }
            }
        }
    }

    override fun onAppAdded(application: ApplicationData) {
        if (appsListComplete || _binding?.rvFirewallApps?.isComputingLayout == true) {
            return
        }

        val uid = application.uid
        appsList.add(0,
                AppFirewall(
                        application,
                        appsAllowLan.contains(uid),
                        appsAllowWifi.contains(uid),
                        appsAllowGsm.contains(uid),
                        appsAllowRoaming.contains(uid),
                        appsAllowVpn.contains(uid))
        )

        handler?.post {
            if (!appsListComplete && _binding?.rvFirewallApps?.isComputingLayout == false) {
                firewallAdapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onClick(v: View?) {
        val context = v?.context

        if (firewallEnabled && !appsListComplete || context == null) {
            return
        }

        when (v.id) {
            R.id.btnTopLanFirewall -> activateAllLan(v.context)
            R.id.btnTopWifiFirewall -> activateAllWifi(v.context)
            R.id.btnTopGsmFirewall -> activateAllGsm(v.context)
            R.id.btnTopRoamingFirewall -> activateAllRoaming(v.context)
            R.id.btnTopVpnFirewall -> activateAllVpn(v.context)
            R.id.btnTopCheckAllFirewall -> activateAll(v.context, true)
            R.id.btnTopUnCheckAllFirewall -> activateAll(v.context, false)
            R.id.btnPowerFirewall -> {
                enableFirewall(v.context)
                modulesStatus.setIptablesRulesUpdateRequested(context, true)
            }
            else -> Log.e(LOG_TAG, "FirewallFragment onClick unknown id: ${v.id}")
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {

        if (!appsListComplete || binding.rvFirewallApps.isComputingLayout) {
            return false
        }

        searchApps(query)
        firewallAdapter?.notifyDataSetChanged()

        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {

        if (!appsListComplete || binding.rvFirewallApps.isComputingLayout) {
            return false
        }

        searchApps(newText)
        firewallAdapter?.notifyDataSetChanged()

        return true
    }

    override fun onCheckedChanged(group: ChipGroup?, checkedId: Int) {
        val context = group?.context ?: return

        if (!appsListComplete || binding.rvFirewallApps.isComputingLayout) {
            return
        }

        when (checkedId) {
            R.id.chipFirewallAll -> chipSelectAllApps(context)
            R.id.chipFirewallSystem -> chipSelectSystemApps(context)
            R.id.chipFirewallUser -> chipSelectUserApps(context)
            R.id.chipFirewallSortName -> sortByName()
            R.id.chipFirewallSortUid -> sortByUid()
            else -> Log.e(LOG_TAG, "FirewallFragment chipGroup onCheckedChanged wrong id: $id")
        }

        firewallAdapter?.notifyDataSetChanged()
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val context = buttonView?.context ?: return

        if (buttonView.id == R.id.menu_switch) {
            if (isChecked) {
                enableFirewall(context)
            } else {
                disableFirewall()
            }
            modulesStatus.setIptablesRulesUpdateRequested(context, true)
        }
    }

    fun isFirewallChangesSavingRequired(): Boolean {
        if (!appsListComplete) {
            return false
        }

        savedAppsListWhenSearch?.let {
            if (it.isNotEmpty()) {
                appsList = it
                savedAppsListWhenSearch = null
            }
        }

        val appsAllowLanToSave = mutableSetOf<Int>()
        val appsAllowWifiToSave = mutableSetOf<Int>()
        val appsAllowGsmToSave = mutableSetOf<Int>()
        val appsAllowRoamingToSave = mutableSetOf<Int>()
        val appsAllowVpnToSave = mutableSetOf<Int>()

        for (appFirewall: AppFirewall in appsList) {
            val uid = appFirewall.applicationData.uid
            if (appFirewall.allowLan) {
                appsAllowLanToSave.add(uid)
            }
            if (appFirewall.allowWifi) {
                appsAllowWifiToSave.add(uid)
            }
            if (appFirewall.allowGsm) {
                appsAllowGsmToSave.add(uid)
            }
            if (appFirewall.allowRoaming) {
                appsAllowRoamingToSave.add(uid)
            }
            if (appFirewall.allowVPN) {
                appsAllowVpnToSave.add(uid)
            }
        }

        var iptablesUpdateRequired = false

        if (appsAllowLanToSave.size != appsAllowLan.size || !appsAllowLanToSave.containsAll(appsAllowLan)) {
            iptablesUpdateRequired = true
        }
        if (appsAllowWifiToSave.size != appsAllowWifi.size || !appsAllowWifiToSave.containsAll(appsAllowWifi)) {
            iptablesUpdateRequired = true
        }
        if (appsAllowGsmToSave.size != appsAllowGsm.size || !appsAllowGsmToSave.containsAll(appsAllowGsm)) {
            iptablesUpdateRequired = true
        }
        if (appsAllowRoamingToSave.size != appsAllowRoaming.size || !appsAllowRoamingToSave.containsAll(appsAllowRoaming)) {
            iptablesUpdateRequired = true
        }
        if (appsAllowVpnToSave.size != appsAllowVpn.size || !appsAllowVpnToSave.containsAll(appsAllowVpn)) {
            iptablesUpdateRequired = true
        }

        return iptablesUpdateRequired
    }

    fun saveFirewallChanges() {

        val context = context ?: return

        savedAppsListWhenSearch?.let {
            if (it.isNotEmpty()) {
                appsList = it
                savedAppsListWhenSearch = null
            }
        }

        val appsAllowLanToSave = mutableSetOf<Int>()
        val appsAllowWifiToSave = mutableSetOf<Int>()
        val appsAllowGsmToSave = mutableSetOf<Int>()
        val appsAllowRoamingToSave = mutableSetOf<Int>()
        val appsAllowVpnToSave = mutableSetOf<Int>()

        for (appFirewall: AppFirewall in appsList) {
            val uid = appFirewall.applicationData.uid
            if (appFirewall.allowLan) {
                appsAllowLanToSave.add(uid)
            }
            if (appFirewall.allowWifi) {
                appsAllowWifiToSave.add(uid)
            }
            if (appFirewall.allowGsm) {
                appsAllowGsmToSave.add(uid)
            }
            if (appFirewall.allowRoaming) {
                appsAllowRoamingToSave.add(uid)
            }
            if (appFirewall.allowVPN) {
                appsAllowVpnToSave.add(uid)
            }
        }

        var iptablesUpdateRequired = false

        if (appsAllowLanToSave.size != appsAllowLan.size || !appsAllowLanToSave.containsAll(appsAllowLan)) {
            appsAllowLan = appsAllowLanToSave
            PrefManager(context).setSetStrPref(APPS_ALLOW_LAN_PREF, intSetToStringSet(appsAllowLan))
            iptablesUpdateRequired = true
        }
        if (appsAllowWifiToSave.size != appsAllowWifi.size || !appsAllowWifiToSave.containsAll(appsAllowWifi)) {
            appsAllowWifi = appsAllowWifiToSave
            PrefManager(context).setSetStrPref(APPS_ALLOW_WIFI_PREF, intSetToStringSet(appsAllowWifi))
            iptablesUpdateRequired = true
        }
        if (appsAllowGsmToSave.size != appsAllowGsm.size || !appsAllowGsmToSave.containsAll(appsAllowGsm)) {
            appsAllowGsm = appsAllowGsmToSave
            PrefManager(context).setSetStrPref(APPS_ALLOW_GSM_PREF, intSetToStringSet(appsAllowGsm))
            iptablesUpdateRequired = true
        }
        if (appsAllowRoamingToSave.size != appsAllowRoaming.size || !appsAllowRoamingToSave.containsAll(appsAllowRoaming)) {
            appsAllowRoaming = appsAllowRoamingToSave
            PrefManager(context).setSetStrPref(APPS_ALLOW_ROAMING, intSetToStringSet(appsAllowRoaming))
            iptablesUpdateRequired = true
        }
        if (appsAllowVpnToSave.size != appsAllowVpn.size || !appsAllowVpnToSave.containsAll(appsAllowVpn)) {
            appsAllowVpn = appsAllowVpnToSave
            PrefManager(context).setSetStrPref(APPS_ALLOW_VPN, intSetToStringSet(appsAllowVpn))
            iptablesUpdateRequired = true
        }

        if (iptablesUpdateRequired) {
            modulesStatus.setIptablesRulesUpdateRequested(context, true)
        }
    }

    private fun stringSetToIntSet(stringSet: Set<String>): Set<Int> {
        val intSet = mutableSetOf<Int>()
        stringSet.forEach { intSet.add(it.toInt()) }
        return intSet
    }

    private fun intSetToStringSet(intSet: MutableSet<Int>): Set<String> {
        val stringSet = mutableSetOf<String>()
        intSet.forEach { stringSet.add(it.toString()) }
        return stringSet
    }

    private fun sortByName() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        comparatorWithName?.let { appsList.sortListWith(it) }
        comparatorWithName?.let { savedAppsListWhenSearch?.sortListWith(it) }
    }

    private fun sortByUid() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        comparatorWithUID?.let { appsList.sortListWith(it) }
        comparatorWithUID?.let { savedAppsListWhenSearch?.sortListWith(it) }
    }

    @SuppressLint("unused")
    private inline fun <T, R : Comparable<R>> CopyOnWriteArrayList<T>.sortListBy(crossinline selector: (T) -> R?) {
        if (size > 1) {
            val list = ArrayList(this)
            list.sortBy(selector)
            clear()
            addAll(list)
        }
    }

    private fun <T> CopyOnWriteArrayList<T>.sortListWith(comparator: Comparator<T>) {
        if (size > 1) {
            val list = ArrayList(this)
            list.sortWith(comparator)
            clear()
            addAll(list)
        }
    }

    private fun enableFirewall(context: Context) {
        firewallEnabled = true
        PrefManager(context).setBoolPref("FirewallEnabled", true)

        binding.llFirewallPower.visibility = View.GONE
        binding.llFirewallTop.visibility = View.VISIBLE
        binding.llFirewallMain.visibility = View.VISIBLE
        binding.rvFirewallApps.visibility = View.VISIBLE

        getDeviceApps(context)

        firewallSwitch?.isChecked = true

        binding.btnPowerFirewall.setOnClickListener(null)
    }

    private fun disableFirewall() {
        firewallEnabled = false
        PrefManager(context).setBoolPref("FirewallEnabled", false)

        binding.llFirewallPower.visibility = View.VISIBLE
        binding.llFirewallTop.visibility = View.GONE
        binding.llFirewallMain.visibility = View.GONE
        binding.rvFirewallApps.visibility = View.GONE
        binding.pbFirewallApp.visibility = View.GONE

        firewallSwitch?.isChecked = false

        binding.btnPowerFirewall.setOnClickListener(this)
    }

    private fun searchApps(text: String?) {
        val context = context ?: return

        searchText = text

        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val allAppsSelected = binding.chipFirewallAll.isChecked
        val systemAppsSelected = binding.chipFirewallSystem.isChecked
        val userAppsSelected = binding.chipFirewallUser.isChecked

        if (text == null || text.isEmpty()) {

            savedAppsListWhenSearch?.let {
                appsList = it
                savedAppsListWhenSearch = null
            }

            if (systemAppsSelected) {
                chipSelectSystemApps(context)
            } else if (userAppsSelected) {
                chipSelectUserApps(context)
            }

            return
        }

        if (savedAppsListWhenSearch == null) {
            savedAppsListWhenSearch = CopyOnWriteArrayList<AppFirewall>(appsList)
        }

        appsList.clear()

        savedAppsListWhenSearch?.forEach { savedApp ->
            if (savedApp.applicationData.toString().lowercase(Locale.ROOT)
                    .contains(text.lowercase(Locale.ROOT).trim())) {
                if (allAppsSelected
                        || systemAppsSelected && savedApp.applicationData.system
                        || userAppsSelected && !savedApp.applicationData.system) {
                    appsList.add(savedApp)
                }

            }
        }

        updateTopIconsData()
        updateTopIcons(context)
    }

    private fun chipSelectAllApps(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        savedAppsListWhenSearch?.let { savedAppsList ->

            if (searchText == null || searchText?.isBlank() == true) {
                appsList = savedAppsList
                savedAppsListWhenSearch = null

                updateTopIconsData()
                updateTopIcons(context)

            } else {
                searchText?.let { text ->
                    searchApps(text)
                }

            }
        }
    }

    private fun chipSelectSystemApps(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        if (savedAppsListWhenSearch == null) {
            savedAppsListWhenSearch = CopyOnWriteArrayList<AppFirewall>(appsList)
        }

        appsList.clear()

        savedAppsListWhenSearch?.forEach { savedApp ->
            if (savedApp.applicationData.system) {
                if (searchText == null || searchText?.isBlank() == true) {
                    appsList.add(savedApp)
                } else {
                    searchText?.let {
                        if (savedApp.applicationData.toString().lowercase(Locale.ROOT)
                                        .contains(it.lowercase(Locale.ROOT).trim())) {
                            appsList.add(savedApp)
                        }
                    }

                }
            }
        }

        updateTopIconsData()
        updateTopIcons(context)
    }

    private fun chipSelectUserApps(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        if (savedAppsListWhenSearch == null) {
            savedAppsListWhenSearch = CopyOnWriteArrayList<AppFirewall>(appsList)
        }

        appsList.clear()

        savedAppsListWhenSearch?.forEach { savedApp ->
            if (!savedApp.applicationData.system) {
                if (searchText == null || searchText?.isBlank() == true) {
                    appsList.add(savedApp)
                } else {
                    searchText?.let {
                        if (savedApp.applicationData.toString().lowercase(Locale.ROOT)
                                        .contains(it.lowercase(Locale.ROOT).trim())) {
                            appsList.add(savedApp)
                        }
                    }

                }
            }
        }

        updateTopIconsData()
        updateTopIcons(context)
    }

    private fun updateTopIconsData() {
        val appListSize = appsList.size
        if (appListSize == 0) {
            return
        }

        allowLanForAll = appsList.count { it.allowLan } == appListSize

        allowWifiForAll = appsList.count { it.allowWifi } == appListSize

        allowGsmForAll = appsList.count { it.allowGsm } == appListSize

        allowRoamingForAll = appsList.count { it.allowRoaming } == appListSize

        allowVPNForAll = appsList.count { it.allowVPN } == appListSize
    }

    private fun updateTopIcons(context: Context) {
        updateLanIcon(context)
        updateWifiIcon(context)
        updateGsmIcon(context)
        updateRoamingIcon(context)
        updateVpnIcon(context)
    }

    private fun activateAllLan(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = CopyOnWriteArrayList<AppFirewall>()

        allowLanForAll = !allowLanForAll

        updateLanIcon(context)

        for (appFirewall: AppFirewall in appsList) {
            appFirewall.apply {
                allowLan = allowLanForAll
                activatedApps.add(this)
            }
        }
        appsList = activatedApps

        firewallAdapter?.notifyDataSetChanged()
    }

    fun updateLanIcon(context: Context?) {
        if (context == null) {
            return
        }

        val icFirewallLan = ContextCompat.getDrawable(context, R.drawable.ic_firewall_lan)
        val icFirewallLanGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_lan_green)

        if (allowLanForAll) {
            binding.btnTopLanFirewall.setImageDrawable(icFirewallLanGreen)
        } else {
            binding.btnTopLanFirewall.setImageDrawable(icFirewallLan)
        }
    }

    private fun activateAllWifi(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = CopyOnWriteArrayList<AppFirewall>()

        allowWifiForAll = !allowWifiForAll

        updateWifiIcon(context)

        for (appFirewall: AppFirewall in appsList) {
            appFirewall.apply {
                allowWifi = allowWifiForAll
                activatedApps.add(this)
            }
        }
        appsList = activatedApps

        firewallAdapter?.notifyDataSetChanged()
    }

    fun updateWifiIcon(context: Context?) {
        if (context == null) {
            return
        }

        val icFirewallWifi = ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_24)
        val icFirewallWifiGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_green_24)

        if (allowWifiForAll) {
            binding.btnTopWifiFirewall.setImageDrawable(icFirewallWifiGreen)
        } else {
            binding.btnTopWifiFirewall.setImageDrawable(icFirewallWifi)
        }
    }

    private fun activateAllGsm(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = CopyOnWriteArrayList<AppFirewall>()

        allowGsmForAll = !allowGsmForAll

        updateGsmIcon(context)

        for (appFirewall: AppFirewall in appsList) {
            appFirewall.apply {
                allowGsm = allowGsmForAll
                activatedApps.add(this)
            }
        }
        appsList = activatedApps

        firewallAdapter?.notifyDataSetChanged()
    }

    fun updateGsmIcon(context: Context?) {
        if (context == null) {
            return
        }

        val icFirewallGsm = ContextCompat.getDrawable(context, R.drawable.ic_firewall_gsm_24)
        val icFirewallGsmGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_gsm_green_24)

        if (allowGsmForAll) {
            binding.btnTopGsmFirewall.setImageDrawable(icFirewallGsmGreen)
        } else {
            binding.btnTopGsmFirewall.setImageDrawable(icFirewallGsm)
        }
    }

    private fun activateAllRoaming(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = CopyOnWriteArrayList<AppFirewall>()

        allowRoamingForAll = !allowRoamingForAll

        updateRoamingIcon(context)

        for (appFirewall: AppFirewall in appsList) {
            appFirewall.apply {
                allowRoaming = allowRoamingForAll
                activatedApps.add(this)
            }
        }
        appsList = activatedApps

        firewallAdapter?.notifyDataSetChanged()
    }

    fun updateRoamingIcon(context: Context?) {
        if (context == null) {
            return
        }

        val icFirewallRoaming = ContextCompat.getDrawable(context, R.drawable.ic_firewall_roaming_24)
        val icFirewallRoamingGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_roaming_green_24)

        if (allowRoamingForAll) {
            binding.btnTopRoamingFirewall.setImageDrawable(icFirewallRoamingGreen)
        } else {
            binding.btnTopRoamingFirewall.setImageDrawable(icFirewallRoaming)
        }
    }

    private fun activateAllVpn(context: Context) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = CopyOnWriteArrayList<AppFirewall>()

        allowVPNForAll = !allowVPNForAll

        updateVpnIcon(context)

        for (appFirewall: AppFirewall in appsList) {
            appFirewall.apply {
                allowVPN = allowVPNForAll
                activatedApps.add(this)
            }
        }
        appsList = activatedApps

        firewallAdapter?.notifyDataSetChanged()
    }

    fun updateVpnIcon(context: Context?) {
        if (context == null) {
            return
        }

        val icFirewallVpn = ContextCompat.getDrawable(context, R.drawable.ic_firewall_vpn_key_24)
        val icFirewallVpnGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_vpn_key_green_24)

        if (allowVPNForAll) {
            binding.btnTopVpnFirewall.setImageDrawable(icFirewallVpnGreen)
        } else {
            binding.btnTopVpnFirewall.setImageDrawable(icFirewallVpn)
        }
    }

    private fun activateAll(context: Context, activate: Boolean) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = CopyOnWriteArrayList<AppFirewall>()

        allowLanForAll = activate
        allowWifiForAll = activate
        allowGsmForAll = activate
        allowRoamingForAll = activate
        allowVPNForAll = activate

        updateTopIcons(context)

        for (appFirewall: AppFirewall in appsList) {
            appFirewall.apply {
                allowLan = activate
                allowWifi = activate
                allowGsm = activate
                allowRoaming = activate
                allowVPN = activate
                activatedApps.add(this)
            }
        }
        appsList = activatedApps

        firewallAdapter?.notifyDataSetChanged()
    }

    private fun activateAllFirsStart(context: Context) {
        activateAll(context, true)

        for (appFirewall: AppFirewall in appsList) {
            val uid = appFirewall.applicationData.uid
            appsAllowLan.add(uid)
            appsAllowWifi.add(uid)
            appsAllowGsm.add(uid)
            appsAllowRoaming.add(uid)
            appsAllowVpn.add(uid)
        }

        PrefManager(context).setSetStrPref(APPS_ALLOW_LAN_PREF, intSetToStringSet(appsAllowLan))
        PrefManager(context).setSetStrPref(APPS_ALLOW_WIFI_PREF, intSetToStringSet(appsAllowWifi))
        PrefManager(context).setSetStrPref(APPS_ALLOW_GSM_PREF, intSetToStringSet(appsAllowGsm))
        PrefManager(context).setSetStrPref(APPS_ALLOW_ROAMING, intSetToStringSet(appsAllowRoaming))
        if (modulesStatus.isRootAvailable) {
            PrefManager(context).setSetStrPref(APPS_ALLOW_VPN, intSetToStringSet(appsAllowVpn))
        }
    }
}

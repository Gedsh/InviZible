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

package pan.alexander.tordnscrypt.settings.firewall

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.CompoundButton
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.chip.ChipGroup
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.databinding.FragmentFirewallBinding
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.OnBackPressListener
import pan.alexander.tordnscrypt.settings.firewall.adapter.FirewallAdapter
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet
import javax.inject.Inject
import javax.inject.Named

@SuppressLint("NotifyDataSetChanged")
class FirewallFragment : Fragment(),
    View.OnClickListener,
    SearchView.OnQueryTextListener,
    ChipGroup.OnCheckedStateChangeListener,
    CompoundButton.OnCheckedChangeListener,
    OnBackPressListener {

    @Inject
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    lateinit var defaultPreferences: dagger.Lazy<SharedPreferences>

    @Inject
    lateinit var preferenceRepository: dagger.Lazy<PreferenceRepository>

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[FirewallViewModel::class.java]
    }

    @Inject
    lateinit var handler: dagger.Lazy<Handler>

    private var _binding: FragmentFirewallBinding? = null
    private val binding get() = _binding!!

    private val modulesStatus = ModulesStatus.getInstance()

    private var firewallAdapter: FirewallAdapter? = null
    private var lastVisibleAdapterPosition: Int = 0

    private var firewallSwitch: SwitchCompat? = null

    private val appsCurrentSet = ConcurrentSkipListSet<FirewallAppModel>()

    private var allowLanForAll = false
    private var allowWifiForAll = false
    private var allowGsmForAll = false
    private var allowRoamingForAll = false
    private var allowVPNForAll = false

    private var appsListComplete = false

    private var searchText: String? = null

    var firewallEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)

        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        firewallEnabled = preferenceRepository.get().getBoolPreference(FIREWALL_ENABLED)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirewallBinding.inflate(inflater, container, false)

        firewallAdapter = FirewallAdapter(
            requireContext(),
            defaultPreferences.get(),
            preferenceRepository.get(),
            ::allowLan,
            ::allowWifi,
            ::allowGsm,
            ::allowRoaming,
            ::allowVpn,
            ::onSortFinished
        )

        (binding.rvFirewallApps.itemAnimator as SimpleItemAnimator)
            .supportsChangeAnimations = false
        binding.rvFirewallApps.adapter = firewallAdapter

        if (firewallEnabled) {
            enableFirewall()
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

        binding.chipGroupFirewall.setOnCheckedStateChangeListener(this)

        binding.chipGroupFirewallSort.setOnCheckedStateChangeListener(this)

        searchText = null

        when {
            binding.chipFirewallSystem.isChecked -> chipSelectSystemApps()
            binding.chipFirewallUser.isChecked -> chipSelectUserApps()
            binding.chipFirewallAll.isChecked -> chipSelectAllApps()
            else -> updateTopIcons()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeFirewallState()
    }

    private fun observeFirewallState() {
        viewModel.firewallStateLiveData.observe(viewLifecycleOwner) {
            when (it) {
                FirewallState.Preparing -> firewallPreparing()
                FirewallState.Ready -> firewallReady()
            }
        }
    }

    private fun firewallPreparing() {
        binding.pbFirewallApp.isIndeterminate = true
        binding.pbFirewallApp.visibility = View.VISIBLE
        appsListComplete = false

        if (!binding.rvFirewallApps.isComputingLayout) {
            firewallAdapter?.updateItems(viewModel.appsCompleteSet, getSortMethod())
        }
    }

    private fun firewallReady() {
        val appListSize = appsCurrentSet.size
        allowLanForAll = viewModel.appsAllowLan.size == appListSize
        allowWifiForAll = viewModel.appsAllowWifi.size == appListSize
        allowGsmForAll = viewModel.appsAllowGsm.size == appListSize
        allowRoamingForAll = viewModel.appsAllowRoaming.size == appListSize
        allowVPNForAll = viewModel.appsAllowVpn.size == appListSize

        binding.pbFirewallApp.isIndeterminate = false
        binding.pbFirewallApp.visibility = View.GONE
        appsListComplete = true

        val preferences = preferenceRepository.get()
        val firewallFirstStart = !preferences.getBoolPreference(FIREWALL_WAS_STARTED)
        if (firewallFirstStart) {
            preferences.setBoolPreference(FIREWALL_WAS_STARTED, true)
        }

        if (firewallFirstStart) {
            activateAll(true)
            viewModel.activateAllFirsStart()
        }

        when {
            binding.chipFirewallSystem.isChecked -> chipSelectSystemApps()
            binding.chipFirewallUser.isChecked -> chipSelectUserApps()
            else -> chipSelectAllApps()
        }

        if (allowLanForAll || allowWifiForAll || allowGsmForAll
            || allowRoamingForAll || allowVPNForAll
        ) {
            updateTopIcons()
        }
    }

    override fun onResume() {
        super.onResume()

        activity?.title = ""

        if (searchText != null && appsListComplete && !binding.rvFirewallApps.isComputingLayout) {
            updateTopIconsData()
            updateTopIcons()
            firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
        }

        if (lastVisibleAdapterPosition > 0 && appsListComplete) {
            (binding.rvFirewallApps.layoutManager as LinearLayoutManager).scrollToPosition(
                lastVisibleAdapterPosition
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        return inflater.inflate(R.menu.firewall_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {

        (menu.findItem(R.id.firewall_search)?.actionView as? SearchView)?.setOnQueryTextListener(
            this
        )
        (menu.findItem(R.id.firewall_switch_item)
            ?.actionView?.findViewById<SwitchCompat>(R.id.menu_switch))?.let {

                firewallSwitch = it
                it.isChecked = firewallEnabled
                it.setOnCheckedChangeListener(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.tooltipText = getString(R.string.firewall_switch)
                }
            }

        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.firewall_settings) {
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, FirewallPreferencesFragment())
                .addToBackStack(null)
                .commit()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()

        if (appsCurrentSet.isNotEmpty() && appsListComplete) {

            lastVisibleAdapterPosition =
                (binding.rvFirewallApps.layoutManager as LinearLayoutManager)
                    .findFirstCompletelyVisibleItemPosition()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        handler.get().removeCallbacksAndMessages(null)

        _binding = null
        firewallAdapter = null
    }

    override fun onClick(v: View?) {
        val context = v?.context

        if (firewallEnabled && !appsListComplete || context == null) {
            return
        }

        when (v.id) {
            R.id.btnTopLanFirewall -> activateAllLan()
            R.id.btnTopWifiFirewall -> activateAllWifi()
            R.id.btnTopGsmFirewall -> activateAllGsm()
            R.id.btnTopRoamingFirewall -> activateAllRoaming()
            R.id.btnTopVpnFirewall -> activateAllVpn()
            R.id.btnTopCheckAllFirewall -> activateAll(true)
            R.id.btnTopUnCheckAllFirewall -> activateAll(false)
            R.id.btnPowerFirewall -> {
                enableFirewall()
                modulesStatus.setIptablesRulesUpdateRequested(context, true)
            }
            else -> loge("FirewallFragment onClick unknown id: ${v.id}")
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {

        if (!appsListComplete || binding.rvFirewallApps.isComputingLayout) {
            return false
        }

        searchApps(query)
        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())

        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {

        if (!appsListComplete || binding.rvFirewallApps.isComputingLayout) {
            return false
        }

        searchApps(newText)
        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())

        return true
    }

    override fun onCheckedChanged(group: ChipGroup, checkedIds: MutableList<Int>) {
        if (!appsListComplete || binding.rvFirewallApps.isComputingLayout) {
            return
        }

        when (checkedIds.firstOrNull()) {
            R.id.chipFirewallAll -> chipSelectAllApps()
            R.id.chipFirewallSystem -> chipSelectSystemApps()
            R.id.chipFirewallUser -> chipSelectUserApps()
            R.id.chipFirewallSortName -> sortByName()
            R.id.chipFirewallSortUid -> sortByUid()
            else -> loge("FirewallFragment chipGroup onCheckedChanged wrong id")
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        val context = buttonView?.context ?: return

        if (buttonView.id == R.id.menu_switch) {
            if (isChecked) {
                enableFirewall()
            } else {
                disableFirewall()
            }
            modulesStatus.setIptablesRulesUpdateRequested(context, true)
        }
    }

    private fun sortByName() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        firewallAdapter?.sortByName()
    }

    private fun sortByUid() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        firewallAdapter?.sortByUid()
    }

    private fun enableFirewall() {
        firewallEnabled = true
        preferenceRepository.get().setBoolPreference(FIREWALL_ENABLED, true)

        binding.llFirewallPower.visibility = View.GONE
        binding.llFirewallTop.visibility = View.VISIBLE
        binding.llFirewallMain.visibility = View.VISIBLE
        binding.rvFirewallApps.visibility = View.VISIBLE

        if (viewModel.appsCompleteSet.isEmpty()) {
            viewModel.getDeviceApps()
        }

        firewallSwitch?.isChecked = true

        binding.btnPowerFirewall.setOnClickListener(null)
    }

    private fun disableFirewall() {
        firewallEnabled = false
        preferenceRepository.get().setBoolPreference(FIREWALL_ENABLED, false)

        binding.llFirewallPower.visibility = View.VISIBLE
        binding.llFirewallTop.visibility = View.GONE
        binding.llFirewallMain.visibility = View.GONE
        binding.rvFirewallApps.visibility = View.GONE
        binding.pbFirewallApp.visibility = View.GONE

        firewallSwitch?.isChecked = false

        binding.btnPowerFirewall.setOnClickListener(this)
    }

    private fun searchApps(text: String?) {

        searchText = text?.trim()?.lowercase()

        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val allAppsSelected = binding.chipFirewallAll.isChecked
        val systemAppsSelected = binding.chipFirewallSystem.isChecked
        val userAppsSelected = binding.chipFirewallUser.isChecked

        if (text?.isEmpty() == true) {

            appsCurrentSet.clear()
            appsCurrentSet.addAll(viewModel.appsCompleteSet)

            if (systemAppsSelected) {
                chipSelectSystemApps()
            } else if (userAppsSelected) {
                chipSelectUserApps()
            }

            return
        }

        appsCurrentSet.clear()

        viewModel.appsCompleteSet.forEach { completeSet ->
            if (completeSet.applicationData.toString().lowercase().contains(searchText ?: "")
                || completeSet.applicationData.pack.lowercase().contains(searchText ?: "")
            ) {
                if (allAppsSelected
                    || systemAppsSelected && completeSet.applicationData.system
                    || userAppsSelected && !completeSet.applicationData.system
                ) {
                    appsCurrentSet.add(completeSet)
                }

            }
        }

        updateTopIconsData()
        updateTopIcons()
    }

    private fun chipSelectAllApps() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        appsCurrentSet.clear()

        if (searchText == null || searchText?.isBlank() == true) {
            appsCurrentSet.addAll(viewModel.appsCompleteSet)

            updateTopIconsData()
            updateTopIcons()

        } else {
            searchText?.let { text ->
                searchApps(text)
            }

        }

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private fun chipSelectSystemApps() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        appsCurrentSet.clear()

        viewModel.appsCompleteSet.forEach { savedApp ->
            if (savedApp.applicationData.system) {
                if (searchText == null || searchText?.isBlank() == true) {
                    appsCurrentSet.add(savedApp)
                } else {
                    searchText?.let {
                        if (savedApp.applicationData.toString().lowercase(Locale.ROOT)
                                .contains(it.lowercase(Locale.ROOT).trim())
                        ) {
                            appsCurrentSet.add(savedApp)
                        }
                    }

                }
            }
        }

        updateTopIconsData()
        updateTopIcons()

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private fun chipSelectUserApps() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        appsCurrentSet.clear()

        viewModel.appsCompleteSet.forEach { savedApp ->
            if (!savedApp.applicationData.system) {
                if (searchText == null || searchText?.isBlank() == true) {
                    appsCurrentSet.add(savedApp)
                } else {
                    searchText?.let {
                        if (savedApp.applicationData.toString().lowercase(Locale.ROOT)
                                .contains(it.lowercase(Locale.ROOT).trim())
                        ) {
                            appsCurrentSet.add(savedApp)
                        }
                    }

                }
            }
        }

        updateTopIconsData()
        updateTopIcons()

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private fun updateTopIconsData() {
        val appListSize = appsCurrentSet.size
        if (appListSize == 0) {
            return
        }

        allowLanForAll = appsCurrentSet.count { it.allowLan } == appListSize

        allowWifiForAll = appsCurrentSet.count { it.allowWifi } == appListSize

        allowGsmForAll = appsCurrentSet.count { it.allowGsm } == appListSize

        allowRoamingForAll = appsCurrentSet.count { it.allowRoaming } == appListSize

        allowVPNForAll = appsCurrentSet.count { it.allowVPN } == appListSize
    }

    private fun updateTopIcons() {
        updateLanIcon()
        updateWifiIcon()
        updateGsmIcon()
        updateRoamingIcon()
        updateVpnIcon()
    }

    private fun activateAllLan() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = hashSetOf<FirewallAppModel>()

        allowLanForAll = !allowLanForAll

        updateLanIcon()

        for (firewallAppModel: FirewallAppModel in appsCurrentSet) {
            firewallAppModel.apply {
                allowLan = if (viewModel.criticalSystemUids.contains(applicationData.uid)) {
                    true
                } else {
                    allowLanForAll
                }
                activatedApps.add(this)
            }
        }
        appsCurrentSet.clear()
        appsCurrentSet.addAll(activatedApps)

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private fun updateLanIcon() {
        if (allowLanForAll) {
            val icFirewallLanGreen =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_lan_green)
            binding.btnTopLanFirewall.setImageDrawable(icFirewallLanGreen)
        } else {
            val icFirewallLan =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_lan)
            binding.btnTopLanFirewall.setImageDrawable(icFirewallLan)
        }
    }

    private fun activateAllWifi() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = hashSetOf<FirewallAppModel>()

        allowWifiForAll = !allowWifiForAll

        updateWifiIcon()

        for (firewallAppModel: FirewallAppModel in appsCurrentSet) {
            firewallAppModel.apply {
                allowWifi = if (viewModel.criticalSystemUids.contains(applicationData.uid)) {
                    true
                } else {
                    allowWifiForAll
                }
                activatedApps.add(this)
            }
        }
        appsCurrentSet.clear()
        appsCurrentSet.addAll(activatedApps)

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private val icFirewallWifiGreen by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_wifi_green_24)
    }
    private val icFirewallWifi by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_wifi_24)
    }

    private fun updateWifiIcon() {
        if (allowWifiForAll) {
            binding.btnTopWifiFirewall.setImageDrawable(icFirewallWifiGreen)
        } else {
            binding.btnTopWifiFirewall.setImageDrawable(icFirewallWifi)
        }
    }

    private fun activateAllGsm() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = hashSetOf<FirewallAppModel>()

        allowGsmForAll = !allowGsmForAll

        updateGsmIcon()

        for (firewallAppModel: FirewallAppModel in appsCurrentSet) {
            firewallAppModel.apply {
                allowGsm = if (viewModel.criticalSystemUids.contains(applicationData.uid)) {
                    true
                } else {
                    allowGsmForAll
                }
                activatedApps.add(this)
            }
        }
        appsCurrentSet.clear()
        appsCurrentSet.addAll(activatedApps)

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private val icFirewallGsmGreen by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_gsm_green_24)
    }
    private val icFirewallGsm by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_gsm_24)
    }

    private fun updateGsmIcon() {
        if (allowGsmForAll) {
            binding.btnTopGsmFirewall.setImageDrawable(icFirewallGsmGreen)
        } else {
            binding.btnTopGsmFirewall.setImageDrawable(icFirewallGsm)
        }
    }

    private fun activateAllRoaming() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = hashSetOf<FirewallAppModel>()

        allowRoamingForAll = !allowRoamingForAll

        updateRoamingIcon()

        for (firewallAppModel: FirewallAppModel in appsCurrentSet) {
            firewallAppModel.apply {
                allowRoaming = if (viewModel.criticalSystemUids.contains(applicationData.uid)) {
                    true
                } else {
                    allowRoamingForAll
                }
                activatedApps.add(this)
            }
        }
        appsCurrentSet.clear()
        appsCurrentSet.addAll(activatedApps)

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private val icFirewallRoamingGreen by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_roaming_green_24)
    }
    private val icFirewallRoaming by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_roaming_24)
    }

    private fun updateRoamingIcon() {
        if (allowRoamingForAll) {

            binding.btnTopRoamingFirewall.setImageDrawable(icFirewallRoamingGreen)
        } else {

            binding.btnTopRoamingFirewall.setImageDrawable(icFirewallRoaming)
        }
    }

    private fun activateAllVpn() {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = hashSetOf<FirewallAppModel>()

        allowVPNForAll = !allowVPNForAll

        updateVpnIcon()

        for (firewallAppModel: FirewallAppModel in appsCurrentSet) {
            firewallAppModel.apply {
                allowVPN = if (viewModel.criticalSystemUids.contains(applicationData.uid)) {
                    true
                } else {
                    allowVPNForAll
                }
                activatedApps.add(this)
            }
        }
        appsCurrentSet.clear()
        appsCurrentSet.addAll(activatedApps)

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private val icFirewallVpnGreen by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_vpn_key_green_24)
    }
    private val icFirewallVpn by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_firewall_vpn_key_24)
    }

    private fun updateVpnIcon() {
        if (allowVPNForAll) {

            binding.btnTopVpnFirewall.setImageDrawable(icFirewallVpnGreen)
        } else {

            binding.btnTopVpnFirewall.setImageDrawable(icFirewallVpn)
        }
    }

    private fun activateAll(activate: Boolean) {
        if (binding.rvFirewallApps.isComputingLayout || !appsListComplete) {
            return
        }

        val activatedApps = hashSetOf<FirewallAppModel>()

        allowLanForAll = activate
        allowWifiForAll = activate
        allowGsmForAll = activate
        allowRoamingForAll = activate
        allowVPNForAll = activate

        updateTopIcons()

        for (firewallAppModel: FirewallAppModel in appsCurrentSet) {
            firewallAppModel.apply {
                allowLan = activate || viewModel.criticalSystemUids.contains(applicationData.uid)
                allowWifi = activate || viewModel.criticalSystemUids.contains(applicationData.uid)
                allowGsm = activate || viewModel.criticalSystemUids.contains(applicationData.uid)
                allowRoaming =
                    activate || viewModel.criticalSystemUids.contains(applicationData.uid)
                allowVPN = activate || viewModel.criticalSystemUids.contains(applicationData.uid)
                activatedApps.add(this)
            }
        }
        appsCurrentSet.clear()
        appsCurrentSet.addAll(activatedApps)

        firewallAdapter?.updateItems(appsCurrentSet, getSortMethod())
    }

    private fun allowLan(uid: Int) {
        appsCurrentSet.find { it.applicationData.uid == uid }?.let { app ->
            app.allowLan = !app.allowLan
            setItem(app)

            if (allowLanForAll) {
                allowLanForAll = false
                updateLanIcon()
            } else if (appsCurrentSet.count { it.allowLan } == appsCurrentSet.size) {
                allowLanForAll = true
                updateLanIcon()
            }
        }
    }

    private fun allowWifi(uid: Int) {
        appsCurrentSet.find { it.applicationData.uid == uid }?.let { app ->
            app.allowWifi = !app.allowWifi
            setItem(app)

            if (allowWifiForAll) {
                allowWifiForAll = false
                updateWifiIcon()
            } else if (appsCurrentSet.count { it.allowWifi } == appsCurrentSet.size) {
                allowWifiForAll = true
                updateWifiIcon()
            }
        }
    }

    private fun allowGsm(uid: Int) {
        appsCurrentSet.find { it.applicationData.uid == uid }?.let { app ->
            app.allowGsm = !app.allowGsm
            setItem(app)

            if (allowGsmForAll) {
                allowGsmForAll = false
                updateGsmIcon()
            } else if (appsCurrentSet.count { it.allowGsm } == appsCurrentSet.size) {
                allowGsmForAll = true
                updateGsmIcon()
            }
        }
    }

    private fun allowRoaming(uid: Int) {
        appsCurrentSet.find { it.applicationData.uid == uid }?.let { app ->
            app.allowRoaming = !app.allowRoaming
            setItem(app)

            if (allowRoamingForAll) {
                allowRoamingForAll = false
                updateRoamingIcon()
            } else if (appsCurrentSet.count { it.allowRoaming } == appsCurrentSet.size) {
                allowRoamingForAll = true
                updateRoamingIcon()
            }
        }
    }

    private fun allowVpn(uid: Int) {
        appsCurrentSet.find { it.applicationData.uid == uid }?.let { app ->
            app.allowVPN = !app.allowVPN
            setItem(app)

            if (allowVPNForAll) {
                allowVPNForAll = false
                updateVpnIcon()
            } else if (appsCurrentSet.count { it.allowVPN } == appsCurrentSet.size) {
                allowVPNForAll = true
                updateVpnIcon()
            }
        }
    }

    private fun setItem(firewallAppModel: FirewallAppModel) {
        appsCurrentSet.remove(firewallAppModel)
        appsCurrentSet.add(firewallAppModel)

        viewModel.appsCompleteSet.remove(firewallAppModel)
        viewModel.appsCompleteSet.add(firewallAppModel)
    }

    private fun getSortMethod(): FirewallAdapter.SortMethod =
        when {
            binding.chipFirewallSortName.isChecked -> FirewallAdapter.SortMethod.BY_NAME
            binding.chipFirewallSortUid.isChecked -> FirewallAdapter.SortMethod.BY_UID
            else -> FirewallAdapter.SortMethod.BY_NAME
        }

    private fun onSortFinished() {
        handler.get().post { _binding?.rvFirewallApps?.scrollToPosition(0) }
    }

    override fun onBackPressed(): Boolean {
        if (isVisible) {
            val savingFirewallChangesRequired: Boolean = viewModel.isFirewallChangesSavingRequired()
            val saveFirewallChangesFragmentIsDisplayed =
                parentFragmentManager.findFragmentByTag(SaveFirewallChangesDialog.TAG) != null
            if (savingFirewallChangesRequired && !saveFirewallChangesFragmentIsDisplayed) {
                SaveFirewallChangesDialog().show(
                    parentFragmentManager,
                    SaveFirewallChangesDialog.TAG
                )
                return true
            }
            return false
        }
        return false
    }

    companion object {
        const val TAG = "pan.alexander.tordnscrypt.settings.FIREWALL_FRAGMENT"
    }
}

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

package pan.alexander.tordnscrypt.settings.firewall.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.RecyclerView
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.firewall.FirewallAppModel
import pan.alexander.tordnscrypt.settings.firewall.toIntSet
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_NEWLY_INSTALLED
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_SHOWS_ALL_APPS

class FirewallAdapter(
    context: Context,
    defaultPreferences: SharedPreferences,
    preferences: PreferenceRepository,
    private val onLanClicked: (uid: Int) -> Unit,
    private val onWiFiClicked: (uid: Int) -> Unit,
    private val onGsmClicked: (uid: Int) -> Unit,
    private val onRoamingClicked: (uid: Int) -> Unit,
    private val onVpnClicked: (uid: Int) -> Unit,
    private val onSortFinished: () -> Unit
) : RecyclerView.Adapter<FirewallAdapter.FirewallViewHolder>() {

    private val diff = AsyncListDiffer(this, FirewallAdapterRecyclerItemDiffCallback()).also {
        it.addListListener { _, _ ->
            onSortFinished()
        }
    }

    private val rootMode = ModulesStatus.getInstance().mode == OperationMode.ROOT_MODE
    private val appsNewlyInstalledSavedSet =
        preferences.getStringSetPreference(APPS_NEWLY_INSTALLED).toIntSet()
            .also { preferences.setStringSetPreference(APPS_NEWLY_INSTALLED, setOf()) }
    private val showAllApps = defaultPreferences.getBoolean(FIREWALL_SHOWS_ALL_APPS, false)

    private val comparatorByName: Comparator<AdapterItem> = compareBy(
        { !it.newlyInstalled },
        { !(it.lan || it.wifi || it.gsm || it.roaming || it.vpn && rootMode) },
        { it.label }
    )

    private val comparatorByUid: Comparator<AdapterItem> = compareBy(
        { !it.newlyInstalled },
        { !(it.lan || it.wifi || it.gsm || it.roaming || it.vpn && rootMode) },
        { it.uid }
    )

    @UiThread
    fun updateItems(firewallApps: Set<FirewallAppModel>, sortMethod: SortMethod) {
        diff.submitList(
            firewallApps.map {
                AdapterItem(
                    uid = it.applicationData.uid,
                    label = it.applicationData.toString(),
                    icon = it.applicationData.icon,
                    system = it.applicationData.system,
                    hasInternetPermission = it.applicationData.hasInternetPermission,
                    lan = it.allowLan,
                    wifi = it.allowWifi,
                    gsm = it.allowGsm,
                    roaming = it.allowRoaming,
                    vpn = it.allowVPN,
                    newlyInstalled = appsNewlyInstalledSavedSet.contains(it.applicationData.uid)
                )
            }.filter {
                if (showAllApps) {
                    true
                } else {
                    it.hasInternetPermission || it.system
                }
            }.sortedWith(
                when (sortMethod) {
                    SortMethod.BY_NAME -> comparatorByName
                    SortMethod.BY_UID -> comparatorByUid
                }
            )
        )
    }

    fun sortByName() {
        diff.submitList(diff.currentList.sortedWith(comparatorByName))
    }

    fun sortByUid() {
        diff.submitList(diff.currentList.sortedWith(comparatorByUid))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirewallViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_firewall, parent, false)

        val modulesStatus = ModulesStatus.getInstance()
        if (modulesStatus.mode == OperationMode.VPN_MODE) {
            itemView.findViewById<ImageButton>(R.id.btnVpnFirewall).visibility = View.GONE
        }

        return FirewallViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FirewallViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return diff.currentList.size
    }

    private fun getItem(position: Int): AdapterItem {
        return diff.currentList[position]
    }

    private val icFirewallLan = ContextCompat.getDrawable(context, R.drawable.ic_firewall_lan)
    private val icFirewallLanGreen =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_lan_green)
    private val icFirewallWifi =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_24)
    private val icFirewallWifiGreen =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_green_24)
    private val icFirewallGsm =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_gsm_24)
    private val icFirewallGsmGreen =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_gsm_green_24)
    private val icFirewallRoaming =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_roaming_24)
    private val icFirewallRoamingGreen =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_roaming_green_24)
    private val icFirewallVpn =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_vpn_key_24)
    private val icFirewallVpnGreen =
        ContextCompat.getDrawable(context, R.drawable.ic_firewall_vpn_key_green_24)

    private val colorRed = ContextCompat.getColor(context, R.color.colorAlert)
    private val colorBlack = ContextCompat.getColor(
        context,
        R.color.textModuleStatusColorStopped
    )
    private val colorGreen = ContextCompat.getColor(context, R.color.userAppWithoutInternetPermission)
    private val colorBrown = ContextCompat.getColor(context, R.color.systemAppWithoutInternetPermission)

    inner class FirewallViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
        View.OnClickListener {
        val context: Context = itemView.context

        private val imgAppIconFirewall = itemView.findViewById<ImageView>(R.id.imgAppIconFirewall)
        private val btnLanFirewall = itemView.findViewById<ImageButton>(R.id.btnLanFirewall)
            .also { it.setOnClickListener(this) }
        private val btnWifiFirewall = itemView.findViewById<ImageButton>(R.id.btnWifiFirewall)
            .also { it.setOnClickListener(this) }
        private val btnGsmFirewall = itemView.findViewById<ImageButton>(R.id.btnGsmFirewall)
            .also { it.setOnClickListener(this) }
        private val btnRoamingFirewall = itemView.findViewById<ImageButton>(R.id.btnRoamingFirewall)
            .also { it.setOnClickListener(this) }
        private val btnVpnFirewall = itemView.findViewById<ImageButton>(R.id.btnVpnFirewall)
            .also { it.setOnClickListener(this) }
        private val tvAppName = itemView.findViewById<TextView>(R.id.tvAppName)

        fun bind(position: Int) {

            if (position < 0 || position > itemCount - 1) {
                return
            }

            val appFirewall = getItem(position)

            imgAppIconFirewall.setImageDrawable(appFirewall.icon)
            val description = StringBuilder().apply {
                append(appFirewall.label)
                if (appFirewall.uid >= 0) {
                    append(" ").append("\u00B7").append(" ")
                    append("UID").append(" ").append(appFirewall.uid)
                }
            }
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M) {
                tvAppName.text = Html.fromHtml(description.toString(), Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                tvAppName.text = Html.fromHtml(description.toString())
            }
            when {
                appFirewall.system && appFirewall.hasInternetPermission ->
                    tvAppName.setTextColor(colorRed)
                appFirewall.system -> tvAppName.setTextColor(colorBrown)
                !appFirewall.hasInternetPermission ->
                    tvAppName.setTextColor(colorGreen)
                else -> tvAppName.setTextColor(colorBlack)
            }

            btnLanFirewall.setImageDrawable(
                if (appFirewall.lan)
                    icFirewallLanGreen
                else
                    icFirewallLan
            )

            btnWifiFirewall.setImageDrawable(
                if (appFirewall.wifi)
                    icFirewallWifiGreen
                else
                    icFirewallWifi
            )

            btnGsmFirewall.setImageDrawable(
                if (appFirewall.gsm)
                    icFirewallGsmGreen
                else
                    icFirewallGsm
            )

            btnRoamingFirewall.setImageDrawable(
                if (appFirewall.roaming)
                    icFirewallRoamingGreen
                else
                    icFirewallRoaming
            )

            btnVpnFirewall.setImageDrawable(
                if (appFirewall.vpn)
                    icFirewallVpnGreen
                else
                    icFirewallVpn
            )
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onClick(v: View?) {
            val id = v?.id
            val position = bindingAdapterPosition

            if (id == null || position < 0 || position > itemCount - 1) {
                return
            }

            val item = getItem(position)

            when (id) {
                R.id.btnLanFirewall -> {
                    item.lan = !item.lan
                    onLanClicked(item.uid)
                }
                R.id.btnWifiFirewall -> {
                    item.wifi = !item.wifi
                    onWiFiClicked(item.uid)
                }
                R.id.btnGsmFirewall -> {
                    item.gsm = !item.gsm
                    onGsmClicked(item.uid)
                }
                R.id.btnRoamingFirewall -> {
                    item.roaming = !item.roaming
                    onRoamingClicked(item.uid)
                }
                R.id.btnVpnFirewall -> {
                    item.vpn = !item.vpn
                    onVpnClicked(item.uid)
                }
                else -> {
                    loge("FirewallAdapter unknown id onclick $id"); return
                }
            }
            notifyItemChanged(position)
        }
    }

    enum class SortMethod {
        BY_NAME,
        BY_UID
    }

    data class AdapterItem(
        val uid: Int,
        val label: String,
        val icon: Drawable?,
        val system: Boolean,
        val hasInternetPermission: Boolean,
        var lan: Boolean,
        var wifi: Boolean,
        var gsm: Boolean,
        var roaming: Boolean,
        var vpn: Boolean,
        val newlyInstalled: Boolean
    )
}

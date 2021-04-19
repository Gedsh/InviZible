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

import android.content.Context
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.OperationMode

class FirewallAdapter(private val firewallFragment: FirewallFragment) :
        RecyclerView.Adapter<FirewallAdapter.FirewallViewHolder>() {

    private var context: Context? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirewallViewHolder {
        context = parent.context
        val itemView = LayoutInflater.from(context).inflate(R.layout.item_firewall, parent, false)

        val modulesStatus = ModulesStatus.getInstance()
        if (modulesStatus.mode == OperationMode.VPN_MODE) {
            itemView.findViewById<ImageButton>(R.id.btnVpnFirewall).visibility = View.GONE
        }

        return FirewallViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FirewallViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemCount(): Int {
        return firewallFragment.appsList.size
    }

    fun getItem(position: Int): AppFirewall {
        return firewallFragment.appsList[position]
    }

    private fun setItem(position: Int,
                        appFirewall: AppFirewall) {
        firewallFragment.appsList[position] = appFirewall

        val uid = appFirewall.applicationData.uid
        run label@{
            firewallFragment.savedAppsListWhenSearch?.forEachIndexed { index, savedApp ->
                if (savedApp.applicationData.uid == uid) {
                    firewallFragment.savedAppsListWhenSearch?.set(index, appFirewall)
                    return@label
                }
            }
        }
    }

    private fun allowLan(position: Int, appFirewall: AppFirewall, allow: Boolean) {
        appFirewall.allowLan = allow
        setItem(position, appFirewall)

        if (firewallFragment.allowLanForAll) {
            firewallFragment.allowLanForAll = false
            firewallFragment.updateLanIcon(context)
        } else if (firewallFragment.appsList.count { it.allowLan } == firewallFragment.appsList.size) {
            firewallFragment.allowLanForAll = true
            firewallFragment.updateLanIcon(context)
        }
    }

    private fun allowWifi(position: Int, appFirewall: AppFirewall, allow: Boolean) {
        appFirewall.allowWifi = allow
        setItem(position, appFirewall)

        if (firewallFragment.allowWifiForAll) {
            firewallFragment.allowWifiForAll = false
            firewallFragment.updateWifiIcon(context)
        } else if (firewallFragment.appsList.count { it.allowWifi } == firewallFragment.appsList.size) {
            firewallFragment.allowWifiForAll = true
            firewallFragment.updateWifiIcon(context)
        }
    }

    private fun allowGsm(position: Int, appFirewall: AppFirewall, allow: Boolean) {
        appFirewall.allowGsm = allow
        setItem(position, appFirewall)

        if (firewallFragment.allowGsmForAll) {
            firewallFragment.allowGsmForAll = false
            firewallFragment.updateGsmIcon(context)
        } else if (firewallFragment.appsList.count { it.allowGsm } == firewallFragment.appsList.size) {
            firewallFragment.allowGsmForAll = true
            firewallFragment.updateGsmIcon(context)
        }
    }

    private fun allowRoaming(position: Int, appFirewall: AppFirewall, allow: Boolean) {
        appFirewall.allowRoaming = allow
        setItem(position, appFirewall)

        if (firewallFragment.allowRoamingForAll) {
            firewallFragment.allowRoamingForAll = false
            firewallFragment.updateRoamingIcon(context)
        } else if (firewallFragment.appsList.count { it.allowRoaming } == firewallFragment.appsList.size) {
            firewallFragment.allowRoamingForAll = true
            firewallFragment.updateRoamingIcon(context)
        }
    }

    private fun allowVpn(position: Int, appFirewall: AppFirewall, allow: Boolean) {
        appFirewall.allowVPN = allow
        setItem(position, appFirewall)

        if (firewallFragment.allowVPNForAll) {
            firewallFragment.allowVPNForAll = false
            firewallFragment.updateVpnIcon(context)
        } else if (firewallFragment.appsList.count { it.allowVPN } == firewallFragment.appsList.size) {
            firewallFragment.allowVPNForAll = true
            firewallFragment.updateVpnIcon(context)
        }
    }

    inner class FirewallViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
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

        private val icFirewallLan = ContextCompat.getDrawable(context, R.drawable.ic_firewall_lan)
        private val icFirewallLanGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_lan_green)
        private val icFirewallWifi = ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_24)
        private val icFirewallWifiGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_wifi_green_24)
        private val icFirewallGsm = ContextCompat.getDrawable(context, R.drawable.ic_firewall_gsm_24)
        private val icFirewallGsmGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_gsm_green_24)
        private val icFirewallRoaming = ContextCompat.getDrawable(context, R.drawable.ic_firewall_roaming_24)
        private val icFirewallRoamingGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_roaming_green_24)
        private val icFirewallVpn = ContextCompat.getDrawable(context, R.drawable.ic_firewall_vpn_key_24)
        private val icFirewallVpnGreen = ContextCompat.getDrawable(context, R.drawable.ic_firewall_vpn_key_green_24)


        fun bind(position: Int) {

            if (position < 0 || position > itemCount - 1) {
                return
            }

            val appFirewall = getItem(position)

            imgAppIconFirewall.setImageDrawable(appFirewall.applicationData.icon)
            val description = StringBuilder().apply {
                append(appFirewall.applicationData.toString())
                if (appFirewall.applicationData.uid >= 0) {
                    append(" ").append("\u00B7").append(" ")
                    append("UID").append(" ").append(appFirewall.applicationData.uid)
                }
            }
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.M) {
                tvAppName.text = Html.fromHtml(description.toString(), Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                tvAppName.text = Html.fromHtml(description.toString())
            }
            if (appFirewall.applicationData.system) {
                tvAppName.setTextColor(ContextCompat.getColor(context, R.color.colorAlert))
            } else {
                tvAppName.setTextColor(ContextCompat.getColor(context, R.color.textModuleStatusColorStopped))
            }

            btnLanFirewall.setImageDrawable(
                    if (appFirewall.allowLan)
                        icFirewallLanGreen
                    else
                        icFirewallLan
            )


            btnWifiFirewall.setImageDrawable(
                    if (appFirewall.allowWifi)
                        icFirewallWifiGreen
                    else
                        icFirewallWifi
            )

            btnGsmFirewall.setImageDrawable(
                    if (appFirewall.allowGsm)
                        icFirewallGsmGreen
                    else
                        icFirewallGsm
            )

            btnRoamingFirewall.setImageDrawable(
                    if (appFirewall.allowRoaming)
                        icFirewallRoamingGreen
                    else
                        icFirewallRoaming
            )

            btnVpnFirewall.setImageDrawable(
                    if (appFirewall.allowVPN)
                        icFirewallVpnGreen
                    else
                        icFirewallVpn
            )
        }

        override fun onClick(v: View?) {
            val id = v?.id
            val position = bindingAdapterPosition

            if (id == null || position < 0 || position > itemCount - 1
                    || !firewallFragment.appsListComplete) {
                return
            }

            val item = getItem(position)

            when (id) {
                R.id.btnLanFirewall -> allowLan(position, item, !item.allowLan)
                R.id.btnWifiFirewall -> allowWifi(position, item, !item.allowWifi)
                R.id.btnGsmFirewall -> allowGsm(position, item, !item.allowGsm)
                R.id.btnRoamingFirewall -> allowRoaming(position, item, !item.allowRoaming)
                R.id.btnVpnFirewall -> allowVpn(position, item, !item.allowVPN)
                else -> {
                    Log.e(LOG_TAG, "FirewallAdapter unknown id onclick $id"); return
                }
            }

            notifyDataSetChanged()
        }
    }
}

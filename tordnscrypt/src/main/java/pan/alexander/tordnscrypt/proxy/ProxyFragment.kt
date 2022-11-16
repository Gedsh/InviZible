package pan.alexander.tordnscrypt.proxy

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

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.settings.SettingsActivity
import pan.alexander.tordnscrypt.databinding.FragmentProxyBinding
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_ADDRESS
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PORT
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.util.concurrent.Future
import javax.inject.Inject

const val CLEARNET_APPS_FOR_PROXY = "clearnetAppsForProxy"
private val IP_REGEX = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
private val PORT_REGEX = Regex("^\\d+$")

class ProxyFragment : Fragment(), View.OnClickListener, TextWatcher {

    @Inject
    lateinit var preferenceRepository: dagger.Lazy<PreferenceRepository>
    @Inject
    lateinit var cachedExecutor: CachedExecutor
    @Inject
    lateinit var handler: dagger.Lazy<Handler>
    @Inject
    lateinit var proxyHelper: ProxyHelper

    private var _binding: FragmentProxyBinding? = null
    private val binding get() = _binding!!

    private var sharedPreferences: SharedPreferences? = null

    private var etBackground: Drawable? = null
    private var futureTask: Future<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)

        super.onCreate(savedInstanceState)

        if (activity == null) {
            return
        }

        val context = activity as Context

        activity?.setTitle(R.string.pref_common_proxy_categ)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentProxyBinding.inflate(inflater, container, false)

        val passAndNameIsEmpty = binding.etProxyPass.text.toString().trim().isEmpty()
                && binding.etProxyUserName.text.toString().trim().isEmpty()

        (binding.btnSelectWoProxyApps as Button).setOnClickListener(this)
        (binding.btnProxySave as Button).setOnClickListener(this)

        (binding.chbProxyDNSCrypt as CompoundButton).apply {
            isEnabled = passAndNameIsEmpty
            isChecked = getBoolFromSharedPreferences("ProxifyDNSCrypt")
        }
        (binding.chbProxyTor as CompoundButton).apply {
            isEnabled = passAndNameIsEmpty
            isChecked = getBoolFromSharedPreferences("ProxifyTor")
        }
        (binding.chbProxyITPD as CompoundButton).apply {
            isEnabled = passAndNameIsEmpty
            isChecked = getBoolFromSharedPreferences("ProxifyITPD")
        }

        binding.etProxyServer.setText(getTextFromSharedPreferences(PROXY_ADDRESS))
        binding.etProxyPort.setText(getTextFromSharedPreferences(PROXY_PORT))

        binding.etProxyUserName.apply {
            setText(getTextFromSharedPreferences("ProxyUserName"))
            addTextChangedListener(this@ProxyFragment)
        }
        binding.etProxyPass.apply {
            setText(getTextFromSharedPreferences("ProxyPass"))
            addTextChangedListener(this@ProxyFragment)
        }

        etBackground = binding.etProxyServer.background

        return binding.root
    }

    override fun onStop() {
        super.onStop()

        val context = activity ?: return

        var serverOrPortChanged = false

        val activateDNSCryptProxy = binding.chbProxyDNSCrypt.isEnabled && binding.chbProxyDNSCrypt.isChecked
        val activateTorProxy = binding.chbProxyTor.isEnabled && binding.chbProxyTor.isChecked
        val activateITPDProxy = binding.chbProxyITPD.isEnabled && binding.chbProxyITPD.isChecked

        saveToSharedPreferences("ProxifyDNSCrypt", activateDNSCryptProxy)
        saveToSharedPreferences("ProxifyTor", activateTorProxy)
        saveToSharedPreferences("ProxifyITPD", activateITPDProxy)

        val proxyServer = binding.etProxyServer.text.toString().trim()
        val proxyPort = binding.etProxyPort.text.toString().trim()
        val proxyUserName = binding.etProxyUserName.text.toString().trim()
        val proxyPass = binding.etProxyPass.text.toString().trim()

        if (proxyServer != getTextFromSharedPreferences(PROXY_ADDRESS)
                || proxyPort != getTextFromSharedPreferences(PROXY_PORT)) {
            serverOrPortChanged = true
        }

        saveToSharedPreferences(PROXY_ADDRESS, proxyServer)
        saveToSharedPreferences(PROXY_PORT, proxyPort)
        saveToSharedPreferences("ProxyUserName", proxyUserName)
        saveToSharedPreferences("ProxyPass", proxyPass)

        val setBypassProxy = preferenceRepository.get().getStringSetPreference(CLEARNET_APPS_FOR_PROXY)

        if (proxyServer.isNotEmpty() && proxyPort.isNotEmpty()
                && (setBypassProxy.isNotEmpty() || proxyServer != LOOPBACK_ADDRESS)) {
            proxyHelper.manageProxy(proxyServer, proxyPort, serverOrPortChanged,
                    activateDNSCryptProxy, activateTorProxy, activateITPDProxy)
        } else {
            proxyHelper.manageProxy(proxyServer, proxyPort, false,
                    enableDNSCryptProxy = false, enableTorProxy = false, enableItpdProxy = false)
        }

        Toast.makeText(context, R.string.toastSettings_saved, Toast.LENGTH_SHORT).show()

    }

    override fun onDestroyView() {
        super.onDestroyView()

        handler.get().removeCallbacksAndMessages(null)

        futureTask?.let { if (it.isCancelled) it.cancel(true) }

        _binding = null
    }

    override fun onClick(view: View?) {
        when (view?.id) {
            binding.btnSelectWoProxyApps.id -> openSelectApplicationsFragment()
            binding.btnProxySave.id -> checkProxy()
        }
    }

    private fun openSelectApplicationsFragment() {

        if (activity == null || _binding == null) {
            return
        }

        val context = activity as Context

        val intent = Intent(context, SettingsActivity::class.java)
        intent.action = "proxy_apps_exclude"
        context.startActivity(intent)
    }

    private fun checkProxy() {
        if (activity == null || _binding == null) {
            return
        }

        val context = activity as Context

        etBackground?.let {
            binding.etProxyServer.background = it
            binding.etProxyPort.background = it
        }

        binding.btnSelectWoProxyApps.setTextColor(ContextCompat.getColor(context, R.color.buttonTextColor))


        val server = binding.etProxyServer.text?.toString()?.trim() ?: ""
        val port = binding.etProxyPort.text?.toString()?.trim() ?: ""

        if (server.isEmpty() || !server.matches(IP_REGEX)) {
            binding.etProxyServer.background = ContextCompat.getDrawable(context, R.drawable.error_hint_selector)
            return
        } else if (server == LOOPBACK_ADDRESS && preferenceRepository.get()
                .getStringSetPreference(CLEARNET_APPS_FOR_PROXY).isEmpty()) {
            binding.tvProxyHint.apply {
                setText(R.string.proxy_select_proxy_app)
                setTextColor(ContextCompat.getColor(context, R.color.textModuleStatusColorAlert))
                binding.scrollProxy.scrollToBottom()
                binding.btnSelectWoProxyApps.setTextColor(ContextCompat.getColor(context, R.color.textModuleStatusColorAlert))
            }
            return
        }

        if (port.isEmpty() || !port.matches(PORT_REGEX)) {
            binding.etProxyPort.background = ContextCompat.getDrawable(context, R.drawable.error_hint_selector)
            return
        }

        futureTask = cachedExecutor.submit {
            try {
                val result = proxyHelper.checkProxyConnectivity(server, port.toInt())

                if (_binding != null) {
                    if (result.matches(Regex("\\d+"))) {
                        handler.get().post {
                            binding.tvProxyHint.apply {
                                text = String.format(getString(R.string.proxy_successful_connection), result)
                                setTextColor(ContextCompat.getColor(context, R.color.textModuleStatusColorRunning))
                                binding.scrollProxy.scrollToBottom()
                            }
                        }
                    } else {
                        handler.get().post {
                            binding.tvProxyHint.apply {
                                text = String.format(getString(R.string.proxy_no_connection), result)
                                setTextColor(ContextCompat.getColor(context, R.color.textModuleStatusColorAlert))
                                binding.scrollProxy.scrollToBottom()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "ProxyFragment checkProxy exception ${e.message} ${e.cause}")
            }

        }
    }

    private fun saveToSharedPreferences(name: String, value: Any?) {
        val editor = sharedPreferences?.edit()

        when (value) {
            is Boolean -> editor?.putBoolean(name, value)
            is String -> editor?.putString(name, value)
        }

        editor?.apply()
    }

    private fun getTextFromSharedPreferences(value: String): String {
        return sharedPreferences?.getString(value, "") ?: ""
    }

    private fun getBoolFromSharedPreferences(value: String): Boolean {
        return sharedPreferences?.getBoolean(value, false) ?: false
    }

    private fun NestedScrollView.scrollToBottom() {
        val lastChild = getChildAt(childCount - 1)
        val bottom = lastChild.bottom + paddingBottom
        val delta = bottom - (scrollY + height)
        smoothScrollBy(0, delta)
    }

    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
    }

    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
    }

    override fun afterTextChanged(p0: Editable?) {
        val passAndNameIsEmpty = binding.etProxyPass.text.toString().trim().isEmpty()
                && binding.etProxyUserName.text.toString().trim().isEmpty()

        (binding.chbProxyDNSCrypt as CompoundButton).isEnabled = passAndNameIsEmpty
        (binding.chbProxyTor as CompoundButton).isEnabled = passAndNameIsEmpty
        (binding.chbProxyITPD as CompoundButton).isEnabled = passAndNameIsEmpty
    }
}

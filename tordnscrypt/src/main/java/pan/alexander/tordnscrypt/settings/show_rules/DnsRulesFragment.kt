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

package pan.alexander.tordnscrypt.settings.show_rules

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.angads25.filepicker.model.DialogConfigs
import com.github.angads25.filepicker.model.DialogProperties
import com.github.angads25.filepicker.view.FilePickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.databinding.FragmentDnsRuleBinding
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.settings.show_rules.recycler.DnsRuleRecycleItem
import pan.alexander.tordnscrypt.settings.show_rules.recycler.DnsRulesRecyclerAdapter
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.settings.show_rules.receiver.DnsRulesReceiver
import java.io.File
import java.util.Date
import javax.inject.Inject

private const val TOTAL_RULES_WARNING_COUNT = 400000
private const val TOTAL_RULES_ALERT_COUNT = 800000

class DnsRulesFragment : Fragment(), DnsRulesReceiver.Callback,
    AddRemoteRulesUrlDialog.OnAddRemoteRulesUrl {

    @Inject
    lateinit var pathVars: dagger.Lazy<PathVars>

    @Inject
    lateinit var dnsRulesReceiver: dagger.Lazy<DnsRulesReceiver>

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory
    private val viewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[DnsRulesViewModel::class.java]
    }

    private var _binding: FragmentDnsRuleBinding? = null
    private val binding get() = _binding!!

    private var rulesAdapter: DnsRulesRecyclerAdapter? = null

    @Suppress("deprecation")
    private val ruleType by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getSerializable(RULE_TYPE_ARG, DnsRuleType::class.java)
        } else {
            arguments?.getSerializable(RULE_TYPE_ARG) as DnsRuleType
        }
    }

    private var importFilesLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)
        super.onCreate(savedInstanceState)

        importFilesLauncher =
            activity?.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
                importRulesCommon(it.toTypedArray())
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = try {
            FragmentDnsRuleBinding.inflate(inflater, container, false)
        } catch (e: Exception) {
            loge("DnsRulesFragment onCreateView", e)
            throw e
        }

        ruleType?.let {
            setTitle(it)
        }

        initRulesRecycler()

        return binding.root
    }

    private fun setTitle(ruleType: DnsRuleType) {
        when (ruleType) {
            DnsRuleType.BLACKLIST -> requireActivity().setTitle(R.string.title_dnscrypt_blacklist)
            DnsRuleType.WHITELIST -> requireActivity().setTitle(R.string.title_dnscrypt_whitelist)
            DnsRuleType.IP_BLACKLIST -> requireActivity().setTitle(R.string.title_dnscrypt_ip_blacklist)
            DnsRuleType.FORWARDING -> requireActivity().setTitle(R.string.title_dnscrypt_forwarding_rules)
            DnsRuleType.CLOAKING -> requireActivity().setTitle(R.string.title_dnscrypt_cloaking_rules)
        }
    }

    private fun initRulesRecycler() {
        rulesAdapter = DnsRulesRecyclerAdapter(
            ::importLocalRules,
            ::deleteLocalRules,
            ::deltaTotalRules,
            ::addRemoteRules,
            ::deleteRemoteRules,
            ::refreshRemoteRules
        ).also {
            it.rulesType = ruleType
        }
        binding.rvDnsRules.layoutManager = LinearLayoutManager(context)
        binding.rvDnsRules.adapter = rulesAdapter
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        registerReceivers()
        observeRules()

        if (savedInstanceState == null) {
            requestRules()
        }
    }

    private fun registerReceivers() = with(dnsRulesReceiver.get()) {
        callback = this@DnsRulesFragment
        dnsRulesReceiver.get().registerReceiver()
    }

    private fun requestRules() {
        ruleType?.let {
            viewModel.requestRules(it)
            showProgressIndicator()
        }
    }

    private fun observeRules() {
        lifecycleScope.launch {
            viewModel.dnsRulesStateFlow
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collectLatest {
                    it?.let { rules ->
                        updateRules(rules)
                    }.also {
                        updateTotalRules(viewModel.totalRulesCount.get())
                    }
                }
        }
    }

    private suspend fun updateRules(rules: List<DnsRuleRecycleItem>) {
        while (binding.rvDnsRules.isComputingLayout) {
            delay(300)
        }
        hideProgressIndicator()
        rulesAdapter?.updateRules(rules)
        hideProgressIndicator()
    }


    private fun deltaTotalRules(delta: Int) {
        updateTotalRules(viewModel.totalRulesCount.addAndGet(delta))
    }

    private fun updateTotalRules(count: Int) {
        binding.tvDnsRuleTotalQuantity.text = String.format(
            getString(R.string.total_rules),
            count
        )
        when (count) {
            in 0..TOTAL_RULES_WARNING_COUNT -> {
                binding.tvDnsRuleTotalQuantity.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.colorGreen)
                )
            }

            in TOTAL_RULES_WARNING_COUNT..TOTAL_RULES_ALERT_COUNT -> {
                binding.tvDnsRuleTotalQuantity.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.colorOrange)
                )
            }

            else -> {
                binding.tvDnsRuleTotalQuantity.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.colorAlert)
                )
            }
        }
    }

    private fun addRemoteRules() {
        val context = context ?: return
        getAddUrlDialog(context).show()
    }

    private fun getAddUrlDialog(context: Context): AlertDialog =
        AddRemoteRulesUrlDialog().also {
            it.callback = this
        }.createDialog(context)

    override fun onRemoteRulesUrlAdded(url: String, name: String) {
        saveRemoteRulesUrl(url)
        rulesAdapter?.updateRemoteRules(
            DnsRuleRecycleItem.DnsRemoteRule(
                name = name,
                url = url,
                date = Date(),
                count = 0,
                size = 0,
                inProgress = true
            )
        )
        processRemoteRules(name)
        binding.rvDnsRules.smoothScrollToPosition(0)
    }

    private fun saveRemoteRulesUrl(url: String) {
        val ruleType = ruleType ?: return
        viewModel.saveRemoteRulesUrl(ruleType, url)
    }

    private fun processRemoteRules(name: String) {
        val ruleType = ruleType ?: return
        val currentRules = getCurrentRules() ?: return
        viewModel.updateRemoteRules(ruleType, currentRules, name)
    }

    private fun refreshRemoteRules() {
        val ruleType = ruleType ?: return
        val currentRules = getCurrentRules() ?: return
        val remoteRules = getCurrentRemoteRules() ?: return
        viewModel.updateRemoteRules(ruleType, currentRules, remoteRules.name)
    }

    private fun deleteRemoteRules() {
        val ruleType = ruleType ?: return
        val currentRules = getCurrentRules() ?: return
        viewModel.deleteRemoteRules(ruleType, currentRules)
    }

    private fun importLocalRules() {
        lifecycleScope.launch {
            if (viewModel.isExternalStorageAllowsDirectAccess()) {
                importRulesWithFilePicker()
            } else {
                importRulesWithSAF()
            }
        }
    }

    private fun importRulesWithFilePicker() {
        val activity = activity ?: return
        val dialog = FilePickerDialog(
            activity,
            getFilePickerProperties(activity)
        )
        dialog.setDialogSelectionListener {
            importRulesCommon(it)
        }
        dialog.show()
    }

    private fun importRulesWithSAF() {
        importFilesLauncher?.launch(arrayOf("text/plain"))
    }

    private fun importRulesCommon(files: Array<*>) {
        val ruleType = ruleType ?: return
        val currentRules = getCurrentRules() ?: return
        viewModel.importLocalRules(ruleType, currentRules, files)
    }

    private fun getFilePickerProperties(context: Context): DialogProperties =
        DialogProperties().apply {
            selection_mode = DialogConfigs.MULTI_MODE
            selection_type = DialogConfigs.FILE_SELECT
            root =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            error_dir = File(pathVars.get().getCacheDirPath(context))
            offset =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            extensions = arrayOf("txt")
        }

    private fun deleteLocalRules() {
        val ruleType = ruleType ?: return
        val currentRules = getCurrentRules() ?: return
        viewModel.deleteLocalRules(ruleType, currentRules)
    }

    private fun getCurrentRules() = rulesAdapter?.getRules()?.toList()

    private fun getCurrentRemoteRules() = rulesAdapter?.getRules()?.find {
        it is DnsRuleRecycleItem.DnsRemoteRule
    } as? DnsRuleRecycleItem.DnsRemoteRule

    override fun onPause() {
        super.onPause()

        val activity = activity ?: return

        getCurrentRules()?.let {
            if (activity.isFinishing) {
                saveRulesPersistent(it)
            } else {
                saveRulesTemporarily(it)
            }
        }
    }

    private fun saveRulesTemporarily(rules: List<DnsRuleRecycleItem>) {
        viewModel.saveTemporarily(rules)
    }

    private fun saveRulesPersistent(rules: List<DnsRuleRecycleItem>) {
        val ruleType = ruleType ?: return
        viewModel.saveRules(ruleType, rules)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        unregisterReceiver()
        _binding = null
        rulesAdapter = null
    }

    private fun unregisterReceiver() = with(dnsRulesReceiver.get()) {
        callback = null
        unregisterReceiver()
    }

    private fun showProgressIndicator() {
        binding.pbDnsRules.visibility = View.VISIBLE
    }

    private fun hideProgressIndicator() {
        binding.pbDnsRules.visibility = View.GONE
    }

    override fun onUpdateRemoteRules(rules: DnsRuleRecycleItem.DnsRemoteRule) {
        rulesAdapter?.updateRemoteRules(rules)
    }

    override fun onUpdateLocalRules(rules: DnsRuleRecycleItem.DnsLocalRule) {
        rulesAdapter?.updateLocalRules(rules)
    }

    override fun onUpdateTotalRules(count: Int) {
        _binding?.let {
            viewModel.totalRulesCount.set(count)
            updateTotalRules(count)
        }
    }

    override fun onUpdateFinished() {
        val ruleType = ruleType ?: return
        viewModel.requestRules(ruleType)
    }

    companion object {
        const val RULE_TYPE_ARG = "pan.alexander.tordnscrypt.RULE_TYPE_ARG"
    }

}

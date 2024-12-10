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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.recycler

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.OnFocusChangeListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.databinding.ItemButtonBinding
import pan.alexander.tordnscrypt.databinding.ItemDnsRuleBinding
import pan.alexander.tordnscrypt.databinding.ItemRulesBinding
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager
import pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_NO_CAPTURING
import pan.alexander.tordnscrypt.utils.Utils
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.text.DateFormat
import kotlin.math.max

private const val DNS_REMOTE_RULE = 1
private const val DNS_REMOTE_RULE_BUTTON = 2
private const val DNS_LOCAL_RULE = 3
private const val DNS_LOCAL_RULE_BUTTON = 4
private const val DNS_SINGLE_RULE = 5
private const val DNS_SINGLE_RULE_BUTTON = 6
private const val DNS_RULE_COMMENT = 7

class DnsRulesRecyclerAdapter(
    private val onImportLocalRules: () -> Unit,
    private val onLocalRulesDelete: () -> Unit,
    private val onDeltaSingleRules: (Int) -> Unit,
    private val onRemoteRulesAdd: () -> Unit,
    private val onRemoteRulesDelete: () -> Unit,
    private val onRemoteRulesRefresh: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var rulesType: DnsRuleType? = null
    private val rules: MutableList<DnsRuleRecycleItem> = mutableListOf()

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var recyclerView: RecyclerView? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return try {
            when (viewType) {
                DNS_REMOTE_RULE, DNS_LOCAL_RULE -> {
                    val itemView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_dns_rule, parent, false)
                    DnsRulesViewHolder(itemView)
                }

                DNS_SINGLE_RULE -> {
                    val itemView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_rules, parent, false)
                    DnsSingleRuleViewHolder(itemView)
                }

                DNS_REMOTE_RULE_BUTTON, DNS_LOCAL_RULE_BUTTON, DNS_SINGLE_RULE_BUTTON -> {
                    val itemView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_button, parent, false)
                    DnsRuleButtonViewHolder(itemView)
                }

                else -> throw IllegalArgumentException("DnsRulesRecyclerAdapter unknown view type")
            }

        } catch (e: Exception) {
            loge("DnsRulesRecyclerAdapter onCreateViewHolder", e)
            throw e
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            DNS_REMOTE_RULE, DNS_LOCAL_RULE -> (holder as DnsRulesViewHolder).bind(position)
            DNS_SINGLE_RULE -> (holder as DnsSingleRuleViewHolder).bind(position)
            DNS_REMOTE_RULE_BUTTON, DNS_LOCAL_RULE_BUTTON, DNS_SINGLE_RULE_BUTTON ->
                (holder as DnsRuleButtonViewHolder).bind(position)

            else -> throw IllegalArgumentException("DnsRulesRecyclerAdapter unknown view type")
        }
    }

    override fun getItemCount(): Int = rules.size

    override fun getItemViewType(position: Int): Int {
        return when (rules[position]) {
            is DnsRuleRecycleItem.DnsRemoteRule -> DNS_REMOTE_RULE
            is DnsRuleRecycleItem.AddRemoteRulesButton -> DNS_REMOTE_RULE_BUTTON
            is DnsRuleRecycleItem.DnsLocalRule -> DNS_LOCAL_RULE
            is DnsRuleRecycleItem.AddLocalRulesButton -> DNS_LOCAL_RULE_BUTTON
            is DnsRuleRecycleItem.DnsSingleRule -> DNS_SINGLE_RULE
            is DnsRuleRecycleItem.AddSingleRuleButton -> DNS_SINGLE_RULE_BUTTON
            is DnsRuleRecycleItem.DnsRuleComment -> DNS_RULE_COMMENT
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        handler.removeCallbacksAndMessages(null)
        this.recyclerView = null
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateRules(rules: List<DnsRuleRecycleItem>) {
        this.rules.apply {
            clear()
            addAll(rules)
            notifyDataSetChanged()
        }
    }

    fun updateRemoteRules(remoteRule: DnsRuleRecycleItem.DnsRemoteRule) {
        for (i in rules.indices) {
            val rule = rules[i]
            if (rule is DnsRuleRecycleItem.DnsRemoteRule) {
                rules[i] = remoteRule
                notifyItemChanged(i, Any())
                break
            } else if (rule is DnsRuleRecycleItem.AddRemoteRulesButton) {
                rules.add(i, remoteRule)
                notifyItemInserted(i)
                break
            }
        }
    }

    fun updateLocalRules(localRule: DnsRuleRecycleItem.DnsLocalRule) {
        for (i in rules.indices) {
            val rule = rules[i]
            if (rule is DnsRuleRecycleItem.DnsLocalRule) {
                rules[i] = localRule
                notifyItemChanged(i, Any())
                break
            } else if (rule is DnsRuleRecycleItem.AddLocalRulesButton) {
                rules.add(i, localRule)
                notifyItemInserted(i)
                break
            }
        }
    }

    fun getRules(): List<DnsRuleRecycleItem> = rules

    private inner class DnsRulesViewHolder(itemView: View) : BaseViewHolder(itemView) {

        private val textColorGreen by lazy {
            ContextCompat.getColor(itemView.context, R.color.colorGreen)
        }

        private val textColorSecondary by lazy {
            ContextCompat.getColor(itemView.context, R.color.colorTextSecondary)
        }

        private val faultColorFault by lazy {
            ContextCompat.getColor(itemView.context, R.color.colorAlert)
        }

        override fun bind(position: Int) {
            when (val rule = rules[position]) {
                is DnsRuleRecycleItem.DnsRemoteRule -> {
                    ItemDnsRuleBinding.bind(itemView).apply {
                        tvDnsRuleFileName.text = rule.name
                        tvDnsRuleUrl.visibility = VISIBLE
                        tvDnsRuleUrl.text = rule.url
                        if (rule.fault) {
                            tvDnsRuleUrl.setTextColor(faultColorFault)
                        } else {
                            tvDnsRuleUrl.setTextColor(textColorSecondary)
                        }
                        tvDnsRuleFileDate.text =
                            DateFormat.getDateInstance(DateFormat.SHORT).format(rule.date)
                        if (rule.fault) {
                            tvDnsRuleFileDate.setTextColor(faultColorFault)
                        } else {
                            tvDnsRuleFileDate.setTextColor(textColorGreen)
                        }
                        tvDnsRuleFileSize.text = Utils.formatFileSizeToReadableUnits(rule.size)
                        if (rule.size == 0L) {
                            tvDnsRuleFileSize.setTextColor(faultColorFault)
                        } else {
                            tvDnsRuleFileSize.setTextColor(textColorGreen)
                        }
                        tvDnsRuleFileQuantity.text = rule.count.toString()
                        if (rule.count == 0) {
                            tvDnsRuleFileQuantity.setTextColor(faultColorFault)
                            tvDnsRuleFileQuantityRules.setTextColor(faultColorFault)
                        } else {
                            tvDnsRuleFileQuantity.setTextColor(textColorGreen)
                            tvDnsRuleFileQuantityRules.setTextColor(textColorGreen)
                        }
                        btnDnsRuleFileDelete.setOnClickListener { deleteRemoteRules() }
                        btnDnsRuleFileRefresh.setOnClickListener { onRemoteRulesRefresh() }
                        if (rule.inProgress) {
                            pbDnsRule.visibility = VISIBLE
                        } else {
                            pbDnsRule.visibility = GONE
                        }
                    }
                }

                is DnsRuleRecycleItem.DnsLocalRule -> {
                    ItemDnsRuleBinding.bind(itemView).apply {
                        val name = rule.name.ifEmpty { "local-rules.txt" }
                        tvDnsRuleFileName.text = name
                        tvDnsRuleUrl.visibility = GONE
                        tvDnsRuleFileDate.text =
                            DateFormat.getDateInstance(DateFormat.SHORT).format(rule.date)
                        if (rule.fault) {
                            tvDnsRuleFileDate.setTextColor(faultColorFault)
                        } else {
                            tvDnsRuleFileDate.setTextColor(textColorGreen)
                        }
                        tvDnsRuleFileSize.text = Utils.formatFileSizeToReadableUnits(rule.size)
                        if (rule.size == 0L) {
                            tvDnsRuleFileSize.setTextColor(faultColorFault)
                        } else {
                            tvDnsRuleFileSize.setTextColor(textColorGreen)
                        }
                        tvDnsRuleFileQuantity.text = rule.count.toString()
                        if (rule.count == 0) {
                            tvDnsRuleFileQuantity.setTextColor(faultColorFault)
                            tvDnsRuleFileQuantityRules.setTextColor(faultColorFault)
                        } else {
                            tvDnsRuleFileQuantity.setTextColor(textColorGreen)
                            tvDnsRuleFileQuantityRules.setTextColor(textColorGreen)
                        }
                        btnDnsRuleFileDelete.setOnClickListener { deleteLocalRules() }
                        btnDnsRuleFileRefresh.visibility = GONE
                        if (rule.inProgress) {
                            pbDnsRule.visibility = VISIBLE
                            pbDnsRule.isIndeterminate = true
                        } else {
                            pbDnsRule.isIndeterminate = false
                            pbDnsRule.visibility = GONE
                        }
                    }
                }

                else -> Unit
            }
        }

        private fun deleteRemoteRules() {
            val adapterPosition = bindingAdapterPosition
            if (adapterPosition != NO_POSITION) {
                rules.removeAt(adapterPosition)
                onRemoteRulesDelete()
                notifyItemRemoved(adapterPosition)
            }
        }

        private fun deleteLocalRules() {
            val adapterPosition = bindingAdapterPosition
            if (adapterPosition != NO_POSITION) {
                rules.removeAt(adapterPosition)
                onLocalRulesDelete()
                notifyItemRemoved(adapterPosition)
            }
        }
    }

    private inner class DnsSingleRuleViewHolder(itemView: View) : BaseViewHolder(itemView),
        View.OnClickListener {

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                val position = bindingAdapterPosition
                if (s == null || position == NO_POSITION) {
                    return
                }

                if (recyclerView?.isComputingLayout == true) {
                    handler.postDelayed(50L) {
                        editRule(position, s.toString())
                    }
                } else {
                    editRule(position, s.toString())
                }
            }

        }

        val onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            val imm =
                itemView.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (hasFocus) {
                imm.showSoftInput(v, 0)
            } else {
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
        }

        override fun bind(position: Int) {
            when (val rule = rules[position]) {
                is DnsRuleRecycleItem.DnsSingleRule -> {
                    ItemRulesBinding.bind(itemView).apply {
                        etRule.setText(rule.rule, TextView.BufferType.EDITABLE)
                        etRule.isEnabled = rule.active
                        etRule.addTextChangedListener(watcher)
                        etRule.onFocusChangeListener = onFocusChangeListener
                        swRuleActive.isChecked = rule.active
                        swRuleActive.setOnClickListener(this@DnsSingleRuleViewHolder)
                        if (rule.protected) {
                            delBtnRules.visibility = GONE
                        } else {
                            delBtnRules.visibility = VISIBLE
                            delBtnRules.setOnClickListener(this@DnsSingleRuleViewHolder)
                        }
                    }
                }

                else -> Unit
            }
        }

        override fun onClick(v: View?) {
            val position = bindingAdapterPosition
            if (position == NO_POSITION) {
                return
            }

            when (v?.id) {
                R.id.delBtnRules -> deleteRule(position)
                R.id.swRuleActive -> toggleRule(position)
            }
        }

    }

    private fun addRule() {

        for (rule in rules) {
            if (rule is DnsRuleRecycleItem.DnsSingleRule && rule.rule.isBlank()) {
                return
            }
        }

        rules.add(
            max(rules.size - 1, 0),
            DnsRuleRecycleItem.DnsSingleRule(
                rule = "",
                protected = false,
                active = true
            )
        )
        onDeltaSingleRules(1)
        notifyItemInserted(rules.size - 1)
    }

    private fun toggleRule(position: Int) {
        val rule = rules[position]
        if (rule is DnsRuleRecycleItem.DnsSingleRule) {
            rules[position] = DnsRuleRecycleItem.DnsSingleRule(
                rule = rule.rule,
                protected = rule.protected,
                active = !rule.active
            )
            onDeltaSingleRules(if (rule.active) -1 else 1)
            notifyItemChanged(position)
        }
    }

    private fun editRule(position: Int, text: String) {
        val rule = rules[position]
        if (rule is DnsRuleRecycleItem.DnsSingleRule) {
            if (text.contains("\n")) {
                text.split("\n").map {
                    it.trim()
                }.map {
                    when (rulesType) {
                        DnsRuleType.BLACKLIST, DnsRuleType.WHITELIST ->
                            Utils.getDomainNameFromUrl(it)

                        DnsRuleType.IP_BLACKLIST -> prepareIPv6IfAny(it)

                        else -> it
                    }
                }.filter {
                    it.isNotBlank() && it.removePrefix("#").matches(getRuleRegex())
                }.takeIf {
                    it.isNotEmpty()
                }?.also {
                    rules[position] = DnsRuleRecycleItem.DnsSingleRule(
                        rule = it.first().removePrefix("#"),
                        protected = false,
                        active = !it.first().startsWith("#")
                    )
                    notifyItemChanged(position)
                }?.drop(1)?.forEachIndexed { index, s ->
                    rules.add(
                        position + index + 1,
                        DnsRuleRecycleItem.DnsSingleRule(
                            rule = s.removePrefix("#"),
                            protected = false,
                            active = !s.startsWith("#")
                        )
                    )
                    notifyItemInserted(position + index + 1)
                }
            } else {
                val textPrepared = when (rulesType) {
                    DnsRuleType.BLACKLIST, DnsRuleType.WHITELIST -> Utils.getDomainNameFromUrl(text)
                    DnsRuleType.IP_BLACKLIST -> prepareIPv6IfAny(text)
                    else -> text
                }
                if (textPrepared.matches(getRuleRegex()) && rule.rule != textPrepared) {
                    rule.rule = textPrepared
                    if (textPrepared != text) {
                        notifyItemChanged(position)
                    }
                }
            }
        }
    }

    private fun prepareIPv6IfAny(line: String): String =
        if (line.matches(Regex(IPv6_REGEX_NO_CAPTURING))) {
            "[$line]"
        } else {
            line
        }

    private fun getRuleRegex() = when (rulesType) {
        DnsRuleType.BLACKLIST -> ImportRulesManager.DnsRulesRegex.blackListHostRulesRegex
        DnsRuleType.WHITELIST -> ImportRulesManager.DnsRulesRegex.whiteListHostRulesRegex
        DnsRuleType.IP_BLACKLIST -> ImportRulesManager.DnsRulesRegex.blacklistIPRulesRegex
        DnsRuleType.FORWARDING -> ImportRulesManager.DnsRulesRegex.forwardingRulesRegex
        DnsRuleType.CLOAKING -> ImportRulesManager.DnsRulesRegex.cloakingRulesRegex
        null -> throw IllegalArgumentException("DnsRulesRecyclerAdapter getRuleRegex rulesType null")
    }

    private fun deleteRule(position: Int) {
        rules.removeAt(position)
        onDeltaSingleRules(-1)
        notifyItemRemoved(position)
    }

    private inner class DnsRuleButtonViewHolder(itemView: View) : BaseViewHolder(itemView) {

        override fun bind(position: Int) {
            when (rules[position]) {
                is DnsRuleRecycleItem.AddRemoteRulesButton -> {
                    ItemButtonBinding.bind(itemView).apply {
                        if (rules.firstOrNull { it is DnsRuleRecycleItem.DnsRemoteRule } == null) {
                            btnAddRuleItem.setText(R.string.dns_rule_add_remote_list)
                        } else {
                            btnAddRuleItem.setText(R.string.dns_rule_replace_remote_list)
                        }
                        btnAddRuleItem.setOnClickListener {
                            onRemoteRulesAdd()
                        }
                    }
                }

                is DnsRuleRecycleItem.AddLocalRulesButton -> {
                    ItemButtonBinding.bind(itemView).apply {
                        if (rules.firstOrNull { it is DnsRuleRecycleItem.DnsLocalRule } == null) {
                            btnAddRuleItem.setText(R.string.dns_rule_add_local_list)
                        } else {
                            btnAddRuleItem.setText(R.string.dns_rule_replace_local_list)
                        }
                        btnAddRuleItem.setOnClickListener {
                            onImportLocalRules()
                        }
                    }
                }

                is DnsRuleRecycleItem.AddSingleRuleButton -> {
                    ItemButtonBinding.bind(itemView).apply {
                        btnAddRuleItem.setText(R.string.dns_rule_add_rule)
                        btnAddRuleItem.setOnClickListener {
                            addRule()
                        }
                    }
                }

                else -> Unit
            }
        }
    }

    private abstract class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(position: Int)
    }

}

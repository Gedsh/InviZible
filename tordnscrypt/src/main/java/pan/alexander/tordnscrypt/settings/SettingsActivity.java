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

package pan.alexander.tordnscrypt.settings;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.Fragment;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import android.view.Menu;
import android.view.MenuItem;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.LangAppCompatActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.proxy.ProxyFragment;
import pan.alexander.tordnscrypt.settings.dnscrypt_servers.PreferencesDNSCryptServers;
import pan.alexander.tordnscrypt.settings.dnscrypt_settings.PreferencesDNSFragment;
import pan.alexander.tordnscrypt.settings.firewall.FirewallFragment;
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType;
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.DnsRulesFragment;
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges;
import pan.alexander.tordnscrypt.settings.itpd_settings.ITPDSubscriptionsFragment;
import pan.alexander.tordnscrypt.settings.tor_apps.UnlockTorAppsFragment;
import pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFragment;
import pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFragment.DeviceOrTether.DEVICE;
import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFragment.DeviceOrTether.TETHER;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;

import javax.inject.Inject;


public class SettingsActivity extends LangAppCompatActivity {

    public static final String dnscrypt_proxy_toml_tag = "pan.alexander.tordnscrypt/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
    public static final String tor_conf_tag = "pan.alexander.tordnscrypt/app_data/tor/tor.conf";
    public static final String itpd_conf_tag = "pan.alexander.tordnscrypt/app_data/itpd/itpd.conf";
    public static final String itpd_tunnels_tag = "pan.alexander.tordnscrypt/app_data/itpd/tunnels.conf";

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;

    public DialogFragment dialogFragment;
    public PreferencesTorFragment preferencesTorFragment;
    private SettingsParser settingsParser;
    private PreferencesDNSFragment preferencesDNSFragment;
    private UnlockTorAppsFragment unlockTorAppsFragment;
    private boolean showMenu;


    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        String appDataDir = pathVars.get().getAppDataDir();

        if (savedInstanceState != null) return;

        settingsParser = new SettingsParser(this, appDataDir);
        settingsParser.activateSettingsParser();

        FragmentTransaction fSupportTrans = getSupportFragmentManager().beginTransaction();
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        logi("SettingsActivity getAction " + intent.getAction());

        if (Objects.equals(intent.getAction(), "DNS_Pref")) {
            dialogFragment = PleaseWaitProgressDialog.getInstance();
            dialogFragment.show(getSupportFragmentManager(), "PleaseWaitProgressDialog");
            FileManager.readTextFile(this, appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml", dnscrypt_proxy_toml_tag);
        } else if (Objects.equals(intent.getAction(), "Tor_Pref")) {
            dialogFragment = PleaseWaitProgressDialog.getInstance();
            dialogFragment.show(getSupportFragmentManager(), "PleaseWaitProgressDialog");
            FileManager.readTextFile(this, appDataDir + "/app_data/tor/tor.conf", tor_conf_tag);
        } else if (Objects.equals(intent.getAction(), "I2PD_Pref")) {
            dialogFragment = PleaseWaitProgressDialog.getInstance();
            dialogFragment.show(getSupportFragmentManager(), "PleaseWaitProgressDialog");
            FileManager.readTextFile(this, appDataDir + "/app_data/i2pd/i2pd.conf", itpd_conf_tag);
        } else if (Objects.equals(intent.getAction(), "fast_Pref")) {
            PreferencesFastFragment preferencesFastFragment = new PreferencesFastFragment();
            fSupportTrans.replace(android.R.id.content, preferencesFastFragment, "fastSettingsFragment");
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "common_Pref")) {
            fSupportTrans.replace(android.R.id.content, new PreferencesCommonFragment());
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "firewall")) {
            fSupportTrans.replace(android.R.id.content, new FirewallFragment(), FirewallFragment.TAG);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "DNS_servers_Pref")) {
            fSupportTrans.replace(android.R.id.content, new PreferencesDNSCryptServers());
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "open_qery_log")) {
            Bundle bundle = new Bundle();
            String path = appDataDir + "/cache/query.log";
            bundle.putString("path", path);
            ShowLogFragment frag = new ShowLogFragment();
            frag.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, frag);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "open_nx_log")) {
            Bundle bundle = new Bundle();
            String path = appDataDir + "/cache/nx.log";
            bundle.putString("path", path);
            ShowLogFragment frag = new ShowLogFragment();
            frag.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, frag);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "forwarding_rules_Pref")) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(DnsRulesFragment.RULE_TYPE_ARG, DnsRuleType.FORWARDING);
            DnsRulesFragment fragment = new DnsRulesFragment();
            fragment.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, fragment);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "cloaking_rules_Pref")) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(DnsRulesFragment.RULE_TYPE_ARG, DnsRuleType.CLOAKING);
            DnsRulesFragment fragment = new DnsRulesFragment();
            fragment.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, fragment);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "blacklist_Pref")) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(DnsRulesFragment.RULE_TYPE_ARG, DnsRuleType.BLACKLIST);
            DnsRulesFragment fragment = new DnsRulesFragment();
            fragment.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, fragment);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "ipblacklist_Pref")) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(DnsRulesFragment.RULE_TYPE_ARG, DnsRuleType.IP_BLACKLIST);
            DnsRulesFragment fragment = new DnsRulesFragment();
            fragment.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, fragment);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "whitelist_Pref")) {
            Bundle bundle = new Bundle();
            bundle.putSerializable(DnsRulesFragment.RULE_TYPE_ARG, DnsRuleType.WHITELIST);
            DnsRulesFragment fragment = new DnsRulesFragment();
            fragment.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, fragment);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "pref_itpd_addressbook_subscriptions")) {
            ITPDSubscriptionsFragment frag = new ITPDSubscriptionsFragment();
            fSupportTrans.replace(android.R.id.content, frag);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_sites_unlock")) {
            UnlockTorIpsFragment unlockTorIpsFragment = UnlockTorIpsFragment.getInstance(DEVICE);
            fSupportTrans.replace(android.R.id.content, unlockTorIpsFragment);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_sites_unlock_tether")) {
            UnlockTorIpsFragment unlockTorIpsFragment = UnlockTorIpsFragment.getInstance(TETHER);
            fSupportTrans.replace(android.R.id.content, unlockTorIpsFragment);
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_apps_unlock")) {
            fSupportTrans.replace(android.R.id.content, new UnlockTorAppsFragment());
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "tor_bridges")) {
            fSupportTrans.replace(android.R.id.content, new PreferencesTorBridges(), "PreferencesTorBridges");
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "use_proxy")) {
            fSupportTrans.replace(android.R.id.content, new ProxyFragment(), "ProxyFragment");
            fSupportTrans.commit();
        } else if (Objects.equals(intent.getAction(), "proxy_apps_exclude")) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("proxy", true);
            UnlockTorAppsFragment unlockTorAppsFragment = new UnlockTorAppsFragment();
            unlockTorAppsFragment.setArguments(bundle);
            fSupportTrans.replace(android.R.id.content, unlockTorAppsFragment);
            fSupportTrans.commit();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttachFragment(@NonNull Fragment fragment) {
        super.onAttachFragment(fragment);

        if (fragment instanceof PreferencesDNSFragment) {
            preferencesDNSFragment = (PreferencesDNSFragment) fragment;
        } else if (fragment instanceof PreferencesTorFragment) {
            preferencesTorFragment = (PreferencesTorFragment) fragment;
        } else if (fragment instanceof UnlockTorAppsFragment) {
            unlockTorAppsFragment = (UnlockTorAppsFragment) fragment;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (settingsParser != null)
            settingsParser.deactivateSettingsParser();

        dialogFragment = null;
        preferencesTorFragment = null;
        settingsParser = null;
        preferencesDNSFragment = null;
        unlockTorAppsFragment = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        String action = null;
        Intent intent = getIntent();

        if (intent != null) {
            action = intent.getAction();
        }

        if (Objects.equals(action, "tor_apps_unlock")
                || Objects.equals(action, "proxy_apps_exclude")) {
            try {
                getMenuInflater().inflate(R.menu.settings_menu, menu);
            } catch (Exception e) {
                loge("SettingsActivity onCreateOptionsMenu", e);
                throw e;
            }

            showMenu = true;
            return true;
        } else {
            showMenu = false;
            return super.onCreateOptionsMenu(menu);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        String action = null;
        Intent intent = getIntent();

        if (intent != null) {
            action = intent.getAction();
        }

        SearchView menuSearchView;

        if (showMenu) {
            MenuItem menuSearch = menu.findItem(R.id.menu_search);
            menuSearchView = (SearchView) menuSearch.getActionView();
        } else {
            return super.onPrepareOptionsMenu(menu);
        }

        if (menuSearchView == null) {
            return super.onPrepareOptionsMenu(menu);
        }

        if ((Objects.equals(action, "tor_apps_unlock")
                || Objects.equals(action, "proxy_apps_exclude"))
                && unlockTorAppsFragment != null) {
            menuSearchView.setOnQueryTextListener(unlockTorAppsFragment);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {// API 5+ solution
            onBackPressed();
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    @Override
    public void onBackPressed() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        Collections.reverse(fragments);
        for (Fragment fragment: fragments) {
            if (fragment instanceof OnBackPressListener) {
                if (((OnBackPressListener) fragment).onBackPressed()) {
                    return;
                }
            }
        }

        super.onBackPressed();

    }
}

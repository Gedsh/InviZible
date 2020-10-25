package pan.alexander.tordnscrypt;
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

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.arp.ArpScannerKt;
import pan.alexander.tordnscrypt.assistance.AccelerateDevelop;
import pan.alexander.tordnscrypt.backup.BackupActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptRunFragment;
import pan.alexander.tordnscrypt.help.HelpActivity;
import pan.alexander.tordnscrypt.iptables.IptablesRules;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.main_fragment.MainFragment;
import pan.alexander.tordnscrypt.main_fragment.ViewPagerAdapter;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.itpd_fragment.ITPDRunFragment;
import pan.alexander.tordnscrypt.tor_fragment.TorRunFragment;
import pan.alexander.tordnscrypt.utils.ApManager;
import pan.alexander.tordnscrypt.utils.AppExitDetectService;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Registration;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class MainActivity extends LangAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static DialogInterface modernDialog = null;
    private static final int CODE_IS_AP_ON = 100;
    private static final int CODE_IS_VPN_ALLOWED = 110;

    public boolean childLockActive = false;
    public AccelerateDevelop accelerateDevelop;
    private volatile boolean vpnRequested;
    private ScheduledFuture<?> scheduledFuture;
    private Handler handler;
    private TopFragment topFragment;
    private DNSCryptRunFragment dNSCryptRunFragment;
    private TorRunFragment torRunFragment;
    private ITPDRunFragment iTPDRunFragment;
    private MainFragment mainFragment;
    private ModulesStatus modulesStatus;
    private ViewPager viewPager;
    private static int viewPagerPosition = 0;
    private MenuItem newIdentityMenuItem;
    private ImageView animatingImage;
    private RotateAnimation rotateAnimation;
    private BroadcastReceiver mainActivityReceiver;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setDayNightTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setBackgroundColor(getResources().getColor(R.color.colorBackground));
        navigationView.setNavigationItemSelectedListener(this);

        changeDrawerWithVersionAndDestination(navigationView);

        viewPager = findViewById(R.id.viewPager);
        if (viewPager != null) {
            viewPager.setOffscreenPageLimit(4);

            ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager(), ViewPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);

            MainFragment mainFragment = new MainFragment();

            DNSCryptRunFragment dnsCryptRunFragment = new DNSCryptRunFragment();

            TorRunFragment torRunFragment = new TorRunFragment();
            ITPDRunFragment itpdRunFragment = new ITPDRunFragment();

            adapter.addFragment(new ViewPagerAdapter.ViewPagerFragment("Main", mainFragment));
            adapter.addFragment(new ViewPagerAdapter.ViewPagerFragment("DNS", dnsCryptRunFragment));
            adapter.addFragment(new ViewPagerAdapter.ViewPagerFragment("Tor", torRunFragment));
            adapter.addFragment(new ViewPagerAdapter.ViewPagerFragment("I2P", itpdRunFragment));

            viewPager.setAdapter(adapter);

            TabLayout tabLayout = findViewById(R.id.tabs);
            tabLayout.setupWithViewPager(viewPager);

            viewPager.setCurrentItem(viewPagerPosition);
        }

        modulesStatus = ModulesStatus.getInstance();

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            handler = new Handler(looper);
        }

        startAppExitDetectService();
    }

    @Override
    public void onResume() {
        super.onResume();

        vpnRequested = false;

        childLockActive = isInterfaceLocked();

        checkUpdates();

        showUpdateResultMessage();

        handleMitmAttackWarning();

        registerBroadcastReceiver();

        if (appVersion.equals("gp")) {
            accelerateDevelop = new AccelerateDevelop(this);
            accelerateDevelop.initBilling();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (new PrefManager(this).getBoolPref("refresh_main_activity")) {
            new PrefManager(this).setBoolPref("refresh_main_activity", false);
            recreate();
        }
    }

    @Override
    public void onAttachFragment(@NonNull Fragment fragment) {
        super.onAttachFragment(fragment);

        if (fragment instanceof DNSCryptRunFragment) {
            dNSCryptRunFragment = (DNSCryptRunFragment) fragment;
        } else if (fragment instanceof TorRunFragment) {
            torRunFragment = (TorRunFragment) fragment;
        } else if (fragment instanceof ITPDRunFragment) {
            iTPDRunFragment = (ITPDRunFragment) fragment;
        } else if (fragment instanceof TopFragment) {
            topFragment = (TopFragment) fragment;
        } else if (fragment instanceof MainFragment) {
            mainFragment = (MainFragment) fragment;
        }
    }

    @SuppressWarnings("deprecation")
    private void setDayNightTheme() {
        try {

            String theme;
            if (appVersion.startsWith("g") && !accelerated) {
                theme = "1";
            } else {
                SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                theme = defaultSharedPreferences.getString("pref_fast_theme", "4");
            }

            switch (Objects.requireNonNull(theme)) {
                case "1":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case "2":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                case "3":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_TIME);
                    break;
                case "4":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                    break;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "MainActivity setDayNightTheme exception " + e.getMessage() + " " + e.getCause());
        }
    }

    private void checkUpdates() {

        if (appVersion.equals("gp") || appVersion.equals("fd")) {
            return;
        }

        Intent intent = getIntent();
        if (Objects.equals(intent.getAction(), "check_update")) {
            if (topFragment != null) {
                topFragment.checkNewVer();
                modernDialog = modernProgressDialog();
            }

            intent.setAction(null);
            setIntent(intent);
        }
    }

    private void handleMitmAttackWarning() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra(ArpScannerKt.mitmAttackWarning, false)
                && (ArpScanner.INSTANCE.getArpAttackDetected() || ArpScanner.INSTANCE.getDhcpGatewayAttackDetected())) {

            handler.postDelayed(() -> {
                DialogFragment commandResult = NotificationDialogFragment.newInstance(getString(R.string.notification_mitm));
                commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
            }, 1000);
        }
    }

    public void showUpdateResultMessage() {

        if (appVersion.equals("gp") || appVersion.equals("fd")) {
            return;
        }

        String updateResultMessage = new PrefManager(this).getStrPref("UpdateResultMessage");
        if (!updateResultMessage.isEmpty()) {
            showUpdateMessage(updateResultMessage);

            new PrefManager(this).setStrPref("UpdateResultMessage", "");
        }
    }

    private boolean isInterfaceLocked() {
        boolean locked = false;
        try {
            String saved_pass = new String(Base64.decode(new PrefManager(this).getStrPref("passwd"), 16));
            locked = saved_pass.contains("-l-o-c-k-e-d");
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "MainActivity Child Lock Exception " + e.getMessage());
        }
        return locked;
    }

    public DNSCryptRunFragment getDNSCryptRunFragment() {
        return dNSCryptRunFragment;
    }

    public TorRunFragment getTorRunFragment() {
        return torRunFragment;
    }

    public ITPDRunFragment getITPDRunFragment() {
        return iTPDRunFragment;
    }

    public MainFragment getMainFragment() {
        return mainFragment;
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        boolean rootIsAvailable = new PrefManager(this).getBoolPref("rootIsAvailable");

        switchIconsDependingOnMode(menu, rootIsAvailable);

        switchChildLockIcon(menu);

        if (rootIsAvailable) {
            switchApIcon(menu);
        }

        showNewTorIdentityIcon(menu);

        return super.onPrepareOptionsMenu(menu);
    }

    private void switchIconsDependingOnMode(Menu menu, boolean rootIsAvailable) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean fixTTL = sharedPreferences.getBoolean("pref_common_fix_ttl", false);
        boolean useModulesWithRoot = sharedPreferences.getBoolean("swUseModulesRoot", false);

        boolean busyBoxIsAvailable = new PrefManager(this).getBoolPref("bbOK");

        boolean mitmDetected = ArpScanner.INSTANCE.getArpAttackDetected()
                || ArpScanner.INSTANCE.getDhcpGatewayAttackDetected();

        fixTTL = fixTTL && !useModulesWithRoot;

        OperationMode mode = UNDEFINED;

        String operationMode = new PrefManager(this).getStrPref("OPERATION_MODE");
        if (!operationMode.isEmpty()) {
            mode = OperationMode.valueOf(operationMode);
        }


        MenuItem menuRootMode = menu.findItem(R.id.menu_root_mode);
        MenuItem menuVPNMode = menu.findItem(R.id.menu_vpn_mode);
        MenuItem menuProxiesMode = menu.findItem(R.id.menu_proxies_mode);

        MenuItem hotSpot = menu.findItem(R.id.item_hotspot);
        MenuItem rootIcon = menu.findItem(R.id.item_root);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            menuVPNMode.setEnabled(false);
            menuVPNMode.setVisible(false);
        }

        if (rootIsAvailable) {

            if (mode == ROOT_MODE) {
                menuRootMode.setChecked(true);
            } else if (mode == PROXY_MODE) {
                menuProxiesMode.setChecked(true);
            } else if (mode == VPN_MODE) {
                menuVPNMode.setChecked(true);
            } else {
                menuRootMode.setChecked(true);
                modulesStatus.setMode(ROOT_MODE);
                mode = ROOT_MODE;
                new PrefManager(this).setStrPref("OPERATION_MODE", mode.toString());
            }

            if (mitmDetected) {
                rootIcon.setIcon(R.drawable.ic_arp_attack_notification);
            } else if (mode == ROOT_MODE && fixTTL) {
                rootIcon.setIcon(R.drawable.ic_ttl_main);
            } else if (mode == ROOT_MODE && busyBoxIsAvailable) {
                rootIcon.setIcon(R.drawable.ic_done_all_white_24dp);
            } else if (mode == ROOT_MODE) {
                rootIcon.setIcon(R.drawable.ic_done_white_24dp);
            } else if (mode == PROXY_MODE) {
                rootIcon.setIcon(R.drawable.ic_warning_white_24dp);
            } else {
                rootIcon.setIcon(R.drawable.ic_vpn_key_white_24dp);
            }

            if (mode == ROOT_MODE) {
                hotSpot.setVisible(true);
                hotSpot.setEnabled(true);
            } else {
                hotSpot.setVisible(false);
                hotSpot.setEnabled(false);
            }

            menuRootMode.setVisible(true);
            menuRootMode.setEnabled(true);

        } else {

            if (mode == PROXY_MODE) {
                menuProxiesMode.setChecked(true);
            } else if (mode == VPN_MODE) {
                menuVPNMode.setChecked(true);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                menuVPNMode.setChecked(true);
                modulesStatus.setMode(VPN_MODE);
                mode = VPN_MODE;
                new PrefManager(this).setStrPref("OPERATION_MODE", mode.toString());
            } else {
                menuProxiesMode.setChecked(true);
                modulesStatus.setMode(PROXY_MODE);
                mode = PROXY_MODE;
                new PrefManager(this).setStrPref("OPERATION_MODE", mode.toString());
            }

            if (mitmDetected) {
                rootIcon.setIcon(R.drawable.ic_arp_attack_notification);
            } else if (mode == PROXY_MODE) {
                rootIcon.setIcon(R.drawable.ic_warning_white_24dp);
            } else {
                rootIcon.setIcon(R.drawable.ic_vpn_key_white_24dp);
            }

            hotSpot.setVisible(false);
            hotSpot.setEnabled(false);

            menuRootMode.setVisible(false);
            menuRootMode.setEnabled(false);
        }
    }

    private void switchApIcon(Menu menu) {
        ApManager apManager = new ApManager(this);
        int apState = apManager.isApOn();

        if (apState == ApManager.apStateON) {

            menu.findItem(R.id.item_hotspot).setIcon(R.drawable.ic_wifi_tethering_green_24dp);

            if (!new PrefManager(this).getBoolPref("APisON")) {

                new PrefManager(this).setBoolPref("APisON", true);

                modulesStatus.setIptablesRulesUpdateRequested(true);
                ModulesAux.requestModulesStatusUpdate(this);

            }

        } else if (apState == ApManager.apStateOFF) {
            menu.findItem(R.id.item_hotspot).setIcon(R.drawable.ic_portable_wifi_off_white_24dp);
            if (new PrefManager(this).getBoolPref("APisON")) {
                new PrefManager(this).setBoolPref("APisON", false);

                modulesStatus.setIptablesRulesUpdateRequested(true);
                ModulesAux.requestModulesStatusUpdate(this);
            }
        } else {
            menu.findItem(R.id.item_hotspot).setVisible(false);
            menu.findItem(R.id.item_hotspot).setEnabled(false);
        }
    }

    private void switchChildLockIcon(Menu menu) {
        MenuItem childLock = menu.findItem(R.id.item_unlock);

        try {
            if (childLockActive) {
                childLock.setIcon(R.drawable.ic_lock_white_24dp);
            } else {
                childLock.setIcon(R.drawable.ic_lock_open_white_24dp);
            }
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "MainActivity Child Lock Exeption " + e.getMessage());
        }
    }

    private void showNewTorIdentityIcon(Menu menu) {
        newIdentityMenuItem = menu.findItem(R.id.item_new_identity);

        if (newIdentityMenuItem == null || modulesStatus == null) {
            return;
        }

        newIdentityMenuItem.setVisible(modulesStatus.getTorState() != STOPPED);
    }

    public void showNewTorIdentityIcon(boolean show) {

        if (newIdentityMenuItem == null || modulesStatus == null) {
            return;
        }

        newIdentityMenuItem.setVisible(show);

        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (isInterfaceLocked() && id != R.id.item_unlock) {
            Toast.makeText(this, getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return false;
        }

        if (id == R.id.item_unlock) {
            if (isInterfaceLocked()) {
                childUnlock(item);
            } else {
                childLock(item);
            }
        } else if (id == R.id.item_hotspot) {
            switchHotspot();
        } else if (id == R.id.item_root) {
            showInfoAboutRoot();
        } else if (id == R.id.item_new_identity) {
            newTorIdentity();
        } else if (id == R.id.menu_root_mode) {
            switchToRootMode(item);
        } else if (id == R.id.menu_vpn_mode) {
            switchToVPNMode(item);
        } else if (id == R.id.menu_proxies_mode) {
            switchToProxyMode(item);
        }
        return super.onOptionsItemSelected(item);
    }

    private void switchHotspot() {
        try {
            ApManager apManager = new ApManager(this);
            if (apManager.configApState()) {
                checkHotspotState();
            } else if (!isFinishing()) {
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                intent.setComponent(cn);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivityForResult(intent, CODE_IS_AP_ON);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "MainActivity switchHotspot exception " + e.getMessage() + " " + e.getCause());
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "MainActivity onOptionsItemSelected exception " + e.getMessage() + " " + e.getCause());
        }
    }

    @SuppressLint("InflateParams")
    private void newTorIdentity() {
        if (modulesStatus != null && newIdentityMenuItem != null && modulesStatus.getTorState() == RUNNING) {

            if (rotateAnimation == null || animatingImage == null) {
                rotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                rotateAnimation.setDuration(1000);
                rotateAnimation.setRepeatCount(3);

                LayoutInflater inflater = getLayoutInflater();
                animatingImage = (ImageView) inflater.inflate(R.layout.icon_image_new_tor_identity, null);
            }

            if (rotateAnimation != null && animatingImage != null) {
                animatingImage.startAnimation(rotateAnimation);
                newIdentityMenuItem.setActionView(animatingImage);
            }

            ModulesRestarter.restartTor(this);

            if (isFinishing() || handler == null) {
                return;
            }

            handler.postDelayed(() -> {
                if (!isFinishing() && newIdentityMenuItem != null && newIdentityMenuItem.getActionView() != null) {
                    Toast.makeText(this, this.getText(R.string.toast_new_tor_identity), Toast.LENGTH_SHORT).show();
                    newIdentityMenuItem.getActionView().clearAnimation();
                    newIdentityMenuItem.setActionView(null);
                }
            }, 3000);
        }
    }

    private void switchToRootMode(MenuItem item) {
        item.setChecked(true);

        new PrefManager(this).setStrPref("OPERATION_MODE", ROOT_MODE.toString());

        Log.i(LOG_TAG, "Root mode enabled");

        boolean fixTTL = modulesStatus.isFixTTL() && !modulesStatus.isUseModulesWithRoot();

        OperationMode operationMode = modulesStatus.getMode();

        if ((operationMode == VPN_MODE) && !fixTTL) {
            ServiceVPNHelper.stop("Switch to root mode", this);
            Toast.makeText(this, getText(R.string.vpn_mode_off), Toast.LENGTH_LONG).show();
        } else if ((operationMode == PROXY_MODE) && fixTTL) {
            prepareVPNService();
        }

        //This start iptables adaptation
        modulesStatus.setMode(ROOT_MODE);
        modulesStatus.setIptablesRulesUpdateRequested(true);
        ModulesAux.requestModulesStatusUpdate(this);

        invalidateOptionsMenu();
    }

    private void switchToProxyMode(MenuItem item) {
        item.setChecked(true);

        new PrefManager(this).setStrPref("OPERATION_MODE", PROXY_MODE.toString());

        Log.i(LOG_TAG, "Proxy mode enabled");

        OperationMode operationMode = modulesStatus.getMode();

        //This stop iptables adaptation
        modulesStatus.setMode(PROXY_MODE);

        if (modulesStatus.isRootAvailable() && operationMode == ROOT_MODE) {
            IptablesRules iptablesRules = new ModulesIptablesRules(this);
            List<String> commands = iptablesRules.clearAll();
            iptablesRules.sendToRootExecService(commands);
            Log.i(LOG_TAG, "Iptables rules removed");
        } else if (operationMode == VPN_MODE) {
            ServiceVPNHelper.stop("Switch to proxy mode", this);
            Toast.makeText(this, getText(R.string.vpn_mode_off), Toast.LENGTH_LONG).show();
        }

        invalidateOptionsMenu();
    }

    private void switchToVPNMode(MenuItem item) {
        item.setChecked(true);

        new PrefManager(this).setStrPref("OPERATION_MODE", VPN_MODE.toString());

        Log.i(LOG_TAG, "VPN mode enabled");

        OperationMode operationMode = modulesStatus.getMode();

        //This stop iptables adaptation
        modulesStatus.setMode(VPN_MODE);

        if (modulesStatus.isRootAvailable() && operationMode == ROOT_MODE) {
            IptablesRules iptablesRules = new ModulesIptablesRules(this);
            List<String> commands = iptablesRules.clearAll();
            iptablesRules.sendToRootExecService(commands);
            Log.i(LOG_TAG, "Iptables rules removed");
        }

        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        ModuleState itpdState = modulesStatus.getItpdState();

        if (dnsCryptState != STOPPED || torState != STOPPED || itpdState != STOPPED) {
            if (modulesStatus.isUseModulesWithRoot()) {
                Toast.makeText(this, "Stop modules...", Toast.LENGTH_LONG).show();
                disableUseModulesWithRoot();
            } else {
                prepareVPNService();
            }
        }

        if (dnsCryptState == STOPPED && torState == STOPPED && itpdState == STOPPED
                && modulesStatus.isUseModulesWithRoot()) {
            disableUseModulesWithRoot();
        }

        invalidateOptionsMenu();
    }

    private void disableUseModulesWithRoot() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.edit().putBoolean("swUseModulesRoot", false).apply();

        ModulesAux.stopModulesIfRunning(this);
        modulesStatus.setUseModulesWithRoot(false);
        modulesStatus.setContextUIDUpdateRequested(true);
        ModulesAux.requestModulesStatusUpdate(this);

        Log.i(LOG_TAG, "Switch to VPN mode, disable use modules with root option");
    }

    private void checkHotspotState() {

        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
        }

        ScheduledExecutorService timer = TopFragment.getModulesLogsTimer();

        if (timer == null || timer.isShutdown()) {
            return;
        }

        scheduledFuture = timer.scheduleAtFixedRate(new Runnable() {

            int loop = 0;

            @Override
            public void run() {

                if (++loop > 3 && scheduledFuture != null && !scheduledFuture.isCancelled()) {
                    scheduledFuture.cancel(false);
                }

                runOnUiThread(() -> invalidateOptionsMenu());
            }
        }, 3, 5, TimeUnit.SECONDS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CODE_IS_AP_ON) {
            checkHotspotState();
        }
        if (requestCode == CODE_IS_VPN_ALLOWED) {
            vpnRequested = false;
            startVPNService(resultCode);
        }
    }


    private void childLock(final MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle(R.string.action_mode_child_lock);
        builder.setMessage(R.string.action_mode_dialog_message_lock);
        builder.setIcon(R.drawable.ic_lock_outline_blue_24dp);


        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
        final EditText input = inputView.findViewById(R.id.etForDialog);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final MainActivity mainActivity = this;

        String saved_pass = new PrefManager(getApplicationContext()).getStrPref("passwd");
        if (!saved_pass.isEmpty()) {
            saved_pass = new String(Base64.decode(saved_pass, 16));
            input.setText(saved_pass);
            input.setSelection(saved_pass.length());
        }
        builder.setView(inputView);

        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            if (input.getText().toString().equals("debug")) {
                TopFragment.debug = !TopFragment.debug;
                Toast.makeText(getApplicationContext(), "Debug mode " + TopFragment.debug, Toast.LENGTH_LONG).show();
            } else {
                String pass = Base64.encodeToString((input.getText().toString() + "-l-o-c-k-e-d").getBytes(), 16);
                new PrefManager(getApplicationContext()).setStrPref("passwd", pass);
                Toast.makeText(getApplicationContext(), getText(R.string.action_mode_dialog_locked), Toast.LENGTH_SHORT).show();
                item.setIcon(R.drawable.ic_lock_white_24dp);
                childLockActive = true;

                DrawerLayout mDrawerLayout = mainActivity.findViewById(R.id.drawer_layout);
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel());

        builder.show();
    }

    private void childUnlock(final MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle(R.string.action_mode_child_lock);
        builder.setMessage(R.string.action_mode_dialog_message_unlock);
        builder.setIcon(R.drawable.ic_lock_outline_blue_24dp);

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
        final EditText input = inputView.findViewById(R.id.etForDialog);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(inputView);

        final MainActivity mainActivity = this;

        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            String saved_pass = new String(Base64.decode(new PrefManager(getApplicationContext()).getStrPref("passwd"), 16));
            if (saved_pass.replace("-l-o-c-k-e-d", "").equals(input.getText().toString())) {
                String pass = Base64.encodeToString((input.getText().toString()).getBytes(), 16);
                new PrefManager(getApplicationContext()).setStrPref("passwd", pass);
                Toast.makeText(getApplicationContext(), getText(R.string.action_mode_dialog_unlocked), Toast.LENGTH_SHORT).show();
                item.setIcon(R.drawable.ic_lock_open_white_24dp);
                childLockActive = false;

                DrawerLayout mDrawerLayout = mainActivity.findViewById(R.id.drawer_layout);
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            } else {
                childUnlock(item);
                Toast.makeText(getApplicationContext(), getText(R.string.action_mode_dialog_wrong_pass), Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel());

        builder.show();

    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();

        if (isInterfaceLocked()) {
            Toast.makeText(this, getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            DrawerLayout drawer = findViewById(R.id.drawer_layout);
            drawer.closeDrawer(GravityCompat.START);
            return false;
        }

        if (id == R.id.nav_backup) {
            Intent intent = new Intent(this, BackupActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_DNS_Pref) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setAction("DNS_Pref");
            startActivity(intent);
        } else if (id == R.id.nav_Tor_Pref) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setAction("Tor_Pref");
            startActivity(intent);
        } else if (id == R.id.nav_I2PD_Pref) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setAction("I2PD_Pref");
            startActivity(intent);
        } else if (id == R.id.nav_fast_Pref) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setAction("fast_Pref");
            startActivity(intent);
        } else if (id == R.id.nav_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_common_Pref) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setAction("common_Pref");
            startActivity(intent);
        } else if (id == R.id.nav_help) {
            Intent intent = new Intent(this, HelpActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_Donate) {
            if (appVersion.startsWith("g")) {
                if (accelerateDevelop != null && !accelerated) {
                    accelerateDevelop.launchBilling(AccelerateDevelop.mSkuId);
                }
            } else {
                String link;
                if (Locale.getDefault().getLanguage().equalsIgnoreCase("ru")) {
                    link = "https://invizible.net/ru/donate/";
                } else {
                    link = "https://invizible.net/en/donate/";
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "MainActivity ACTION_VIEW exception " + e.getMessage() + " " + e.getCause());
                }

            }
        } else if (id == R.id.nav_Code) {
            Registration registration = new Registration(this);
            registration.showEnterCodeDialog();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    public void showUpdateMessage(final String message) {
        if (modernDialog != null)
            modernDialog.dismiss();
        modernDialog = null;

        if (isFinishing() || handler == null) {
            return;
        }

        handler.postDelayed(() -> {
            if (!isFinishing()) {
                DialogFragment commandResult = NotificationDialogFragment.newInstance(message);
                commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
            }
        }, 500);

    }

    public DialogInterface modernProgressDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
        builder.setTitle(R.string.update_checking_title);
        builder.setMessage(R.string.update_checking_message);
        builder.setIcon(R.drawable.ic_visibility_off_black_24dp);
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            if (modernDialog != null) {
                modernDialog.dismiss();
                modernDialog = null;

                //////////////To STOP UPDATES CHECK/////////////////////////////////////////////////////
                TopFragment topFragment = (TopFragment) getSupportFragmentManager().findFragmentByTag("topFragmentTAG");
                if (topFragment != null && topFragment.updateCheck != null && topFragment.updateCheck.context != null) {
                    topFragment.updateCheck.context = null;
                }

            }
        });

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        builder.setCancelable(false);

        return builder.show();
    }

    private void startAppExitDetectService() {

        if (isFinishing() || handler == null) {
            return;
        }

        handler.postDelayed(() -> {
            if (!isFinishing()) {
                Intent intent = new Intent(this, AppExitDetectService.class);
                startService(intent);
                Log.i(LOG_TAG, "Start app exit detect service");
            }
        }, 1000);
    }

    private void showInfoAboutRoot() {
        boolean rootIsAvailable = new PrefManager(this).getBoolPref("rootIsAvailable");
        boolean busyBoxIsAvailable = new PrefManager(this).getBoolPref("bbOK");
        boolean mitmDetected = ArpScanner.INSTANCE.getArpAttackDetected()
                || ArpScanner.INSTANCE.getDhcpGatewayAttackDetected();

        if (mitmDetected) {
            DialogFragment commandResult = NotificationDialogFragment.newInstance(getString(R.string.notification_mitm));
            commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
        } else if (rootIsAvailable) {
            DialogFragment commandResult;
            if (busyBoxIsAvailable) {
                commandResult = NotificationDialogFragment.newInstance(TopFragment.verSU + "\n\t\n" + TopFragment.verBB);
            } else {
                commandResult = NotificationDialogFragment.newInstance(TopFragment.verSU);
            }
            commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
        } else {
            DialogFragment commandResult;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                commandResult = NotificationDialogFragment.newInstance(R.string.message_no_root_used);
            } else {
                commandResult = NotificationDialogFragment.newInstance(R.string.message_no_root_used_kitkat);
            }

            commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
        }
    }

    public void prepareVPNService() {
        Log.i(LOG_TAG, "MainActivity prepare VPN Service");

        final Intent prepareIntent = VpnService.prepare(this);

        if (prepareIntent == null) {
            startVPNService(RESULT_OK);
        } else if (!vpnRequested && !isFinishing()) {
            vpnRequested = true;
            try {
                startActivityForResult(prepareIntent, CODE_IS_VPN_ALLOWED);
            } catch (Exception e) {
                if (!isFinishing()) {
                    Toast.makeText(this, getString(R.string.wrong), Toast.LENGTH_SHORT).show();
                }
                Log.e(LOG_TAG, "Main Activity prepareVPNService exception " + e.getMessage() + " " + e.getCause());
            }

        }

    }

    private void startVPNService(int resultCode) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("VPNServiceEnabled", resultCode == RESULT_OK).apply();
        if (resultCode == RESULT_OK) {
            ServiceVPNHelper.start("VPN Service is Prepared", this);
            Toast.makeText(this, getText(R.string.vpn_mode_active), Toast.LENGTH_SHORT).show();
        } else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this, getText(R.string.vpn_mode_off), Toast.LENGTH_LONG).show();
            ModulesAux.stopModulesIfRunning(this);
        }
    }

    public void changeDrawerWithVersionAndDestination(NavigationView navigationView) {
        if (navigationView == null) {
            return;
        }

        MenuItem item = navigationView.getMenu().findItem(R.id.nav_Donate);
        if ((appVersion.startsWith("g") && accelerated) || appVersion.startsWith("p") || appVersion.startsWith("f")) {
            if (item != null) {
                item.setVisible(false);
            }
        } else {
            if (item != null) {
                item.setVisible(true);

                if (appVersion.startsWith("g")) {
                    item.setTitle(R.string.premium);
                }
            }
        }

        item = navigationView.getMenu().findItem(R.id.nav_Code);
        if (appVersion.startsWith("l")) {
            if (item != null) {
                item.setVisible(true);
            }
        } else {
            if (item != null) {
                item.setVisible(false);
            }
        }
    }

    private void registerBroadcastReceiver() {
        mainActivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ArpScannerKt.mitmAttackWarning.equals(intent.getAction())) {
                    handler.post(() -> invalidateOptionsMenu());
                }
            }
        };

        IntentFilter mitmDetected = new IntentFilter(ArpScannerKt.mitmAttackWarning);
        LocalBroadcastManager.getInstance(this).registerReceiver(mainActivityReceiver, mitmDetected);
    }

    private void unregisterBroadcastReceiver() {
        if (mainActivityReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mainActivityReceiver);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        unregisterBroadcastReceiver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }

        if (modernDialog != null) {
            modernDialog.dismiss();
            modernDialog = null;
        }

        if (viewPager != null) {
            viewPagerPosition = viewPager.getCurrentItem();
            viewPager = null;
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        if (accelerateDevelop != null) {
            accelerateDevelop = null;
        }

        topFragment = null;
        dNSCryptRunFragment = null;
        torRunFragment = null;
        iTPDRunFragment = null;
        mainFragment = null;

        newIdentityMenuItem = null;
        animatingImage = null;
        rotateAnimation = null;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK && handler != null) {
            Log.e(LOG_TAG, "FORCE CLOSE ALL");

            Toast.makeText(this, "Force Close ...", Toast.LENGTH_LONG).show();

            ModulesKiller.forceCloseApp(PathVars.getInstance(this));

            handler.postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, ModulesService.class);
                intent.setAction(ModulesService.actionStopService);
                startService(intent);
            }, 3000);

            handler.postDelayed(() -> System.exit(0), 5000);


            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }
}

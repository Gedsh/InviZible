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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import pan.alexander.tordnscrypt.backup.BackupActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.help.HelpActivity;
import pan.alexander.tordnscrypt.iptables.IptablesRules;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.ApManager;
import pan.alexander.tordnscrypt.utils.AppExitDetectService;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Registration;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class MainActivity extends LangAppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static DialogInterface modernDialog = null;
    private static final int CODE_IS_AP_ON = 100;

    public boolean childLockActive = false;
    private Timer timer;
    private Handler handler;
    private TopFragment topFragment;
    private DNSCryptRunFragment dNSCryptRunFragment;
    private TorRunFragment torRunFragment;
    private ITPDRunFragment iTPDRunFragment;
    private ModulesStatus modulesStatus;

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
        navigationView.setNavigationItemSelectedListener(this);

        modulesStatus = ModulesStatus.getInstance();

        startAppExitDetectService();
    }

    @Override
    public void onResume() {
        super.onResume();

        handler = new Handler();

        childLockActive = isInterfaceLocked();

        checkUpdates();

        showUpdateResultMessage();
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
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        if (fragment instanceof DNSCryptRunFragment) {
            dNSCryptRunFragment = (DNSCryptRunFragment) fragment;
        } else if (fragment instanceof TorRunFragment) {
            torRunFragment = (TorRunFragment) fragment;
        } else if (fragment instanceof ITPDRunFragment) {
            iTPDRunFragment = (ITPDRunFragment) fragment;
        } else if (fragment instanceof TopFragment) {
            topFragment = (TopFragment) fragment;
        }
    }

    private void setDayNightTheme() {
        SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            String theme = defaultSharedPreferences.getString("pref_fast_theme", "4");
            switch (Objects.requireNonNull(theme)) {
                case "1":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    break;
                case "2":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    break;
                case "3":
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
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

    public void showUpdateResultMessage() {
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

    public TopFragment getTopFragment() {
        return topFragment;
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

        return super.onPrepareOptionsMenu(menu);
    }

    private void switchIconsDependingOnMode(Menu menu, boolean rootIsAvailable) {

        boolean busyBoxIsAvailable = new PrefManager(this).getBoolPref("bbOK");
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
            }

            if (mode == ROOT_MODE && busyBoxIsAvailable) {
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
            } else {
                menuProxiesMode.setChecked(true);
                modulesStatus.setMode(PROXY_MODE);
                mode = PROXY_MODE;
            }

            if (mode == PROXY_MODE) {
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
            new PrefManager(this).setBoolPref("APisON", false);
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        switch (id) {
            case R.id.item_unlock:
                if (isInterfaceLocked()) {
                    childUnlock(item);
                } else {
                    childLock(item);
                }
                break;
            case R.id.item_hotspot:
                switchHotspot();
                break;
            case R.id.item_root:
                showInfoAboutRoot();
                break;
            case R.id.menu_root_mode:
                switchToRootMode(item);
                break;
            case R.id.menu_vpn_mode:
                switchToVPNMode(item);
                break;
            case R.id.menu_proxies_mode:
                switchToProxyMode(item);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void switchHotspot() {
        try {
            ApManager apManager = new ApManager(this);
            if (apManager.configApState()) {
                checkHotspotState();
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                intent.setComponent(cn);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivityForResult(intent, CODE_IS_AP_ON);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "MainActivity onOptionsItemSelected exception " + e.getMessage() + " " + e.getCause());
        }
    }

    private void switchToRootMode(MenuItem item) {
        item.setChecked(true);

        new PrefManager(this).setStrPref("OPERATION_MODE", ROOT_MODE.toString());

        Log.i(LOG_TAG, "Root mode enabled");

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
            String[] commands = iptablesRules.clearAll();
            iptablesRules.sendToRootExecService(commands);
            Log.i(LOG_TAG, "Iptables rules removed");
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
            String[] commands = iptablesRules.clearAll();
            iptablesRules.sendToRootExecService(commands);
            Log.i(LOG_TAG, "Iptables rules removed");
        }
    }

    private void checkHotspotState() {

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        timer = new Timer();
        timer.schedule(new TimerTask() {

            int loop = 0;

            @Override
            public void run() {

                if (++loop > 3) {
                    timer.cancel();
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        invalidateOptionsMenu();
                    }
                });
            }
        }, 3000, 5000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CODE_IS_AP_ON) {
            checkHotspotState();
        }
    }


    private void childLock(final MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
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

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
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
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        AlertDialog view = builder.show();
        Objects.requireNonNull(view.getWindow()).getDecorView().setBackgroundColor(Color.TRANSPARENT);
    }

    private void childUnlock(final MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.action_mode_child_lock);
        builder.setMessage(R.string.action_mode_dialog_message_unlock);
        builder.setIcon(R.drawable.ic_lock_outline_blue_24dp);

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
        final EditText input = inputView.findViewById(R.id.etForDialog);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(inputView);

        final MainActivity mainActivity = this;

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
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
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });

        AlertDialog view = builder.show();
        Objects.requireNonNull(view.getWindow()).getDecorView().setBackgroundColor(Color.TRANSPARENT);
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();

        if (childLockActive) {
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
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://invizible.net/donate"));
            startActivity(intent);
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

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                DialogFragment commandResult = NotificationDialogFragment.newInstance(message);
                commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
            }
        }, 500);

    }

    public DialogInterface modernProgressDialog() {
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this, R.style.CustomDialogTheme);
        builder.setTitle(R.string.update_checking_title);
        builder.setMessage(R.string.update_checking_message);
        builder.setIcon(R.drawable.ic_visibility_off_black_24dp);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (modernDialog != null) {
                    modernDialog.dismiss();
                    modernDialog = null;

                    //////////////To STOP UPDATES CHECK/////////////////////////////////////////////////////
                    TopFragment topFragment = (TopFragment) getSupportFragmentManager().findFragmentByTag("topFragmentTAG");
                    if (topFragment != null) {
                        topFragment.updateCheck.context = null;
                    }

                }
            }
        });

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        builder.setCancelable(false);

        AlertDialog view = builder.show();
        Objects.requireNonNull(view.getWindow()).getDecorView().setBackgroundColor(Color.TRANSPARENT);

        return view;
    }

    private void startAppExitDetectService() {
        Intent intent = new Intent(this, AppExitDetectService.class);
        startService(intent);
    }

    private void showInfoAboutRoot() {
        boolean rootIsAvailable = new PrefManager(this).getBoolPref("rootIsAvailable");
        boolean busyBoxIsAvailable = new PrefManager(this).getBoolPref("bbOK");

        if (rootIsAvailable) {
            DialogFragment commandResult;
            if (busyBoxIsAvailable) {
                commandResult = NotificationDialogFragment.newInstance(TopFragment.verSU + "\n\t\n" + TopFragment.verBB);
            } else {
                commandResult = NotificationDialogFragment.newInstance(TopFragment.verSU);
            }
            commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
        } else {
            DialogFragment commandResult = NotificationDialogFragment.newInstance(R.string.message_no_root_used);
            commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null)
            timer.cancel();
        if (modernDialog != null) {
            modernDialog.dismiss();
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.e(LOG_TAG, "FORCE CLOSE ALL");

            Toast.makeText(this, "Force Close ...", Toast.LENGTH_LONG).show();

            ModulesKiller.forceCloseApp(new PathVars(this));

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(MainActivity.this, ModulesService.class);
                    stopService(intent);
                }
            }, 3000);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    System.exit(0);
                }
            }, 5000);


            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

}

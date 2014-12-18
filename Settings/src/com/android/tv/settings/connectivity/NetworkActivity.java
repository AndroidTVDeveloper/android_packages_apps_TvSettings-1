/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.settings.connectivity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.IntentFilter;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import com.android.tv.settings.dialog.SettingsLayoutActivity;
import com.android.tv.settings.dialog.Layout;
import com.android.tv.settings.dialog.Layout.Header;
import com.android.tv.settings.dialog.Layout.Action;
import com.android.tv.settings.dialog.Layout.Status;
import com.android.tv.settings.dialog.Layout.Static;
import com.android.tv.settings.dialog.Layout.StringGetter;
import com.android.tv.settings.dialog.Layout.LayoutGetter;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Activity to manage network settings.
 */
public class NetworkActivity extends SettingsLayoutActivity implements
        ConnectivityListener.Listener {

    private static final boolean DEBUG = false;
    private static final String TAG = "NetworkActivity";
    private static final int REQUEST_CODE_ADVANCED_OPTIONS = 1;
    private static final int WIFI_REFRESH_INTERVAL_CAP_MILLIS = 5 * 1000;//15 * 1000; wxl reduce wait time for wifi ap refreshing

    private ConnectivityListener mConnectivityListener;
    private Resources mRes;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mRes = getResources();
        mConnectivityListener = new ConnectivityListener(this, this);
        mConnectivityListener.start();
        super.onCreate(savedInstanceState);

        //add for wifi device switch
        mIntentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        mConnectivityListener.stop();
        mContext.unregisterReceiver(mReceiver);
    }

    @Override
    public void onConnectivityChange(Intent intent) {
        mEthernetConnectedDescription.refreshView();
        mWifiConnectedDescription.refreshView();
    }

    @Override
    public void onResume() {
        if (mWifiShortListLayout != null) {
            mWifiShortListLayout.onWifiListInvalidated();
        }
        if (mWifiAllListLayout != null) {
            mWifiAllListLayout.onWifiListInvalidated();
        }
        super.onResume();
    }

    StringGetter mEthernetConnectedDescription = new StringGetter() {
        private boolean lastIsEthernetConnected;
        @Override
        public String get() {
            lastIsEthernetConnected =
                    mConnectivityListener.getConnectivityStatus().isEthernetConnected();
            int resId = lastIsEthernetConnected ? R.string.connected : R.string.not_connected;
            return mRes.getString(resId);
        }
        @Override
        public void refreshView() {
            if (mConnectivityListener.getConnectivityStatus().isEthernetConnected() !=
                    lastIsEthernetConnected) {
                super.refreshView();
            }
        }
    };

    StringGetter mWifiConnectedDescription = new StringGetter() {
        private boolean lastIsWifiConnected;
        @Override
        public String get() {
            lastIsWifiConnected = mConnectivityListener.getConnectivityStatus().isWifiConnected();
            int resId = lastIsWifiConnected ? R.string.connected : R.string.not_connected;
            return mRes.getString(resId);
        }
        @Override
        public void refreshView() {
            if (mConnectivityListener.getConnectivityStatus().isWifiConnected() !=
                    lastIsWifiConnected) {
                super.refreshView();
            }
        }
    };

    StringGetter mEthernetIPAddress = new StringGetter() {
        public String get() {
            ConnectivityListener.ConnectivityStatus status =
                    mConnectivityListener.getConnectivityStatus();
            if (status.isEthernetConnected()) {
                return mConnectivityListener.getEthernetIpAddress();
            } else {
                return "";
            }
        }
    };

    StringGetter mEthernetMacAddress = new StringGetter() {
        public String get() {
            return mConnectivityListener.getEthernetMacAddress();
        }
    };

    LayoutGetter mEthernetAdvancedLayout = new LayoutGetter() {
        public Layout get() {
            Layout layout = new Layout();
            // Do not check Ethernet's availability here
            // because it might not be active due to the invalid configuration.
            IpConfiguration ipConfiguration = mConnectivityListener.getIpConfiguration();
            if (ipConfiguration != null) {
                int proxySettingsResourceId =
                    (ipConfiguration.getProxySettings() == ProxySettings.STATIC) ?
                        R.string.wifi_action_proxy_manual :
                        R.string.wifi_action_proxy_none;
                int ipSettingsResourceId =
                    (ipConfiguration.getIpAssignment() == IpAssignment.STATIC) ?
                        R.string.wifi_action_static :
                        R.string.wifi_action_dhcp;
                layout
                    .add(new Action.Builder(mRes, ACTION_ETHERNET_PROXY_SETTINGS)
                            .title(R.string.title_wifi_proxy_settings)
                            .description(proxySettingsResourceId).build())
                    .add(new Action.Builder(mRes, ACTION_ETHERNET_IP_SETTINGS)
                            .title(R.string.title_wifi_ip_settings)
                            .description(ipSettingsResourceId).build());
            } else {
                layout
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_internet_connection)
                            .description(R.string.not_connected).build());
            }
            return layout;
        }
    };

    LayoutGetter mEthernetLayout = new LayoutGetter() {
        public Layout get() {
            boolean ethernetConnected =
                    mConnectivityListener.getConnectivityStatus().isEthernetConnected();
            if (ethernetConnected) {
                return new Layout()
                    .add(new Status.Builder(mRes).title(R.string.title_internet_connection)
                            .description(R.string.connected).build())
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_ip_address)
                            .description(mEthernetIPAddress)
                            .build())
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_mac_address)
                            .description(mEthernetMacAddress)
                            .build())
                    .add(new Header.Builder(mRes)
                            .title(R.string.wifi_action_advanced_options_title).build()
                        .add(mEthernetAdvancedLayout)
                     );

            } else {
                return new Layout()
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_internet_connection)
                            .description(R.string.not_connected)
                            .build())
                    .add(new Header.Builder(mRes)
                            .title(R.string.wifi_action_advanced_options_title).build()
                        .add(mEthernetAdvancedLayout)
                    );
            }
        }
    };

    private static final int NUMBER_SIGNAL_LEVELS = 4;
    private static final int ACTION_WIFI_FORGET_NETWORK = 1;
    private static final int ACTION_WIFI_PROXY_SETTINGS = 4;
    private static final int ACTION_WIFI_IP_SETTINGS = 5;
    private static final int ACTION_ETHERNET_PROXY_SETTINGS = 6;
    private static final int ACTION_ETHERNET_IP_SETTINGS = 7;

    private final Context mContext = this;

    private int getCurrentNetworkIconResourceId(
            ScanResult scanResult, int signalLevel) {
        int resourceId = 0;
        if (scanResult != null) {
            WifiSecurity security = WifiSecurity.getSecurity(scanResult);
            if (security.isOpen()) {
                switch (signalLevel) {
                    case 0:
                        resourceId = R.drawable.ic_settings_wifi_active_1;
                        break;
                    case 1:
                        resourceId = R.drawable.ic_settings_wifi_active_2;
                        break;
                    case 2:
                        resourceId = R.drawable.ic_settings_wifi_active_3;
                        break;
                    case 3:
                        resourceId = R.drawable.ic_settings_wifi_active_4;
                        break;
                }
            } else {
                switch (signalLevel) {
                    case 0:
                        resourceId = R.drawable.ic_settings_wifi_secure_active_1;
                        break;
                    case 1:
                        resourceId = R.drawable.ic_settings_wifi_secure_active_2;
                        break;
                    case 2:
                        resourceId = R.drawable.ic_settings_wifi_secure_active_3;
                        break;
                    case 3:
                        resourceId = R.drawable.ic_settings_wifi_secure_active_4;
                        break;
                }
            }
        }
        return resourceId;
    }

    private String getSignalStrength() {
        String[] signalLevels = mRes.getStringArray(R.array.wifi_signal_strength);
        int strength = mConnectivityListener.getWifiSignalStrength(signalLevels.length);
        return signalLevels[strength];
    }

    LayoutGetter mWifiInfoLayout = new LayoutGetter() {
        public Layout get() {
            Layout layout = new Layout();
            ConnectivityListener.ConnectivityStatus status =
                    mConnectivityListener.getConnectivityStatus();
            boolean isConnected = status.isWifiConnected();
            if (isConnected) {
                layout
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_internet_connection)
                            .description(R.string.connected).build())
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_ip_address)
                            .description(mConnectivityListener.getWifiIpAddress()).build())
                    .add(new Status.Builder(mRes)
                             .title(R.string.title_mac_address)
                            .description(mConnectivityListener.getWifiMacAddress()).build())
                    .add(new Status.Builder(mRes)
                             .title(R.string.title_signal_strength)
                            .description(getSignalStrength()).build());
            } else {
                layout
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_internet_connection)
                            .description(R.string.not_connected).build());
            }
            return layout;
        }
    };

    LayoutGetter mWifiAdvancedLayout = new LayoutGetter() {
        public Layout get() {
            Layout layout = new Layout();
            WifiConfiguration wifiConfiguration = mConnectivityListener.getWifiConfiguration();
            if (wifiConfiguration != null) {
                int proxySettingsResourceId =
                    (wifiConfiguration.getProxySettings() == ProxySettings.NONE) ?
                        R.string.wifi_action_proxy_none :
                        R.string.wifi_action_proxy_manual;
                int ipSettingsResourceId =
                   (wifiConfiguration.getIpAssignment() == IpAssignment.STATIC) ?
                        R.string.wifi_action_static :
                        R.string.wifi_action_dhcp;
                layout
                    .add(new Action.Builder(mRes, ACTION_WIFI_PROXY_SETTINGS)
                            .title(R.string.title_wifi_proxy_settings)
                            .description(proxySettingsResourceId).build())
                    .add(new Action.Builder(mRes, ACTION_WIFI_IP_SETTINGS)
                            .title(R.string.title_wifi_ip_settings)
                            .description(ipSettingsResourceId).build());
            } else {
                layout
                    .add(new Status.Builder(mRes)
                            .title(R.string.title_internet_connection)
                            .description(R.string.not_connected).build());
            }
            return layout;
        }
    };

    private void addWifiConnectedHeader(Layout layout, String SSID) {
        layout
            .add(new Header.Builder(mRes)
                    .title(SSID)
                    .description(R.string.connected).build()
                .add(new Header.Builder(mRes)
                        .title(R.string.wifi_action_status_info).build()
                    .add(mWifiInfoLayout)
                )
                .add(new Header.Builder(mRes)
                        .title(R.string.wifi_action_advanced_options_title).build()
                    .add(mWifiAdvancedLayout)
                )
                .add(new Header.Builder(mRes)
                        .title(R.string.wifi_forget_network).build()
                    .add(new Action.Builder(mRes, ACTION_WIFI_FORGET_NETWORK)
                            .title(R.string.title_ok).build())
                    .add(new Action.Builder(mRes, Action.ACTION_BACK)
                            .title(R.string.title_cancel).build())
                 )
            );
    }

    private class WifiListLayout extends LayoutGetter implements
            ConnectivityListener.WifiNetworkListener {
        private final boolean mTop3EntriesOnly;
        private String mSelectedTitle;
        private long mLastWifiRefresh = 0;

        private final Runnable mRefreshViewRunnable = new Runnable() {
            @Override
            public void run() {
                Layout.Node selected = getSelectedNode();
                if (selected != null) {
                    mSelectedTitle = selected.getTitle();
                }
                refreshView();
            }
        };

        WifiListLayout(boolean top3EntriesOnly) {
            mTop3EntriesOnly = top3EntriesOnly;
        }

        @Override
        public Layout get() {
            mConnectivityListener.startScanningWifi(this);
            mLastWifiRefresh = SystemClock.elapsedRealtime();
            mHandler.removeCallbacks(mRefreshViewRunnable);
            return initAvailableWifiNetworks(mTop3EntriesOnly, mSelectedTitle).
                    setSelectedByTitle(mSelectedTitle);
        }

        @Override
        public void onMovedOffScreen() {
            mHandler.removeCallbacks(mRefreshViewRunnable);
            mConnectivityListener.stopScanningWifi(this);
        }

        @Override
        public void onWifiListChanged() {
            long now = SystemClock.elapsedRealtime();
            long millisToNextRefreshView =
                    WIFI_REFRESH_INTERVAL_CAP_MILLIS - now + mLastWifiRefresh;
            mHandler.removeCallbacks(mRefreshViewRunnable);
            mHandler.postDelayed(mRefreshViewRunnable, millisToNextRefreshView);
        }

        /**
         * Wifi network configuration has changed and an immediate refresh of the list of Wifi
         * networks is required.
         */
        public void onWifiListInvalidated() {
            mLastWifiRefresh = 0;
            onWifiListChanged();
        }

        /**
         * Create a list of available Wifi networks sorted by connection status (a connected Wifi
         * network is shown at the first position on the list) and signal strength, with the
         * provisio that the wireless network with SSID "mustHave" should be included in the list
         * even if it would be otherwise excluded.
         *
         * @param top3EntriesOnly Show only 3 entries in the list.
         * @param mustHave        Include this wifi network in the list even if it would otherwise
         *                        be excluded by virtue of inadequate signal strength.
         */
        private Layout initAvailableWifiNetworks(boolean top3EntriesOnly, String mustHave) {
            List<ScanResult> networks = mConnectivityListener.getAvailableNetworks();
            Layout layout = new Layout();
            if (networks.size() > 0) {
                int maxItems = top3EntriesOnly ? 3 : Integer.MAX_VALUE;
                // "networks" is already sorted by the signal strength and connection status.
                // Generate a new list with size less than "maxItems" that ensures "mustHave" is
                // included.
                boolean haveMustHave = false;
                List<ScanResult> displayList = new ArrayList<ScanResult>();
                for (ScanResult scanResult : networks) {
                    if (!haveMustHave && TextUtils.equals(scanResult.SSID, mustHave)) {
                        haveMustHave = true;
                        if (displayList.size() == maxItems) {
                            displayList.remove(maxItems-1);
                        }
                        displayList.add(scanResult);
                    } else if (displayList.size() < maxItems) {
                        displayList.add(scanResult);
                    }
                    if (haveMustHave && displayList.size() == maxItems) {
                        break;
                    }
                }

                // If a network is connected, it will be the first on the list.
                boolean isConnected =
                    mConnectivityListener.getConnectivityStatus().isWifiConnected();
                for (ScanResult network : displayList) {
                    if (network != null) {
                        WifiSecurity security = WifiSecurity.getSecurity(network);

                        String networkDescription =
                            security.isOpen() ? "" : security.getName(mContext);
                        Intent intent =
                            WifiConnectionActivity.createIntent(mContext, network, security);
                        int signalLevel = WifiManager.calculateSignalLevel(
                                network.level, NUMBER_SIGNAL_LEVELS);
                        //TODO implement signal dependent list icon.
                        /*
                        int imageResourceId = getNetworkIconResourceId(network, signalLevel);
                        if (WifiConfigHelper.areSameNetwork(mWifiManager, network,
                                currentConnection)) {
                            networkDescription = getString(R.string.connected);
                            signalLevel = WifiManager.calculateSignalLevel(
                                    currentConnection.getRssi(), NUMBER_SIGNAL_LEVELS);
                            imageResourceId = getCurrentNetworkIconResourceId(network, signalLevel);
                        } */

                        if (isConnected) {
                            addWifiConnectedHeader(layout, network.SSID);
                        } else {
                            layout.add(new Action.Builder(mRes, intent)
                                    .title(network.SSID)
                                    .description(networkDescription).build());
                        }
                    }
                    isConnected = false;
                }
            } else {
                if (wifiDevSwitchEnable) {
                    if (getWifiDevEnable()) {
                        layout.add(new Action.Builder(mRes, 0)
                           .title(R.string.title_wifi_no_networks_available).build());
                    }
                    else {
                        layout.add(new Action.Builder(mRes, 0)
                           .title(R.string.title_wifi_should_open_first).build());
                    }
                }
                else {
                    if (!getWifiDevEnable()) {
                        setWifiDevEnable(true);
                    }
                    layout.add(new Action.Builder(mRes, 0)
                           .title(R.string.title_wifi_no_networks_available).build());
                }
            }
            return layout;
        }
    };

    private final WifiListLayout mWifiShortListLayout = new WifiListLayout(true);

    private final WifiListLayout mWifiAllListLayout = new WifiListLayout(false);

    //add for wifi device switch 20141203
    private static final boolean wifiDevSwitchEnable = true;
    private static final int ACTION_WIFI_SWITCH = 0xF0;
    private static final String KEY_ON_OFF = "on_off";
    private static final int DISABLED = 0;
    private static final int ENABLED = 1;
    private static final int MSG_WIFI_ON = 0xE0;
    private static final int MSG_WIFI_OFF = 0xE1;
    private WifiManager mWifiManager = null;
    private IntentFilter mIntentFilter;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if ((state == WifiManager.WIFI_STATE_ENABLED) || (state == WifiManager.WIFI_STATE_DISABLED)) {
                    mWifiStateDescription.refreshView();
                    mWifiShortListLayout.onWifiListInvalidated();
                    mWifiAllListLayout.onWifiListInvalidated();
                }
            }
        }
    };

    private Handler mDevHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WIFI_ON:
                    setWifiDevEnable(true);
                    break;
                case MSG_WIFI_OFF:
                    setWifiDevEnable(false);
                    break;
                default:
                    Log.e(TAG,"[handleMessage]error msg.what:"+msg.what);
                    break;
            }
        }
    };

    private void sendWifiOnMsg() {
         if (mDevHandler != null) {
            Message msg = mDevHandler.obtainMessage(MSG_WIFI_ON);
            mDevHandler.sendMessage(msg);
        }
    }

    private void sendWifiOffMsg() {
         if (mDevHandler != null) {
            Message msg = mDevHandler.obtainMessage(MSG_WIFI_OFF);
            mDevHandler.sendMessage(msg);
        }
    }

    private LayoutGetter getOnOffLayout(final int action) {
        return new LayoutGetter() {
            @Override
            public Layout get() {
                Bundle on = new Bundle();
                on.putBoolean(KEY_ON_OFF, true);
                Bundle off = new Bundle();
                off.putBoolean(KEY_ON_OFF, false);

                Layout layout = new Layout()
                            .add(new Action.Builder(mRes, action)
                                    .title(R.string.on)
                                    .data(on)
                                    .checked(getWifiDevEnable() == true)
                                    .build())
                            .add(new Action.Builder(mRes, action)
                                    .title(R.string.off)
                                    .data(off)
                                    .checked(getWifiDevEnable() == false)
                                    .build());

                return layout;
            }
        };
    }

    StringGetter mWifiStateDescription = new StringGetter() {
        private boolean lastWifiState;
        @Override
        public String get() {
            lastWifiState = getWifiDevEnable();
            int resId = lastWifiState ? R.string.on : R.string.off;
            return mRes.getString(resId);
        }
        @Override
        public void refreshView() {
            if (getWifiDevEnable() != lastWifiState) {
                super.refreshView();
            }
        }
    };

    private boolean getWifiDevEnable() {
        boolean ret = false;
        int wifiState = getWifiState();
        if ((wifiState == WifiManager.WIFI_STATE_ENABLING) ||
            (wifiState == WifiManager.WIFI_STATE_ENABLED)) {
            ret = true;
        }
        return ret;
    }

    private int getWifiState() {
        if (mWifiManager == null) {
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        }
        return mWifiManager.getWifiState();
    }

    private void setWifiDevEnable(boolean enable) {
        int wifiState = getWifiState();
        if (enable && ((wifiState != WifiManager.WIFI_STATE_ENABLING) ||
                    (wifiState != WifiManager.WIFI_STATE_ENABLED))) {
            mWifiManager.setWifiEnabled(true);
        }
        else if(!enable && ((wifiState != WifiManager.WIFI_STATE_DISABLING) ||
                        (wifiState != WifiManager.WIFI_STATE_DISABLED))) {
            mWifiManager.setWifiEnabled(false);
        }
        else {
            Log.i(TAG,"[setWifiDevEnable] wifiState:" + wifiState);
        }
    }
    //end add

    @Override
    public Layout createLayout() {
        return
            new Layout()
                .breadcrumb(getString(R.string.header_category_device))
                .add(new Header.Builder(mRes)
                        .icon(R.drawable.ic_settings_wifi_4)
                        .title(R.string.connectivity_network)
                        .description(mWifiConnectedDescription)
                        .build()
                    .add(new Header.Builder(mRes).title(R.string.connectivity_wifi)
                            .contentIconRes(R.drawable.ic_settings_wifi_4)
                            .description(mWifiConnectedDescription).build()
                        .add(new Static.Builder(mRes)
                                .title(R.string.wifi_setting_available_networks)
                                .build())
                        .add(mWifiShortListLayout)
                        .add(new Header.Builder(mRes)
                                .title(R.string.wifi_setting_see_all)
                                .build()
                            .add(mWifiAllListLayout)
                        )
                        .add(new Header.Builder(mRes)
                            .title(R.string.title_wifi_device)
                            .description(mWifiStateDescription)
                            .build()
                            .add(getOnOffLayout(ACTION_WIFI_SWITCH)))
                        .add(new Static.Builder(mRes)
                                .title(R.string.wifi_setting_header_other_options)
                                .build())
                        .add(new Action.Builder(mRes,
                                 new Intent(this, WpsConnectionActivity.class))
                                .title(R.string.wifi_setting_other_options_wps)
                                .build())
                        .add(new Action.Builder(mRes,
                                new Intent(this, AddWifiNetworkActivity.class))
                                .title(R.string.wifi_setting_other_options_add_network)
                                .build())
                    )
                    .add(new Header.Builder(mRes)
                            .title(R.string.connectivity_ethernet)
                            .contentIconRes(R.drawable.ic_settings_ethernet)
                            .description(mEthernetConnectedDescription)
                            .build()
                        .add(mEthernetLayout)
                     )
                 );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_ADVANCED_OPTIONS && resultCode == RESULT_OK) {
            //TODO make sure view reflects model deltas
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onActionFocused(Layout.LayoutRow item) {
        int resId = item.getContentIconRes();
        if (resId != 0) {
            setIcon(resId);
        }
    }

    @Override
    public void onActionClicked(Action action) {
        switch (action.getId()) {
            case Action.ACTION_INTENT:
                startActivityForResult(action.getIntent(), REQUEST_CODE_ADVANCED_OPTIONS);
                break;
            case ACTION_WIFI_FORGET_NETWORK:
                mConnectivityListener.forgetWifiNetwork();
                goBackToTitle(mRes.getString(R.string.connectivity_wifi));
                mWifiShortListLayout.onWifiListInvalidated();
                mWifiAllListLayout.onWifiListInvalidated();
                break;
            case ACTION_WIFI_PROXY_SETTINGS: {
                int networkId = mConnectivityListener.getWifiNetworkId();
                if (networkId != -1) {
                    startActivityForResult(EditProxySettingsActivity.createIntent(this, networkId),
                            REQUEST_CODE_ADVANCED_OPTIONS);
                }
                break;
            }
            case ACTION_WIFI_IP_SETTINGS: {
                int networkId = mConnectivityListener.getWifiNetworkId();
                if (networkId != -1) {
                    startActivityForResult(EditIpSettingsActivity.createIntent(this, networkId),
                            REQUEST_CODE_ADVANCED_OPTIONS);
                }
                break;
            }
            case ACTION_ETHERNET_PROXY_SETTINGS: {
                int networkId = WifiConfiguration.INVALID_NETWORK_ID;
                startActivityForResult(EditProxySettingsActivity.createIntent(this, networkId),
                        REQUEST_CODE_ADVANCED_OPTIONS);
                break;
            }
            case ACTION_ETHERNET_IP_SETTINGS: {
                int networkId = WifiConfiguration.INVALID_NETWORK_ID;
                startActivityForResult(EditIpSettingsActivity.createIntent(this, networkId),
                        REQUEST_CODE_ADVANCED_OPTIONS);
                break;
            }
            case ACTION_WIFI_SWITCH: {
                if (getWifiDevEnable()) {
                    sendWifiOffMsg();
                }
                else {
                    sendWifiOnMsg();
                }
                goBackToTitle(mRes.getString(R.string.connectivity_wifi));
                break;
            }
        }
    }
}

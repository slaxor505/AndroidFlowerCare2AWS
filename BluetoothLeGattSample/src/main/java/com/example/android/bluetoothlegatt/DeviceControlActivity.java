/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity
{

    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";

    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final String DEV_ADDR = "DEV_ADDR" ;
    private static final int JOB_ID = 1001;

    private TextView mConnectionState;

    private TextView mDataField;

    private String mDeviceName;

    private String mDeviceAddress;

    private ExpandableListView mGattServicesList;

    private BluetoothLeService mBluetoothLeService;

    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    private boolean mConnected = false;

    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";

    private final String LIST_UUID = "UUID";

    private int POLL_INTERVAL;

    private PubSubActivity AWSpublisher;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection()
    {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service)
        {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();

            if (!mBluetoothLeService.initialize())
            {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            // Automatically connects to the device upon successful start-up initialization.
            //mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            Log.i(TAG, "BroadcastReceiver - onReceive. Action: "+action.toString());

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action))
            {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            }
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action))
            {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            }
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
            {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
                startMonitoring();
            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))
            {
                mBluetoothLeService.disconnect();
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                publishSensorData(intent.getByteArrayExtra(BluetoothLeService.SENSOR_DATA));

            }
            else if (BluetoothLeService.SENSOR_READ_READY.equals(action))
            {
                //*****then reading sensor data
                int groupPosition = -1;
                int childPosition = 0;

                int serviceCount = mGattCharacteristics.size();

                for(int i=0; i < serviceCount; i++)
                {
                    ArrayList<BluetoothGattCharacteristic> s = mGattCharacteristics.get(i);
                    for(int j=0; j < s.size(); j++)
                    {
                        BluetoothGattCharacteristic c = s.get(j);
                        UUID uuid = c.getUuid();
                        if (uuid.compareTo(UUID.fromString(SampleGattAttributes.CHARACTERISTIC_READ_SENSOR_DATA)) == 0)
                        {
                            Log.i(TAG, "Found characteristic CHARACTERISTIC_READ_SENSOR_DATA");
                            groupPosition = i;
                            childPosition = j;
                            break;
                        }
                    }
                }

                if (groupPosition == -1)
                    return;

                final BluetoothGattCharacteristic characteristic =
                        mGattCharacteristics.get(groupPosition).get(childPosition);
                //final int charaProp = characteristic.getProperties();

                mBluetoothLeService.readCharacteristic(characteristic);

                //******

            }

            else if (BluetoothLeJobService.READ_PUBLISH_COMPLETE.equals(action)){

                Log.i(TAG, "READ_PUBLISH_COMPLETE update received.");
                displayData(intent.getStringExtra(BluetoothLeJobService.UPDATE_TIMESTAMP));

            }
        }
    };

    private void startMonitoring()
    {
        if (mGattCharacteristics != null)
        {
            int groupPosition = -1;
            int childPosition = 0;

            int serviceCount = mGattCharacteristics.size();

            for(int i=0; i < serviceCount; i++)
            {
                ArrayList<BluetoothGattCharacteristic> s = mGattCharacteristics.get(i);
                for(int j=0; j < s.size(); j++)
                {
                    BluetoothGattCharacteristic c = s.get(j);
                    UUID uuid = c.getUuid();
                    if (uuid.compareTo(UUID.fromString(SampleGattAttributes.CHARACTERISTIC_WRITE_MODE_CHANGE)) == 0)
                    {
                        Log.i(TAG, "Found characteristic CHARACTERISTIC_WRITE_MODE_CHANGE");
                        groupPosition = i;
                        childPosition = j;
                        break;
                    }
                }
            }

            if (groupPosition == -1)
                return;

            final BluetoothGattCharacteristic characteristic =
                    mGattCharacteristics.get(groupPosition).get(childPosition);
            final int charaProp = characteristic.getProperties();


            byte[] CMD_DATA_MODE_CHANGE =  { (byte)0xa0, (byte)0x1f };

            byte[] myChar = characteristic.getValue();

            characteristic.setValue(CMD_DATA_MODE_CHANGE);
            mBluetoothLeService.writeCharacteristic(characteristic);

//            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0)
//            {
//                // If there is an active notification on a characteristic, clear
//                // it first so it doesn't update the data field on the user interface.
//                if (mNotifyCharacteristic != null)
//                {
//                    mBluetoothLeService.setCharacteristicNotification(
//                            mNotifyCharacteristic, false);
//                    mNotifyCharacteristic = null;
//                }
//                //mBluetoothLeService.readCharacteristic(characteristic);
//
//                Byte[] CMD_DATA_MODE_CHANGE = new Byte[] { (byte)0xa0, (byte)0x1f };
//
//                byte[] myChar = characteristic.getValue();
//                //characteristic.setValue((byte)0x11);
//                //mBluetoothLeService.writeCharacteristic(characteristic);
//
//            }

//            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0)
//            {
//                mNotifyCharacteristic = characteristic;
//                mBluetoothLeService.setCharacteristicNotification(
//                        characteristic, true);
//            }

        }

    }


    private void clearUI()
    {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        //mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);

        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);



        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        //Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        //bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        //create AWS IOT handler
        AWSpublisher = new PubSubActivity(getApplicationContext());

        POLL_INTERVAL = DeviceScanActivity.GetPollInterval();
        Log.i(TAG, "Poll Interval is "+POLL_INTERVAL+ " sec");

    }

    @Override
    protected void onResume()
    {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
//        if (mBluetoothLeService != null)
//        {
//            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
//            Log.d(TAG, "Connect request result=" + result);
//        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        //unbindService(mServiceConnection);
        mBluetoothLeService = null;

        Log.i(TAG, "on Destroy. Cancel service. "+JOB_ID);

        JobScheduler jobScheduler = (JobScheduler) getSystemService(
                Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(JOB_ID);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected)
        {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        }
        else
        {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.menu_connect:
                startPolling();
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case R.id.menu_monitor:
                startMonitoring();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startPolling() {

        Log.i(TAG, "Starting polling service.");

        JobScheduler jobScheduler = (JobScheduler) getSystemService(
                Context.JOB_SCHEDULER_SERVICE);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(DEV_ADDR, mDeviceAddress);
        JobInfo jobInfo = new JobInfo.Builder(JOB_ID,
                new ComponentName(this, BluetoothLeJobService.class))
                .setPeriodic(POLL_INTERVAL*1000)
                .setExtras(bundle)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        jobScheduler.schedule(jobInfo);

        //mBluetoothLeService.connect();

    }

    private void updateConnectionState(final int resourceId)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data)
    {
        if (data != null)
        {
            mDataField.setText(data);
        }
    }

    private void publishSensorData(byte[] data) {
        if (SensorData.validate(data)) {
            Log.i(TAG, "Sensor data is valid.");
            SensorData mSensorData = new SensorData(data, mDeviceName, mDeviceAddress);

            //PubSubActivity AWSpublisher = new PubSubActivity(getApplicationContext());
            AWSpublisher.connect(mSensorData.jsonData);
            //AWSpublisher.publish();
            AWSpublisher.disconnect();

        }

        else {
            Log.i(TAG, "Sensor data is invalid. Not publishing.");
        }


    }




    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices)
    {
        if (gattServices == null)
            return;

        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);

        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();

        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices)
        {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();

            uuid = gattService.getUuid().toString();

            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();

            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics)
            {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();

                uuid = gattCharacteristic.getUuid().toString();

                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }



    }

    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeJobService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeJobService.ACTION_GATT_DISCONNECTED);
        //intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        //intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        //intentFilter.addAction(BluetoothLeService.SENSOR_READ_READY);
        intentFilter.addAction(BluetoothLeJobService.READ_PUBLISH_COMPLETE);
        return intentFilter;
    }
}

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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service
{

    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;

    private BluetoothAdapter mBluetoothAdapter;

    private String mBluetoothDeviceAddress;

    private BluetoothGatt mBluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;

    private static final int STATE_CONNECTING = 1;

    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";

    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";

    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";

    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";

    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static String SENSOR_DATA =
            "com.example.bluetooth.le.SENSOR_DATA";

    public final static String SENSOR_READ_READY =
            "com.example.bluetooth.le.SENSOR_READ_READY";


    public final static UUID UUID_SAMPLE_CHARACTERISTIC =
            UUID.fromString(SampleGattAttributes.SERVICE_GUID);

    //SLaX
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            Log.i(TAG, "onConnectionStateChange");
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                Log.i(TAG, "Connected to GATT server.");
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                //broadcastUpdate(intentAction);
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                mBluetoothGatt.discoverServices());

            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                Log.i(TAG, "Disconnected from GATT server.");
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            Log.i(TAG, "onServicesDiscovered");
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                //broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

                extractGattServices(getSupportedGattServices());
                startMonitoring();


            }
            else
            {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status)
        {
            Log.i(TAG, "onCharacteristicRead. Value: "+SampleGattAttributes.toHexString(characteristic.getValue()));
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                //broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

                Log.i(TAG, "onCharacteristicRead. Disconnecting from BLE device");

                disconnect();
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));

                final byte[] data = characteristic.getValue();

//                if (data != null && data.length > 0)
//                {
//                    final StringBuilder stringBuilder = new StringBuilder(data.length);
//                    for (byte byteChar : data)
//                        stringBuilder.append(String.format("%02X ", byteChar));
//                }

                Log.i(TAG, "onCharacteristicRead. Publishing data to AWS IOT");
                publishSensorData(data);

                Log.i(TAG, "onCharacteristicRead. READ/PUBLISH completed");

            }
        }

        private void publishSensorData(byte[] data) {
            if (SensorData.validate(data)) {
                Log.i(TAG, "Sensor data is valid.");
                SensorData mSensorData = new SensorData(data, "Flower care", mBluetoothDeviceAddress);

                PubSubActivity AWSpublisher = new PubSubActivity(getApplicationContext());
                AWSpublisher.connect(mSensorData.jsonData);
                //AWSpublisher.publish();
                AWSpublisher.disconnect();

            }

            else {
                Log.i(TAG, "Sensor data is invalid. Not publishing.");
            }


        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {
            Log.i(TAG, "onCharacteristicChanged. Value: "+SampleGattAttributes.toHexString(characteristic.getValue()));
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        public void onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status)
        {
            Log.i(TAG, "onCharacteristicWrite. Value: "+SampleGattAttributes.toHexString(characteristic.getValue()));
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                if ((characteristic.getUuid().toString().equals(SampleGattAttributes.CHARACTERISTIC_WRITE_MODE_CHANGE)) && (Arrays.equals(characteristic.getValue(), SampleGattAttributes.CMD_DATA_MODE_CHANGE)))
                {
                    //broadcastUpdate(SENSOR_READ_READY, characteristic);

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

                    final BluetoothGattCharacteristic newCharacteristic =
                            mGattCharacteristics.get(groupPosition).get(childPosition);
                    //final int charaProp = characteristic.getProperties();

                    readCharacteristic(newCharacteristic);

                    //******


                }
                else {
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            }
        }

    };

    private void broadcastUpdate(final String action)
    {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }


    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic)
    {
        final Intent intent = new Intent(action);

        Log.i(TAG, "broadcastUpdate " + action);

        final byte[] data = characteristic.getValue();

        if (data != null && data.length > 0)
        {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            intent.putExtra(SENSOR_DATA,data);
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder
    {
        BluetoothLeService getService()
        {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();

        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize()
    {
        Log.i(TAG, "initialize");

        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null)
        {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null)
            {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null)
        {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address)
    {
        Log.i(TAG, "connect: " + address);

        if (mBluetoothAdapter == null || address == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null)
        {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect())
            {
                mConnectionState = STATE_CONNECTING;
                return true;
            }
            else
            {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null)
        {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect()
    {
        Log.i(TAG, "disconnect");

        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close()
    {
        Log.i(TAG, "close");

        if (mBluetoothGatt == null)
        {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        final Boolean opRes = mBluetoothGatt.readCharacteristic(characteristic);
        Log.i(TAG, "Characteristic read triggered. Result: "+opRes);
    }

    //****SLAX
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        final Boolean opRes = mBluetoothGatt.writeCharacteristic(characteristic);
        Log.i(TAG, "Characteristic write triggered. Result: "+opRes);
    }


    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(SampleGattAttributes.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices()
    {
        if (mBluetoothGatt == null)
            return null;

        return mBluetoothGatt.getServices();
    }


    protected static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    ///*****SLaX****
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
            writeCharacteristic(characteristic);

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

    // !!!Original description!!!SLaX Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void extractGattServices(List<BluetoothGattService> gattServices)
    {
        final String LIST_NAME = "NAME";
        final String LIST_UUID = "UUID";

        if (gattServices == null)
            return;

        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);

        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData;
        gattCharacteristicData = new ArrayList<ArrayList<HashMap<String, String>>>();

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

}

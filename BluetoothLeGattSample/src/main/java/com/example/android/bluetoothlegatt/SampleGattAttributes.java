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

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes
{

    private static HashMap<String, String> attributes = new HashMap();

    public static String SERVICE_GUID = "0000fff0-0000-1000-8000-00805f9b34fb";
    //public static String CHARACTERISTIC_GUID = "0000fff4-0000-1000-8000-00805f9b34fb";
    public static String CHARACTERISTIC_GUID = "0000fff1-0000-1000-8000-00805f9b34fb";

    public static String CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb";

    public static String CHARACTERISTIC_WRITE_GUID = "0000fff4-0000-1000-8000-00805f9b34fb";

    //miflora
//    _HANDLE_READ_VERSION_BATTERY = 0x38 | handle = 0x0038, uuid = 00001a02-0000-1000-8000-00805f9b34fb
//    _HANDLE_READ_NAME = 0x03 | handle = 0x0003, uuid = 00002a00-0000-1000-8000-00805f9b34fb
//    _HANDLE_READ_SENSOR_DATA = 0x35 | handle = 0x0035, uuid = 00001a01-0000-1000-8000-00805f9b34fb
//    _HANDLE_WRITE_MODE_CHANGE = 0x33 | handle = 0x0033, uuid = 00001a00-0000-1000-8000-00805f9b34fb
//    _DATA_MODE_CHANGE = bytes([0xA0, 0x1F])

    public static String CHARACTERISTIC_WRITE_MODE_CHANGE = "00001a00-0000-1000-8000-00805f9b34fb";
    //public static String CHARACTERISTIC_WRITE_MODE_CHANGE = "0000fff3-0000-1000-8000-00805f9b34fb";

    public static String CHARACTERISTIC_READ_SENSOR_DATA = "00001a01-0000-1000-8000-00805f9b34fb";
    //public static String CHARACTERISTIC_READ_SENSOR_DATA = "0000fff1-0000-1000-8000-00805f9b34fb";

    public static byte[] CMD_DATA_MODE_CHANGE = { (byte)0xa0, (byte)0x1f };

    static
    {
        // Sample Services.
        attributes.put("0000fff0-0000-1000-8000-00805f9b34fb", "Test Service");
    }

    public static String lookup(String uuid, String defaultName)
    {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }

    public static String toHexString(byte[] bytes) {
        char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v/16];
            hexChars[j*2 + 1] = hexArray[v%16];
        }
        return new String(hexChars);
    }
}


package com.example.android.bluetoothlegatt;

import android.app.LauncherActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class SensorData {

    byte[] rawData;
    String jsonData;
    String sensorName;
    String sensorAddress;


    public SensorData(byte[] rawData, String sensorName, String sensorAddress) {
        this.rawData = rawData;
        this.sensorName = sensorName;
        this.sensorAddress = sensorAddress;
        //parse data and generate JSON message
        //this.jsonData = "json:valuetest";
        parse();
    }


//        def _check_data(self):
//        """Ensure that the data in the cache is valid.
//
//        If it's invalid, the cache is wiped.
//        """
//        if not self.cache_available():
//        return
//        if self._cache[7] > 100:  # moisture over 100 procent
//        self.clear_cache()
//        return
//        if self._firmware_version >= "2.6.6":
//        if sum(self._cache[10:]) == 0:
//        self.clear_cache()
//        return
//        if sum(self._cache) == 0:
//        self.clear_cache()
//        return

    public static boolean validate (byte[] data) {
        if (data[7] > 100) {
            return false;
        }
        int sum = 0;
        //for FW >= '2.6.6'
        for (int i = 10; i < data.length; i++) {
            sum += data[i];
        }
//        //for all others
//        sum=0;
//        for (int i:this.rawData) {
//            sum+=rawData[i];
//        }

        if (sum==0) {return false;}

        return true;

    }

        //        def _parse_data(self):
        //        """Parses the byte array returned by the sensor.
        //
        //        The sensor returns 16 bytes in total. It's unclear what the meaning of these bytes
        //        is beyond what is decoded in this method.
        //
        //                semantics of the data (in little endian encoding):
        //        bytes 0-1: temperature in 0.1 °C
        //        byte 2: unknown
        //        bytes 3-4: brightness in Lux
        //        bytes 5-6: unknown
        //        byte 7: conductivity in µS/cm
        //        byte 8-9: brightness in Lux
        //        bytes 10-15: unknown
        //        """
        //        data = self._cache
        //        res = dict()
        //        temp, res[MI_LIGHT], res[MI_MOISTURE], res[MI_CONDUCTIVITY] = \
        //        unpack('<hxIBhxxxxxx', data)
        //        res[MI_TEMPERATURE] = temp/10.0
        //        return res
//    MI_TEMPERATURE = "temperature"
//    MI_LIGHT = "light"
//    MI_MOISTURE = "moisture"
//    MI_CONDUCTIVITY = "conductivity"
//    MI_BATTERY = "battery"


    public void parse () {

        JSONObject jObjectData = new JSONObject();

        float temperature = ((this.rawData[0] & 0xFF) + 0x100 * (this.rawData[1]& 0xFF))/10;
        int bright = (this.rawData[3] & 0xFF) + 0x100 * (this.rawData[4]& 0xFF);
        int moisture= this.rawData[7] & 0xFF;
        int conductivity = (this.rawData[8] & 0xFF) + 0x100 * (this.rawData[9]& 0xFF);

        try {
            jObjectData.put("temperature", temperature);
            jObjectData.put("brightness", bright);
            jObjectData.put("conductivity", conductivity);
            jObjectData.put("moisture", moisture);
            jObjectData.put("SerialNumber", this.sensorName.toLowerCase()+"-"+this.sensorAddress.toUpperCase());


            jsonData = jObjectData.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }


    }

}
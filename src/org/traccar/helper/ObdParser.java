package org.traccar.helper;

import org.traccar.model.ExtendedInfoFormatter;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by pankaj on 23/8/16.
 *
 */
public class ObdParser {
    static Map<String, Integer> pidByteLengthMap = new HashMap<>();
    static Map<String, String> pidNameMap = new HashMap<>();

    static {
        pidByteLengthMap.put("00", 5);
        pidByteLengthMap.put("05", 1);
        pidByteLengthMap.put("07", 1);
        pidByteLengthMap.put("0b", 1);
        pidByteLengthMap.put("0c", 2);
        pidByteLengthMap.put("0d", 1);
        pidByteLengthMap.put("0e", 1);
        pidByteLengthMap.put("0f", 1);
        pidByteLengthMap.put("11", 1);
        pidByteLengthMap.put("42", 2);

        pidNameMap.put("00", "PID_SUPPORT_1");
        pidNameMap.put("05", "COOLANT_TEMP");
        pidNameMap.put("07", "LONG_FUEL_TRIM_1");
        pidNameMap.put("0b", "INTAKE_ABS_PRESS");
        pidNameMap.put("0c", "RPM");
        pidNameMap.put("0d", "SPEED");
        pidNameMap.put("0e", "TIMING_ADV");
        pidNameMap.put("0f", "INTAKE_AIR_TEMP");
        pidNameMap.put("11", "THROTTLE");
        pidNameMap.put("42", "VOLTAGE");
    }

    public static void parseData (String obdData, ExtendedInfoFormatter extendedInfo){
        int cursor = 0;
        while(cursor<=obdData.length()-1){
            String pid = obdData.substring(cursor, cursor+2);
            Integer dataLength = pidByteLengthMap.get(pid);
            if(dataLength==null){
                Log.warning("Unable to recognize OBD PID - " + pid +" In "+obdData);
                return;
            }
            String data = obdData.substring(cursor+2, cursor+2+dataLength);
            extendedInfo.set(pidNameMap.get(pid), decodeData(pid, data));
            cursor=cursor + 2 + dataLength;
        }
    }

    private static String decodeData(String pid, String data){
        if (pid.equalsIgnoreCase("00"))
            return data;

        int dataInt = Integer.parseInt(data, 16);
        int firstByte, secondByte;
        switch (pid){
            case "05":
                return String.valueOf(dataInt-40);
            case "07":
                return String.valueOf((dataInt/1.28)-100);
            case "0b":
                return String.valueOf(dataInt);
            case "0c":
                firstByte = Integer.parseInt(data.substring(0,2),16);
                secondByte = Integer.parseInt(data.substring(2),16);
                return String.valueOf(((256*firstByte)+secondByte)/(float)4);
            case "0d":
                return String.valueOf(dataInt);
            case "0e":
                return String.valueOf(((float)dataInt/2)-64);
            case "0f":
                return String.valueOf(dataInt-40);
            case "11":
                return String.valueOf(((float)100/255)*dataInt);
            case "42":
                firstByte = Integer.parseInt(data.substring(0,2),16);
                secondByte = Integer.parseInt(data.substring(2),16);
                return String.valueOf((256*firstByte+secondByte)/(float)1000);
        }
        return data;
    }
}

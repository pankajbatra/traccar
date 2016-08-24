/*
 * Copyright 2012 - 2014 Anton Tananaev (anton.tananaev@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import java.util.Properties;
import java.util.regex.Pattern;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;

import org.traccar.BaseProtocolDecoder;
import org.traccar.database.DataManager;
import org.traccar.helper.*;
import org.traccar.model.ExtendedInfoFormatter;
import org.traccar.model.Position;

public class Tk103ProtocolDecoder extends BaseProtocolDecoder {

    public Tk103ProtocolDecoder(DataManager dataManager, String protocol, Properties properties) {
        super(dataManager, protocol, properties);
    }

    private static final Pattern PATTERN = new PatternBuilder()
            .number("(d+)(,)?")                  // device id
            .expression(".{4},?")                // command
            .number("d*")                        // imei?
            .number("(dd)(dd)(dd),?")            // date
            .expression("([AV]),?")              // validity
            .number("(dd)(dd.d+)")               // latitude
            .expression("([NS]),?")
            .number("(ddd)(dd.d+)")              // longitude
            .expression("([EW]),?")
            .number("(d+.d)(?:d*,)?")            // speed
            .number("(dd)(dd)(dd),?")            // time
            .number("(d+.?d{1,2}),?")            // course
            .number("(?:([01]{8})|(x{8}))?,?")   // state
            .number("(?:L(x+))?")                // odometer
            .any()
            .text(")").optional()
            .compile();

    private static final Pattern PATTERN_BATTERY = new PatternBuilder()
            .number("(d+),")                     // device id
            .text("ZC20,")
            .number("(dd)(dd)(dd),")             // date (ddmmyy)
            .number("(dd)(dd)(dd),")             // time
            .number("d+,")                       // battery level
            .number("(d+),")                     // battery voltage
            .number("(d+),")                     // power voltage
            .number("d+")                        // installed
            .compile();

    private static final Pattern PATTERN_NETWORK = new PatternBuilder()
            .number("(d{12})")                   // device id
            .text("BZ00,")
            .number("(d+),")                     // mcc
            .number("(d+),")                     // mnc
            .number("(x+),")                     // lac
            .number("(x+),")                     // cid
            .any()
            .compile();

    private static final Pattern PATTERN_ALARM = new PatternBuilder()
            .number("(d+)(,)?")                  // device id
            .text("BQ81,ALARM,")                 // command
            .number("(d+),")                     // type
            .number("(d+),")                     // value
            .number("(dd)(dd)(dd)?")            // date
            .expression("([AV])?")              // validity
            .number("(dd)(dd.d+)")               // latitude
            .expression("([NS])?")
            .number("(ddd)(dd.d+)")              // longitude
            .expression("([EW])?")

            .number("(d+.d)(?:d*,)?")            // speed
            .number("(dd)(dd)(dd),?")            // time
            .number("(d+.?d{1,2}),?")            // course
            .number("(?:([01]{8})|(x{8}))?,?")   // state
            .any()
            .compile();
    private static final Pattern PATTERN_OBD = new PatternBuilder()
            .number("(d+)(,)?")                  // device id
            .text("BQ86,")                       // command
            .number("(d+),")                     // manufacturer
            .number("(x+),")                     // values
            .number("(dd)(dd)(dd)?")            // date
            .number("(dd)(dd)(dd),?")            // time
            .number("(?:([01]{8})|(x{8}))?,?")   // state
            .any()
            .compile();

    public static void main(String[] args) {
        String sentence = "027028257228BQ86,0,05800b160c171e0d150f5b11320e97077a000100050000423365,160822042330,01000001"; //IO state 8
        Parser parser = new Parser(PATTERN_OBD, sentence);
        System.out.println(parser.matches());
        System.out.println("IMEI:"+parser.next());
        parser.next();
        System.out.println("Manufacturer:" + parser.next());
        String obdData = parser.next();
        System.out.println("Values:"+ obdData);
        DateBuilder dateBuilder = new DateBuilder();
        dateBuilder.setDate(parser.nextInt(), parser.nextInt(), parser.nextInt());
        dateBuilder.setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
        System.out.println("Date:" + dateBuilder.getDate());
        String status = parser.next();
        System.out.println("Status: "+status);
        if (status != null) {
            int value = Integer.parseInt(new StringBuilder(status).reverse().toString(), 2);
            System.out.println("Charge: " + !BitUtil.check(value, 0));
            System.out.println("ignition: " + BitUtil.check(value, 1));
        }
        if(parser.hasNext()) {
            System.out.println(parser.next());
        }
        ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter("Tk03");
        ObdParser.parseData(obdData, extendedInfo);
        System.out.println(extendedInfo.toString());
    }

    private boolean setImei(Parser parser, Position position){
        String imei = parser.next();
        try {
            position.setDeviceId(getDataManager().getDeviceByImei(imei).getId());
        } catch(Exception error) {
            // Compatibility mode (remove in future)
            try {
                position.setDeviceId(getDataManager().getDeviceByImei("000" + imei).getId());
            } catch(Exception error2) {
                Log.warning("Unknown device - " + imei);
                return false;
            }
        }
        return true;
    }

    private void processLocationMessage(Parser parser, Position position, DateBuilder dateBuilder,
                                        ExtendedInfoFormatter extendedInfo, boolean isAlarm){
        position.setValid(parser.next().equals("A"));
        position.setLatitude(parser.nextCoordinate());
        position.setLongitude(parser.nextCoordinate());

        // Speed
        position.setSpeed(parser.nextDouble() * 0.539957);

        // Time
        dateBuilder.setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
        position.setTime(dateBuilder.getDate());

        // Course
        position.setCourse(parser.nextDouble());

        // State
        String status = parser.next();
        if (status != null) {
            extendedInfo.set("state", status); // binary status
            int value = Integer.parseInt(new StringBuilder(status).reverse().toString(), 2);
            extendedInfo.set("charge", !BitUtil.check(value, 0));
            extendedInfo.set("ignition", BitUtil.check(value, 1));
        }

        if(!isAlarm) {
            if (parser.hasNext())
                extendedInfo.set("state", parser.next()); // hex status

            // Mileage
            if (parser.hasNext())
                extendedInfo.set("mileage", parser.nextLong(16));
        }

        position.setExtendedInfo(extendedInfo.toString());

    }


    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {

        String sentence = (String) msg;

        // Find message start
        int beginIndex = sentence.indexOf('(');
        if (beginIndex != -1) {
            sentence = sentence.substring(beginIndex + 1);
        }

        String id = sentence.substring(0, 12);
        String type = sentence.substring(12, 16);
        // Send response
        if (channel != null) {
            if (type.equals("BP00")) {
                String content = sentence.substring(sentence.length() - 3);
                channel.write("(" + id + "AP01" + content + ")");
            } else if (type.equals("BP05")) {
                channel.write("(" + id + "AP05)");
            }
//            else if (type.equals("BQ86")) {
//                channel.write("(" + id + "AQ80,OBDS,0,2,1,30,0)");
//            }
        }

        Position position = new Position();
        ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter(getProtocol());

        Parser parser = new Parser(PATTERN_BATTERY, sentence);
        if (parser.matches()) {
            // Get device by IMEI
            if(!setImei(parser, position))
                return null;

            DateBuilder dateBuilder = new DateBuilder()
                    .setDateReverse(parser.nextInt(), parser.nextInt(), parser.nextInt())
                    .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());

            getLastLocation(position, dateBuilder.getDate());

            int battery = parser.nextInt();
            if (battery != 65535) {
                extendedInfo.set("battery", battery);
            }

            int power = parser.nextInt();
            if (power != 65535) {
                extendedInfo.set("power", battery);
            }

            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        parser = new Parser(PATTERN_NETWORK, sentence);
        if (parser.matches()) {
            if(!setImei(parser, position))
                return null;

            getLastLocation(position, null);

            extendedInfo.set("mcc", parser.nextInt());
            extendedInfo.set("mnc", parser.nextInt());
            extendedInfo.set("lac", parser.nextInt(16));
            extendedInfo.set("cid", parser.nextInt(16));

            return position;
        }

        parser = new Parser(PATTERN_ALARM, sentence);
        if(parser.matches()){
            if(!setImei(parser, position))
                return null;
            String alarm = parser.next();
            int alarmType = parser.nextInt();
            String alarmTypeString = null;
            switch (alarmType){
                case 0:
                    alarmTypeString = "LOW_BATTERY";
                    break;
                case 1:
                    alarmTypeString = "OVER_SPEED";
                    break;
                case 2:
                    alarmTypeString = "IDLING";
                    break;
                case 3:
                    alarmTypeString = "FAST_ACC";
                    break;
                case 4:
                    alarmTypeString = "SHARP_SLOWDOWN";
                    break;
                case 5:
                    alarmTypeString = "HIGH_TEMP";
                    break;
                default:
                    alarmTypeString = String.valueOf(alarmType);
            }
            extendedInfo.set("alarmType", alarmTypeString);
            extendedInfo.set("alarmValue", parser.nextLong());
            DateBuilder dateBuilder = new DateBuilder();
            dateBuilder.setDate(parser.nextInt(), parser.nextInt(), parser.nextInt());
            processLocationMessage(parser, position, dateBuilder, extendedInfo, true);
            return position;
        }

        parser = new Parser(PATTERN_OBD, sentence);
        if(parser.matches()){
            if(!setImei(parser, position))
                return null;
            String command = parser.next();

            extendedInfo.set("manufacturer", parser.next());
            String obdString = parser.next();
            extendedInfo.set("obdValueString", obdString);
            ObdParser.parseData(obdString, extendedInfo);
            DateBuilder dateBuilder = new DateBuilder();
            dateBuilder.setDate(parser.nextInt(), parser.nextInt(), parser.nextInt());
            dateBuilder.setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
            position.setTime(dateBuilder.getDate());
            getLastLocation(position, dateBuilder.getDate());
            String status = parser.next();
            if (status != null) {
                extendedInfo.set("state", status); // binary status
                int value = Integer.parseInt(new StringBuilder(status).reverse().toString(), 2);
                extendedInfo.set("charge", !BitUtil.check(value, 0));
                extendedInfo.set("ignition", BitUtil.check(value, 1));
            }
            position.setExtendedInfo(extendedInfo.toString());
            return position;
        }

        // Parse message
        parser = new Parser(PATTERN, sentence);
        if (!parser.matches()) {
            if(type.equals("BP00") || type.equals("BP05") || type.equals("BQ85") || type.equals("BR00") || type.equals("BR01"))
                return null;
            else {
                Log.warning("Unable to recognize data - " + sentence);
                return null;
            }
        }

        if(!setImei(parser, position))
            return null;

        int alarm = sentence.indexOf("BO01");
        if (alarm != -1) {
            int alarmType = Integer.parseInt(sentence.substring(alarm + 4, alarm + 5));
            String alarmTypeString = null;
            switch (alarmType){
                case 0:
                    alarmTypeString = "POWER_OFF";
                    break;
                case 1:
                    alarmTypeString = "ACCIDENT";
                    break;
                case 2:
                    alarmTypeString = "SOS";
                    break;
                case 3:
                    alarmTypeString = "ANTI_THEFT";
                    break;
                case 4:
                    alarmTypeString = "LOW_SPEED";
                    break;
                case 5:
                    alarmTypeString = "OVER_SPEED";
                    break;
                case 6:
                    alarmTypeString = "GEOFENCE";
                    break;
                case 7:
                    alarmTypeString = "VIBRATION";
                    break;
                case 8:
                    alarmTypeString = "LOW_POWER";
                    break;

            }
            extendedInfo.set("alarm", alarmTypeString);
        }

        // Date
        DateBuilder dateBuilder = new DateBuilder();
        if (parser.next() == null) {
            dateBuilder.setDate(parser.nextInt(), parser.nextInt(), parser.nextInt());
        } else {
            dateBuilder.setDateReverse(parser.nextInt(), parser.nextInt(), parser.nextInt());
        }

        processLocationMessage(parser, position, dateBuilder, extendedInfo, false);
        return position;
    }

}

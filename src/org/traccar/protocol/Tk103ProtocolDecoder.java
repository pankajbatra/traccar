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

    public static void main(String[] args) {
        String sentence = "027028257228BQ81,ALARM,3,18," +
                "160822" + //date 6
                "A" + //validity 1
                "2825.4034N" + //latitude 9+1
                "07702.3111E" + //longitude 10+1
                "043.7" +//speed 5
                "042326" + //time 6
                "346.24," + // course 6
                "01000001"; //IO state 8
                // 0 - ON power, 1 - off power
                // 0 - ACC close, 1 - ACC open
                // 0 - Did not start, 1 - just turning, 2- reverse turning
                // 0 - Did not start, 1 - empty, 2- heavy
                // 0 - Did not start, 1 - front door open, 2- front door close
        Parser parser = new Parser(PATTERN_ALARM, sentence);
        System.out.println(parser.matches());

        System.out.println("IMEI:"+ parser.next());
        System.out.println("Command:"+ parser.next());
        System.out.println("Type:"+ parser.next());
        System.out.println("Value:"+ parser.next());
        DateBuilder dateBuilder = new DateBuilder();
        dateBuilder.setDate(parser.nextInt(), parser.nextInt(), parser.nextInt());
        System.out.println("Date:" + dateBuilder.getDate());
        System.out.println("Validity:" + parser.next().equals("A"));
        System.out.println("Lat:"+ parser.nextCoordinate());
        System.out.println("Long:"+ parser.nextCoordinate());
        System.out.println("Speed:"+ parser.nextDouble() * 0.539957);
        dateBuilder.setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());
        System.out.println("Date:" + dateBuilder.getDate());
        System.out.println("Course:" + parser.nextDouble());
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
        
        // Send response
        if (channel != null) {
            String id = sentence.substring(0, 12);
            String type = sentence.substring(12, 16);
            if (type.equals("BP00")) {
                String content = sentence.substring(sentence.length() - 3);
                channel.write("(" + id + "AP01" + content + ")");
                return null;
            } else if (type.equals("BP05")) {
                channel.write("(" + id + "AP05)");
            }
        }

        Position position = new Position();
        ExtendedInfoFormatter extendedInfo = new ExtendedInfoFormatter(getProtocol());

        Parser parser = new Parser(PATTERN_BATTERY, sentence);
        if (parser.matches()) {
            // Get device by IMEI
            String imei = parser.next();
            try {
                position.setDeviceId(getDataManager().getDeviceByImei(imei).getId());
            } catch(Exception error) {
                // Compatibility mode (remove in future)
                try {
                    position.setDeviceId(getDataManager().getDeviceByImei("000" + imei).getId());
                } catch(Exception error2) {
                    Log.warning("Unknown device - " + imei);
                    return null;
                }
            }

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
            // Get device by IMEI
            String imei = parser.next();
            try {
                position.setDeviceId(getDataManager().getDeviceByImei(imei).getId());
            } catch(Exception error) {
                // Compatibility mode (remove in future)
                try {
                    position.setDeviceId(getDataManager().getDeviceByImei("000" + imei).getId());
                } catch(Exception error2) {
                    Log.warning("Unknown device - " + imei);
                    return null;
                }
            }
            getLastLocation(position, null);

            extendedInfo.set("mcc", parser.nextInt());
            extendedInfo.set("mnc", parser.nextInt());
            extendedInfo.set("lac", parser.nextInt(16));
            extendedInfo.set("cid", parser.nextInt(16));

            return position;
        }

        // Parse message
        parser = new Parser(PATTERN, sentence);
        if (!parser.matches()) {
            Log.warning("Unable to recognize data - " + sentence);
            return null;
        }

        // Get device by IMEI
        String imei = parser.next();
        try {
            position.setDeviceId(getDataManager().getDeviceByImei(imei).getId());
        } catch(Exception error) {
            // Compatibility mode (remove in future)
            try {
                position.setDeviceId(getDataManager().getDeviceByImei("000" + imei).getId());
            } catch(Exception error2) {
                Log.warning("Unknown device - " + imei);
                return null;
            }
        }

        int alarm = sentence.indexOf("BO01");
        if (alarm != -1) {
            extendedInfo.set("alarm", Integer.parseInt(sentence.substring(alarm + 4, alarm + 5)));
        }

        // Date
        DateBuilder dateBuilder = new DateBuilder();
        if (parser.next() == null) {
            dateBuilder.setDate(parser.nextInt(), parser.nextInt(), parser.nextInt());
        } else {
            dateBuilder.setDateReverse(parser.nextInt(), parser.nextInt(), parser.nextInt());
        }

        // Validity
        position.setValid(parser.next().equals("A"));
        position.setLatitude(parser.nextCoordinate());
        position.setLongitude(parser.nextCoordinate());

        // Altitude
//        position.setAltitude(0.0);

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
        extendedInfo.set("state", parser.next()); // hex status

        // Milage
        if (parser.hasNext()) {
            extendedInfo.set("milage", parser.nextLong(16));
        }

        position.setExtendedInfo(extendedInfo.toString());
        return position;
    }

}

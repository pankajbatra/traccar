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
package org.traccar.database;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.android.gcm.server.Sender;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.sql.DataSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.traccar.helper.DriverDelegate;
import org.traccar.helper.Log;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.xml.sax.InputSource;

/**
 * Database abstraction class
 */
public class DataManager {

    public DataManager(Properties properties) throws Exception {
        if (properties != null) {
            initDatabase(properties);
            initGcm(properties);
            
            // Refresh delay
            String refreshDelay = properties.getProperty("database.refreshDelay");
            if (refreshDelay != null) {
                devicesRefreshDelay = Long.valueOf(refreshDelay) * 1000;
            } else {
                devicesRefreshDelay = DEFAULT_REFRESH_DELAY * 1000;
            }
        }
    }
    
    private DataSource dataSource;
    
    public DataSource getDataSource() {
        return dataSource;
    }

    private Sender gcmSender;

    /**
     * Database statements
     */
    private NamedParameterStatement queryGetDevices;
    private NamedParameterStatement queryAddPosition;
    private NamedParameterStatement queryUpdateLatestPosition;
    private NamedParameterStatement queryGetGcmIds;
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("d/MM/yyyy h:mm:ssa");

    /**
     * Initialize database
     */
    private void initDatabase(Properties properties) throws Exception {

        // Load driver
        String driver = properties.getProperty("database.driver");
        if (driver != null) {
            String driverFile = properties.getProperty("database.driverFile");

            if (driverFile != null) {
                URL url = new URL("jar:file:" + new File(driverFile).getAbsolutePath() + "!/");
                URLClassLoader cl = new URLClassLoader(new URL[]{url});
                Driver d = (Driver) Class.forName(driver, true, cl).newInstance();
                DriverManager.registerDriver(new DriverDelegate(d));
            } else {
                Class.forName(driver);
            }
        }
        
        // Initialize data source
        ComboPooledDataSource ds = new ComboPooledDataSource();
        ds.setDriverClass(properties.getProperty("database.driver"));
        ds.setJdbcUrl(properties.getProperty("database.url"));
        ds.setUser(properties.getProperty("database.user"));
        ds.setPassword(properties.getProperty("database.password"));
        ds.setIdleConnectionTestPeriod(600);
        ds.setTestConnectionOnCheckin(true);
        dataSource = ds;

        // Load statements from configuration
        String query;

        query = properties.getProperty("database.selectDevice");
        if (query != null) {
            queryGetDevices = new NamedParameterStatement(query, dataSource);
        }

        query = properties.getProperty("database.insertPosition");
        if (query != null) {
            queryAddPosition = new NamedParameterStatement(query, dataSource, Statement.RETURN_GENERATED_KEYS);
        }

        query = properties.getProperty("database.updateLatestPosition");
        if (query != null) {
            queryUpdateLatestPosition = new NamedParameterStatement(query, dataSource);
        }
    }

    private void initGcm(Properties properties) throws Exception {
        String enableGcm = properties.getProperty("gcm.enable");
        String gcmApiKey = properties.getProperty("gcm.apiKey");
        String query = properties.getProperty("database.getGcmIds");
        if (enableGcm != null && Boolean.valueOf(enableGcm) && gcmApiKey!=null && gcmApiKey.length()>10 && query != null) {
            gcmSender = new Sender(gcmApiKey);
            Log.info("Created GCM Sender");
            queryGetGcmIds = new NamedParameterStatement(query, dataSource);
            Log.info("GCM Id query: "+queryGetGcmIds);
        }
    }

    private final NamedParameterStatement.ResultSetProcessor<Device> deviceResultSetProcessor = new NamedParameterStatement.ResultSetProcessor<Device>() {
        @Override
        public Device processNextRow(ResultSet rs) throws SQLException {
            Device device = new Device();
            device.setId(rs.getLong("id"));
            device.setImei(rs.getString("imei"));
            ResultSetMetaData metaData = rs.getMetaData();
            if(metaData.getColumnCount()>2 && metaData.getColumnLabel(3).equals("uid")){
                device.setUniqueId(rs.getString("uid"));
            }
            return device;
        }
    };

    private final NamedParameterStatement.ResultSetProcessor<String> gcmResultSetProcessor =
            new NamedParameterStatement.ResultSetProcessor<String>() {
        @Override
        public String processNextRow(ResultSet rs) throws SQLException {
            return rs.getString(1);
        }
    };

    public List<Device> getDevices() throws SQLException {
        if (queryGetDevices != null) {
            return queryGetDevices.prepare().executeQuery(deviceResultSetProcessor);
        } else {
            return new LinkedList<Device>();
        }
    }

    public void sendGcmMessage(Position position) throws SQLException, IOException {
        if (queryGetGcmIds != null && getDeviceById(position.getDeviceId())!=null) {
            List<String> gcmIds = assignGcmVariables(queryGetGcmIds.prepare(), position).executeQuery(gcmResultSetProcessor);
            if(gcmIds.size()>0){
                Message message = new Message.Builder()
                        .collapseKey("gps_data")
                        .timeToLive(600)
                        .addData("uid", String.valueOf(getDeviceById(position.getDeviceId()).getUniqueId()))
                        .addData("latitude", String.valueOf(position.getLatitude()))
                        .addData("longitude", String.valueOf(position.getLongitude()))
                        .addData("time", DATE_FORMAT.format(position.getTime()))
                        .build();
                Log.info("Sending GCM message:"+message.getData()+" to gcmIds: "+gcmIds);
                MulticastResult result = gcmSender.send(message, gcmIds, 1);
                Log.info("GCM Server Response:"+result.toString());
            }
        }

    }

    private NamedParameterStatement.Params assignGcmVariables(NamedParameterStatement.Params params, Position position) throws SQLException {
        params.setLong("device_id", position.getDeviceId());
        return params;
    }

    /**
     * Devices cache
     */
    private Map<String, Device> devices;
    private Map<Long, Device> deviceIdMap;
    private Calendar devicesLastUpdate;
    private long devicesRefreshDelay;
    private static final long DEFAULT_REFRESH_DELAY = 300;

    public Device getDeviceByImei(String imei) throws SQLException {

        if (devices == null || !devices.containsKey(imei) ||
                (Calendar.getInstance().getTimeInMillis() - devicesLastUpdate.getTimeInMillis() > devicesRefreshDelay)) {
            devices = new HashMap<String, Device>();
            deviceIdMap = new HashMap<Long, Device>();
            for (Device device : getDevices()) {
                devices.put(device.getImei(), device);
                deviceIdMap.put(device.getId(), device);
            }
            devicesLastUpdate = Calendar.getInstance();
        }

        return devices.get(imei);
    }

    public Device getDeviceById(Long id){
        return deviceIdMap.get(id);
    }

    private NamedParameterStatement.ResultSetProcessor<Long> generatedKeysResultSetProcessor = new NamedParameterStatement.ResultSetProcessor<Long>() {
        @Override
        public Long processNextRow(ResultSet rs) throws SQLException {
            return rs.getLong(1);
        }
    };

    public synchronized Long addPosition(Position position) throws SQLException {
        if (queryAddPosition != null) {
            List<Long> result = assignVariables(queryAddPosition.prepare(), position).executeUpdate(generatedKeysResultSetProcessor);
            if (result != null && !result.isEmpty()) {
                return result.iterator().next();
            }
        }
        return null;
    }

    public void updateLatestPosition(Position position, Long positionId) throws SQLException {
        if (queryUpdateLatestPosition != null) {
            assignVariables(queryUpdateLatestPosition.prepare(), position).setLong("id", positionId).executeUpdate();
        }
    }

    private NamedParameterStatement.Params assignVariables(NamedParameterStatement.Params params, Position position) throws SQLException {

        params.setLong("device_id", position.getDeviceId());
        params.setTimestamp("time", position.getTime());
        params.setBoolean("valid", position.getValid());
        params.setDouble("altitude", position.getAltitude());
        params.setDouble("latitude", position.getLatitude());
        params.setDouble("longitude", position.getLongitude());
        params.setDouble("speed", position.getSpeed());
        params.setDouble("course", position.getCourse());
        params.setString("address", position.getAddress());
        params.setString("extended_info", position.getExtendedInfo());

        // DELME: Temporary compatibility support
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            InputSource source = new InputSource(new StringReader(position.getExtendedInfo()));
            String index = xpath.evaluate("/info/index", source);
            if (!index.isEmpty()) {
                params.setLong("id", Long.valueOf(index));
            } else {
                params.setLong("id", null);
            }
            source = new InputSource(new StringReader(position.getExtendedInfo()));
            String power = xpath.evaluate("/info/power", source);
            if (!power.isEmpty()) {
                params.setDouble("power", Double.valueOf(power));
            } else {
                params.setLong("power", null);
            }
        } catch (XPathExpressionException e) {
            Log.warning("Error in XML: " + position.getExtendedInfo(), e);
            params.setLong("id", null);
            params.setLong("power", null);
        }

        return params;
    }

}

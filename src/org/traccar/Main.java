/*
 * Copyright 2012 - 2013 Anton Tananaev (anton.tananaev@gmail.com)
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
package org.traccar;

import java.util.Calendar;
import java.util.Locale;

import org.traccar.database.DataManager;
import org.traccar.helper.Log;

public class Main {
    
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        System.setProperty(DataManager.DEVICE_CACHE_UPDATED_AT, String.valueOf(Calendar.getInstance().getTimeInMillis()));

        final ServerManager service = new ServerManager();
        service.init(args);

        Log.info("Starting server...");
        Log.logSystemInfo();
        
        Log.info("Version: " + Main.class.getPackage().getImplementationVersion());

        service.start();

        // Shutdown server properly
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Log.info("Shutting down server...");
                service.stop();
            }
        });
    }

}

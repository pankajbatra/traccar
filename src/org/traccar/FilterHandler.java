/*
 * Copyright 2014 Anton Tananaev (anton.tananaev@gmail.com)
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.traccar.helper.DistanceCalculator;
import org.traccar.helper.Log;
import org.traccar.model.Position;

public class FilterHandler extends OneToOneDecoder {

    private boolean filterInvalid;
    private boolean filterZero;
    private boolean filterDuplicate;
    private int filterDistance;
    private long filterLimit;
    
    private final Map<Long, Position> lastPositions = new HashMap<Long, Position>();

    public FilterHandler(
            boolean filterInvalid,
            boolean filterZero,
            boolean filterDuplicate,
            int filterDistance,
            long filterLimit) {

        this.filterInvalid = filterInvalid;
        this.filterZero = filterZero;
        this.filterDuplicate = filterDuplicate;
        this.filterDistance = filterDistance;
        this.filterLimit = filterLimit;
    }
    
    public FilterHandler(Properties properties) {

        String value = properties.getProperty("filter.invalid");
        if (value != null) filterInvalid = Boolean.valueOf(value);

        value = properties.getProperty("filter.zero");
        if (value != null) filterZero = Boolean.valueOf(value);

        value = properties.getProperty("filter.duplicate");
        if (value != null) filterDuplicate = Boolean.valueOf(value);

        value = properties.getProperty("filter.distance");
        if (value != null) filterDistance = Integer.valueOf(value);

        value = properties.getProperty("filter.limit");
        if (value != null) filterLimit = Long.valueOf(value) * 1000;
    }
    
    private boolean filterInvalid(Position position) {
        return filterInvalid && !position.getValid();
    }
    
    private boolean filterZero(Position position) {
        return filterZero &&
                (position.getLatitude() == 0.0) &&
                (position.getLongitude() == 0.0);
    }
    
    private boolean filterDuplicate(Position position) {
        if (filterDuplicate) {
            Position last = lastPositions.get(position.getDeviceId());
            if (last != null) {
                return position.getTime().equals(last.getTime()) &&
                        ((last.getExtendedInfo()==null && position.getExtendedInfo()==null) ||
                                (position.getExtendedInfo()==null) ||
                                (last.getExtendedInfo()!=null && last.getExtendedInfo().equalsIgnoreCase(position.getExtendedInfo())));
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    private boolean filterDistance(Position position) {
        if (filterDistance != 0) {
            Position last = lastPositions.get(position.getDeviceId());
            if (last != null) {
                double distance = DistanceCalculator.distance(
                        position.getLatitude(), position.getLongitude(),
                        last.getLatitude(), last.getLongitude());
                return distance < filterDistance;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    private boolean filterLimit(Position position) {
        if (filterLimit != 0) {
            Position last = lastPositions.get(position.getDeviceId());
            if (last != null) {
                return (position.getTime().getTime() - last.getTime().getTime()) > filterLimit;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isDataChanged(Position p){
        Position last = lastPositions.get(p.getDeviceId());
        if(last==null || (p.getExtendedInfo()==null && last.getExtendedInfo()==null))
            return false;
        else if (last.getExtendedInfo()==null || !last.getExtendedInfo().equalsIgnoreCase(p.getExtendedInfo()))
            return true;
        return false;
    }
    
    private boolean filter(Position p) {
        
        boolean result =
                filterInvalid(p) ||
                filterZero(p) ||
                filterDuplicate(p);

        boolean isDataChanged = isDataChanged(p);

        if (!result && (filterLimit(p) || !filterDistance(p))) {
            result = false;
        }
        else if (!result && isDataChanged)
            result = false;
        else result = true;

        if (!result) {
            if(!isDataChanged && filterDistance(p)){
                Log.info("Distance has not changed, update existing record.");
                //update existing record
                Position last = lastPositions.get(p.getDeviceId());
                p.setStartTime(last.getStartTime());
                p.setDatabaseId(last.getDatabaseId());
            }
            lastPositions.put(p.getDeviceId(), p);
        }
        else
            Log.info("Position filtered from " + p.getDeviceId());

//        if(result)
//            Log.warning("Filtering record - "+"Invalid: "+filterInvalid(p)+" Zero:"+filterZero(p)+
//                    " Duplicate:"+filterDuplicate(p)+ " DataChanged: "+isDataChanged(p) +" Limit:"+filterLimit(p)
//            +" Distance:"+filterDistance(p));

        return result;
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, Object msg)
            throws Exception {
        
        if (msg instanceof Position) {
            if (filter((Position) msg)) {
                return null;
            }
        } else if (msg instanceof List) {
            Iterator<Position> i = ((List<Position>) msg).iterator();
            while (i.hasNext()) {
               if (filter(i.next())) {
                   i.remove();
               }
            }
        }

        return msg;
    }

}

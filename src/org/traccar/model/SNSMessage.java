package org.traccar.model;

import java.util.Date;

/**
 * Created by pankaj on 3/3/16.
 *
 */
public class SNSMessage {
    private Double latitude;
    private Double longitude;
    private Double altitude;
    private Double speed;
    private Double course;
    private Double bearing;
    private Double accuracy;
    private Date createdAt;
    private Date updatedAt;
    private String deviceId;
    private String extendedInfo;
    private String provider;

    public static SNSMessage fromPosition(Position position, String imei){
        SNSMessage message = new SNSMessage();
        message.setLatitude(position.getLatitude());
        message.setLongitude(position.getLongitude());
        message.setAltitude(position.getAltitude());
        message.setSpeed(position.getSpeed());
        message.setCourse(position.getCourse());
        message.setCreatedAt(position.getStartTime());
        message.setUpdatedAt(position.getTime());
        message.setExtendedInfo(position.getExtendedInfo());
        message.setDeviceId(imei);
        message.setProvider("gps_tracker");
        return message;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getCourse() {
        return course;
    }

    public void setCourse(Double course) {
        this.course = course;
    }

    public Double getBearing() {
        return bearing;
    }

    public void setBearing(Double bearing) {
        this.bearing = bearing;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getExtendedInfo() {
        return extendedInfo;
    }

    public void setExtendedInfo(String extendedInfo) {
        this.extendedInfo = extendedInfo;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }
}


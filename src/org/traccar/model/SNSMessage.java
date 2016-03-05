package org.traccar.model;

import java.util.Date;

/**
 * Created by pankaj on 3/3/16.
 *
 */
public class SNSMessage {
    private double latitude;
    private double longitude;
    private double altitude;
    private double speed;
    private double course;
    private double bearing;
    private double accuracy;
    private long createdAt;
    private long updatedAt;
    private String deviceId;
    private String extendedInfo;
    private String provider;
    private String externalId;

    public static SNSMessage fromPosition(Position position, String imei, String externalId){
        SNSMessage message = new SNSMessage();
        message.setLatitude(position.getLatitude());
        message.setLongitude(position.getLongitude());
        message.setAltitude(position.getAltitude());
        message.setSpeed(position.getSpeed());
        message.setCourse(position.getCourse());
        message.setCreatedAt(position.getStartTime().getTime());
        message.setUpdatedAt(position.getTime().getTime());
        message.setExtendedInfo(position.getExtendedInfo());
        message.setDeviceId(imei);
        message.setExternalId(externalId);
        message.setProvider("gps_tracker");
        return message;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getCourse() {
        return course;
    }

    public void setCourse(double course) {
        this.course = course;
    }

    public double getBearing() {
        return bearing;
    }

    public void setBearing(double bearing) {
        this.bearing = bearing;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
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


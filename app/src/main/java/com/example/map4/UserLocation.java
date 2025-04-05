package com.example.map4;

public class UserLocation {
    private double latitude;
    private double longitude;
    private Boolean isFree;

    public UserLocation() {
        this.isFree = true;
    }


    public UserLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.isFree = true;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

}

package com.wolfox.gps.model;

/**
 * WFLocation — نموذج الموقع الجغرافي الموحد داخل WolFox GPS Module
 */
public class WFLocation {

    private double latitude;
    private double longitude;
    private double altitude;
    private float accuracy;
    private float speed;
    private float bearing;
    private long timestamp;
    private String label;

    public WFLocation() {
        this.accuracy  = 1.0f;
        this.altitude  = 0.0;
        this.speed     = 0.0f;
        this.bearing   = 0.0f;
        this.timestamp = System.currentTimeMillis();
    }

    public WFLocation(double latitude, double longitude) {
        this();
        this.latitude  = latitude;
        this.longitude = longitude;
    }

    public WFLocation(double latitude, double longitude, String label) {
        this(latitude, longitude);
        this.label = label;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public double getLatitude()              { return latitude; }
    public void   setLatitude(double v)      { this.latitude = v; }

    public double getLongitude()             { return longitude; }
    public void   setLongitude(double v)     { this.longitude = v; }

    public double getAltitude()              { return altitude; }
    public void   setAltitude(double v)      { this.altitude = v; }

    public float  getAccuracy()              { return accuracy; }
    public void   setAccuracy(float v)       { this.accuracy = v; }

    public float  getSpeed()                 { return speed; }
    public void   setSpeed(float v)          { this.speed = v; }

    public float  getBearing()               { return bearing; }
    public void   setBearing(float v)        { this.bearing = v; }

    public long   getTimestamp()             { return timestamp; }
    public void   setTimestamp(long v)       { this.timestamp = v; }

    public String getLabel()                 { return label; }
    public void   setLabel(String v)         { this.label = v; }

    // ─── Utility ──────────────────────────────────────────────────────────────

    public boolean isValid() {
        return latitude  >= -90  && latitude  <= 90
            && longitude >= -180 && longitude <= 180;
    }

    @Override
    public String toString() {
        return "WFLocation{lat=" + latitude + ", lng=" + longitude
                + (label != null ? ", label=" + label : "") + "}";
    }
}

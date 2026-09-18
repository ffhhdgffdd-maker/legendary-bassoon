package com.wolfox.gps.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * FavoriteLocation — موقع محفوظ في قائمة المفضلة
 */
public class FavoriteLocation {

    private long   id;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private long   createdAt;
    private long   updatedAt;

    public FavoriteLocation() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public FavoriteLocation(String name, double latitude, double longitude) {
        this();
        this.name      = name;
        this.latitude  = latitude;
        this.longitude = longitude;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public long   getId()                { return id; }
    public void   setId(long v)          { this.id = v; }

    public String getName()              { return name; }
    public void   setName(String v)      { this.name = v; }

    public String getAddress()           { return address; }
    public void   setAddress(String v)   { this.address = v; }

    public double getLatitude()          { return latitude; }
    public void   setLatitude(double v)  { this.latitude = v; }

    public double getLongitude()         { return longitude; }
    public void   setLongitude(double v) { this.longitude = v; }

    public long   getCreatedAt()         { return createdAt; }
    public void   setCreatedAt(long v)   { this.createdAt = v; }

    public long   getUpdatedAt()         { return updatedAt; }
    public void   setUpdatedAt(long v)   { this.updatedAt = v; }

    // ─── JSON ─────────────────────────────────────────────────────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id",        id);
        obj.put("name",      name != null ? name : "");
        obj.put("address",   address != null ? address : "");
        obj.put("latitude",  latitude);
        obj.put("longitude", longitude);
        obj.put("createdAt", createdAt);
        obj.put("updatedAt", updatedAt);
        return obj;
    }

    public static FavoriteLocation fromJson(JSONObject obj) throws JSONException {
        FavoriteLocation f = new FavoriteLocation();
        f.id        = obj.optLong("id", 0);
        f.name      = obj.optString("name", "");
        f.address   = obj.optString("address", "");
        f.latitude  = obj.getDouble("latitude");
        f.longitude = obj.getDouble("longitude");
        f.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        f.updatedAt = obj.optLong("updatedAt", f.createdAt);
        return f;
    }

    public WFLocation toWFLocation() {
        return new WFLocation(latitude, longitude, name);
    }
}

package com.wolfox.gps.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * HistoryEntry — سجل عملية واحدة في تاريخ الاستخدام
 */
public class HistoryEntry {

    public enum Action {
        START_MOCK,
        STOP_MOCK,
        SEARCH,
        ACTIVATE,
        CHANGE_LOCATION,
        ADD_FAVORITE,
        IMPORT,
        EXPORT
    }

    private long   id;
    private Action action;
    private double latitude;
    private double longitude;
    private String detail;
    private long   timestamp;

    public HistoryEntry() {
        this.timestamp = System.currentTimeMillis();
    }

    public HistoryEntry(Action action, double lat, double lng, String detail) {
        this();
        this.action    = action;
        this.latitude  = lat;
        this.longitude = lng;
        this.detail    = detail;
    }

    public HistoryEntry(Action action, String detail) {
        this();
        this.action = action;
        this.detail = detail;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public long   getId()                { return id; }
    public void   setId(long v)          { this.id = v; }

    public Action getAction()            { return action; }
    public void   setAction(Action v)    { this.action = v; }

    public double getLatitude()          { return latitude; }
    public void   setLatitude(double v)  { this.latitude = v; }

    public double getLongitude()         { return longitude; }
    public void   setLongitude(double v) { this.longitude = v; }

    public String getDetail()            { return detail; }
    public void   setDetail(String v)    { this.detail = v; }

    public long   getTimestamp()         { return timestamp; }
    public void   setTimestamp(long v)   { this.timestamp = v; }

    // ─── Label ────────────────────────────────────────────────────────────────

    public String getActionLabel() {
        if (action == null) return "—";
        switch (action) {
            case START_MOCK:       return "▶ بدء التزييف";
            case STOP_MOCK:        return "⏹ إيقاف التزييف";
            case SEARCH:           return "🔍 بحث";
            case ACTIVATE:         return "✅ تفعيل";
            case CHANGE_LOCATION:  return "📍 تغيير الموقع";
            case ADD_FAVORITE:     return "⭐ إضافة مفضلة";
            case IMPORT:           return "📥 استيراد";
            case EXPORT:           return "📤 تصدير";
            default:               return action.name();
        }
    }

    // ─── JSON ─────────────────────────────────────────────────────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id",        id);
        obj.put("action",    action != null ? action.name() : "");
        obj.put("latitude",  latitude);
        obj.put("longitude", longitude);
        obj.put("detail",    detail != null ? detail : "");
        obj.put("timestamp", timestamp);
        return obj;
    }

    public static HistoryEntry fromJson(JSONObject obj) throws JSONException {
        HistoryEntry e = new HistoryEntry();
        e.id        = obj.optLong("id", 0);
        e.detail    = obj.optString("detail", "");
        e.latitude  = obj.optDouble("latitude", 0);
        e.longitude = obj.optDouble("longitude", 0);
        e.timestamp = obj.optLong("timestamp", System.currentTimeMillis());
        try {
            e.action = Action.valueOf(obj.optString("action", ""));
        } catch (IllegalArgumentException ex) {
            e.action = null;
        }
        return e;
    }
}

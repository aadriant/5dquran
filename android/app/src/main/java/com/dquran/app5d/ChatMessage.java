package com.dquran.app5d;

import org.json.JSONException;
import org.json.JSONObject;

public class ChatMessage {
    private String id;
    private String sender; // "user" or "admin"
    private String userName;
    private String tipe; // "kesalahan", "usulan", "tanya", "balasan"
    private String pesan;
    private String createdAt;
    private long timestamp;
    private boolean isSync;

    public ChatMessage(String id, String sender, String userName, String tipe, String pesan, String createdAt, long timestamp, boolean isSync) {
        this.id = id;
        this.sender = sender;
        this.userName = userName;
        this.tipe = tipe;
        this.pesan = pesan;
        this.createdAt = createdAt;
        this.timestamp = timestamp;
        this.isSync = isSync;
    }

    public String getId() { return id; }
    public String getSender() { return sender; }
    public boolean isAdmin() { return "admin".equalsIgnoreCase(sender); }
    public String getUserName() { return userName; }
    public String getTipe() { return tipe; }
    public String getPesan() { return pesan; }
    public String getCreatedAt() { return createdAt; }
    public long getTimestamp() { return timestamp; }
    public boolean isSync() { return isSync; }
    public void setSync(boolean sync) { isSync = sync; }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("sender", sender);
            obj.put("user_name", userName);
            obj.put("tipe", tipe);
            obj.put("pesan", pesan);
            obj.put("created_at", createdAt);
            obj.put("timestamp", timestamp);
            obj.put("is_sync", isSync);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return obj;
    }

    public static ChatMessage fromJson(JSONObject obj) {
        if (obj == null) return null;
        String id = obj.optString("id", "");
        String sender = obj.optString("sender", "user");
        String userName = obj.optString("user_name", "");
        String tipe = obj.optString("tipe", "usulan");
        String pesan = obj.optString("pesan", "");
        String createdAt = obj.optString("created_at", "");
        long timestamp = obj.optLong("timestamp", System.currentTimeMillis() / 1000);
        boolean isSync = obj.optBoolean("is_sync", true);
        return new ChatMessage(id, sender, userName, tipe, pesan, createdAt, timestamp, isSync);
    }
}

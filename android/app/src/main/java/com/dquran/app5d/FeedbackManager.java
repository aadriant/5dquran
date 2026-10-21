package com.dquran.app5d;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FeedbackManager {

    private static final String PREF_NAME = "feedback_prefs";
    private static final String KEY_DEVICE_ID = "device_user_id";
    private static final String KEY_MESSAGES = "cached_messages";
    private static final String API_BASE_URL = "https://5dquran.amiri.id/api.php";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface FeedbackCallback {
        void onSuccess(List<ChatMessage> messages);
        void onError(String errorMessage);
    }

    public static String getUserId(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String id = sp.getString(KEY_DEVICE_ID, null);
        if (id == null || id.isEmpty()) {
            id = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            sp.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static synchronized List<ChatMessage> getLocalMessages(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsonStr = sp.getString(KEY_MESSAGES, null);
        List<ChatMessage> list = new ArrayList<>();

        if (jsonStr != null && !jsonStr.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    ChatMessage msg = ChatMessage.fromJson(arr.getJSONObject(i));
                    if (msg != null) list.add(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (list.isEmpty()) {
            // Pesan sambutan awal dari pengembang
            ChatMessage welcome = new ChatMessage(
                    "welcome_msg_01",
                    "admin",
                    "Tim Pengembang 5D Quran",
                    "balasan",
                    "Assalamu'alaikum Warahmatullahi Wabarakatuh.\n\nSelamat datang di Layanan Chat & Bantuan 5D Quran!\nJika Anda menemukan kesalahan/bug pada ayat maupun sistem, atau mempunyai ide usulan untuk pengembangan aplikasi ini, silakan sampaikan di sini.\n\nTim pengembang akan membaca laporan Anda dan membalas langsung di percakapan ini.",
                    new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date()),
                    System.currentTimeMillis() / 1000,
                    true
            );
            list.add(welcome);
            saveLocalMessages(context, list);
        }

        return list;
    }

    public static synchronized void saveLocalMessages(Context context, List<ChatMessage> list) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (ChatMessage m : list) {
            arr.put(m.toJson());
        }
        sp.edit().putString(KEY_MESSAGES, arr.toString()).apply();
    }

    public static void sendMessage(Context context, String tipe, String pesan, FeedbackCallback callback) {
        final Context appCtx = context.getApplicationContext();
        final String userId = getUserId(appCtx);
        final String nowStr = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date());
        final String msgId = "local_" + System.currentTimeMillis();

        ChatMessage newMsg = new ChatMessage(
                msgId,
                "user",
                "Saya",
                tipe,
                pesan,
                nowStr,
                System.currentTimeMillis() / 1000,
                false
        );

        List<ChatMessage> currentList = getLocalMessages(appCtx);
        currentList.add(newMsg);
        saveLocalMessages(appCtx, currentList);

        if (callback != null) {
            callback.onSuccess(currentList);
        }

        // Kirim ke server di background
        executor.execute(() -> {
            boolean success = false;
            try {
                URL url = new URL(API_BASE_URL + "?a=chat_send");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                payload.put("user_id", userId);
                payload.put("user_name", "Pengguna 5D Quran");
                payload.put("tipe", tipe);
                payload.put("pesan", pesan);
                payload.put("device_info", Build.MANUFACTURER + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");
                payload.put("app_version", "1.0.1");

                byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                    os.flush();
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    if (res.optBoolean("ok", false)) {
                        success = true;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isSuccess = success;
            mainHandler.post(() -> {
                if (isSuccess) {
                    List<ChatMessage> list = getLocalMessages(appCtx);
                    for (ChatMessage m : list) {
                        if (msgId.equals(m.getId())) {
                            m.setSync(true);
                            break;
                        }
                    }
                    saveLocalMessages(appCtx, list);
                    // Langsung coba ambil pesan terbaru dari server
                    fetchServerMessages(appCtx, callback);
                } else if (callback != null) {
                    // Masih tersimpan secara offline
                    callback.onSuccess(getLocalMessages(appCtx));
                }
            });
        });
    }

    public static void fetchServerMessages(Context context, FeedbackCallback callback) {
        final Context appCtx = context.getApplicationContext();
        final String userId = getUserId(appCtx);

        executor.execute(() -> {
            List<ChatMessage> serverMessages = new ArrayList<>();
            boolean success = false;
            try {
                URL url = new URL(API_BASE_URL + "?a=chat_get&user_id=" + userId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    if (res.optBoolean("ok", false) && res.has("messages")) {
                        JSONArray arr = res.getJSONArray("messages");
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            ChatMessage msg = ChatMessage.fromJson(obj);
                            if (msg != null) serverMessages.add(msg);
                        }
                        success = true;
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isSuccess = success;
            final List<ChatMessage> fetched = serverMessages;
            mainHandler.post(() -> {
                if (isSuccess && !fetched.isEmpty()) {
                    List<ChatMessage> local = getLocalMessages(appCtx);
                    Set<String> existingIds = new HashSet<>();
                    for (ChatMessage m : local) {
                        existingIds.add(m.getId());
                    }

                    boolean updated = false;
                    for (ChatMessage sMsg : fetched) {
                        if (!existingIds.contains(sMsg.getId())) {
                            local.add(sMsg);
                            existingIds.add(sMsg.getId());
                            updated = true;
                        }
                    }

                    if (updated) {
                        saveLocalMessages(appCtx, local);
                    }
                    if (callback != null) callback.onSuccess(local);
                } else if (callback != null) {
                    callback.onSuccess(getLocalMessages(appCtx));
                }
            });
        });
    }
}

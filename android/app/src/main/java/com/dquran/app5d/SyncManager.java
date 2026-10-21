package com.dquran.app5d;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SyncManager {

    private static final String TAG = "SyncManager";
    public static final String SYNC_URL = "https://5dquran.amiri.id/api.php?a=sync_app";

    public interface OnSyncCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public static void syncWithWeb(Context context, OnSyncCallback callback) {
        if (!PreferenceManager.isLoggedIn(context)) {
            if (callback != null) callback.onError("Pengguna belum login.");
            return;
        }

        String email = PreferenceManager.getUserEmail(context);
        if (email.isEmpty() || !email.contains("@")) {
            if (callback != null) callback.onError("Email Google tidak valid.");
            return;
        }

        String name = PreferenceManager.getUserName(context);
        String photo = PreferenceManager.getUserPhotoUrl(context);
        String googleId = PreferenceManager.getUserId(context);

        String localQari = PreferenceManager.getQariId(context);
        String webQari = mapAndroidQariToWeb(localQari);
        boolean tulisContoh = PreferenceManager.isTulisDenganContoh(context);
        String contohStr = tulisContoh ? "ya" : "tidak";

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject payload = new JSONObject();
                payload.put("email", email);
                payload.put("nama", name);
                payload.put("foto", photo);
                payload.put("google_id", googleId);

                // Update settings to server
                JSONObject updateSetting = new JSONObject();
                updateSetting.put("qari", webQari);
                updateSetting.put("contoh", contohStr);
                updateSetting.put("mushaf", PreferenceManager.getHurufMushaf(context));
                updateSetting.put("tampilan", PreferenceManager.getTampilanAyat(context));
                payload.put("update_setting", updateSetting);

                // Update last marks to server
                JSONObject updateMarks = new JSONObject();
                int lrSurah = PreferenceManager.getLastReadSurah(context);
                int lrAyah  = PreferenceManager.getLastReadAyah(context);
                if (lrSurah > 0 && lrAyah > 0) {
                    JSONObject m = new JSONObject();
                    m.put("surah", lrSurah);
                    m.put("ayat", lrAyah);
                    m.put("nama", PreferenceManager.getLastReadSurahName(context));
                    updateMarks.put("terakhir", m);
                }

                int ltSurah = PreferenceManager.getLastTulisSurah(context);
                int ltAyah  = PreferenceManager.getLastTulisAyah(context);
                if (ltSurah > 0 && ltAyah > 0) {
                    JSONObject m = new JSONObject();
                    m.put("surah", ltSurah);
                    m.put("ayat", ltAyah);
                    m.put("nama", PreferenceManager.getLastTulisSurahName(context));
                    updateMarks.put("terakhir_tulis", m);
                }

                int lhSurah = PreferenceManager.getLastHafalanSurah(context);
                int lhAyah  = PreferenceManager.getLastHafalanAyah(context);
                if (lhSurah > 0 && lhAyah > 0) {
                    JSONObject m = new JSONObject();
                    m.put("surah", lhSurah);
                    m.put("ayat", lhAyah);
                    m.put("nama", PreferenceManager.getLastHafalanSurahName(context));
                    updateMarks.put("terakhir_hafalan", m);
                }
                payload.put("update_marks", updateMarks);

                URL url = new URL(SYNC_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "5DQuran-Android/1.0");

                byte[] outBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(outBytes);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    if (res.optBoolean("ok", false)) {
                        // Apply settings from web
                        JSONObject sObj = res.optJSONObject("setting");
                        if (sObj != null) {
                            String sQari = sObj.optString("qari", "");
                            if (!sQari.isEmpty()) {
                                mapWebQariToAndroid(context, sQari);
                            }
                            String sContoh = sObj.optString("contoh", "ya");
                            boolean serverContoh = "ya".equalsIgnoreCase(sContoh);
                            PreferenceManager.setTulisDenganContoh(context, serverContoh);
                            PreferenceManager.setHafalanDenganContoh(context, serverContoh);

                            String sMushaf = sObj.optString("mushaf", "");
                            if (!sMushaf.isEmpty()) {
                                PreferenceManager.setHurufMushaf(context, sMushaf);
                            }
                            String sTampilan = sObj.optString("tampilan", "");
                            if (!sTampilan.isEmpty()) {
                                PreferenceManager.setTampilanAyat(context, sTampilan);
                            }
                        }

                        // Apply last read if exists
                        JSONObject tRead = res.optJSONObject("terakhir");
                        if (tRead != null) {
                            int s = tRead.optInt("surah", 0);
                            int a = tRead.optInt("ayat", 0);
                            String n = tRead.optString("nama", "");
                            if (s > 0 && a > 0) {
                                PreferenceManager.setLastRead(context, s, n, a);
                            }
                        }

                        // Apply last tulis if exists
                        JSONObject tTulis = res.optJSONObject("terakhir_tulis");
                        if (tTulis != null) {
                            int s = tTulis.optInt("surah", 0);
                            int a = tTulis.optInt("ayat", 0);
                            String n = tTulis.optString("nama", "");
                            if (s > 0 && a > 0) {
                                PreferenceManager.setLastTulis(context, s, n, a);
                            }
                        }

                        // Apply last hafalan if exists
                        JSONObject tHafal = res.optJSONObject("terakhir_hafalan");
                        if (tHafal != null) {
                            int s = tHafal.optInt("surah", 0);
                            int a = tHafal.optInt("ayat", 0);
                            String n = tHafal.optString("nama", "");
                            if (s > 0 && a > 0) {
                                PreferenceManager.setLastHafalan(context, s, n, a);
                            }
                        }

                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (callback != null) callback.onSuccess("Profil & pengaturan tersinkron dengan web (amiri.id)");
                        });
                        return;
                    }
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onError("Respon server: " + code);
                });
            } catch (Exception e) {
                Log.e(TAG, "Sync error", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (callback != null) callback.onError("Koneksi sinkronisasi gagal: " + e.getMessage());
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static String mapAndroidQariToWeb(String qariId) {
        if ("Abdurrahmaan_As-Sudais_192kbps".equals(qariId)) return "sudais";
        if ("Ghamadi_40kbps".equals(qariId)) return "ghamdi";
        if ("Husary_128kbps".equals(qariId)) return "husary";
        if ("Minshawy_Murattal_128kbps".equals(qariId)) return "minshawi";
        return "alafasy";
    }

    private static void mapWebQariToAndroid(Context context, String webQari) {
        switch (webQari.toLowerCase()) {
            case "sudais":
                PreferenceManager.setQari(context, "Abdurrahmaan_As-Sudais_192kbps", "Syaikh Abdul Rahman As-Sudais");
                break;
            case "ghamdi":
                PreferenceManager.setQari(context, "Ghamadi_40kbps", "Syaikh Saad Al-Ghamdi");
                break;
            case "husary":
                PreferenceManager.setQari(context, "Husary_128kbps", "Syaikh Mahmud Khalil Al-Husary");
                break;
            case "minshawi":
                PreferenceManager.setQari(context, "Minshawy_Murattal_128kbps", "Syaikh Muhammad Siddiq Al-Minshawi");
                break;
            case "alafasy":
            default:
                PreferenceManager.setQari(context, "Alafasy_128kbps", "Syaikh Mishari Rashid Alafasy");
                break;
        }
    }
}

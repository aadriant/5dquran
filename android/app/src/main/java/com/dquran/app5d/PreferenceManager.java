package com.dquran.app5d;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import java.util.HashSet;
import java.util.Set;

public class PreferenceManager {

    private static final String PREF_NAME = "5dquran_prefs";

    public static final String KEY_QARI_ID = "qari_id";
    public static final String KEY_QARI_NAME = "qari_name";
    public static final String KEY_FONT_SIZE = "arabic_font_size";

    public static final String KEY_LAST_SURAH = "last_surah";
    public static final String KEY_LAST_SURAH_NAME = "last_surah_name";
    public static final String KEY_LAST_AYAH = "last_ayah";

    // Last position for Hafalan & Tulis activities
    public static final String KEY_LAST_HAFALAN_SURAH = "last_hafalan_surah";
    public static final String KEY_LAST_HAFALAN_SURAH_NAME = "last_hafalan_surah_name";
    public static final String KEY_LAST_HAFALAN_AYAH = "last_hafalan_ayah";

    public static final String KEY_LAST_TULIS_SURAH = "last_tulis_surah";
    public static final String KEY_LAST_TULIS_SURAH_NAME = "last_tulis_surah_name";
    public static final String KEY_LAST_TULIS_AYAH = "last_tulis_ayah";

    // Last activity type ("baca", "tulis", "hafalan") & timestamp
    public static final String KEY_LAST_ACTIVITY = "last_activity";
    public static final String KEY_LAST_ACTIVITY_TIME = "last_activity_time";

    // Progress sets: stored as "surah#ayah" strings
    public static final String KEY_PROGRESS_BACA = "progress_baca";
    public static final String KEY_PROGRESS_HAFALAN = "progress_hafalan";
    public static final String KEY_PROGRESS_TULISAN = "progress_tulisan";

    // Total ayat in Al-Quran
    public static final int TOTAL_AYAT = 6236;

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ── QARI PREFERENCES ──
    public static String getQariId(Context context) {
        return getPrefs(context).getString(KEY_QARI_ID, "Alafasy_128kbps");
    }

    public static String getQariName(Context context) {
        return getPrefs(context).getString(KEY_QARI_NAME, "Syaikh Mishari Rashid Alafasy");
    }

    public static void setQari(Context context, String qariId, String qariName) {
        getPrefs(context).edit()
                .putString(KEY_QARI_ID, qariId)
                .putString(KEY_QARI_NAME, qariName)
                .apply();
    }

    // ── FONT SIZE PREFERENCES ──
    public static int getArabicFontSize(Context context) {
        return getPrefs(context).getInt(KEY_FONT_SIZE, 23);
    }

    public static void setArabicFontSize(Context context, int size) {
        getPrefs(context).edit().putInt(KEY_FONT_SIZE, size).apply();
    }

    // ── CONTOH PREFERENCES (TULIS & HAFALAN) ──
    public static final String KEY_TULIS_CONTOH = "tulis_contoh";
    public static final String KEY_HAFALAN_CONTOH = "hafalan_contoh";

    public static boolean isTulisDenganContoh(Context context) {
        return getPrefs(context).getBoolean(KEY_TULIS_CONTOH, true);
    }

    public static void setTulisDenganContoh(Context context, boolean denganContoh) {
        getPrefs(context).edit().putBoolean(KEY_TULIS_CONTOH, denganContoh).apply();
    }

    public static boolean isHafalanDenganContoh(Context context) {
        return getPrefs(context).getBoolean(KEY_HAFALAN_CONTOH, true);
    }

    public static void setHafalanDenganContoh(Context context, boolean denganContoh) {
        getPrefs(context).edit().putBoolean(KEY_HAFALAN_CONTOH, denganContoh).apply();
    }

    // ── MODE TERUSAN HAFALAN (DEFAULT: TIDAK / FALSE) ──
    public static final String KEY_HAFALAN_TERUSAN = "hafalan_terusan";

    public static boolean isHafalanTerusan(Context context) {
        return getPrefs(context).getBoolean(KEY_HAFALAN_TERUSAN, false);
    }

    public static void setHafalanTerusan(Context context, boolean terusan) {
        getPrefs(context).edit().putBoolean(KEY_HAFALAN_TERUSAN, terusan).apply();
    }

    // ── TAMPILAN AYAT (ARAB + TERJEMAH vs ARAB SAJA) ──
    public static final String KEY_TAMPILAN_AYAT = "tampilan_ayat";

    public static String getTampilanAyat(Context context) {
        return getPrefs(context).getString(KEY_TAMPILAN_AYAT, "lengkap");
    }

    public static void setTampilanAyat(Context context, String tampilan) {
        getPrefs(context).edit().putString(KEY_TAMPILAN_AYAT, tampilan).apply();
    }

    public static boolean isArabSaja(Context context) {
        return "arab".equalsIgnoreCase(getTampilanAyat(context));
    }

    // ── MODE TAMPILAN SURAT: CARD (PER AYAT) vs PAGE (PER HALAMAN MUSHAF) ──
    public static final String KEY_SURAH_VIEW_MODE = "surah_view_mode"; // "card" vs "page"

    public static String getSurahViewMode(Context context) {
        return getPrefs(context).getString(KEY_SURAH_VIEW_MODE, "card");
    }

    public static void setSurahViewMode(Context context, String mode) {
        getPrefs(context).edit().putString(KEY_SURAH_VIEW_MODE, mode).apply();
    }

    // ── HURUF MUSHAF (MADANI, NASKH, INDOPAK) ──
    public static final String KEY_HURUF_MUSHAF = "huruf_mushaf";
    private static Typeface cachedMadani = null;
    private static Typeface cachedNaskh = null;
    private static Typeface cachedIndopak = null;

    public static String getHurufMushaf(Context context) {
        return getPrefs(context).getString(KEY_HURUF_MUSHAF, "madani");
    }

    public static void setHurufMushaf(Context context, String huruf) {
        getPrefs(context).edit().putString(KEY_HURUF_MUSHAF, huruf).apply();
    }

    public static Typeface getMushafTypeface(Context context) {
        String huruf = getHurufMushaf(context).toLowerCase();
        try {
            if ("naskh".equals(huruf)) {
                if (cachedNaskh == null) {
                    cachedNaskh = Typeface.createFromAsset(context.getAssets(), "fonts/naskh.ttf");
                }
                return cachedNaskh;
            } else if ("indopak".equals(huruf)) {
                if (cachedIndopak == null) {
                    cachedIndopak = Typeface.createFromAsset(context.getAssets(), "fonts/indopak.ttf");
                }
                return cachedIndopak;
            } else {
                // Default: madani
                if (cachedMadani == null) {
                    cachedMadani = Typeface.createFromAsset(context.getAssets(), "fonts/madani.ttf");
                }
                return cachedMadani;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Typeface.DEFAULT;
        }
    }

    // ── LAST READ (BACA) PREFERENCES ──
    public static int getLastReadSurah(Context context) {
        return getPrefs(context).getInt(KEY_LAST_SURAH, 1);
    }

    public static String getLastReadSurahName(Context context) {
        return getPrefs(context).getString(KEY_LAST_SURAH_NAME, "Al-Fatihah");
    }

    public static int getLastReadAyah(Context context) {
        return getPrefs(context).getInt(KEY_LAST_AYAH, 1);
    }

    public static void setLastRead(Context context, int surah, String surahName, int ayah) {
        getPrefs(context).edit()
                .putInt(KEY_LAST_SURAH, surah)
                .putString(KEY_LAST_SURAH_NAME, surahName)
                .putInt(KEY_LAST_AYAH, ayah)
                .putString(KEY_LAST_ACTIVITY, "baca")
                .putLong(KEY_LAST_ACTIVITY_TIME, System.currentTimeMillis())
                .apply();
    }

    // ── LAST HAFALAN POSITION ──
    public static int getLastHafalanSurah(Context context) {
        return getPrefs(context).getInt(KEY_LAST_HAFALAN_SURAH, 0);
    }

    public static String getLastHafalanSurahName(Context context) {
        return getPrefs(context).getString(KEY_LAST_HAFALAN_SURAH_NAME, "");
    }

    public static int getLastHafalanAyah(Context context) {
        return getPrefs(context).getInt(KEY_LAST_HAFALAN_AYAH, 1);
    }

    public static void setLastHafalan(Context context, int surah, String surahName, int ayah) {
        getPrefs(context).edit()
                .putInt(KEY_LAST_HAFALAN_SURAH, surah)
                .putString(KEY_LAST_HAFALAN_SURAH_NAME, surahName)
                .putInt(KEY_LAST_HAFALAN_AYAH, ayah)
                .putString(KEY_LAST_ACTIVITY, "hafalan")
                .putLong(KEY_LAST_ACTIVITY_TIME, System.currentTimeMillis())
                .apply();
    }

    // ── LAST TULIS POSITION ──
    public static int getLastTulisSurah(Context context) {
        return getPrefs(context).getInt(KEY_LAST_TULIS_SURAH, 0);
    }

    public static String getLastTulisSurahName(Context context) {
        return getPrefs(context).getString(KEY_LAST_TULIS_SURAH_NAME, "");
    }

    public static int getLastTulisAyah(Context context) {
        return getPrefs(context).getInt(KEY_LAST_TULIS_AYAH, 1);
    }

    public static void setLastTulis(Context context, int surah, String surahName, int ayah) {
        getPrefs(context).edit()
                .putInt(KEY_LAST_TULIS_SURAH, surah)
                .putString(KEY_LAST_TULIS_SURAH_NAME, surahName)
                .putInt(KEY_LAST_TULIS_AYAH, ayah)
                .putString(KEY_LAST_ACTIVITY, "tulis")
                .putLong(KEY_LAST_ACTIVITY_TIME, System.currentTimeMillis())
                .apply();
    }

    public static void setLastActivity(Context context, String type) {
        getPrefs(context).edit()
                .putString(KEY_LAST_ACTIVITY, type)
                .putLong(KEY_LAST_ACTIVITY_TIME, System.currentTimeMillis())
                .apply();
    }

    public static String getLastActivity(Context context) {
        return getPrefs(context).getString(KEY_LAST_ACTIVITY, null);
    }

    // ── PROGRESS TRACKING (Baca / Hafalan / Tulisan) ──

    private static String ayahKey(int surah, int ayah) {
        return surah + "#" + ayah;
    }

    /** Mark an ayah as read (Baca). Idempotent. */
    public static void markBaca(Context context, int surah, int ayah) {
        addProgress(context, KEY_PROGRESS_BACA, surah, ayah);
    }

    /** Mark an ayah hafalan complete (only if wrong==0 && missing==0). Idempotent. */
    public static void markHafalan(Context context, int surah, int ayah) {
        addProgress(context, KEY_PROGRESS_HAFALAN, surah, ayah);
    }

    /** Mark an ayah tulisan complete (only if wrong==0 && missing==0). Idempotent. */
    public static void markTulisan(Context context, int surah, int ayah) {
        addProgress(context, KEY_PROGRESS_TULISAN, surah, ayah);
    }

    public static void addProgressTulisan(Context context, int surah, int ayah) {
        markTulisan(context, surah, ayah);
    }

    private static void addProgress(Context context, String key, int surah, int ayah) {
        Set<String> current = new HashSet<>(getPrefs(context).getStringSet(key, new HashSet<>()));
        current.add(ayahKey(surah, ayah));
        getPrefs(context).edit().putStringSet(key, current).apply();
    }

    public static int getProgressBaca(Context context) {
        return getPrefs(context).getStringSet(KEY_PROGRESS_BACA, new HashSet<>()).size();
    }

    public static int getProgressHafalan(Context context) {
        return getPrefs(context).getStringSet(KEY_PROGRESS_HAFALAN, new HashSet<>()).size();
    }

    public static int getProgressTulisan(Context context) {
        return getPrefs(context).getStringSet(KEY_PROGRESS_TULISAN, new HashSet<>()).size();
    }

    // ── USER PROFILE & GOOGLE AUTH ──
    public static final String KEY_USER_LOGGED_IN = "user_logged_in";
    public static final String KEY_USER_NAME      = "user_name";
    public static final String KEY_USER_EMAIL     = "user_email";
    public static final String KEY_USER_PHOTO_URL = "user_photo_url";
    public static final String KEY_USER_ID        = "user_id";

    public static boolean isLoggedIn(Context context) {
        return getPrefs(context).getBoolean(KEY_USER_LOGGED_IN, false);
    }

    public static String getUserName(Context context) {
        return getPrefs(context).getString(KEY_USER_NAME, "Pengguna");
    }

    public static String getUserEmail(Context context) {
        return getPrefs(context).getString(KEY_USER_EMAIL, "");
    }

    public static String getUserPhotoUrl(Context context) {
        return getPrefs(context).getString(KEY_USER_PHOTO_URL, "");
    }

    public static String getUserId(Context context) {
        return getPrefs(context).getString(KEY_USER_ID, "");
    }

    public static void setUserProfile(Context context, String name, String email, String photoUrl, String id) {
        getPrefs(context).edit()
                .putBoolean(KEY_USER_LOGGED_IN, true)
                .putString(KEY_USER_NAME, name != null ? name : "Pengguna")
                .putString(KEY_USER_EMAIL, email != null ? email : "")
                .putString(KEY_USER_PHOTO_URL, photoUrl != null ? photoUrl : "")
                .putString(KEY_USER_ID, id != null ? id : "")
                .apply();
    }

    public static void logoutUser(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_USER_LOGGED_IN, false)
                .putString(KEY_USER_NAME, "")
                .putString(KEY_USER_EMAIL, "")
                .putString(KEY_USER_PHOTO_URL, "")
                .putString(KEY_USER_ID, "")
                .apply();
    }

    // ── DRAFT KETIKAN TULIS PER AYAT ──
    public static void setTulisDraft(Context context, int surah, int ayah, String draft) {
        getPrefs(context).edit().putString("tulis_draft_s" + surah + "_a" + ayah, draft != null ? draft : "").apply();
    }

    public static String getTulisDraft(Context context, int surah, int ayah) {
        return getPrefs(context).getString("tulis_draft_s" + surah + "_a" + ayah, "");
    }

    public static void clearTulisDraft(Context context, int surah, int ayah) {
        getPrefs(context).edit().remove("tulis_draft_s" + surah + "_a" + ayah).apply();
    }

    // ── MAIN SCREEN ORDER MODE ("surah", "juz", "page", "turun") ──
    public static final String KEY_MAIN_ORDER_MODE = "main_order_mode";

    public static String getMainOrderMode(Context context) {
        return getPrefs(context).getString(KEY_MAIN_ORDER_MODE, "surah");
    }

    public static void setMainOrderMode(Context context, String mode) {
        getPrefs(context).edit().putString(KEY_MAIN_ORDER_MODE, mode).apply();
    }

    // ── TERJEMAHAN PER KATA (WORD-BY-WORD) MODE ──
    public static final String KEY_WORD_BY_WORD = "word_by_word_enabled";

    public static boolean isWordByWord(Context context) {
        return getPrefs(context).getBoolean(KEY_WORD_BY_WORD, false);
    }

    public static void setWordByWord(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_WORD_BY_WORD, enabled).apply();
    }

    // ── HASIL PERIKSA HAFALAN PER AYAT ──
    public static void saveHafalanEvaluation(Context context, int surah, int ayah, int score, String scoreText, String feedback, String userText) {
        String keyPrefix = "eval_s" + surah + "_a" + ayah + "_";
        getPrefs(context).edit()
                .putInt(keyPrefix + "score", score)
                .putString(keyPrefix + "score_text", scoreText != null ? scoreText : "")
                .putString(keyPrefix + "feedback", feedback != null ? feedback : "")
                .putString(keyPrefix + "user_text", userText != null ? userText : "")
                .putLong(keyPrefix + "time", System.currentTimeMillis())
                .apply();
    }

    public static boolean hasHafalanEvaluation(Context context, int surah, int ayah) {
        return getPrefs(context).contains("eval_s" + surah + "_a" + ayah + "_score");
    }

    public static int getHafalanScore(Context context, int surah, int ayah) {
        return getPrefs(context).getInt("eval_s" + surah + "_a" + ayah + "_score", 0);
    }

    public static String getHafalanScoreText(Context context, int surah, int ayah) {
        return getPrefs(context).getString("eval_s" + surah + "_a" + ayah + "_score_text", "");
    }

    public static String getHafalanFeedback(Context context, int surah, int ayah) {
        return getPrefs(context).getString("eval_s" + surah + "_a" + ayah + "_feedback", "");
    }

    public static String getHafalanUserText(Context context, int surah, int ayah) {
        return getPrefs(context).getString("eval_s" + surah + "_a" + ayah + "_user_text", "");
    }
}


package com.dquran.app5d;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class QuranRepository {

    private static List<SurahModel> cachedSurahs = null;
    private static JSONObject cachedQuran = null;
    private static JSONObject cachedHadis = null;

    public static String readAssetString(Context context, String filename) {
        try (InputStream is = context.getAssets().open(filename)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static String cleanArabicSurahName(String name) {
        if (name == null) return "";
        return name.replaceFirst("^\\s*سُ?ورَ?ةُ?\\s*", "").trim();
    }

    public static List<SurahModel> getSurahs(Context context) {
        if (cachedSurahs != null) return cachedSurahs;
        List<SurahModel> list = new ArrayList<>();
        try {
            String jsonStr = readAssetString(context, "surahs.json");
            if (jsonStr != null) {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int number = obj.getInt("number");
                    String rawName = obj.getString("name");
                    String name = cleanArabicSurahName(rawName);
                    String englishName = obj.getString("englishName");
                    String indoName = obj.optString("englishNameTranslation", englishName);
                    int count = obj.getInt("numberOfAyahs");
                    String type = obj.getString("revelationType");
                    list.add(new SurahModel(number, name, englishName, indoName, count, type));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        cachedSurahs = list;
        return list;
    }

    public static String getSurahName(Context context, int surahNumber) {
        List<SurahModel> list = getSurahs(context);
        for (SurahModel s : list) {
            if (s.getNumber() == surahNumber) return s.getEnglishName();
        }
        return "Surah " + surahNumber;
    }

    public static List<AyahModel> getAyahs(Context context, int surahNumber) {
        List<AyahModel> list = new ArrayList<>();
        try {
            if (cachedQuran == null) {
                String jsonStr = readAssetString(context, "quran.json");
                if (jsonStr != null) {
                    cachedQuran = new JSONObject(jsonStr);
                }
            }
            if (cachedQuran != null && cachedQuran.has(String.valueOf(surahNumber))) {
                JSONObject sObj = cachedQuran.getJSONObject(String.valueOf(surahNumber));
                JSONArray ayahsArr = sObj.getJSONArray("ayahs");
                for (int i = 0; i < ayahsArr.length(); i++) {
                    JSONObject aObj = ayahsArr.getJSONObject(i);
                    int numberInSurah = aObj.getInt("numberInSurah");
                    int number = aObj.getInt("number");
                    String arabic = aObj.getString("arabic");
                    String indo = aObj.getString("indo");
                    if (surahNumber != 1 && surahNumber != 9 && numberInSurah == 1) {
                        arabic = stripBismillahPrefix(arabic);
                    }
                    list.add(new AyahModel(numberInSurah, number, arabic, indo));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static String stripBismillahPrefix(String text) {
        if (text == null) return "";
        String trimmed = text.replace("\ufeff", "").trim();
        String[] words = trimmed.split("\\s+");
        if (words.length >= 5 && words[0].startsWith("ب") && words[1].contains("ل") && words[2].contains("ح") && words[3].contains("ح")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 4; i < words.length; i++) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(words[i]);
            }
            return sb.toString().trim();
        }
        return trimmed;
    }

    public static String getTafsirIbnuKatsir(Context context, int surah, int ayah) {
        // 1. Check internal storage first (downloaded files)
        try {
            java.io.File file = new java.io.File(context.getFilesDir(), "tafsir_" + surah + ".json");
            if (file.exists()) {
                byte[] bytes = new byte[(int) file.length()];
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    fis.read(bytes);
                }
                String jsonStr = new String(bytes, StandardCharsets.UTF_8);
                JSONObject obj = new JSONObject(jsonStr);
                if (obj.has(String.valueOf(ayah))) {
                    return obj.getString(String.valueOf(ayah));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Check bundled assets
        try {
            String filename = "ibnukatsir/" + surah + ".json";
            String jsonStr = readAssetString(context, filename);
            if (jsonStr != null) {
                JSONObject obj = new JSONObject(jsonStr);
                if (obj.has(String.valueOf(ayah))) {
                    return obj.getString(String.valueOf(ayah));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null; // Signals that tafsir is not yet downloaded
    }

    public static boolean saveDownloadedTafsir(Context context, int surah, String jsonContent) {
        try {
            java.io.File file = new java.io.File(context.getFilesDir(), "tafsir_" + surah + ".json");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getHadisAyat(Context context, int surah, int ayah) {
        try {
            if (cachedHadis == null) {
                String jsonStr = readAssetString(context, "hadis_ayat.json");
                if (jsonStr != null) {
                    cachedHadis = new JSONObject(jsonStr);
                }
            }
            if (cachedHadis != null) {
                JSONObject byAyat = cachedHadis.optJSONObject("hadis_by_ayat");
                if (byAyat != null) {
                    String key = surah + ":" + ayah;
                    if (byAyat.has(key)) {
                        JSONArray arr = byAyat.getJSONArray(key);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            sb.append("• ").append(item.optString("arabic")).append("\n\n");
                            sb.append("\"").append(item.optString("indo")).append("\"\n");
                            sb.append("— ").append(item.optString("source")).append(" (").append(item.optString("narrator")).append(")\n\n");
                        }
                        return sb.toString().trim();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Hadis terkait dan asbabun nuzul untuk ayat ini menguatkan makna ketakwaan, ibadah ikhlas, dan penegasan sunnah Rasulullah ﷺ.";
    }

    public static List<WordModel> getWordByWord(Context context, int surah, int ayah) {
        // 1. Check local file storage (downloaded cache)
        try {
            java.io.File file = new java.io.File(context.getFilesDir(), "wbw_" + surah + "_" + ayah + ".json");
            if (file.exists()) {
                byte[] bytes = new byte[(int) file.length()];
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                    fis.read(bytes);
                }
                String content = new String(bytes, StandardCharsets.UTF_8);
                List<WordModel> parsed = parseWordJson(content, ayah);
                if (parsed != null && !parsed.isEmpty()) return parsed;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Check bundled assets (assets/wbw/{surah}.json)
        try {
            String filename = "wbw/" + surah + ".json";
            String jsonStr = readAssetString(context, filename);
            if (jsonStr != null) {
                List<WordModel> parsed = parseWordJson(jsonStr, ayah);
                if (parsed != null && !parsed.isEmpty()) return parsed;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean saveWordByWord(Context context, int surah, int ayah, String jsonContent) {
        try {
            java.io.File file = new java.io.File(context.getFilesDir(), "wbw_" + surah + "_" + ayah + ".json");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                fos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<WordModel> parseWordJson(String jsonStr, int ayahNumber) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        List<WordModel> list = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            // Case 1: Bundled format { "1": [ { "pos": 1, "ar": "...", "tl": "...", "tr": "..." } ] }
            String ayahKey = String.valueOf(ayahNumber);
            if (root.has(ayahKey)) {
                JSONArray arr = root.getJSONArray(ayahKey);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int pos = obj.optInt("pos", i + 1);
                    String ar = obj.optString("ar", "");
                    String tl = obj.optString("tl", "");
                    String tr = obj.optString("tr", "");
                    list.add(new WordModel(pos, ar, tl, tr));
                }
                return list;
            }

            // Case 2: Quran.com API response { "verse": { "words": [ ... ] } }
            if (root.has("verse")) {
                JSONObject verseObj = root.getJSONObject("verse");
                if (verseObj.has("words")) {
                    JSONArray wordsArr = verseObj.getJSONArray("words");
                    for (int i = 0; i < wordsArr.length(); i++) {
                        JSONObject w = wordsArr.getJSONObject(i);
                        String charType = w.optString("char_type_name", "word");
                        if ("end".equalsIgnoreCase(charType)) continue;

                        int pos = w.optInt("position", i + 1);
                        String ar = w.optString("text_uthmani", w.optString("text", ""));
                        String tl = "";
                        if (w.has("transliteration")) {
                            tl = w.getJSONObject("transliteration").optString("text", "");
                        }
                        String tr = "";
                        if (w.has("translation")) {
                            tr = w.getJSONObject("translation").optString("text", "");
                        }
                        if (!ar.isEmpty()) {
                            list.add(new WordModel(pos, ar, tl, tr));
                        }
                    }
                    return list;
                }
            }

            // Case 3: Direct words array
            if (root.has("words")) {
                JSONArray wordsArr = root.getJSONArray("words");
                for (int i = 0; i < wordsArr.length(); i++) {
                    JSONObject w = wordsArr.getJSONObject(i);
                    int pos = w.optInt("pos", w.optInt("position", i + 1));
                    String ar = w.optString("ar", w.optString("text_uthmani", ""));
                    String tl = w.optString("tl", "");
                    String tr = w.optString("tr", "");
                    list.add(new WordModel(pos, ar, tl, tr));
                }
                return list;
            }
        } catch (Exception e) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject w = arr.getJSONObject(i);
                    int pos = w.optInt("pos", w.optInt("position", i + 1));
                    String ar = w.optString("ar", w.optString("text_uthmani", ""));
                    String tl = w.optString("tl", "");
                    String tr = w.optString("tr", "");
                    list.add(new WordModel(pos, ar, tl, tr));
                }
                return list;
            } catch (Exception ex) {
                e.printStackTrace();
            }
        }
        return list.isEmpty() ? null : list;
    }
}

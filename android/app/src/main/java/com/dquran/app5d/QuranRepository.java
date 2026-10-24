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

    public static SurahModel getSurahByNumber(Context context, int surahNumber) {
        List<SurahModel> list = getSurahs(context);
        for (SurahModel s : list) {
            if (s.getNumber() == surahNumber) return s;
        }
        return null;
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

    private static final java.util.Map<Integer, java.util.Map<Integer, List<WordModel>>> wbwSurahCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static List<WordModel> getWordByWord(Context context, int surah, int ayah) {
        // 0. Check in-memory cache
        java.util.Map<Integer, List<WordModel>> surahMap = wbwSurahCache.get(surah);
        if (surahMap != null && surahMap.containsKey(ayah)) {
            return surahMap.get(ayah);
        }

        // 1. Check bundled assets (assets/wbw/{surah}.json) & populate whole surah
        try {
            String filename = "wbw/" + surah + ".json";
            String jsonStr = readAssetString(context, filename);
            if (jsonStr != null) {
                java.util.Map<Integer, List<WordModel>> parsedMap = parseWholeSurahWbw(jsonStr);
                if (parsedMap != null && !parsedMap.isEmpty()) {
                    wbwSurahCache.put(surah, parsedMap);
                    if (parsedMap.containsKey(ayah)) {
                        return parsedMap.get(ayah);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Fallback check local file storage (downloaded cache per ayah)
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

        return null;
    }

    public static java.util.Map<Integer, List<WordModel>> parseWholeSurahWbw(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;
        java.util.Map<Integer, List<WordModel>> map = new java.util.HashMap<>();
        try {
            JSONObject root = new JSONObject(jsonStr);
            java.util.Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    int ayahNum = Integer.parseInt(key);
                    JSONArray arr = root.getJSONArray(key);
                    List<WordModel> list = new ArrayList<>(arr.length());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        int pos = obj.optInt("pos", i + 1);
                        String ar = obj.optString("ar", "");
                        String tl = obj.optString("tl", "");
                        String tr = obj.optString("tr", "");
                        list.add(new WordModel(pos, ar, tl, tr));
                    }
                    map.put(ayahNum, list);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
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

    private static JSONObject cachedPagesMeta = null;
    private static JSONArray cachedPagesArray = null;
    private static JSONArray cachedRukusArray = null;
    private static JSONArray cachedJuzsArray = null;

    private static List<QuranItem> cachedSurahItems = null;
    private static List<QuranItem> cachedJuzItems = null;
    private static List<QuranItem> cachedPageItems = null;
    private static List<QuranItem> cachedRevelationItems = null;

    public static final int[] REVELATION_ORDER = {
        96, 68, 73, 74, 1, 111, 81, 87, 92, 89, 93, 94, 103, 100, 108, 102, 107, 109, 105, 113,
        114, 112, 53, 80, 97, 91, 85, 95, 106, 101, 75, 104, 77, 50, 90, 86, 54, 38, 7, 72,
        36, 25, 35, 19, 20, 56, 26, 27, 28, 17, 10, 11, 12, 15, 6, 37, 31, 34, 39, 40,
        41, 42, 43, 44, 45, 46, 51, 88, 18, 16, 71, 14, 21, 23, 32, 52, 67, 69, 70, 78,
        79, 82, 84, 30, 29, 83, 2, 98, 64, 62, 8, 47, 3, 61, 57, 4, 65, 59, 33, 63,
        24, 58, 22, 48, 66, 60, 110, 49, 9, 5, 99, 55, 76, 13
    };

    private static synchronized void ensurePagesMeta(Context context) {
        if (cachedPagesMeta != null) return;
        try {
            String jsonStr = readAssetString(context, "pages_meta.json");
            if (jsonStr != null) {
                cachedPagesMeta = new JSONObject(jsonStr);
                cachedPagesArray = cachedPagesMeta.optJSONArray("pages");
                cachedRukusArray = cachedPagesMeta.optJSONArray("rukus");
                cachedJuzsArray = cachedPagesMeta.optJSONArray("juzs");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getPageForAyah(Context context, int surah, int ayah) {
        ensurePagesMeta(context);
        if (cachedPagesArray != null) {
            for (int i = cachedPagesArray.length() - 1; i >= 0; i--) {
                JSONObject p = cachedPagesArray.optJSONObject(i);
                if (p != null) {
                    int s = p.optInt("surah", 1);
                    int a = p.optInt("ayah", 1);
                    if (surah > s || (surah == s && ayah >= a)) {
                        return i + 1;
                    }
                }
            }
        }
        return 1;
    }

    public static int getJuzForAyah(Context context, int surah, int ayah) {
        ensurePagesMeta(context);
        if (cachedJuzsArray != null) {
            for (int i = cachedJuzsArray.length() - 1; i >= 0; i--) {
                JSONObject j = cachedJuzsArray.optJSONObject(i);
                if (j != null) {
                    int s = j.optInt("surah", 1);
                    int a = j.optInt("ayah", 1);
                    if (surah > s || (surah == s && ayah >= a)) {
                        return i + 1;
                    }
                }
            }
        }
        return 1;
    }

    public static int getRukuForAyah(Context context, int surah, int ayah) {
        ensurePagesMeta(context);
        if (cachedRukusArray != null) {
            for (int i = cachedRukusArray.length() - 1; i >= 0; i--) {
                JSONObject r = cachedRukusArray.optJSONObject(i);
                if (r != null) {
                    int s = r.optInt("surah", 1);
                    int a = r.optInt("ayah", 1);
                    if (surah > s || (surah == s && ayah >= a)) {
                        return i + 1;
                    }
                }
            }
        }
        return 1;
    }

    public static List<MushafPageModel> getMushafPagesForSurah(Context context, int surahNumber) {
        List<AyahModel> allAyahs = getAyahs(context, surahNumber);
        List<MushafPageModel> pages = new ArrayList<>();
        if (allAyahs == null || allAyahs.isEmpty()) return pages;

        int currentPageNum = -1;
        List<AyahModel> pageAyahs = new ArrayList<>();

        for (AyahModel a : allAyahs) {
            int pNum = getPageForAyah(context, surahNumber, a.getNumberInSurah());
            if (pNum != currentPageNum) {
                if (currentPageNum != -1 && !pageAyahs.isEmpty()) {
                    int ruku = getRukuForAyah(context, surahNumber, pageAyahs.get(0).getNumberInSurah());
                    boolean isStart = (pageAyahs.get(0).getNumberInSurah() == 1);
                    pages.add(new MushafPageModel(currentPageNum, ruku, 1, new ArrayList<>(pageAyahs), isStart));
                    pageAyahs.clear();
                }
                currentPageNum = pNum;
            }
            pageAyahs.add(a);
        }

        if (currentPageNum != -1 && !pageAyahs.isEmpty()) {
            int ruku = getRukuForAyah(context, surahNumber, pageAyahs.get(0).getNumberInSurah());
            boolean isStart = (pageAyahs.get(0).getNumberInSurah() == 1);
            pages.add(new MushafPageModel(currentPageNum, ruku, 1, new ArrayList<>(pageAyahs), isStart));
        }

        return pages;
    }

    public static List<QuranItem> getItemsBySurah(Context context) {
        if (cachedSurahItems != null) return cachedSurahItems;
        List<SurahModel> surahs = getSurahs(context);
        List<QuranItem> items = new ArrayList<>(surahs.size());
        for (SurahModel s : surahs) {
            String type = "Meccan".equalsIgnoreCase(s.getRevelationType()) ? "Makkiyah" :
                          ("Medinan".equalsIgnoreCase(s.getRevelationType()) ? "Madaniyah" : s.getRevelationType());
            items.add(new QuranItem(
                    QuranItem.Mode.SURAH,
                    s.getNumber(),
                    s.getEnglishName(),
                    s.getIndonesianName() + " · " + s.getNumberOfAyahs() + " Ayat",
                    type,
                    cleanArabicSurahName(s.getName()),
                    s.getNumber(),
                    1,
                    s
            ));
        }
        cachedSurahItems = items;
        return items;
    }

    public static List<QuranItem> getItemsByJuz(Context context) {
        if (cachedJuzItems != null) return cachedJuzItems;
        ensurePagesMeta(context);
        List<QuranItem> items = new ArrayList<>();
        if (cachedJuzsArray != null) {
            for (int i = 0; i < cachedJuzsArray.length(); i++) {
                JSONObject j = cachedJuzsArray.optJSONObject(i);
                if (j != null) {
                    int juzNum = i + 1;
                    int sNum = j.optInt("surah", 1);
                    int aNum = j.optInt("ayah", 1);
                    SurahModel sModel = getSurahByNumber(context, sNum);
                    String sName = sModel != null ? sModel.getEnglishName() : ("Surah " + sNum);
                    int pageNum = getPageForAyah(context, sNum, aNum);
                    String arab = sModel != null ? cleanArabicSurahName(sModel.getName()) : "";

                    items.add(new QuranItem(
                            QuranItem.Mode.JUZ,
                            juzNum,
                            "Juz " + juzNum,
                            sName + " : " + aNum + " · Mulai Hal. " + pageNum,
                            "Juz " + juzNum,
                            arab,
                            sNum,
                            aNum,
                            sModel
                    ));
                }
            }
        }
        cachedJuzItems = items;
        return items;
    }

    public static List<QuranItem> getItemsByPage(Context context) {
        if (cachedPageItems != null) return cachedPageItems;
        ensurePagesMeta(context);
        List<QuranItem> items = new ArrayList<>();
        if (cachedPagesArray != null) {
            for (int i = 0; i < cachedPagesArray.length(); i++) {
                JSONObject p = cachedPagesArray.optJSONObject(i);
                if (p != null) {
                    int pageNum = i + 1;
                    int sNum = p.optInt("surah", 1);
                    int aNum = p.optInt("ayah", 1);
                    SurahModel sModel = getSurahByNumber(context, sNum);
                    String sName = sModel != null ? sModel.getEnglishName() : ("Surah " + sNum);
                    int juzNum = getJuzForAyah(context, sNum, aNum);
                    String arab = sModel != null ? cleanArabicSurahName(sModel.getName()) : "";

                    items.add(new QuranItem(
                            QuranItem.Mode.PAGE,
                            pageNum,
                            "Halaman " + pageNum,
                            sName + " : " + aNum + " · Juz " + juzNum,
                            "Juz " + juzNum,
                            arab,
                            sNum,
                            aNum,
                            sModel
                    ));
                }
            }
        }
        cachedPageItems = items;
        return items;
    }

    public static List<QuranItem> getItemsByRevelation(Context context) {
        if (cachedRevelationItems != null) return cachedRevelationItems;
        List<QuranItem> items = new ArrayList<>(REVELATION_ORDER.length);
        for (int i = 0; i < REVELATION_ORDER.length; i++) {
            int wahyuNum = i + 1;
            int sNum = REVELATION_ORDER[i];
            SurahModel s = getSurahByNumber(context, sNum);
            if (s != null) {
                String type = "Meccan".equalsIgnoreCase(s.getRevelationType()) ? "Makkiyah" :
                              ("Medinan".equalsIgnoreCase(s.getRevelationType()) ? "Madaniyah" : s.getRevelationType());
                items.add(new QuranItem(
                        QuranItem.Mode.TURUN,
                        wahyuNum,
                        s.getEnglishName(),
                        "Surah ke-" + sNum + " · " + s.getNumberOfAyahs() + " Ayat (" + s.getIndonesianName() + ")",
                        "Wahyu ke-" + wahyuNum + " · " + type,
                        cleanArabicSurahName(s.getName()),
                        sNum,
                        1,
                        s
                ));
            }
        }
        cachedRevelationItems = items;
        return items;
    }
}

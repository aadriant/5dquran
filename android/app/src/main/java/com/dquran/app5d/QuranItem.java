package com.dquran.app5d;

public class QuranItem {

    public enum Mode {
        SURAH,
        JUZ,
        PAGE,
        TURUN
    }

    private final Mode mode;
    private final int number;         // Nomor badge (Surah #, Juz #, Halaman #, atau Wahyu #)
    private final String title;       // Judul utama (misal: "Al-Fatihah", "Juz 1", "Halaman 1")
    private final String subtitle;    // Sub judul / info ayat (misal: "Pembukaan · 7 Ayat", "Al-Fatihah : 1 · Mulai Hal. 1", "Al-Fatihah : 1 · Juz 1")
    private final String tag;         // Chip tag (misal: "Makkiyah", "Juz 1", "Wahyu ke-1 · Makkiyah")
    private final String arabic;      // Teks / nama kaligrafi Arab
    private final int targetSurah;    // Nomor surat tujuan navigasi
    private final int targetAyah;     // Nomor ayat tujuan navigasi
    private final SurahModel surahRef;// Referensi objek SurahModel asli (jika ada)

    public QuranItem(Mode mode, int number, String title, String subtitle, String tag, String arabic, int targetSurah, int targetAyah, SurahModel surahRef) {
        this.mode = mode;
        this.number = number;
        this.title = title;
        this.subtitle = subtitle;
        this.tag = tag;
        this.arabic = arabic;
        this.targetSurah = targetSurah;
        this.targetAyah = targetAyah;
        this.surahRef = surahRef;
    }

    public Mode getMode() {
        return mode;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getTag() {
        return tag;
    }

    public String getArabic() {
        return arabic;
    }

    public int getTargetSurah() {
        return targetSurah;
    }

    public int getTargetAyah() {
        return targetAyah;
    }

    public SurahModel getSurahRef() {
        return surahRef;
    }
}

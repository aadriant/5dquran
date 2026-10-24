package com.dquran.app5d;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MushafPdfGenerator {

    public interface OnPdfGenerateListener {
        void onProgress(String status);
        void onSuccess(File pdfFile, int totalAyahs, int totalPages);
        void onEmpty(String message);
        void onError(Exception e);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Standard A4 dimensions in PostScript points (72 dpi)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;

    private static final int MARGIN_X = 42;
    private static final int MARGIN_TOP = 48;
    private static final int MARGIN_BOTTOM = 46;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X; // 511 pt
    private static final int CONTENT_BOTTOM = PAGE_HEIGHT - MARGIN_BOTTOM; // 796 pt

    public static class AyahEntry {
        public final int surah;
        public final int ayah;
        public final String text;

        public AyahEntry(int surah, int ayah, String text) {
            this.surah = surah;
            this.ayah = ayah;
            this.text = text;
        }
    }

    /**
     * Generate PDF for Tulis (ayat yang telah diketik user).
     */
    public static void generateTulisanPdf(Context context, OnPdfGenerateListener listener) {
        generatePdfAsync(context, false, listener);
    }

    /**
     * Generate PDF for Hafalan (ayat yang telah dihafal & direkam user).
     */
    public static void generateHafalanPdf(Context context, OnPdfGenerateListener listener) {
        generatePdfAsync(context, true, listener);
    }

    private static void generatePdfAsync(Context context, boolean isHafalan, OnPdfGenerateListener listener) {
        executor.execute(() -> {
            try {
                notifyProgress(listener, "Mengumpulkan data ayat " + (isHafalan ? "hafalan" : "tulisan") + "...");
                List<AyahEntry> entries = collectAyahEntries(context, isHafalan);

                if (entries.isEmpty()) {
                    mainHandler.post(() -> {
                        String msg = isHafalan
                                ? "Belum ada riwayat hafalan atau rekaman suara yang tersimpan.\nSilakan setor hafalan terlebih dahulu di menu Dihafal."
                                : "Belum ada ayat yang diketik atau disimpan.\nSilakan ketik ayat Al-Quran terlebih dahulu di menu Ditulis.";
                        listener.onEmpty(msg);
                    });
                    return;
                }

                notifyProgress(listener, "Menyusun tata letak Mushaf (" + entries.size() + " ayat)...");

                File pdfFile = buildPdfDocument(context, entries, isHafalan, listener);

                mainHandler.post(() -> {
                    // Estimate pages or get from result
                    listener.onSuccess(pdfFile, entries.size(), 1);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> listener.onError(e));
            }
        });
    }

    private static void notifyProgress(OnPdfGenerateListener listener, String status) {
        if (listener != null) {
            mainHandler.post(() -> listener.onProgress(status));
        }
    }

    /**
     * Kumpulkan seluruh ayat yang relevan (urut surah ASC, ayah ASC).
     */
    private static List<AyahEntry> collectAyahEntries(Context context, boolean isHafalan) {
        Map<String, AyahEntry> map = new HashMap<>();

        if (isHafalan) {
            // 1. Dari SharedPreferences KEY_PROGRESS_HAFALAN
            SharedPreferences prefs = context.getSharedPreferences("5dquran_prefs", Context.MODE_PRIVATE);
            Set<String> set = prefs.getStringSet(PreferenceManager.KEY_PROGRESS_HAFALAN, new HashSet<>());
            for (String key : set) {
                String[] parts = key.split("#");
                if (parts.length == 2) {
                    try {
                        int s = Integer.parseInt(parts[0]);
                        int a = Integer.parseInt(parts[1]);
                        map.put(s + "#" + a, new AyahEntry(s, a, ""));
                    } catch (Exception ignored) {}
                }
            }

            // 2. Dari folder rekaman hafalan_audio
            File audioDir = new File(context.getCacheDir(), "hafalan_audio");
            if (audioDir.exists() && audioDir.isDirectory()) {
                File[] files = audioDir.listFiles();
                if (files != null) {
                    Pattern p = Pattern.compile("^recitation_s(\\d+)_a(\\d+)\\.m4a$");
                    for (File f : files) {
                        if (f.isFile() && f.length() > 0) {
                            Matcher m = p.matcher(f.getName());
                            if (m.matches()) {
                                try {
                                    int s = Integer.parseInt(m.group(1));
                                    int a = Integer.parseInt(m.group(2));
                                    map.put(s + "#" + a, new AyahEntry(s, a, ""));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }

            // Lengkapi teks ayat dari QuranRepository
            List<AyahEntry> sorted = new ArrayList<>(map.values());
            Collections.sort(sorted, (o1, o2) -> {
                if (o1.surah != o2.surah) return Integer.compare(o1.surah, o2.surah);
                return Integer.compare(o1.ayah, o2.ayah);
            });

            // Isi teks resmi mushaf
            Map<Integer, List<AyahModel>> cacheAyahs = new HashMap<>();
            List<AyahEntry> result = new ArrayList<>();
            for (AyahEntry e : sorted) {
                if (!cacheAyahs.containsKey(e.surah)) {
                    cacheAyahs.put(e.surah, QuranRepository.getAyahs(context, e.surah));
                }
                List<AyahModel> list = cacheAyahs.get(e.surah);
                String arabic = "";
                if (list != null && e.ayah >= 1 && e.ayah <= list.size()) {
                    arabic = list.get(e.ayah - 1).getArabic();
                }
                result.add(new AyahEntry(e.surah, e.ayah, arabic));
            }
            return result;

        } else {
            // TULISAN:
            SharedPreferences prefs = context.getSharedPreferences("5dquran_prefs", Context.MODE_PRIVATE);

            // 1. Kumpulkan semua draft ketikan dari "tulis_draft_s{s}_a{a}"
            Map<String, ?> allEntries = prefs.getAll();
            Pattern draftPattern = Pattern.compile("^tulis_draft_s(\\d+)_a(\\d+)$");
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                Matcher m = draftPattern.matcher(entry.getKey());
                if (m.matches()) {
                    Object val = entry.getValue();
                    if (val instanceof String) {
                        String userText = ((String) val).trim();
                        if (!userText.isEmpty()) {
                            try {
                                int s = Integer.parseInt(m.group(1));
                                int a = Integer.parseInt(m.group(2));
                                map.put(s + "#" + a, new AyahEntry(s, a, userText));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            // 2. Kumpulkan juga dari progress tulisan
            Set<String> set = prefs.getStringSet(PreferenceManager.KEY_PROGRESS_TULISAN, new HashSet<>());
            for (String key : set) {
                String[] parts = key.split("#");
                if (parts.length == 2) {
                    try {
                        int s = Integer.parseInt(parts[0]);
                        int a = Integer.parseInt(parts[1]);
                        if (!map.containsKey(s + "#" + a)) {
                            map.put(s + "#" + a, new AyahEntry(s, a, ""));
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Urutkan
            List<AyahEntry> sorted = new ArrayList<>(map.values());
            Collections.sort(sorted, (o1, o2) -> {
                if (o1.surah != o2.surah) return Integer.compare(o1.surah, o2.surah);
                return Integer.compare(o1.ayah, o2.ayah);
            });

            // Jika teks masih kosong tapi tercatat di progress, isi dengan teks acuan mushaf
            Map<Integer, List<AyahModel>> cacheAyahs = new HashMap<>();
            List<AyahEntry> result = new ArrayList<>();
            for (AyahEntry e : sorted) {
                String text = e.text;
                if (text == null || text.trim().isEmpty()) {
                    if (!cacheAyahs.containsKey(e.surah)) {
                        cacheAyahs.put(e.surah, QuranRepository.getAyahs(context, e.surah));
                    }
                    List<AyahModel> list = cacheAyahs.get(e.surah);
                    if (list != null && e.ayah >= 1 && e.ayah <= list.size()) {
                        text = list.get(e.ayah - 1).getArabic();
                    }
                }
                result.add(new AyahEntry(e.surah, e.ayah, text != null ? text.trim() : ""));
            }
            return result;
        }
    }

    /**
     * Konversi angka ke digit Arab Timur (١, ٢, ٣...)
     */
    public static String toArabicDigits(int number) {
        char[] arabicDigits = {'\u0660', '\u0661', '\u0662', '\u0663', '\u0664', '\u0665', '\u0666', '\u0667', '\u0668', '\u0669'};
        String s = String.valueOf(number);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            int d = s.charAt(i) - '0';
            if (d >= 0 && d <= 9) {
                sb.append(arabicDigits[d]);
            } else {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    /**
     * Render mushaf dokumen PDF sesuai gambar referensi.
     */
    private static File buildPdfDocument(Context context, List<AyahEntry> entries, boolean isHafalan, OnPdfGenerateListener listener) throws Exception {
        PdfDocument pdfDocument = new PdfDocument();

        // Typefaces
        Typeface quranTypeface = PreferenceManager.getMushafTypeface(context);
        Typeface serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD);
        Typeface sansRegular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
        Typeface sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);

        // Paints
        TextPaint arabicPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        arabicPaint.setTypeface(quranTypeface);
        arabicPaint.setTextSize(18f);
        arabicPaint.setColor(0xFF1E293B);

        TextPaint surahTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        surahTitlePaint.setTypeface(serifBold);
        surahTitlePaint.setTextSize(16f);
        surahTitlePaint.setColor(0xFF1E293B);
        surahTitlePaint.setTextAlign(Paint.Align.CENTER);

        TextPaint surahSubPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        surahSubPaint.setTypeface(sansRegular);
        surahSubPaint.setTextSize(8.5f);
        surahSubPaint.setColor(0xFF64748B);
        surahSubPaint.setTextAlign(Paint.Align.CENTER);

        TextPaint bismillahPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bismillahPaint.setTypeface(quranTypeface);
        bismillahPaint.setTextSize(17f);
        bismillahPaint.setColor(0xFF0F172A);
        bismillahPaint.setTextAlign(Paint.Align.CENTER);

        TextPaint headerFooterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        headerFooterPaint.setTypeface(sansRegular);
        headerFooterPaint.setTextSize(8.5f);
        headerFooterPaint.setColor(0xFF64748B);

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(0xFFFAF8F2); // Warm Ivory Mushaf Paper

        Paint outerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerBorderPaint.setStyle(Paint.Style.STROKE);
        outerBorderPaint.setStrokeWidth(1.4f);
        outerBorderPaint.setColor(0xFF2E6F40); // Quranic Deep Emerald/Olive

        Paint innerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerBorderPaint.setStyle(Paint.Style.STROKE);
        innerBorderPaint.setStrokeWidth(0.8f);
        innerBorderPaint.setColor(0xFFD4AF37); // Classic Mushaf Gold

        Paint surahBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        surahBoxPaint.setColor(0xFFF1F8F1); // Soft Sage Green background

        Paint surahBoxBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        surahBoxBorderPaint.setStyle(Paint.Style.STROKE);
        surahBoxBorderPaint.setStrokeWidth(1.0f);
        surahBoxBorderPaint.setColor(0xFFA5D6A7);

        Paint numberCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numberCirclePaint.setColor(0xFFE8F5E9);

        Paint numberCircleBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numberCircleBorderPaint.setStyle(Paint.Style.STROKE);
        numberCircleBorderPaint.setStrokeWidth(1.2f);
        numberCircleBorderPaint.setColor(0xFF2E6F40);

        Paint numberTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        numberTextPaint.setTypeface(sansBold);
        numberTextPaint.setTextSize(10f);
        numberTextPaint.setColor(0xFF1B5E20);
        numberTextPaint.setTextAlign(Paint.Align.CENTER);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFFE2E8F0);
        linePaint.setStrokeWidth(0.8f);

        // Metadata user & timestamp
        String userName = PreferenceManager.getUserName(context);
        if (userName == null || userName.isEmpty()) userName = "Pengguna 5D Quran";
        String docTitle = isHafalan ? "Mushaf Hafalan Pribadi" : "Mushaf Tulis Pribadi";
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy HH:mm", new Locale("id", "ID"));
        String dateStr = sdf.format(new Date());

        // Kelompokkan entries per surah
        Map<Integer, List<AyahEntry>> surahGroups = new HashMap<>();
        List<Integer> surahOrder = new ArrayList<>();
        for (AyahEntry e : entries) {
            if (!surahGroups.containsKey(e.surah)) {
                surahGroups.put(e.surah, new ArrayList<>());
                surahOrder.add(e.surah);
            }
            surahGroups.get(e.surah).add(e);
        }

        // Mulai pembuatan halaman
        int pageNumber = 1;
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
        PdfDocument.Page currentPage = pdfDocument.startPage(pageInfo);
        Canvas canvas = currentPage.getCanvas();

        // Gambar dekorasi halaman pertama
        drawPageDecorations(canvas, pageNumber, docTitle, userName, dateStr, bgPaint, outerBorderPaint, innerBorderPaint, headerFooterPaint, linePaint);

        int currentY = MARGIN_TOP + 18; // Mulai di bawah header atas

        // List surahs info dari QuranRepository
        List<SurahModel> allSurahs = QuranRepository.getSurahs(context);
        Map<Integer, SurahModel> surahInfoMap = new HashMap<>();
        for (SurahModel sm : allSurahs) {
            surahInfoMap.put(sm.getNumber(), sm);
        }

        for (int surahNum : surahOrder) {
            List<AyahEntry> surahAyahs = surahGroups.get(surahNum);
            SurahModel surahInfo = surahInfoMap.get(surahNum);
            String latinName = surahInfo != null ? surahInfo.getEnglishName() : "Surah " + surahNum;
            String indoTrans = surahInfo != null ? surahInfo.getIndonesianName() : "";
            int totalVersesInSurah = surahInfo != null ? surahInfo.getNumberOfAyahs() : surahAyahs.size();
            String revType = surahInfo != null ? ("Meccan".equalsIgnoreCase(surahInfo.getRevelationType()) ? "Makkiyah" : "Madaniyah") : "";

            // Tentukan tinggi Surah Banner Box
            int bannerHeight = 62;
            int bismillahHeight = (surahNum != 1 && surahNum != 9) ? 36 : 8;
            int neededHeaderHeight = bannerHeight + bismillahHeight + 20;

            // Jika sisa halaman tidak cukup untuk header surah + minimal 2 baris ayat
            if (currentY + neededHeaderHeight > CONTENT_BOTTOM - 60) {
                pdfDocument.finishPage(currentPage);
                pageNumber++;
                pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                currentPage = pdfDocument.startPage(pageInfo);
                canvas = currentPage.getCanvas();
                drawPageDecorations(canvas, pageNumber, docTitle, userName, dateStr, bgPaint, outerBorderPaint, innerBorderPaint, headerFooterPaint, linePaint);
                currentY = MARGIN_TOP + 18;
            }

            // 1. Gambar Surah Header Banner (Persis seperti gambar referensi)
            RectF boxRect = new RectF(MARGIN_X + 16, currentY, PAGE_WIDTH - MARGIN_X - 16, currentY + bannerHeight);
            canvas.drawRoundRect(boxRect, 10f, 10f, surahBoxPaint);
            canvas.drawRoundRect(boxRect, 10f, 10f, surahBoxBorderPaint);

            // Lingkaran nomor surat di tengah atas
            float circleCenterX = PAGE_WIDTH / 2f;
            float circleCenterY = currentY + 14f;
            canvas.drawCircle(circleCenterX, circleCenterY, 11f, numberCirclePaint);
            canvas.drawCircle(circleCenterX, circleCenterY, 11f, numberCircleBorderPaint);
            canvas.drawText(String.valueOf(surahNum), circleCenterX, circleCenterY + 3.5f, numberTextPaint);

            // Nama Latin Surah
            canvas.drawText(latinName, circleCenterX, currentY + 39f, surahTitlePaint);

            // Subtitle arti / terjemahan & jumlah ayat
            String subInfo = (indoTrans.isEmpty() ? "" : indoTrans + " • ") + totalVersesInSurah + " Ayat" + (revType.isEmpty() ? "" : " • " + revType);
            canvas.drawText(subInfo, circleCenterX, currentY + 52f, surahSubPaint);

            currentY += bannerHeight + 10;

            // 2. Gambar Bismillah Banner (Kecuali Surah 9 & Surah 1)
            if (surahNum != 1 && surahNum != 9) {
                // Gambar ornamen sayap di samping Bismillah
                float bismillahY = currentY + 16;
                canvas.drawText("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", circleCenterX, bismillahY, bismillahPaint);

                // Garis ornamen di kanan dan kiri
                float lineWidth = 60f;
                canvas.drawLine(circleCenterX - 150f, bismillahY - 5f, circleCenterX - 150f + lineWidth, bismillahY - 5f, linePaint);
                canvas.drawLine(circleCenterX + 150f - lineWidth, bismillahY - 5f, circleCenterX + 150f, bismillahY - 5f, linePaint);

                currentY += bismillahHeight;
            }

            // 3. Susun Teks Ayat Mengalir (Continuous Quranic Flow)
            StringBuilder surahTextBuilder = new StringBuilder();
            for (int i = 0; i < surahAyahs.size(); i++) {
                AyahEntry ae = surahAyahs.get(i);
                String verseText = ae.text;
                if (verseText == null || verseText.trim().isEmpty()) {
                    continue;
                }
                // Strip bismillah jika ada di awal ayat 1 (karena sudah digambar di banner)
                if (surahNum != 1 && surahNum != 9 && ae.ayah == 1) {
                    verseText = QuranRepository.stripBismillahPrefix(verseText);
                }

                if (surahTextBuilder.length() > 0) {
                    surahTextBuilder.append(" ");
                }
                surahTextBuilder.append(verseText.trim());

                // Ornamen tanda nomor ayat mushaf
                surahTextBuilder.append(" \uFD3F").append(toArabicDigits(ae.ayah)).append("\uFD3E");
            }

            String fullSurahText = surahTextBuilder.toString().trim();
            if (fullSurahText.isEmpty()) {
                currentY += 14;
                continue;
            }

            // Buat StaticLayout untuk teks Arab RTL
            StaticLayout staticLayout = createRtlStaticLayout(fullSurahText, arabicPaint, CONTENT_WIDTH);

            // Render StaticLayout dengan pagination baris per baris
            int lineCount = staticLayout.getLineCount();
            int currentLine = 0;

            while (currentLine < lineCount) {
                int availableHeight = CONTENT_BOTTOM - currentY;
                int singleLineHeight = staticLayout.getLineBottom(currentLine) - staticLayout.getLineTop(currentLine);

                // Jika halaman tidak cukup untuk 1 baris
                if (availableHeight < singleLineHeight + 8) {
                    pdfDocument.finishPage(currentPage);
                    pageNumber++;
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create();
                    currentPage = pdfDocument.startPage(pageInfo);
                    canvas = currentPage.getCanvas();
                    drawPageDecorations(canvas, pageNumber, docTitle, userName, dateStr, bgPaint, outerBorderPaint, innerBorderPaint, headerFooterPaint, linePaint);
                    currentY = MARGIN_TOP + 18;
                    availableHeight = CONTENT_BOTTOM - currentY;
                }

                // Cari berapa baris yang muat di sisa halaman ini
                int lineEnd = currentLine;
                while (lineEnd < lineCount) {
                    int chunkH = staticLayout.getLineBottom(lineEnd) - staticLayout.getLineTop(currentLine);
                    if (chunkH > availableHeight) {
                        break;
                    }
                    lineEnd++;
                }

                if (lineEnd == currentLine) {
                    lineEnd = currentLine + 1; // Minimal render 1 baris
                }

                int topOffset = staticLayout.getLineTop(currentLine);
                int bottomOffset = staticLayout.getLineBottom(lineEnd - 1);
                int renderedHeight = bottomOffset - topOffset;

                canvas.save();
                // Batasi area render ke canvas saat ini
                canvas.clipRect(MARGIN_X, currentY, PAGE_WIDTH - MARGIN_X, currentY + renderedHeight);
                canvas.translate(MARGIN_X, currentY - topOffset);
                staticLayout.draw(canvas);
                canvas.restore();

                currentY += renderedHeight + 10;
                currentLine = lineEnd;
            }

            // Spasi pemisah antar surah
            currentY += 20;
        }

        // Tutup halaman terakhir
        pdfDocument.finishPage(currentPage);

        // Simpan ke file Dokumen
        File docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (docsDir == null) {
            docsDir = context.getFilesDir();
        }
        File outFolder = new File(docsDir, "5DQuran");
        if (!outFolder.exists()) outFolder.mkdirs();

        String filePrefix = isHafalan ? "Mushaf_Hafalan_" : "Mushaf_Tulis_";
        String cleanName = userName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String fileName = filePrefix + cleanName + "_" + System.currentTimeMillis() + ".pdf";
        File finalFile = new File(outFolder, fileName);

        FileOutputStream fos = new FileOutputStream(finalFile);
        pdfDocument.writeTo(fos);
        fos.flush();
        fos.close();
        pdfDocument.close();

        return finalFile;
    }

    /**
     * Gambar frame, border ganda, ornamen sudut, header dan footer halaman mushaf.
     */
    private static void drawPageDecorations(Canvas canvas, int pageNumber, String title, String userName,
                                            String dateStr, Paint bgPaint, Paint outerBorderPaint,
                                            Paint innerBorderPaint, Paint headerFooterPaint, Paint linePaint) {
        // 1. Background kertas mushaf krem
        canvas.drawColor(0xFFFAF8F2);

        // 2. Outer Border ganda khas Mushaf Al-Quran
        RectF outerRect = new RectF(MARGIN_X - 14, MARGIN_TOP - 22, PAGE_WIDTH - MARGIN_X + 14, PAGE_HEIGHT - MARGIN_BOTTOM + 22);
        canvas.drawRoundRect(outerRect, 10f, 10f, outerBorderPaint);

        // 3. Inner Border tipis emas
        RectF innerRect = new RectF(MARGIN_X - 10, MARGIN_TOP - 18, PAGE_WIDTH - MARGIN_X + 10, PAGE_HEIGHT - MARGIN_BOTTOM + 18);
        canvas.drawRoundRect(innerRect, 8f, 8f, innerBorderPaint);

        // Ornamen 4 sudut (decorative accents)
        drawCornerAccent(canvas, outerRect.left, outerRect.top, innerBorderPaint);
        drawCornerAccent(canvas, outerRect.right, outerRect.top, innerBorderPaint);
        drawCornerAccent(canvas, outerRect.left, outerRect.bottom, innerBorderPaint);
        drawCornerAccent(canvas, outerRect.right, outerRect.bottom, innerBorderPaint);

        // 4. Header Atas Mushaf (seperti referensi: Page X • Ruku / Dokumen)
        float headerY = MARGIN_TOP - 4;
        headerFooterPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("5D Quran", MARGIN_X, headerY, headerFooterPaint);

        headerFooterPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(title, PAGE_WIDTH / 2f, headerY, headerFooterPaint);

        headerFooterPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Halaman " + pageNumber, PAGE_WIDTH - MARGIN_X, headerY, headerFooterPaint);

        // Garis tipis pemisah header
        canvas.drawLine(MARGIN_X - 4, MARGIN_TOP + 4, PAGE_WIDTH - MARGIN_X + 4, MARGIN_TOP + 4, linePaint);

        // 5. Footer Bawah Mushaf
        float footerY = PAGE_HEIGHT - MARGIN_BOTTOM + 12;
        headerFooterPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Nama: " + userName, MARGIN_X, footerY, headerFooterPaint);

        headerFooterPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(dateStr, PAGE_WIDTH - MARGIN_X, footerY, headerFooterPaint);

        // Garis tipis pemisah footer
        canvas.drawLine(MARGIN_X - 4, PAGE_HEIGHT - MARGIN_BOTTOM, PAGE_WIDTH - MARGIN_X + 4, PAGE_HEIGHT - MARGIN_BOTTOM, linePaint);
    }

    private static void drawCornerAccent(Canvas canvas, float cx, float cy, Paint paint) {
        float size = 5f;
        canvas.drawRect(cx - size, cy - size, cx + size, cy + size, paint);
    }

    /**
     * Buat StaticLayout dengan orientasi RTL (Right-to-Left) untuk ligatur teks Al-Quran.
     */
    private static StaticLayout createRtlStaticLayout(CharSequence text, TextPaint paint, int width) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setLineSpacing(14f, 1.35f)
                    .setIncludePad(true)
                    .build();
        } else {
            return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.35f, 14f, true);
        }
    }
}

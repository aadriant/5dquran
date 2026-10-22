package com.dquran.app5d;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PelajariActivity extends AppCompatActivity {

    private int surahNumber = 1;
    private int ayahNumber = 1;
    private List<AyahModel> ayahs;
    private AyahModel currentAyah;
    private List<WordModel> currentWords = null;

    private TextView tvPelajariTitle, tvPelajariAyahBadge;
    private TextView tvPelajariArabic, tvPelajariIndo;
    private TextView tvTafsirIbnuKatsir, tvHadisAyat;

    // Kosakata per kata UI
    private ProgressBar pbKosakata;
    private TextView tvKosakataStatus;
    private ViewGroup layoutKosakataItems;
    private TextView btnReloadKosakata;

    // Download Tafsir UI
    private View layoutDownloadTafsir;
    private TextView btnDownloadTafsir;
    private ProgressBar pbDownloadTafsir;
    private TextView tvDownloadStatus;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDownloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pelajari);

        surahNumber = getIntent().getIntExtra("surah_number", 1);
        ayahNumber = getIntent().getIntExtra("ayah_number", 1);

        initViews();
        loadAyahs();
    }

    private void initViews() {
        tvPelajariTitle = findViewById(R.id.tv_pelajari_title);
        tvPelajariAyahBadge = findViewById(R.id.tv_pelajari_ayah_badge);
        tvPelajariArabic = findViewById(R.id.tv_pelajari_arabic);
        tvPelajariIndo = findViewById(R.id.tv_pelajari_indo);

        tvTafsirIbnuKatsir = findViewById(R.id.tv_tafsir_ibnukatsir);
        tvHadisAyat = findViewById(R.id.tv_hadis_ayat);

        // Kosakata UI
        pbKosakata = findViewById(R.id.pb_kosakata);
        tvKosakataStatus = findViewById(R.id.tv_kosakata_status);
        layoutKosakataItems = findViewById(R.id.layout_kosakata_items);
        btnReloadKosakata = findViewById(R.id.btn_reload_kosakata);

        btnReloadKosakata.setOnClickListener(v -> loadKosakataPerKata(surahNumber, ayahNumber, true));

        layoutDownloadTafsir = findViewById(R.id.layout_download_tafsir);
        btnDownloadTafsir = findViewById(R.id.btn_download_tafsir);
        pbDownloadTafsir = findViewById(R.id.pb_download_tafsir);
        tvDownloadStatus = findViewById(R.id.tv_download_status);

        findViewById(R.id.btn_back_pelajari).setOnClickListener(v -> finish());

        // Share Buttons
        View btnShareAll = findViewById(R.id.btn_share_all);
        if (btnShareAll != null) btnShareAll.setOnClickListener(v -> shareAllTadabur());

        View btnShareAyah = findViewById(R.id.btn_share_ayah);
        if (btnShareAyah != null) btnShareAyah.setOnClickListener(v -> shareAyah());

        View btnShareKosakata = findViewById(R.id.btn_share_kosakata);
        if (btnShareKosakata != null) btnShareKosakata.setOnClickListener(v -> shareKosakata());

        View btnShareTafsir = findViewById(R.id.btn_share_tafsir);
        if (btnShareTafsir != null) btnShareTafsir.setOnClickListener(v -> shareTafsir());

        findViewById(R.id.btn_pelajari_prev).setOnClickListener(v -> {
            if (ayahNumber > 1) {
                ayahNumber--;
                displayAyahAndStudy();
            }
        });

        findViewById(R.id.btn_pelajari_next).setOnClickListener(v -> {
            if (ayahNumber < ayahs.size()) {
                ayahNumber++;
                displayAyahAndStudy();
            }
        });

        btnDownloadTafsir.setOnClickListener(v -> {
            if (!isDownloading) {
                downloadTafsirOnline(surahNumber);
            }
        });
    }

    private void loadAyahs() {
        ayahs = QuranRepository.getAyahs(this, surahNumber);
        if (ayahs.isEmpty()) {
            Toast.makeText(this, "Data ayat tidak ditemukan", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (ayahNumber < 1 || ayahNumber > ayahs.size()) {
            ayahNumber = 1;
        }
        displayAyahAndStudy();
    }

    private void displayAyahAndStudy() {
        currentAyah = ayahs.get(ayahNumber - 1);
        String surahName = QuranRepository.getSurahName(this, surahNumber);
        tvPelajariTitle.setText("Tadabur: " + surahName);
        tvPelajariAyahBadge.setText(surahName + " : Ayat " + ayahNumber);
        tvPelajariArabic.setText(currentAyah.getArabic());
        tvPelajariArabic.setTypeface(PreferenceManager.getMushafTypeface(this));
        tvPelajariArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tvPelajariArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        tvPelajariArabic.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        tvPelajariArabic.setGravity(android.view.Gravity.RIGHT);
        tvPelajariIndo.setText(currentAyah.getIndo());

        // 0. Kosakata Per Kata (Cara baca & Arti)
        loadKosakataPerKata(surahNumber, ayahNumber, false);

        // 1. Tafsir Ibnu Katsir
        String ikHtml = QuranRepository.getTafsirIbnuKatsir(this, surahNumber, ayahNumber);
        if (ikHtml != null && !ikHtml.trim().isEmpty()) {
            tvTafsirIbnuKatsir.setText(HtmlCompat.fromHtml(ikHtml, HtmlCompat.FROM_HTML_MODE_LEGACY));
            layoutDownloadTafsir.setVisibility(View.GONE);
        } else {
            tvTafsirIbnuKatsir.setText("Tafsir lengkap untuk surat ini belum diunduh ke memori HP. Silakan ketuk tombol di bawah untuk mengunduh sekali dan dapat dibaca offline selamanya.");
            layoutDownloadTafsir.setVisibility(View.VISIBLE);
            btnDownloadTafsir.setVisibility(View.VISIBLE);
            pbDownloadTafsir.setVisibility(View.GONE);
            tvDownloadStatus.setVisibility(View.GONE);
        }

        // 2. Hadis Terkait & Asbabun Nuzul
        String hadisText = QuranRepository.getHadisAyat(this, surahNumber, ayahNumber);
        tvHadisAyat.setText(hadisText);
    }

    private void loadKosakataPerKata(int surah, int ayah, boolean forceReload) {
        layoutKosakataItems.removeAllViews();

        if (!forceReload) {
            List<WordModel> cached = QuranRepository.getWordByWord(this, surah, ayah);
            if (cached != null && !cached.isEmpty()) {
                currentWords = cached;
                pbKosakata.setVisibility(View.GONE);
                tvKosakataStatus.setVisibility(View.GONE);
                btnReloadKosakata.setVisibility(View.GONE);
                renderWordItems(cached);
                return;
            }
        }

        pbKosakata.setVisibility(View.VISIBLE);
        tvKosakataStatus.setVisibility(View.GONE);
        btnReloadKosakata.setVisibility(View.GONE);

        final int targetSurah = surah;
        final int targetAyah = ayah;

        executor.execute(() -> {
            List<WordModel> words = null;
            try {
                String urlStr = "https://api.quran.com/api/v4/verses/by_key/" + targetSurah + ":" + targetAyah
                        + "?language=id&words=true&word_fields=text_uthmani,translation,transliteration";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();
                    String respStr = sb.toString();
                    words = QuranRepository.parseWordJson(respStr, targetAyah);
                    if (words != null && !words.isEmpty()) {
                        QuranRepository.saveWordByWord(PelajariActivity.this, targetSurah, targetAyah, respStr);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final List<WordModel> finalWords = words;
            mainHandler.post(() -> {
                if (surahNumber != targetSurah || ayahNumber != targetAyah) {
                    return; // Stale request
                }
                pbKosakata.setVisibility(View.GONE);
                if (finalWords != null && !finalWords.isEmpty()) {
                    currentWords = finalWords;
                    tvKosakataStatus.setVisibility(View.GONE);
                    btnReloadKosakata.setVisibility(View.GONE);
                    renderWordItems(finalWords);
                } else {
                    currentWords = null;
                    renderFallbackWords();
                }
            });
        });
    }

    private void renderWordItems(List<WordModel> words) {
        layoutKosakataItems.removeAllViews();
        if (words == null || words.isEmpty()) return;

        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        android.graphics.Typeface mushafTf = PreferenceManager.getMushafTypeface(this);

        for (WordModel word : words) {
            View itemView = inflater.inflate(R.layout.item_wbw_reference, layoutKosakataItems, false);
            TextView tvArabic = itemView.findViewById(R.id.tv_wbw_arabic);
            TextView tvMeaning = itemView.findViewById(R.id.tv_wbw_meaning);

            tvArabic.setText(word.getArabic());
            if (mushafTf != null) {
                tvArabic.setTypeface(mushafTf);
            }
            String tr = word.getTranslation();
            tvMeaning.setText((tr == null || tr.trim().isEmpty()) ? "-" : tr.trim());
            layoutKosakataItems.addView(itemView);
        }
    }

    private void renderFallbackWords() {
        layoutKosakataItems.removeAllViews();
        tvKosakataStatus.setVisibility(View.VISIBLE);
        tvKosakataStatus.setText("⚠️ Kosakata lengkap memerlukan internet untuk diunduh pertama kali.");
        btnReloadKosakata.setVisibility(View.VISIBLE);

        if (currentAyah == null || currentAyah.getArabic() == null) return;
        String[] tokens = currentAyah.getArabic().trim().split("\\s+");
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        android.graphics.Typeface mushafTf = PreferenceManager.getMushafTypeface(this);

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) continue;
            View itemView = inflater.inflate(R.layout.item_wbw_reference, layoutKosakataItems, false);
            TextView tvArabic = itemView.findViewById(R.id.tv_wbw_arabic);
            TextView tvMeaning = itemView.findViewById(R.id.tv_wbw_meaning);

            tvArabic.setText(token);
            if (mushafTf != null) {
                tvArabic.setTypeface(mushafTf);
            }
            tvMeaning.setText("Kata " + (i + 1));
            layoutKosakataItems.addView(itemView);
        }
    }

    private void downloadTafsirOnline(int surah) {
        isDownloading = true;
        btnDownloadTafsir.setVisibility(View.GONE);
        pbDownloadTafsir.setVisibility(View.VISIBLE);
        tvDownloadStatus.setVisibility(View.VISIBLE);
        tvDownloadStatus.setText("Mengunduh tafsir Surat " + surah + " dari server...");

        executor.execute(() -> {
            boolean success = false;
            try {
                URL url = new URL("https://equran.id/api/v2/tafsir/" + surah);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if (json.has("data")) {
                        JSONObject dataObj = json.getJSONObject("data");
                        if (dataObj.has("tafsir")) {
                            JSONArray tafsirArr = dataObj.getJSONArray("tafsir");
                            JSONObject out = new JSONObject();
                            for (int i = 0; i < tafsirArr.length(); i++) {
                                JSONObject item = tafsirArr.getJSONObject(i);
                                int ayatNum = item.getInt("ayat");
                                String teks = item.getString("teks");
                                String formatted = "<h3>Tafsir Ayat " + ayatNum + "</h3>\n<p>" + teks.replace("\n\n", "</p>\n<p>") + "</p>";
                                out.put(String.valueOf(ayatNum), formatted);
                            }
                            success = QuranRepository.saveDownloadedTafsir(PelajariActivity.this, surah, out.toString());
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isSuccess = success;
            mainHandler.post(() -> {
                isDownloading = false;
                if (isSuccess) {
                    Toast.makeText(PelajariActivity.this, "✅ Tafsir Surat " + surah + " berhasil diunduh!", Toast.LENGTH_SHORT).show();
                    displayAyahAndStudy();
                } else {
                    Toast.makeText(PelajariActivity.this, "Gagal mengunduh tafsir. Periksa koneksi internet Anda.", Toast.LENGTH_LONG).show();
                    btnDownloadTafsir.setVisibility(View.VISIBLE);
                    pbDownloadTafsir.setVisibility(View.GONE);
                    tvDownloadStatus.setVisibility(View.GONE);
                }
            });
        });
    }

    private void shareText(String title, String content) {
        try {
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("text/plain");
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            sendIntent.putExtra(Intent.EXTRA_TEXT, content);
            startActivity(Intent.createChooser(sendIntent, "Bagikan via:"));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membagikan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAyah() {
        if (currentAyah == null) return;
        String surahName = QuranRepository.getSurahName(this, surahNumber);
        String text = "📖 QS. " + surahName + " : Ayat " + ayahNumber + "\n\n"
                + currentAyah.getArabic() + "\n\n"
                + "Artinya:\n\"" + currentAyah.getIndo() + "\"\n\n"
                + "Dibagikan via 5D Quran (v1.0.2)";
        shareText("Ayat QS. " + surahName + ":" + ayahNumber, text);
    }

    private void shareKosakata() {
        if (currentAyah == null) return;
        String surahName = QuranRepository.getSurahName(this, surahNumber);
        StringBuilder sb = new StringBuilder();
        sb.append("📖 Kosakata Per Kata QS. ").append(surahName).append(" : Ayat ").append(ayahNumber).append("\n\n");
        sb.append("Teks Ayat:\n").append(currentAyah.getArabic()).append("\n\n");
        sb.append("Arti Kata demi Kata:\n");
        if (currentWords != null && !currentWords.isEmpty()) {
            for (WordModel w : currentWords) {
                String ar = w.getArabic();
                String tr = w.getTranslation();
                if (ar != null && !ar.trim().isEmpty()) {
                    sb.append("• ").append(ar).append(" : ").append(tr != null && !tr.trim().isEmpty() ? tr.trim() : "-").append("\n");
                }
            }
        } else {
            sb.append("(Terjemah lengkap): \"").append(currentAyah.getIndo()).append("\"\n");
        }
        sb.append("\nDibagikan via 5D Quran (v1.0.2)");
        shareText("Kosakata QS. " + surahName + ":" + ayahNumber, sb.toString());
    }

    private void shareTafsir() {
        if (currentAyah == null) return;
        String surahName = QuranRepository.getSurahName(this, surahNumber);
        String tafsir = tvTafsirIbnuKatsir != null ? tvTafsirIbnuKatsir.getText().toString() : "";
        StringBuilder sb = new StringBuilder();
        sb.append("📜 Tafsir Ibnu Katsir QS. ").append(surahName).append(" : Ayat ").append(ayahNumber).append("\n\n");
        sb.append("Ayat:\n").append(currentAyah.getArabic()).append("\n\n");
        sb.append("Artinya:\n\"").append(currentAyah.getIndo()).append("\"\n\n");
        sb.append("Penjelasan Tafsir:\n").append(tafsir).append("\n\n");
        sb.append("Dibagikan via 5D Quran (v1.0.2)");
        shareText("Tafsir Ibnu Katsir QS. " + surahName + ":" + ayahNumber, sb.toString());
    }

    private void shareAllTadabur() {
        if (currentAyah == null) return;
        String surahName = QuranRepository.getSurahName(this, surahNumber);
        StringBuilder sb = new StringBuilder();
        sb.append("🌟 Tadabur Al-Qur'an — QS. ").append(surahName).append(" : Ayat ").append(ayahNumber).append("\n\n");
        sb.append("【 Teks Ayat 】\n").append(currentAyah.getArabic()).append("\n\n");
        sb.append("【 Terjemah 】\n\"").append(currentAyah.getIndo()).append("\"\n\n");

        if (currentWords != null && !currentWords.isEmpty()) {
            sb.append("【 Kosakata Kata Demi Kata 】\n");
            for (WordModel w : currentWords) {
                String ar = w.getArabic();
                String tr = w.getTranslation();
                if (ar != null && !ar.trim().isEmpty()) {
                    sb.append("• ").append(ar).append(" : ").append(tr != null && !tr.trim().isEmpty() ? tr.trim() : "-").append("\n");
                }
            }
            sb.append("\n");
        }

        String tafsir = tvTafsirIbnuKatsir != null ? tvTafsirIbnuKatsir.getText().toString() : "";
        if (tafsir != null && !tafsir.isEmpty() && !tafsir.contains("belum diunduh")) {
            sb.append("【 Ringkasan Tafsir Ibnu Katsir 】\n").append(tafsir).append("\n\n");
        }

        String hadis = tvHadisAyat != null ? tvHadisAyat.getText().toString() : "";
        if (hadis != null && !hadis.isEmpty() && !hadis.contains("Belum ada catatan")) {
            sb.append("【 Hadis Terkait / Asbabun Nuzul 】\n").append(hadis).append("\n\n");
        }

        sb.append("Dibagikan via 5D Quran (v1.0.2)");
        shareText("Tadabur Lengkap QS. " + surahName + ":" + ayahNumber, sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

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

    private TextView tvPelajariTitle, tvPelajariAyahBadge;
    private TextView tvPelajariArabic, tvPelajariIndo;
    private TextView tvTafsirIbnuKatsir, tvHadisAyat;

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
                + "Dibagikan via 5D Quran (v1.0.4)";
        shareText("Ayat QS. " + surahName + ":" + ayahNumber, text);
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
        sb.append("Dibagikan via 5D Quran (v1.0.4)");
        shareText("Tafsir Ibnu Katsir QS. " + surahName + ":" + ayahNumber, sb.toString());
    }

    private void shareAllTadabur() {
        if (currentAyah == null) return;
        String surahName = QuranRepository.getSurahName(this, surahNumber);
        StringBuilder sb = new StringBuilder();
        sb.append("🌟 Tadabur Al-Qur'an — QS. ").append(surahName).append(" : Ayat ").append(ayahNumber).append("\n\n");
        sb.append("【 Teks Ayat 】\n").append(currentAyah.getArabic()).append("\n\n");
        sb.append("【 Terjemah 】\n\"").append(currentAyah.getIndo()).append("\"\n\n");

        String tafsir = tvTafsirIbnuKatsir != null ? tvTafsirIbnuKatsir.getText().toString() : "";
        if (tafsir != null && !tafsir.isEmpty() && !tafsir.contains("belum diunduh")) {
            sb.append("【 Ringkasan Tafsir Ibnu Katsir 】\n").append(tafsir).append("\n\n");
        }

        String hadis = tvHadisAyat != null ? tvHadisAyat.getText().toString() : "";
        if (hadis != null && !hadis.isEmpty() && !hadis.contains("Belum ada catatan")) {
            sb.append("【 Hadis Terkait / Asbabun Nuzul 】\n").append(hadis).append("\n\n");
        }

        sb.append("Dibagikan via 5D Quran (v1.0.4)");
        shareText("Tadabur Lengkap QS. " + surahName + ":" + ayahNumber, sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

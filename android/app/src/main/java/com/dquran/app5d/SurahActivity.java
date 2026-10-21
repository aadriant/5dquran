package com.dquran.app5d;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SurahActivity extends AppCompatActivity {

    public static class WordTiming {
        public final String word;
        public final long startMs;
        public final long endMs;

        public WordTiming(String word, long startMs, long endMs) {
            this.word = word;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private int surahNumber = 1;
    private int targetAyah = 1;
    private List<AyahModel> ayahs;
    private SurahModel currentSurah;

    private RecyclerView rvAyahs;
    private AyahAdapter adapter;
    private TextView tvTitleSurah, tvSubtitleSurah;
    private View cardBismillah;
    private ImageButton btnSurahAudio;

    // Mini Player
    private View bottomPlayerBar;
    private ImageButton btnPlayerToggle;
    private TextView tvPlayerStatus, tvPlayerCurrent;
    private TextView btnPlayerStop;

    private MediaPlayer mediaPlayer;
    private int currentPlayingAyahIndex = -1;
    private boolean isPlaying = false;

    // Karaoke state
    private List<WordTiming> currentWordTimings = new ArrayList<>();
    private int lastKaraokeWordIndex = -1;
    private final Handler karaokeHandler = new Handler(Looper.getMainLooper());
    private Runnable karaokeRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_surah);

        surahNumber = getIntent().getIntExtra("surah_number", 1);
        targetAyah = getIntent().getIntExtra("target_ayah", 1);
        boolean autoPlay = getIntent().getBooleanExtra("auto_play", false);

        initViews();
        loadData();

        if (targetAyah > 1 && ayahs != null && targetAyah <= ayahs.size()) {
            rvAyahs.scrollToPosition(targetAyah - 1);
        }

        if (autoPlay && !ayahs.isEmpty()) {
            playAyah(targetAyah > 1 ? targetAyah - 1 : 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView tvBismillah = findViewById(R.id.tv_bismillah);
        if (tvBismillah != null) {
            tvBismillah.setTypeface(PreferenceManager.getMushafTypeface(this));
            tvBismillah.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvBismillah.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvBismillah.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            tvBismillah.setGravity(android.view.Gravity.RIGHT);
        }
        if (tvPlayerCurrent != null) {
            tvPlayerCurrent.setTypeface(PreferenceManager.getMushafTypeface(this));
            tvPlayerCurrent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvPlayerCurrent.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvPlayerCurrent.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            tvPlayerCurrent.setGravity(android.view.Gravity.RIGHT);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void initViews() {
        rvAyahs = findViewById(R.id.rv_ayahs);
        tvTitleSurah = findViewById(R.id.tv_title_surah);
        tvSubtitleSurah = findViewById(R.id.tv_subtitle_surah);
        cardBismillah = findViewById(R.id.card_bismillah);
        btnSurahAudio = findViewById(R.id.btn_surah_audio);

        bottomPlayerBar = findViewById(R.id.bottom_player_bar);
        btnPlayerToggle = findViewById(R.id.btn_player_toggle);
        tvPlayerStatus = findViewById(R.id.tv_player_status);
        tvPlayerCurrent = findViewById(R.id.tv_player_current);
        btnPlayerStop = findViewById(R.id.btn_player_stop);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnPlayerToggle.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    isPlaying = false;
                    btnPlayerToggle.setImageResource(R.drawable.ic_play_arrow);
                    tvPlayerStatus.setText("Jeda (Paused)");
                } else {
                    mediaPlayer.start();
                    isPlaying = true;
                    btnPlayerToggle.setImageResource(R.drawable.ic_pause);
                    tvPlayerStatus.setText("Memutar...");
                }
            } else if (!ayahs.isEmpty()) {
                playAyah(0, true);
            }
        });

        btnPlayerStop.setOnClickListener(v -> stopAudio());

        btnSurahAudio.setOnClickListener(v -> {
            if (isPlaying) {
                stopAudio();
            } else if (!ayahs.isEmpty()) {
                playAyah(0, true);
            }
        });
    }

    private void loadData() {
        List<SurahModel> surahs = QuranRepository.getSurahs(this);
        for (SurahModel s : surahs) {
            if (s.getNumber() == surahNumber) {
                currentSurah = s;
                break;
            }
        }

        if (currentSurah != null) {
            String cleanArab = QuranRepository.cleanArabicSurahName(currentSurah.getName());
            tvTitleSurah.setText(currentSurah.getEnglishName() + " (" + cleanArab + ")");
            String type = "Meccan".equalsIgnoreCase(currentSurah.getRevelationType()) ? "Makkiyah" : currentSurah.getRevelationType();
            tvSubtitleSurah.setText(currentSurah.getIndonesianName() + " · " + currentSurah.getNumberOfAyahs() + " Ayat · " + type);
            // Record last read surah and target ayah
            PreferenceManager.setLastRead(this, surahNumber, currentSurah.getEnglishName(), targetAyah > 0 ? targetAyah : 1);
        }

        // Hide Bismillah for At-Taubah (Surah 9)
        if (surahNumber == 9 || surahNumber == 1) {
            cardBismillah.setVisibility(View.GONE);
        } else {
            cardBismillah.setVisibility(View.VISIBLE);
        }

        ayahs = QuranRepository.getAyahs(this, surahNumber);
        rvAyahs.setLayoutManager(new LinearLayoutManager(this));

        float fontSize = PreferenceManager.getArabicFontSize(this);
        adapter = new AyahAdapter(this, ayahs, fontSize, (ayah, actionType) -> {
            int index = ayahs.indexOf(ayah);
            // Update last read to this ayah
            String surahName = currentSurah != null ? currentSurah.getEnglishName() : QuranRepository.getSurahName(this, surahNumber);
            PreferenceManager.setLastRead(SurahActivity.this, surahNumber, surahName, ayah.getNumberInSurah());

            switch (actionType) {
                case "dengar":
                    playAyah(index);
                    break;
                case "kosakata":
                    showKosakataDialog(ayah);
                    break;
                case "hafal": {
                    Intent intent = new Intent(SurahActivity.this, HafalanActivity.class);
                    intent.putExtra("surah_number", surahNumber);
                    intent.putExtra("ayah_number", ayah.getNumberInSurah());
                    startActivity(intent);
                    break;
                }
                case "tulis": {
                    Intent intent = new Intent(SurahActivity.this, TulisActivity.class);
                    intent.putExtra("surah_number", surahNumber);
                    intent.putExtra("ayah_number", ayah.getNumberInSurah());
                    startActivity(intent);
                    break;
                }
                case "pelajari": {
                    Intent intent = new Intent(SurahActivity.this, PelajariActivity.class);
                    intent.putExtra("surah_number", surahNumber);
                    intent.putExtra("ayah_number", ayah.getNumberInSurah());
                    startActivity(intent);
                    break;
                }
            }
        });

        rvAyahs.setAdapter(adapter);

        // Track ayahs as read when they scroll into view (Kriteria Baca)
        rvAyahs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null) return;
                int first = lm.findFirstVisibleItemPosition();
                int last  = lm.findLastVisibleItemPosition();
                for (int i = first; i <= last && i >= 0 && i < ayahs.size(); i++) {
                    PreferenceManager.markBaca(SurahActivity.this, surahNumber, ayahs.get(i).getNumberInSurah());
                }
                if (first >= 0 && first < ayahs.size()) {
                    String sName = currentSurah != null ? currentSurah.getEnglishName() : QuranRepository.getSurahName(SurahActivity.this, surahNumber);
                    PreferenceManager.setLastRead(SurahActivity.this, surahNumber, sName, ayahs.get(first).getNumberInSurah());
                }
            }
        });
        // Mark first visible ayah immediately
        rvAyahs.post(() -> {
            if (!ayahs.isEmpty()) {
                PreferenceManager.markBaca(this, surahNumber, ayahs.get(0).getNumberInSurah());
            }
        });
    }

    private void playAyah(int index) {
        playAyah(index, false); // Single ayah play only
    }

    private void playAyah(int index, boolean autoContinue) {
        if (index < 0 || index >= ayahs.size()) return;

        currentPlayingAyahIndex = index;
        AyahModel ayah = ayahs.get(index);

        // Update last read
        String surahName = currentSurah != null ? currentSurah.getEnglishName() : QuranRepository.getSurahName(this, surahNumber);
        PreferenceManager.setLastRead(this, surahNumber, surahName, ayah.getNumberInSurah());

        stopAudioInternal();

        // Smooth scroll to active ayah
        if (rvAyahs != null) {
            rvAyahs.smoothScrollToPosition(index);
        }

        String qariId = PreferenceManager.getQariId(this);
        String qariName = PreferenceManager.getQariName(this);
        String primaryUrl = String.format(Locale.US, "https://mirrors.quranicaudio.com/everyayah/%s/%03d%03d.mp3", qariId, surahNumber, ayah.getNumberInSurah());
        String fallbackUrl = String.format(Locale.US, "https://everyayah.com/data/%s/%03d%03d.mp3", qariId, surahNumber, ayah.getNumberInSurah());

        tvPlayerStatus.setText("Memutar: " + surahName + " Ayat " + ayah.getNumberInSurah() + " (" + qariName + ")");
        tvPlayerCurrent.setText(ayah.getArabic());
        btnPlayerToggle.setImageResource(R.drawable.ic_pause);
        isPlaying = true;

        startMediaPlayer(primaryUrl, fallbackUrl, ayah, autoContinue);
    }

    private void startMediaPlayer(String url, String fallbackUrl, AyahModel ayah, boolean autoContinue) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                long dur = mp.getDuration();
                if (dur <= 0) dur = 5000;
                currentWordTimings = estimateWordTimings(ayah.getArabic(), dur);
                lastKaraokeWordIndex = -1;
                startKaraokeTicker(ayah);
            });
            mediaPlayer.setOnCompletionListener(mp -> {
                stopKaraokeTicker();
                // If autoContinue is true, proceed to next verse. Otherwise stop after this ayah!
                if (autoContinue && currentPlayingAyahIndex + 1 < ayahs.size()) {
                    playAyah(currentPlayingAyahIndex + 1, true);
                } else {
                    stopAudio();
                }
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                if (fallbackUrl != null && !url.equals(fallbackUrl)) {
                    stopAudioInternal();
                    startMediaPlayer(fallbackUrl, null, ayah, autoContinue);
                    return true;
                }
                Toast.makeText(SurahActivity.this, "Gagal memutar audio ayat ini.", Toast.LENGTH_SHORT).show();
                stopAudio();
                return true;
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            stopAudio();
        }
    }

    // ── KARAOKE WORD TICKER & HIGHLIGHTING ──

    private void startKaraokeTicker(AyahModel ayah) {
        stopKaraokeTicker();
        karaokeRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying) {
                    try {
                        int currentPos = mediaPlayer.getCurrentPosition();
                        updateKaraokeTick(ayah, currentPos);
                    } catch (Exception ignored) {}
                    karaokeHandler.postDelayed(this, 50); // 50ms tick for fluid karaoke
                }
            }
        };
        karaokeHandler.post(karaokeRunnable);
    }

    private void stopKaraokeTicker() {
        if (karaokeRunnable != null) {
            karaokeHandler.removeCallbacks(karaokeRunnable);
            karaokeRunnable = null;
        }
    }

    private void updateKaraokeTick(AyahModel ayah, int currentPos) {
        if (currentWordTimings.isEmpty()) return;

        int curIdx = -1;
        for (int i = 0; i < currentWordTimings.size(); i++) {
            WordTiming wt = currentWordTimings.get(i);
            if (currentPos >= wt.startMs && currentPos < wt.endMs) {
                curIdx = i;
                break;
            } else if (currentPos < wt.startMs) {
                curIdx = Math.max(0, i - 1);
                break;
            }
        }
        if (curIdx == -1 && currentPos >= currentWordTimings.get(currentWordTimings.size() - 1).endMs) {
            curIdx = currentWordTimings.size() - 1;
        }

        if (curIdx != lastKaraokeWordIndex) {
            lastKaraokeWordIndex = curIdx;
            if (adapter != null) {
                adapter.setKaraokeHighlight(rvAyahs, currentPlayingAyahIndex, curIdx);
            }
            if (tvPlayerCurrent != null) {
                AyahAdapter.applyKaraokeSpannable(tvPlayerCurrent, ayah.getArabic(), curIdx);
            }
        }
    }

    private List<WordTiming> estimateWordTimings(String arabicText, long durationMs) {
        List<WordTiming> list = new ArrayList<>();
        if (arabicText == null || arabicText.trim().isEmpty()) return list;
        String[] words = arabicText.trim().split("\\s+");
        if (words.length == 0) return list;

        String diacritics = "[\u064B-\u065F\u0610-\u061A\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]";
        int[] weights = new int[words.length];
        int totalWeight = 0;
        for (int i = 0; i < words.length; i++) {
            String stripped = words[i].replaceAll(diacritics, "");
            weights[i] = Math.max(stripped.length(), 1);
            totalWeight += weights[i];
        }
        if (totalWeight == 0) totalWeight = words.length;

        long leadIn = (long) Math.min(durationMs * 0.05f, 300f);
        long spoken = Math.max(durationMs - leadIn, 100);
        float gapRatio = 0.07f;

        long elapsed = leadIn;
        for (int i = 0; i < words.length; i++) {
            float fraction = (float) weights[i] / totalWeight;
            long talkDur = (long) (fraction * spoken * (1f - gapRatio));
            long gapDur = (long) (fraction * spoken * gapRatio);
            long start = elapsed;
            elapsed += talkDur + gapDur;
            list.add(new WordTiming(words[i], start, elapsed));
        }
        return list;
    }

    private void stopAudioInternal() {
        stopKaraokeTicker();
        if (adapter != null) {
            adapter.clearKaraokeHighlight(rvAyahs);
        }
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }

    private void stopAudio() {
        stopAudioInternal();
        isPlaying = false;
        currentPlayingAyahIndex = -1;
        lastKaraokeWordIndex = -1;
        btnPlayerToggle.setImageResource(R.drawable.ic_play_arrow);
        tvPlayerStatus.setText("Audio Dihentikan");
        tvPlayerCurrent.setText("Pilih ayat untuk memutar");
    }

    private void showKosakataDialog(AyahModel ayah) {
        if (ayah == null) return;
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_kosakata_ayat);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = dialog.findViewById(R.id.tv_dlg_kosa_title);
        TextView tvArabicFull = dialog.findViewById(R.id.tv_dlg_kosa_arabic_full);
        TextView tvIndoFull = dialog.findViewById(R.id.tv_dlg_kosa_indo_full);
        ImageButton btnClose = dialog.findViewById(R.id.btn_dlg_kosa_close);
        ProgressBar pb = dialog.findViewById(R.id.pb_dlg_kosa);
        FlowLayout flowWords = dialog.findViewById(R.id.flow_dlg_words);

        String sName = currentSurah != null ? currentSurah.getEnglishName() : ("Surat " + surahNumber);
        tvTitle.setText(sName + " : Ayat " + ayah.getNumberInSurah());
        tvArabicFull.setText(ayah.getArabic());
        tvArabicFull.setTypeface(PreferenceManager.getMushafTypeface(this));
        tvArabicFull.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tvArabicFull.setTextDirection(View.TEXT_DIRECTION_RTL);
        tvArabicFull.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        tvArabicFull.setGravity(android.view.Gravity.RIGHT);
        tvIndoFull.setText(ayah.getIndo());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // Load words
        final int aNum = ayah.getNumberInSurah();
        List<WordModel> cached = QuranRepository.getWordByWord(this, surahNumber, aNum);
        if (cached != null && !cached.isEmpty()) {
            renderDialogWords(flowWords, cached);
        } else {
            // Render fallback immediately so user sees words in reference format right away
            renderDialogFallback(flowWords, ayah.getArabic());
            // Then fetch online if possible
            if (pb != null) pb.setVisibility(View.VISIBLE);
            new Thread(() -> {
                try {
                    String urlStr = "https://api.quran.com/api/v4/verses/by_key/" + surahNumber + ":" + aNum
                            + "?language=id&words=true&word_fields=text_uthmani,translation,transliteration";
                    java.net.URL url = new java.net.URL(urlStr);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    if (conn.getResponseCode() == 200) {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        String resp = sb.toString();
                        List<WordModel> words = QuranRepository.parseWordJson(resp, aNum);
                        if (words != null && !words.isEmpty()) {
                            QuranRepository.saveWordByWord(SurahActivity.this, surahNumber, aNum, resp);
                            runOnUiThread(() -> {
                                if (pb != null) pb.setVisibility(View.GONE);
                                renderDialogWords(flowWords, words);
                            });
                        }
                    }
                    conn.disconnect();
                } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    if (pb != null) pb.setVisibility(View.GONE);
                });
            }).start();
        }

        dialog.show();
    }

    private void renderDialogWords(FlowLayout flow, List<WordModel> words) {
        if (flow == null || words == null) return;
        flow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        Typeface tf = PreferenceManager.getMushafTypeface(this);
        for (WordModel w : words) {
            View v = inflater.inflate(R.layout.item_wbw_reference, flow, false);
            TextView tvAr = v.findViewById(R.id.tv_wbw_arabic);
            TextView tvTr = v.findViewById(R.id.tv_wbw_meaning);
            tvAr.setText(w.getArabic());
            if (tf != null) tvAr.setTypeface(tf);
            String tr = w.getTranslation();
            tvTr.setText((tr == null || tr.trim().isEmpty()) ? "-" : tr.trim());
            flow.addView(v);
        }
    }

    private void renderDialogFallback(FlowLayout flow, String arabicText) {
        if (flow == null || arabicText == null) return;
        flow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        Typeface tf = PreferenceManager.getMushafTypeface(this);
        String[] tokens = arabicText.trim().split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.isEmpty()) continue;
            View v = inflater.inflate(R.layout.item_wbw_reference, flow, false);
            TextView tvAr = v.findViewById(R.id.tv_wbw_arabic);
            TextView tvTr = v.findViewById(R.id.tv_wbw_meaning);
            tvAr.setText(token);
            if (tf != null) tvAr.setTypeface(tf);
            tvTr.setText("Kata " + (i + 1));
            flow.addView(v);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudio();
    }
}

package com.dquran.app5d;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SurahActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_RECORD = 301;

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

    // View Mode & Page View (Versi 1.0.2 Feature 1)
    private String currentViewMode = "card"; // "card" or "page"
    private ImageButton btnToggleViewMode;
    private RecyclerView rvPages;
    private MushafPageAdapter pageAdapter;
    private List<MushafPageModel> mushafPages = new ArrayList<>();

    // Mode 1: Ayahs Card RecyclerView
    private RecyclerView rvAyahs;
    private AyahAdapter adapter;
    private TextView tvTitleSurah, tvSubtitleSurah;
    private View cardBismillah;
    private ImageButton btnSurahAudio;

    // Page View Action Sheet
    private View layoutPageActionSheet;
    private TextView tvSheetAyahBadge, tvSheetIndo;
    private ImageButton btnSheetClose;
    private TextView btnSheetDengar, btnSheetKosakata, btnSheetHafal, btnSheetTulis, btnSheetTafsir;
    private AyahModel selectedPageAyah = null;

    // Compact Hafalan Footer in Page View (Versi 1.0.2 Feature 2 & 3)
    private View layoutPageHafalanFooter;
    private TextView tvFooterHafalBadge, tvFooterHafalIndicator, tvFooterHafalStatus;
    private ImageButton btnFooterHafalMic, btnFooterHafalPlay, btnFooterHafalClose;
    private TextView btnFooterHafalEye, btnFooterHafalDone;
    private AyahModel footerHafalAyah = null;
    private boolean isPageHafalMasked = false;
    private SpeechRecognizer footerSpeechRecognizer;
    private boolean isFooterRecordingActive = false;
    private StringBuilder footerAccumulatedSpeech = new StringBuilder();
    private MediaPlayer footerPreviewPlayer;

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

        if (targetAyah > 1) {
            scrollToAyah(targetAyah);
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
        if (pageAdapter != null) {
            pageAdapter.notifyDataSetChanged();
        }
    }

    private void initViews() {
        rvAyahs = findViewById(R.id.rv_ayahs);
        rvPages = findViewById(R.id.rv_pages);
        tvTitleSurah = findViewById(R.id.tv_title_surah);
        tvSubtitleSurah = findViewById(R.id.tv_subtitle_surah);
        cardBismillah = findViewById(R.id.card_bismillah);
        btnSurahAudio = findViewById(R.id.btn_surah_audio);
        btnToggleViewMode = findViewById(R.id.btn_toggle_view_mode);

        bottomPlayerBar = findViewById(R.id.bottom_player_bar);
        btnPlayerToggle = findViewById(R.id.btn_player_toggle);
        tvPlayerStatus = findViewById(R.id.tv_player_status);
        tvPlayerCurrent = findViewById(R.id.tv_player_current);
        btnPlayerStop = findViewById(R.id.btn_player_stop);

        // Page Action Sheet
        layoutPageActionSheet = findViewById(R.id.layout_page_action_sheet);
        tvSheetAyahBadge = findViewById(R.id.tv_sheet_ayah_badge);
        tvSheetIndo = findViewById(R.id.tv_sheet_indo);
        btnSheetClose = findViewById(R.id.btn_sheet_close);
        btnSheetDengar = findViewById(R.id.btn_sheet_dengar);
        btnSheetKosakata = findViewById(R.id.btn_sheet_kosakata);
        btnSheetHafal = findViewById(R.id.btn_sheet_hafal);
        btnSheetTulis = findViewById(R.id.btn_sheet_tulis);
        btnSheetTafsir = findViewById(R.id.btn_sheet_tafsir);

        // Footer Hafalan
        layoutPageHafalanFooter = findViewById(R.id.layout_page_hafalan_footer);
        tvFooterHafalBadge = findViewById(R.id.tv_footer_hafal_badge);
        tvFooterHafalIndicator = findViewById(R.id.tv_footer_hafal_indicator);
        tvFooterHafalStatus = findViewById(R.id.tv_footer_hafal_status);
        btnFooterHafalMic = findViewById(R.id.btn_footer_hafal_mic);
        btnFooterHafalPlay = findViewById(R.id.btn_footer_hafal_play);
        btnFooterHafalEye = findViewById(R.id.btn_footer_hafal_eye);
        btnFooterHafalDone = findViewById(R.id.btn_footer_hafal_done);
        btnFooterHafalClose = findViewById(R.id.btn_footer_hafal_close);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        btnToggleViewMode.setOnClickListener(v -> toggleViewMode());

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
            PreferenceManager.setLastRead(this, surahNumber, currentSurah.getEnglishName(), targetAyah > 0 ? targetAyah : 1);
        }

        // Hide Bismillah for At-Taubah (Surah 9)
        if (surahNumber == 9 || surahNumber == 1) {
            cardBismillah.setVisibility(View.GONE);
        } else {
            cardBismillah.setVisibility(View.VISIBLE);
        }

        // Mode 1: Ayah list setup
        ayahs = QuranRepository.getAyahs(this, surahNumber);
        rvAyahs.setLayoutManager(new LinearLayoutManager(this));

        float fontSize = PreferenceManager.getArabicFontSize(this);
        adapter = new AyahAdapter(this, ayahs, fontSize, (ayah, actionType) -> {
            int index = ayahs.indexOf(ayah);
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

        // Mode 2: Mushaf Pages Setup (Continuous Justified Text Per Page)
        mushafPages = QuranRepository.getMushafPagesForSurah(this, surahNumber);
        rvPages.setLayoutManager(new LinearLayoutManager(this));
        pageAdapter = new MushafPageAdapter(this, currentSurah, mushafPages, this::onPageAyahClicked);
        rvPages.setAdapter(pageAdapter);

        rvPages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm == null || mushafPages.isEmpty()) return;
                int first = lm.findFirstVisibleItemPosition();
                if (first >= 0 && first < mushafPages.size()) {
                    MushafPageModel page = mushafPages.get(first);
                    if (page.getAyahs() != null && !page.getAyahs().isEmpty()) {
                        AyahModel firstA = page.getAyahs().get(0);
                        String sName = currentSurah != null ? currentSurah.getEnglishName() : QuranRepository.getSurahName(SurahActivity.this, surahNumber);
                        PreferenceManager.setLastRead(SurahActivity.this, surahNumber, sName, firstA.getNumberInSurah());
                        for (AyahModel a : page.getAyahs()) {
                            PreferenceManager.markBaca(SurahActivity.this, surahNumber, a.getNumberInSurah());
                        }
                    }
                }
            }
        });

        // Restore saved view mode preference
        currentViewMode = PreferenceManager.getSurahViewMode(this);
        applyViewMode(currentViewMode, false);

        rvAyahs.post(() -> {
            if (!ayahs.isEmpty()) {
                PreferenceManager.markBaca(this, surahNumber, ayahs.get(0).getNumberInSurah());
            }
        });
    }

    private void scrollToAyah(int ayahNum) {
        if ("page".equalsIgnoreCase(currentViewMode)) {
            if (pageAdapter != null) {
                int pos = pageAdapter.findPositionForAyah(ayahNum);
                rvPages.scrollToPosition(pos);
            }
        } else {
            if (ayahs != null && ayahNum <= ayahs.size() && ayahNum >= 1) {
                rvAyahs.scrollToPosition(ayahNum - 1);
            }
        }
    }

    // ── VIEW MODE TOGGLE (CARD VIEW vs PAGE VIEW) ──

    private void toggleViewMode() {
        String newMode = "card".equalsIgnoreCase(currentViewMode) ? "page" : "card";
        applyViewMode(newMode, true);
    }

    private void applyViewMode(String mode, boolean notifyToast) {
        currentViewMode = mode;
        PreferenceManager.setSurahViewMode(this, mode);

        if ("page".equalsIgnoreCase(mode)) {
            rvAyahs.setVisibility(View.GONE);
            rvPages.setVisibility(View.VISIBLE);
            cardBismillah.setVisibility(View.GONE);
            btnToggleViewMode.setImageResource(R.drawable.ic_view_list);
            btnToggleViewMode.setContentDescription("Ganti ke Tampilan Per Ayat");

            if (targetAyah > 1 && pageAdapter != null) {
                int pos = pageAdapter.findPositionForAyah(targetAyah);
                rvPages.scrollToPosition(pos);
            }
            if (notifyToast) {
                Toast.makeText(this, "Mode Tampilan Halaman Mushaf", Toast.LENGTH_SHORT).show();
            }
        } else {
            rvAyahs.setVisibility(View.VISIBLE);
            rvPages.setVisibility(View.GONE);
            if (surahNumber != 9 && surahNumber != 1) {
                cardBismillah.setVisibility(View.VISIBLE);
            } else {
                cardBismillah.setVisibility(View.GONE);
            }
            btnToggleViewMode.setImageResource(R.drawable.ic_view_page);
            btnToggleViewMode.setContentDescription("Ganti ke Tampilan Halaman Mushaf");

            hidePageActionSheet();
            stopFooterHafalan();

            if (targetAyah > 1 && ayahs != null && targetAyah <= ayahs.size()) {
                rvAyahs.scrollToPosition(targetAyah - 1);
            }
            if (notifyToast) {
                Toast.makeText(this, "Mode Tampilan Per Ayat", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── PAGE VIEW AYAH INTERACTION & ACTION SHEET ──

    private void onPageAyahClicked(AyahModel ayah) {
        if (ayah == null) return;
        selectedPageAyah = ayah;

        if (layoutPageHafalanFooter != null && layoutPageHafalanFooter.getVisibility() == View.VISIBLE) {
            startFooterHafalanForAyah(ayah);
        } else {
            showPageActionSheet(ayah);
        }
    }

    private void showPageActionSheet(AyahModel ayah) {
        if (layoutPageActionSheet == null || ayah == null) return;
        tvSheetAyahBadge.setText("Ayat " + ayah.getNumberInSurah());
        tvSheetIndo.setText(ayah.getIndo());
        layoutPageActionSheet.setVisibility(View.VISIBLE);

        if (pageAdapter != null) {
            pageAdapter.setActiveAyah(ayah.getNumberInSurah(), false);
        }

        btnSheetClose.setOnClickListener(v -> hidePageActionSheet());

        btnSheetDengar.setOnClickListener(v -> {
            hidePageActionSheet();
            playAyah(ayahs.indexOf(ayah));
        });

        btnSheetKosakata.setOnClickListener(v -> {
            hidePageActionSheet();
            showKosakataDialog(ayah);
        });

        btnSheetHafal.setOnClickListener(v -> {
            hidePageActionSheet();
            startFooterHafalanForAyah(ayah);
        });

        btnSheetTulis.setOnClickListener(v -> {
            hidePageActionSheet();
            Intent intent = new Intent(this, TulisActivity.class);
            intent.putExtra("surah_number", surahNumber);
            intent.putExtra("ayah_number", ayah.getNumberInSurah());
            startActivity(intent);
        });

        btnSheetTafsir.setOnClickListener(v -> {
            hidePageActionSheet();
            Intent intent = new Intent(this, PelajariActivity.class);
            intent.putExtra("surah_number", surahNumber);
            intent.putExtra("ayah_number", ayah.getNumberInSurah());
            startActivity(intent);
        });
    }

    private void hidePageActionSheet() {
        if (layoutPageActionSheet != null) {
            layoutPageActionSheet.setVisibility(View.GONE);
        }
        if (pageAdapter != null && (layoutPageHafalanFooter == null || layoutPageHafalanFooter.getVisibility() != View.VISIBLE)) {
            pageAdapter.setActiveAyah(-1, false);
        }
    }

    // ── COMPACT HAFALAN FOOTER IN PAGE VIEW (Versi 1.0.2 Feature 2 & 3) ──

    private void startFooterHafalanForAyah(AyahModel ayah) {
        if (ayah == null) return;
        footerHafalAyah = ayah;
        isPageHafalMasked = false;

        hidePageActionSheet();
        if (layoutPageHafalanFooter != null) {
            layoutPageHafalanFooter.setVisibility(View.VISIBLE);
        }

        if (pageAdapter != null) {
            pageAdapter.setActiveAyah(ayah.getNumberInSurah(), false);
            int p = pageAdapter.findPositionForAyah(ayah.getNumberInSurah());
            rvPages.smoothScrollToPosition(p);
        }

        tvFooterHafalBadge.setText("🧠 Ayat " + ayah.getNumberInSurah());
        tvFooterHafalIndicator.setText("Siap");
        tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.emerald));
        tvFooterHafalStatus.setText("Ketuk mic untuk rekam (bisa jeda nafas)");
        btnFooterHafalMic.setImageResource(R.drawable.ic_mic);
        btnFooterHafalEye.setText("👁️ Tutup");

        btnFooterHafalMic.setOnClickListener(v -> toggleFooterHafalanRecording());

        btnFooterHafalPlay.setOnClickListener(v -> playFooterAyahAudio(ayah));

        btnFooterHafalEye.setOnClickListener(v -> {
            isPageHafalMasked = !isPageHafalMasked;
            btnFooterHafalEye.setText(isPageHafalMasked ? "👁️ Buka" : "👁️ Tutup");
            if (pageAdapter != null && footerHafalAyah != null) {
                pageAdapter.setActiveAyah(footerHafalAyah.getNumberInSurah(), isPageHafalMasked);
            }
        });

        btnFooterHafalDone.setOnClickListener(v -> finishAndEvaluateFooterHafalan());

        btnFooterHafalClose.setOnClickListener(v -> stopFooterHafalan());
    }

    private void playFooterAyahAudio(AyahModel ayah) {
        if (ayah == null) return;
        try {
            if (footerPreviewPlayer != null) {
                footerPreviewPlayer.release();
                footerPreviewPlayer = null;
            }
            String qariId = PreferenceManager.getQariId(this);
            String url = String.format(Locale.US, "https://mirrors.quranicaudio.com/everyayah/%s/%03d%03d.mp3", qariId, surahNumber, ayah.getNumberInSurah());
            footerPreviewPlayer = new MediaPlayer();
            footerPreviewPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            footerPreviewPlayer.setDataSource(url);
            footerPreviewPlayer.setOnPreparedListener(MediaPlayer::start);
            footerPreviewPlayer.setOnCompletionListener(mp -> {
                mp.release();
                footerPreviewPlayer = null;
            });
            footerPreviewPlayer.prepareAsync();
            Toast.makeText(this, "Memutar audio contoh ayat " + ayah.getNumberInSurah(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopFooterHafalan() {
        stopFooterListening(true);
        if (footerPreviewPlayer != null) {
            try { footerPreviewPlayer.release(); } catch (Exception ignored) {}
            footerPreviewPlayer = null;
        }
        if (layoutPageHafalanFooter != null) {
            layoutPageHafalanFooter.setVisibility(View.GONE);
        }
        if (pageAdapter != null) {
            pageAdapter.setActiveAyah(-1, false);
        }
        footerHafalAyah = null;
        isPageHafalMasked = false;
    }

    private void toggleFooterHafalanRecording() {
        if (isFooterRecordingActive) {
            finishAndEvaluateFooterHafalan();
        } else {
            startFooterListening();
        }
    }

    private void startFooterListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQ_RECORD);
            return;
        }

        if (footerSpeechRecognizer == null) {
            initFooterSpeechRecognizer();
        }

        footerAccumulatedSpeech.setLength(0);
        isFooterRecordingActive = true;
        btnFooterHafalMic.setImageResource(R.drawable.ic_pause);
        tvFooterHafalIndicator.setText("● Merekam...");
        tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.red_error));
        tvFooterHafalStatus.setText("Silakan baca ayat ini...");

        restartFooterListeningIntent();
    }

    private void restartFooterListeningIntent() {
        if (!isFooterRecordingActive || footerSpeechRecognizer == null) return;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ar-SA", "ar-EG", "ar"});
            // Generous silence timeouts for long verses and pauses between breath
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 12000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L);
            footerSpeechRecognizer.startListening(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopFooterListening(boolean resetStatus) {
        isFooterRecordingActive = false;
        if (footerSpeechRecognizer != null) {
            try { footerSpeechRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        if (btnFooterHafalMic != null) {
            btnFooterHafalMic.setImageResource(R.drawable.ic_mic);
        }
        if (resetStatus && tvFooterHafalIndicator != null) {
            tvFooterHafalIndicator.setText("Siap");
            tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.emerald));
        }
    }

    private void finishAndEvaluateFooterHafalan() {
        stopFooterListening(false);
        if (tvFooterHafalIndicator != null) {
            tvFooterHafalIndicator.setText("Selesai Rekam");
            tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
        }
        evaluateFooterHafalan();
    }

    private void initFooterSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Pengenal suara Google tidak tersedia pada perangkat ini.", Toast.LENGTH_LONG).show();
            return;
        }

        footerSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        footerSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (tvFooterHafalIndicator != null) {
                    tvFooterHafalIndicator.setText("● Siap Mendengarkan");
                    tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.emerald));
                }
            }
            @Override public void onBeginningOfSpeech() {
                if (tvFooterHafalIndicator != null) {
                    tvFooterHafalIndicator.setText("● Merekam...");
                    tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.red_error));
                }
            }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                if (isFooterRecordingActive) {
                    if (tvFooterHafalIndicator != null) {
                        tvFooterHafalIndicator.setText("⏳ Jeda Nafas");
                        tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
                    }
                    if (tvFooterHafalStatus != null) {
                        tvFooterHafalStatus.setText("Jeda nafas... Silakan lanjutkan atau ketuk '✓ Nilai'");
                    }
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isFooterRecordingActive) {
                            restartFooterListeningIntent();
                        }
                    }, 300);
                }
            }
            @Override public void onError(int error) {
                if (isFooterRecordingActive && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isFooterRecordingActive) {
                            restartFooterListeningIntent();
                        }
                    }, 400);
                    return;
                }
                stopFooterListening(true);
            }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                if (matches != null && !matches.isEmpty()) {
                    String seg = matches.get(0);
                    if (seg != null && !seg.trim().isEmpty()) {
                        if (footerAccumulatedSpeech.length() > 0) footerAccumulatedSpeech.append(" ");
                        footerAccumulatedSpeech.append(seg.trim());
                        if (tvFooterHafalStatus != null) {
                            tvFooterHafalStatus.setText(footerAccumulatedSpeech.toString().trim());
                        }
                    }
                }
                if (isFooterRecordingActive) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isFooterRecordingActive) {
                            restartFooterListeningIntent();
                        }
                    }, 300);
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partials = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                if (partials != null && !partials.isEmpty()) {
                    String live = partials.get(0);
                    if (live != null && !live.trim().isEmpty()) {
                        String full = (footerAccumulatedSpeech.length() > 0 ? (footerAccumulatedSpeech + " ") : "") + live.trim();
                        if (tvFooterHafalStatus != null) {
                            tvFooterHafalStatus.setText(full);
                        }
                    }
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void evaluateFooterHafalan() {
        if (footerHafalAyah == null) return;
        String spoken = footerAccumulatedSpeech.toString().trim();
        if (spoken.isEmpty()) {
            Toast.makeText(this, "Belum ada suara yang terekam. Silakan ketuk mic dan baca ayat ini.", Toast.LENGTH_SHORT).show();
            return;
        }

        RecitationEvaluator.EvaluationResult res = RecitationEvaluator.evaluate(footerHafalAyah.getArabic(), spoken);
        PreferenceManager.markHafalan(this, surahNumber, footerHafalAyah.getNumberInSurah());
        showHafalanResultDialog(footerHafalAyah, res);
    }

    private void showHafalanResultDialog(AyahModel ayah, RecitationEvaluator.EvaluationResult res) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_hafalan_result);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvAyahBadge = dialog.findViewById(R.id.tv_dlg_result_ayah_badge);
        TextView tvScore = dialog.findViewById(R.id.tv_dlg_result_score);
        TextView tvGrade = dialog.findViewById(R.id.tv_dlg_result_grade);
        TextView tvStats = dialog.findViewById(R.id.tv_dlg_result_stats);
        TextView tvDiff = dialog.findViewById(R.id.tv_dlg_result_diff);
        TextView tvSpoken = dialog.findViewById(R.id.tv_dlg_result_spoken);
        ImageButton btnClose = dialog.findViewById(R.id.btn_dlg_result_close);
        TextView btnRetry = dialog.findViewById(R.id.btn_dlg_result_retry);
        TextView btnNext = dialog.findViewById(R.id.btn_dlg_result_next);

        String sName = currentSurah != null ? currentSurah.getEnglishName() : ("Surat " + surahNumber);
        tvAyahBadge.setText(sName + " : Ayat " + ayah.getNumberInSurah());

        int scorePercent = Math.round(res.accuracy);
        tvScore.setText(scorePercent + "%");

        if (scorePercent >= 85) {
            tvScore.setTextColor(getResources().getColor(R.color.emerald));
            tvGrade.setText("Mumtaz (Sempurna / Sangat Baik)");
            tvGrade.setTextColor(getResources().getColor(R.color.emerald));
        } else if (scorePercent >= 65) {
            tvScore.setTextColor(getResources().getColor(R.color.gold_primary));
            tvGrade.setText("Jayyid (Baik)");
            tvGrade.setTextColor(getResources().getColor(R.color.gold_primary));
        } else {
            tvScore.setTextColor(getResources().getColor(R.color.red_error));
            tvGrade.setText("Perlu Diulang (Dha'if)");
            tvGrade.setTextColor(getResources().getColor(R.color.red_error));
        }

        tvStats.setText("Benar: " + res.correctWords + " kata · Kurang/Salah: " + (res.missingWords + res.wrongWords) + " kata");

        // Format Word Diff with Colored Spans
        Typeface tf = PreferenceManager.getMushafTypeface(this);
        if (tf != null) tvDiff.setTypeface(tf);

        SpannableStringBuilder diffSsb = new SpannableStringBuilder();
        for (RecitationEvaluator.WordDiff wd : res.diffs) {
            int st = diffSsb.length();
            diffSsb.append(wd.targetWord);
            int en = diffSsb.length();
            int color = wd.isCorrect ? 0xFF00FFD5 : 0xFFFF5555;
            diffSsb.setSpan(new ForegroundColorSpan(color), st, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            diffSsb.append(" ");
        }
        tvDiff.setText(diffSsb);

        if (tf != null) tvSpoken.setTypeface(tf);
        tvSpoken.setText(res.spokenText.isEmpty() ? "-" : res.spokenText);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnRetry.setOnClickListener(v -> {
            dialog.dismiss();
            startFooterListening();
        });

        btnNext.setOnClickListener(v -> {
            dialog.dismiss();
            int curAyahNum = ayah.getNumberInSurah();
            if (ayahs != null && curAyahNum < ayahs.size()) {
                AyahModel nextAyah = ayahs.get(curAyahNum);
                startFooterHafalanForAyah(nextAyah);
            } else {
                Toast.makeText(SurahActivity.this, "Alhamdulillah, sudah mencapai akhir surat!", Toast.LENGTH_LONG).show();
            }
        });

        dialog.show();
    }

    // ── AUDIO PLAYER LOGIC ──

    private void playAyah(int index) {
        playAyah(index, false);
    }

    private void playAyah(int index, boolean autoContinue) {
        if (index < 0 || index >= ayahs.size()) return;

        currentPlayingAyahIndex = index;
        AyahModel ayah = ayahs.get(index);

        String surahName = currentSurah != null ? currentSurah.getEnglishName() : QuranRepository.getSurahName(this, surahNumber);
        PreferenceManager.setLastRead(this, surahNumber, surahName, ayah.getNumberInSurah());

        stopAudioInternal();

        if ("page".equalsIgnoreCase(currentViewMode)) {
            if (pageAdapter != null) {
                int pos = pageAdapter.findPositionForAyah(ayah.getNumberInSurah());
                rvPages.smoothScrollToPosition(pos);
                pageAdapter.setActiveAyah(ayah.getNumberInSurah(), false);
            }
        } else {
            if (rvAyahs != null) {
                rvAyahs.smoothScrollToPosition(index);
            }
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
                    karaokeHandler.postDelayed(this, 50);
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

        final int aNum = ayah.getNumberInSurah();
        List<WordModel> cached = QuranRepository.getWordByWord(this, surahNumber, aNum);
        if (cached != null && !cached.isEmpty()) {
            renderDialogWords(flowWords, cached);
        } else {
            renderDialogFallback(flowWords, ayah.getArabic());
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
        stopFooterHafalan();
        if (footerSpeechRecognizer != null) {
            try { footerSpeechRecognizer.destroy(); } catch (Exception ignored) {}
            footerSpeechRecognizer = null;
        }
    }
}

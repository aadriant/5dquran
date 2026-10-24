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
import androidx.recyclerview.widget.LinearSmoothScroller;
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
    private ImageButton btnToggleWbw;
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
    private TextView btnSheetDengar, btnSheetHafal, btnSheetTulis, btnSheetTafsir;
    private AyahModel selectedPageAyah = null;

    // Murojaah Mode (Mengikuti Suara User + Mode Karaoke, auto-advance, timeout 2 menit)
    private View layoutPageHafalanFooter;
    private TextView tvFooterHafalBadge, tvFooterHafalIndicator, tvFooterHafalStatus;
    private ImageButton btnFooterHafalMic, btnFooterHafalPlay, btnFooterHafalClose;
    private TextView btnFooterHafalEye, btnFooterHafalDone;
    private SpeechRecognizer footerSpeechRecognizer;
    private MediaPlayer footerPreviewPlayer;

    private boolean isMurojaahActive = false;
    private boolean isMurojaahPaused = false;
    private int currentMurojaahIndex = 0;
    private AyahModel currentMurojaahAyah = null;
    private int lastMurojaahWordIndex = -1;
    private final Handler murojaahSilenceHandler = new Handler(Looper.getMainLooper());
    private Runnable murojaahSilenceRunnable;
    private static final long MUROJAAH_SILENCE_TIMEOUT_MS = 120000L; // 2 menit

    // Mini Player
    private View bottomPlayerBar;
    private ImageButton btnPlayerToggle;
    private TextView tvPlayerStatus, tvPlayerSub, tvPlayerCurrent;
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
        initMurojaahTimeout();
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
            adapter.setWordByWordEnabled(PreferenceManager.isWordByWord(this));
            adapter.notifyDataSetChanged();
        }
        if (pageAdapter != null) {
            pageAdapter.setWordByWordEnabled(PreferenceManager.isWordByWord(this));
            pageAdapter.notifyDataSetChanged();
        }
        updateWbwButtonUi();
    }

    private void initViews() {
        rvAyahs = findViewById(R.id.rv_ayahs);
        rvPages = findViewById(R.id.rv_pages);
        tvTitleSurah = findViewById(R.id.tv_title_surah);
        tvSubtitleSurah = findViewById(R.id.tv_subtitle_surah);
        cardBismillah = findViewById(R.id.card_bismillah);
        btnSurahAudio = findViewById(R.id.btn_surah_audio);
        btnToggleViewMode = findViewById(R.id.btn_toggle_view_mode);
        btnToggleWbw = findViewById(R.id.btn_toggle_wbw);

        bottomPlayerBar = findViewById(R.id.bottom_player_bar);
        btnPlayerToggle = findViewById(R.id.btn_player_toggle);
        tvPlayerStatus = findViewById(R.id.tv_player_status);
        tvPlayerSub = findViewById(R.id.tv_player_sub);
        tvPlayerCurrent = findViewById(R.id.tv_player_current);
        btnPlayerStop = findViewById(R.id.btn_player_stop);

        // Page Action Sheet
        layoutPageActionSheet = findViewById(R.id.layout_page_action_sheet);
        tvSheetAyahBadge = findViewById(R.id.tv_sheet_ayah_badge);
        tvSheetIndo = findViewById(R.id.tv_sheet_indo);
        btnSheetClose = findViewById(R.id.btn_sheet_close);
        btnSheetDengar = findViewById(R.id.btn_sheet_dengar);
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

        updateWbwButtonUi();
        if (btnToggleWbw != null) {
            btnToggleWbw.setOnClickListener(v -> {
                boolean current = PreferenceManager.isWordByWord(this);
                boolean next = !current;
                PreferenceManager.setWordByWord(this, next);
                updateWbwButtonUi();
                if (adapter != null) {
                    adapter.setWordByWordEnabled(next);
                }
                if (pageAdapter != null) {
                    pageAdapter.setWordByWordEnabled(next);
                }
                if (next) {
                    Toast.makeText(this, "Terjemahan Per Kata: Aktif", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Terjemahan Per Kata: Nonaktif", Toast.LENGTH_SHORT).show();
                }
            });
        }

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
        adapter = new AyahAdapter(this, surahNumber, ayahs, fontSize, (ayah, actionType) -> {
            int index = ayahs.indexOf(ayah);
            String surahName = currentSurah != null ? currentSurah.getEnglishName() : QuranRepository.getSurahName(this, surahNumber);
            PreferenceManager.setLastRead(SurahActivity.this, surahNumber, surahName, ayah.getNumberInSurah());

            switch (actionType) {
                case "dengar":
                    playAyah(index);
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

    private void scrollToAyahAccurate(int index) {
        if (index < 0 || ayahs == null || index >= ayahs.size()) return;
        AyahModel target = ayahs.get(index);

        if ("page".equalsIgnoreCase(currentViewMode)) {
            if (pageAdapter != null && rvPages != null) {
                pageAdapter.setActiveAyah(target.getNumberInSurah(), false);
                pageAdapter.scrollToAyah(rvPages, target.getNumberInSurah());
            }
        } else {
            if (rvAyahs != null) {
                LinearLayoutManager lm = (LinearLayoutManager) rvAyahs.getLayoutManager();
                if (lm != null) {
                    int first = lm.findFirstCompletelyVisibleItemPosition();
                    int last = lm.findLastCompletelyVisibleItemPosition();
                    if (index >= first && index <= last && index != 0) {
                        return;
                    }
                    LinearSmoothScroller scroller = new LinearSmoothScroller(this) {
                        @Override
                        protected int getVerticalSnapPreference() {
                            return SNAP_TO_START;
                        }
                    };
                    scroller.setTargetPosition(index);
                    lm.startSmoothScroll(scroller);
                }
            }
        }
    }

    private void scrollToAyah(int ayahNum) {
        if (ayahs != null && ayahNum >= 1 && ayahNum <= ayahs.size()) {
            scrollToAyahAccurate(ayahNum - 1);
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
            stopMurojaah();

            if (targetAyah > 1 && ayahs != null && targetAyah <= ayahs.size()) {
                rvAyahs.scrollToPosition(targetAyah - 1);
            }
            if (notifyToast) {
                Toast.makeText(this, "Mode Tampilan Per Ayat", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateWbwButtonUi() {
        if (btnToggleWbw == null) return;
        boolean isWbw = PreferenceManager.isWordByWord(this);
        if (isWbw) {
            btnToggleWbw.setColorFilter(ContextCompat.getColor(this, R.color.gold_primary));
            btnToggleWbw.setBackgroundResource(R.drawable.bg_chip_action_active);
            btnToggleWbw.setContentDescription("Terjemahan Per Kata: Aktif (Ketuk untuk mematikan)");
        } else {
            btnToggleWbw.setColorFilter(Color.parseColor("#94A3B8"));
            btnToggleWbw.setBackgroundResource(0);
            btnToggleWbw.setContentDescription("Terjemahan Per Kata: Nonaktif (Ketuk untuk mengaktifkan)");
        }
    }

    // ── PAGE VIEW AYAH INTERACTION & ACTION SHEET ──

    private void onPageAyahClicked(AyahModel ayah) {
        if (ayah == null) return;
        selectedPageAyah = ayah;

        if (layoutPageHafalanFooter != null && layoutPageHafalanFooter.getVisibility() == View.VISIBLE) {
            startMurojaahForAyah(ayah);
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

        btnSheetHafal.setOnClickListener(v -> {
            hidePageActionSheet();
            startMurojaahForAyah(ayah);
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

    // ── MUROJAAH MODE (Versi 1.0.4 Feature 4: Karaoke Suara User, Auto-Advance, 2 Menit Timeout) ──

    private void initMurojaahTimeout() {
        murojaahSilenceRunnable = () -> {
            if (isMurojaahActive && !isMurojaahPaused) {
                pauseMurojaah();
                if (tvFooterHafalIndicator != null) {
                    tvFooterHafalIndicator.setText("⏸️ Dijeda (2 Menit)");
                    tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
                }
                if (tvFooterHafalStatus != null) {
                    tvFooterHafalStatus.setText("Murojaah dijeda otomatis karena 2 menit tanpa suara. Ketuk tombol mic untuk lanjut.");
                }
                Toast.makeText(SurahActivity.this, "Murojaah dijeda otomatis: 2 menit tanpa suara", Toast.LENGTH_LONG).show();
            }
        };
    }

    private void resetMurojaahSilenceTimeout() {
        if (murojaahSilenceHandler != null && murojaahSilenceRunnable != null) {
            murojaahSilenceHandler.removeCallbacks(murojaahSilenceRunnable);
            if (isMurojaahActive && !isMurojaahPaused) {
                murojaahSilenceHandler.postDelayed(murojaahSilenceRunnable, MUROJAAH_SILENCE_TIMEOUT_MS);
            }
        }
    }

    private void cancelMurojaahSilenceTimeout() {
        if (murojaahSilenceHandler != null && murojaahSilenceRunnable != null) {
            murojaahSilenceHandler.removeCallbacks(murojaahSilenceRunnable);
        }
    }

    public void startMurojaahForAyah(AyahModel ayah) {
        if (ayah == null) return;
        stopAudio();
        hidePageActionSheet();

        int targetIdx = 0;
        if (ayahs != null) {
            for (int i = 0; i < ayahs.size(); i++) {
                if (ayahs.get(i).getNumberInSurah() == ayah.getNumberInSurah()) {
                    targetIdx = i;
                    currentMurojaahAyah = ayahs.get(i);
                    break;
                }
            }
        }
        if (currentMurojaahAyah == null) currentMurojaahAyah = ayah;
        currentMurojaahIndex = targetIdx;
        isMurojaahActive = true;
        isMurojaahPaused = false;
        lastMurojaahWordIndex = -1;

        if (layoutPageHafalanFooter != null) {
            layoutPageHafalanFooter.setVisibility(View.VISIBLE);
        }

        // Teks ayat selalu terbuka (mode karaoke qari), sembunyikan toggle mata & tombol nilai manual
        if (btnFooterHafalEye != null) btnFooterHafalEye.setVisibility(View.GONE);
        if (btnFooterHafalDone != null) btnFooterHafalDone.setVisibility(View.GONE);

        if (tvFooterHafalBadge != null) tvFooterHafalBadge.setText("🔄 Ayat " + ayah.getNumberInSurah());
        if (tvFooterHafalIndicator != null) {
            tvFooterHafalIndicator.setText("● Murojaah Aktif");
            tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.emerald));
        }
        if (tvFooterHafalStatus != null) {
            tvFooterHafalStatus.setText("Silakan baca ayat ini... Kata akan menyala sesuai suara Anda");
        }
        if (btnFooterHafalMic != null) btnFooterHafalMic.setImageResource(R.drawable.ic_pause);

        if (pageAdapter != null) {
            pageAdapter.setActiveAyah(ayah.getNumberInSurah(), false);
            pageAdapter.clearKaraokeHighlight(rvPages);
        }
        if (adapter != null && currentMurojaahIndex >= 0 && ayahs != null && currentMurojaahIndex < ayahs.size()) {
            adapter.clearKaraokeHighlight(rvAyahs);
        }
        scrollToAyahAccurate(currentMurojaahIndex);

        btnFooterHafalMic.setOnClickListener(v -> toggleMurojaahPause());
        btnFooterHafalPlay.setOnClickListener(v -> playFooterAyahAudio(currentMurojaahAyah));
        btnFooterHafalClose.setOnClickListener(v -> stopMurojaah());

        resetMurojaahSilenceTimeout();
        startMurojaahListening();
    }

    private void toggleMurojaahPause() {
        if (!isMurojaahActive) return;
        if (isMurojaahPaused) {
            resumeMurojaah();
        } else {
            pauseMurojaah();
        }
    }

    private void pauseMurojaah() {
        isMurojaahPaused = true;
        cancelMurojaahSilenceTimeout();
        if (footerSpeechRecognizer != null) {
            try { footerSpeechRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        if (btnFooterHafalMic != null) {
            btnFooterHafalMic.setImageResource(R.drawable.ic_mic);
        }
        if (tvFooterHafalIndicator != null) {
            tvFooterHafalIndicator.setText("⏸️ Dijeda");
            tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
        }
        if (tvFooterHafalStatus != null) {
            tvFooterHafalStatus.setText("Murojaah dijeda. Ketuk tombol mic untuk melanjutkan.");
        }
    }

    private void resumeMurojaah() {
        if (!isMurojaahActive) return;
        isMurojaahPaused = false;
        if (btnFooterHafalMic != null) {
            btnFooterHafalMic.setImageResource(R.drawable.ic_pause);
        }
        if (tvFooterHafalIndicator != null) {
            tvFooterHafalIndicator.setText("● Murojaah Aktif");
            tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.emerald));
        }
        if (tvFooterHafalStatus != null) {
            tvFooterHafalStatus.setText("Melanjutkan murojaah dari ayat " + (currentMurojaahAyah != null ? currentMurojaahAyah.getNumberInSurah() : "") + "...");
        }
        resetMurojaahSilenceTimeout();
        restartMurojaahListeningIntent();
    }

    private void stopMurojaah() {
        isMurojaahActive = false;
        isMurojaahPaused = false;
        cancelMurojaahSilenceTimeout();

        if (footerSpeechRecognizer != null) {
            try { footerSpeechRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        if (footerPreviewPlayer != null) {
            try { footerPreviewPlayer.release(); } catch (Exception ignored) {}
            footerPreviewPlayer = null;
        }
        if (layoutPageHafalanFooter != null) {
            layoutPageHafalanFooter.setVisibility(View.GONE);
        }
        if (pageAdapter != null) {
            pageAdapter.clearKaraokeHighlight(rvPages);
            pageAdapter.setActiveAyah(-1, false);
        }
        if (adapter != null) {
            adapter.clearKaraokeHighlight(rvAyahs);
        }
        currentMurojaahAyah = null;
    }

    private void advanceMurojaahToNextAyah() {
        if (!isMurojaahActive || isMurojaahPaused) return;

        if (currentMurojaahAyah != null) {
            PreferenceManager.markHafalan(this, surahNumber, currentMurojaahAyah.getNumberInSurah());
        }

        if (ayahs != null && currentMurojaahIndex < ayahs.size() - 1) {
            currentMurojaahIndex++;
            currentMurojaahAyah = ayahs.get(currentMurojaahIndex);
            lastMurojaahWordIndex = -1;
            resetMurojaahSilenceTimeout();

            if (tvFooterHafalBadge != null) {
                tvFooterHafalBadge.setText("🔄 Ayat " + currentMurojaahAyah.getNumberInSurah());
            }
            if (tvFooterHafalStatus != null) {
                tvFooterHafalStatus.setText("Lanjut ayat " + currentMurojaahAyah.getNumberInSurah() + "... Teruskan membaca");
            }

            if (pageAdapter != null) {
                pageAdapter.clearKaraokeHighlight(rvPages);
                pageAdapter.setActiveAyah(currentMurojaahAyah.getNumberInSurah(), false);
            }
            if (adapter != null) {
                adapter.clearKaraokeHighlight(rvAyahs);
            }
            scrollToAyahAccurate(currentMurojaahIndex);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isMurojaahActive && !isMurojaahPaused) {
                    restartMurojaahListeningIntent();
                }
            }, 300);
        } else {
            stopMurojaah();
            Toast.makeText(this, "🎉 Masya Allah! Selesai murojaah seluruh surah ini.", Toast.LENGTH_LONG).show();
        }
    }

    private void startMurojaahListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQ_RECORD);
            return;
        }

        if (footerSpeechRecognizer == null) {
            initFooterSpeechRecognizer();
        }

        restartMurojaahListeningIntent();
    }

    private void restartMurojaahListeningIntent() {
        if (!isMurojaahActive || isMurojaahPaused || footerSpeechRecognizer == null) return;
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ar-SA", "ar-EG", "ar"});
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 120000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 120000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000L);
            footerSpeechRecognizer.startListening(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_RECORD && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (isMurojaahActive && currentMurojaahAyah != null) {
                startMurojaahListening();
            }
        } else if (requestCode == PERMISSION_REQ_RECORD) {
            Toast.makeText(this, "Izin mikrofon diperlukan untuk murojaah suara", Toast.LENGTH_SHORT).show();
            stopMurojaah();
        }
    }

    private void initFooterSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Pengenal suara Google tidak tersedia pada perangkat ini.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            footerSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        } catch (Throwable t) {
            t.printStackTrace();
            Toast.makeText(this, "Gagal mengaktifkan pengenal suara: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        footerSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                resetMurojaahSilenceTimeout();
                if (tvFooterHafalIndicator != null && isMurojaahActive && !isMurojaahPaused) {
                    tvFooterHafalIndicator.setText("● Siap Mendengarkan");
                    tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.emerald));
                }
            }
            @Override public void onBeginningOfSpeech() {
                resetMurojaahSilenceTimeout();
                if (tvFooterHafalIndicator != null && isMurojaahActive && !isMurojaahPaused) {
                    tvFooterHafalIndicator.setText("● Suara Terdeteksi");
                    tvFooterHafalIndicator.setTextColor(getResources().getColor(R.color.red_error));
                }
            }
            @Override public void onRmsChanged(float rmsdB) {
                if (rmsdB > 2.0f) resetMurojaahSilenceTimeout();
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                if (isMurojaahActive && !isMurojaahPaused) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isMurojaahActive && !isMurojaahPaused) {
                            restartMurojaahListeningIntent();
                        }
                    }, 250);
                }
            }
            @Override public void onError(int error) {
                if (isMurojaahActive && !isMurojaahPaused) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isMurojaahActive && !isMurojaahPaused) {
                            restartMurojaahListeningIntent();
                        }
                    }, 350);
                }
            }
            @Override public void onResults(Bundle results) {
                resetMurojaahSilenceTimeout();
                if (!isMurojaahActive || isMurojaahPaused || currentMurojaahAyah == null) return;
                ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                if (matches != null && !matches.isEmpty()) {
                    String finalSpoken = matches.get(0).trim();
                    if (!finalSpoken.isEmpty()) {
                        if (tvFooterHafalStatus != null) {
                            tvFooterHafalStatus.setText("🎙️ " + finalSpoken);
                        }
                        int wordIdx = RecitationEvaluator.findSpokenWordIndex(currentMurojaahAyah.getArabic(), finalSpoken, lastMurojaahWordIndex);
                        if (wordIdx >= 0) {
                            lastMurojaahWordIndex = wordIdx;
                            if (pageAdapter != null) {
                                pageAdapter.setKaraokeHighlight(rvPages, currentMurojaahAyah.getNumberInSurah(), wordIdx);
                            }
                            if (adapter != null && currentMurojaahIndex >= 0) {
                                adapter.setKaraokeHighlight(rvAyahs, currentMurojaahIndex, wordIdx);
                            }
                        }
                        String[] targetWords = currentMurojaahAyah.getArabic().trim().split("\\s+");
                        RecitationEvaluator.EvaluationResult res = RecitationEvaluator.evaluate(currentMurojaahAyah.getArabic(), finalSpoken);
                        if (res.accuracy >= 65f || (wordIdx >= targetWords.length - 1 && targetWords.length > 0)) {
                            advanceMurojaahToNextAyah();
                            return;
                        }
                    }
                }
                if (isMurojaahActive && !isMurojaahPaused) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (isMurojaahActive && !isMurojaahPaused) {
                            restartMurojaahListeningIntent();
                        }
                    }, 250);
                }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                resetMurojaahSilenceTimeout();
                if (!isMurojaahActive || isMurojaahPaused || currentMurojaahAyah == null) return;
                ArrayList<String> partials = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                if (partials != null && !partials.isEmpty()) {
                    String live = partials.get(0).trim();
                    if (!live.isEmpty()) {
                        if (tvFooterHafalStatus != null) {
                            tvFooterHafalStatus.setText("🎙️ " + live);
                        }
                        int wordIdx = RecitationEvaluator.findSpokenWordIndex(currentMurojaahAyah.getArabic(), live, lastMurojaahWordIndex);
                        if (wordIdx >= 0) {
                            lastMurojaahWordIndex = wordIdx;
                            if (pageAdapter != null) {
                                pageAdapter.setKaraokeHighlight(rvPages, currentMurojaahAyah.getNumberInSurah(), wordIdx);
                            }
                            if (adapter != null && currentMurojaahIndex >= 0) {
                                adapter.setKaraokeHighlight(rvAyahs, currentMurojaahIndex, wordIdx);
                            }
                        }
                        String[] targetWords = currentMurojaahAyah.getArabic().trim().split("\\s+");
                        if (wordIdx >= targetWords.length - 1 && targetWords.length > 0) {
                            RecitationEvaluator.EvaluationResult res = RecitationEvaluator.evaluate(currentMurojaahAyah.getArabic(), live);
                            if (res.accuracy >= 60f) {
                                advanceMurojaahToNextAyah();
                            }
                        }
                    }
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
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

        scrollToAyahAccurate(index);

        String qariId = PreferenceManager.getQariId(this);
        String qariName = PreferenceManager.getQariName(this);
        String primaryUrl = String.format(Locale.US, "https://mirrors.quranicaudio.com/everyayah/%s/%03d%03d.mp3", qariId, surahNumber, ayah.getNumberInSurah());
        String fallbackUrl = String.format(Locale.US, "https://everyayah.com/data/%s/%03d%03d.mp3", qariId, surahNumber, ayah.getNumberInSurah());

        tvPlayerStatus.setText("Memutar: " + surahName + " Ayat " + ayah.getNumberInSurah());
        if (tvPlayerSub != null) {
            tvPlayerSub.setText(qariName + " · Menyimak bacaan qari");
        }
        if (tvPlayerCurrent != null) {
            tvPlayerCurrent.setVisibility(View.GONE);
        }
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
                currentWordTimings = estimateWordTimings(ayah, dur);
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
            if (pageAdapter != null) {
                pageAdapter.setKaraokeHighlight(rvPages, ayah.getNumberInSurah(), curIdx);
            }
        }
    }

    private List<WordTiming> estimateWordTimings(AyahModel ayah, long durationMs) {
        List<WordTiming> list = new ArrayList<>();
        if (ayah == null) return list;

        List<String> words = new ArrayList<>();
        if (PreferenceManager.isWordByWord(this)) {
            List<WordModel> wbw = QuranRepository.getWordByWord(this, surahNumber, ayah.getNumberInSurah());
            if (wbw != null && !wbw.isEmpty()) {
                for (WordModel w : wbw) {
                    words.add(w.getArabic());
                }
            }
        }
        if (words.isEmpty()) {
            String text = ayah.getArabic();
            if (text != null && !text.trim().isEmpty()) {
                for (String t : text.trim().split("\\s+")) {
                    if (!t.isEmpty()) words.add(t);
                }
            }
        }
        if (words.isEmpty()) return list;

        String diacritics = "[\u064B-\u065F\u0610-\u061A\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]";
        int[] weights = new int[words.size()];
        int totalWeight = 0;
        for (int i = 0; i < words.size(); i++) {
            String stripped = words.get(i).replaceAll(diacritics, "");
            weights[i] = Math.max(stripped.length(), 1);
            totalWeight += weights[i];
        }
        if (totalWeight == 0) totalWeight = words.size();

        long leadIn = (long) Math.min(durationMs * 0.05f, 300f);
        long spoken = Math.max(durationMs - leadIn, 100);
        float gapRatio = 0.07f;

        long elapsed = leadIn;
        for (int i = 0; i < words.size(); i++) {
            float fraction = (float) weights[i] / totalWeight;
            long talkDur = (long) (fraction * spoken * (1f - gapRatio));
            long gapDur = (long) (fraction * spoken * gapRatio);
            long start = elapsed;
            elapsed += talkDur + gapDur;
            list.add(new WordTiming(words.get(i), start, elapsed));
        }
        return list;
    }

    private void stopAudioInternal() {
        stopKaraokeTicker();
        if (adapter != null) {
            adapter.clearKaraokeHighlight(rvAyahs);
        }
        if (pageAdapter != null) {
            pageAdapter.clearKaraokeHighlight(rvPages);
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
        if (tvPlayerSub != null) {
            tvPlayerSub.setText("Pilih ayat untuk memutar suara per ayat");
        }
        if (tvPlayerCurrent != null) {
            tvPlayerCurrent.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAudio();
        stopMurojaah();
        if (footerSpeechRecognizer != null) {
            try { footerSpeechRecognizer.destroy(); } catch (Exception ignored) {}
            footerSpeechRecognizer = null;
        }
    }
}

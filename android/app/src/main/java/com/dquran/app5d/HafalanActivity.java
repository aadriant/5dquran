package com.dquran.app5d;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HafalanActivity extends AppCompatActivity {

    private static final int PERMISSION_REQ_CODE = 200;

    // Scoring constants (from update.md §13)
    private static final float BOBOT_NEAR    = 0.75f;
    private static final float PENALTI_WRONG = 0.25f;
    private static final float AMBANG_MIRIP  = 0.6f;

    private int surahNumber = 1;
    private int ayahNumber = 1;
    private List<AyahModel> ayahs;
    private AyahModel currentAyah;
    private String surahName = "";

    private TextView tvHafalTitle, tvCurrentAyahBadge;
    private TextView tvTargetArabic, tvTargetIndo;
    private TextView btnToggleHide;
    private TextView btnToggleTerusan;
    private FrameLayout btnMic;
    private ImageView ivMicIcon;
    private TextView tvMicStatus;

    // Mode Terusan (Continuous setoran) state & 2 minute silence timeout
    private boolean isTerusan = false;
    private boolean isTransitioningToNext = false;
    private final Handler autoNextHandler = new Handler(Looper.getMainLooper());
    private final Handler silenceTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable silenceTimeoutRunnable;
    private static final long SILENCE_TIMEOUT_MS = 120000L; // 2 menit (120 detik)

    // Live speech tracking & Periksa button
    private View cardLiveSpeech;
    private TextView tvLiveRecordingIndicator;
    private TextView tvLiveSpeech;
    private View layoutActionPeriksaHafalan;
    private TextView btnPeriksaHafalan;
    private String lastSpokenText = "";

    private View cardHafalResult;
    private TextView tvScoreBadge, tvUserRecitation, tvCorrectionFeedback;
    private TextView tvWordDiff;

    // Audio Comparison Section (User Recording vs Qari)
    private View layoutAudioComparison;
    private View btnRecordUserAudio;
    private ImageView ivRecordIcon;
    private TextView tvRecordLabel;
    private View btnPlayUserRecording;
    private ImageView ivUserPlayIcon;
    private TextView tvUserPlayLabel;

    private View btnListenCorrect;
    private ImageView ivQariPlayIcon;
    private TextView tvQariPlayLabel;
    private TextView tvAudioPlayStatus;

    // Speech & Media Recording (Separate clean flows: mic = live AI karaoke evaluation, audio section = pure recording & playback)
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isHidden = false;
    private boolean isRecordingSessionActive = false;
    private StringBuilder accumulatedSpeech = new StringBuilder();
    private int lastLiveWordIndex = -1;

    private MediaRecorder mediaRecorder;
    private File recordedAudioFile;
    private boolean isMediaRecording = false;

    // Players
    private MediaPlayer qariPlayer;
    private MediaPlayer userPlayer;
    private boolean isPlayingUserAudio = false;
    private boolean isPlayingQariAudio = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hafalan);

        surahNumber = getIntent().getIntExtra("surah_number", 1);
        ayahNumber  = getIntent().getIntExtra("ayah_number", 1);

        initSilenceTimeout();
        initViews();
        loadAyahs();
        initSpeechRecognizer();
    }

    private void initViews() {
        tvHafalTitle        = findViewById(R.id.tv_hafal_title);
        tvCurrentAyahBadge  = findViewById(R.id.tv_current_ayah_badge);
        tvTargetArabic      = findViewById(R.id.tv_target_arabic);
        tvTargetIndo        = findViewById(R.id.tv_target_indo);
        btnToggleHide       = findViewById(R.id.btn_toggle_hide);
        btnToggleTerusan    = findViewById(R.id.btn_toggle_terusan);

        isTerusan = PreferenceManager.isHafalanTerusan(this);
        updateTerusanUi();
        if (btnToggleTerusan != null) {
            btnToggleTerusan.setOnClickListener(v -> {
                isTerusan = !isTerusan;
                PreferenceManager.setHafalanTerusan(this, isTerusan);
                updateTerusanUi();
                if (isTerusan) {
                    Toast.makeText(this, "Mode Terusan Aktif: lanjut otomatis setelah selesai & benar", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Mode Terusan Nonaktif", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnMic      = findViewById(R.id.btn_mic);
        ivMicIcon   = findViewById(R.id.iv_mic_icon);
        tvMicStatus = findViewById(R.id.tv_mic_status);

        cardLiveSpeech              = findViewById(R.id.card_live_speech);
        tvLiveRecordingIndicator    = findViewById(R.id.tv_live_recording_indicator);
        tvLiveSpeech                = findViewById(R.id.tv_live_speech);
        layoutActionPeriksaHafalan  = findViewById(R.id.layout_action_periksa_hafalan);
        btnPeriksaHafalan           = findViewById(R.id.btn_periksa_hafalan);

        cardHafalResult      = findViewById(R.id.card_hafal_result);
        tvScoreBadge         = findViewById(R.id.tv_score_badge);
        tvUserRecitation     = findViewById(R.id.tv_user_recitation);
        tvCorrectionFeedback = findViewById(R.id.tv_correction_feedback);
        tvWordDiff           = null;

        // Audio Comparison Section
        layoutAudioComparison = findViewById(R.id.layout_audio_comparison);
        btnRecordUserAudio    = findViewById(R.id.btn_record_user_audio);
        ivRecordIcon          = findViewById(R.id.iv_record_icon);
        tvRecordLabel         = findViewById(R.id.tv_record_label);

        btnPlayUserRecording  = findViewById(R.id.btn_play_user_recording);
        ivUserPlayIcon        = findViewById(R.id.iv_user_play_icon);
        tvUserPlayLabel       = findViewById(R.id.tv_user_play_label);

        btnListenCorrect  = findViewById(R.id.btn_listen_correct);
        ivQariPlayIcon    = findViewById(R.id.iv_qari_play_icon);
        tvQariPlayLabel   = findViewById(R.id.tv_qari_play_label);
        tvAudioPlayStatus = findViewById(R.id.tv_audio_play_status);

        if (btnRecordUserAudio != null) {
            btnRecordUserAudio.setOnClickListener(v -> {
                if (isMediaRecording) {
                    stopUserAudioRecording();
                } else {
                    checkPermissionAndStartUserRecording();
                }
            });
        }

        if (btnPlayUserRecording != null) {
            btnPlayUserRecording.setOnClickListener(v -> {
                if (isPlayingUserAudio) {
                    stopUserPlayback();
                } else {
                    playUserRecordedAudio();
                }
            });
        }

        if (btnListenCorrect != null) {
            btnListenCorrect.setOnClickListener(v -> {
                if (isPlayingQariAudio) {
                    stopQariPlayback();
                } else {
                    playQariAudio();
                }
            });
        }

        findViewById(R.id.btn_back_hafal).setOnClickListener(v -> finish());

        findViewById(R.id.btn_prev_ayah).setOnClickListener(v -> {
            if (ayahNumber > 1) {
                autoNextHandler.removeCallbacksAndMessages(null);
                cancelSilenceTimeout();
                isTransitioningToNext = false;
                stopListeningAndRecording();
                stopAllAudio();
                ayahNumber--;
                displayAyah();
            }
        });

        findViewById(R.id.btn_next_ayah).setOnClickListener(v -> {
            if (ayahNumber < ayahs.size()) {
                autoNextHandler.removeCallbacksAndMessages(null);
                cancelSilenceTimeout();
                isTransitioningToNext = false;
                stopListeningAndRecording();
                stopAllAudio();
                ayahNumber++;
                displayAyah();
            }
        });

        btnToggleHide.setOnClickListener(v -> {
            isHidden = !isHidden;
            PreferenceManager.setHafalanDenganContoh(this, !isHidden);
            btnToggleHide.setText(isHidden ? "👁️ Tampilkan" : "👁️ Sembunyikan");
            updateTargetArabicKaraoke(lastLiveWordIndex);
        });

        btnMic.setOnClickListener(v -> {
            if (isListening || isRecordingSessionActive || isMediaRecording) {
                autoNextHandler.removeCallbacksAndMessages(null);
                cancelSilenceTimeout();
                isTransitioningToNext = false;
                stopListeningAndRecording();
                tvMicStatus.setText("Setor hafalan dihentikan manual.");
            } else {
                checkPermissionAndStartListening();
            }
        });

        if (btnPeriksaHafalan != null) {
            btnPeriksaHafalan.setOnClickListener(v -> {
                if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                    if (isTerusan && isRecitationCompleteAndCorrect(lastSpokenText.trim())) {
                        handleAyahCompleteAndCorrect(lastSpokenText.trim());
                    } else {
                        evaluateRecitation(lastSpokenText.trim());
                    }
                } else {
                    Toast.makeText(this, "Silakan baca ayat ini terlebih dahulu", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void loadAyahs() {
        ayahs = QuranRepository.getAyahs(this, surahNumber);
        if (ayahs.isEmpty()) {
            Toast.makeText(this, "Data ayat tidak ditemukan", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (ayahNumber < 1 || ayahNumber > ayahs.size()) ayahNumber = 1;

        surahName = QuranRepository.getSurahName(this, surahNumber);
        displayAyah();
    }

    private void displayAyah() {
        currentAyah = ayahs.get(ayahNumber - 1);
        String displayName = surahName.isEmpty() ? "Surah " + surahNumber : surahName;
        tvHafalTitle.setText("Setor Hafalan: " + displayName);
        tvCurrentAyahBadge.setText(displayName + " : Ayat " + ayahNumber);

        boolean denganContoh = PreferenceManager.isHafalanDenganContoh(this);
        isHidden = !denganContoh;
        btnToggleHide.setText(isHidden ? "👁️ Tampilkan" : "👁️ Sembunyikan");
        lastLiveWordIndex = -1;
        updateTargetArabicKaraoke(-1);

        applyTypeface();

        tvTargetIndo.setText(currentAyah.getIndo());
        if (cardHafalResult != null) cardHafalResult.setVisibility(View.GONE);
        if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.GONE);
        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.GONE);
        if (tvWordDiff != null) tvWordDiff.setVisibility(View.GONE);
        tvMicStatus.setText("Ketuk mikrofon & baca ayat ini dengan tartil");
        lastSpokenText = "";

        // Save last hafalan position
        PreferenceManager.setLastHafalan(this, surahNumber, surahName, ayahNumber);

        // Load saved evaluation if available!
        loadSavedHafalanEvaluation(surahNumber, ayahNumber);

        // Check if an existing recording exists for this ayah
        setupRecordedAudioFile();
        updateAudioComparisonUi();
    }

    private void applyTypeface() {
        Typeface tf = PreferenceManager.getMushafTypeface(this);
        if (tvTargetArabic != null) {
            tvTargetArabic.setTypeface(tf);
            tvTargetArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvTargetArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvTargetArabic.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            tvTargetArabic.setGravity(android.view.Gravity.RIGHT);
        }
        if (tvLiveSpeech != null) {
            tvLiveSpeech.setTypeface(tf);
            tvLiveSpeech.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvLiveSpeech.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvLiveSpeech.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            tvLiveSpeech.setGravity(android.view.Gravity.RIGHT);
        }
        if (tvUserRecitation != null) {
            tvUserRecitation.setTypeface(tf);
            tvUserRecitation.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvUserRecitation.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvUserRecitation.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            tvUserRecitation.setGravity(android.view.Gravity.RIGHT);
        }
        if (tvWordDiff != null) {
            tvWordDiff.setTypeface(tf);
            tvWordDiff.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvWordDiff.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvWordDiff.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            tvWordDiff.setGravity(android.view.Gravity.RIGHT);
        }
    }

    private void loadSavedHafalanEvaluation(int surah, int ayah) {
        if (PreferenceManager.hasHafalanEvaluation(this, surah, ayah)) {
            int score = PreferenceManager.getHafalanScore(this, surah, ayah);
            String scoreText = PreferenceManager.getHafalanScoreText(this, surah, ayah);
            String feedback = PreferenceManager.getHafalanFeedback(this, surah, ayah);
            String userText = PreferenceManager.getHafalanUserText(this, surah, ayah);

            if (cardHafalResult != null) cardHafalResult.setVisibility(View.VISIBLE);
            if (tvScoreBadge != null) {
                tvScoreBadge.setText(scoreText);
                if (score >= 90) {
                    tvScoreBadge.setTextColor(getResources().getColor(R.color.emerald));
                } else if (score >= 70) {
                    tvScoreBadge.setTextColor(getResources().getColor(R.color.gold_primary));
                } else {
                    tvScoreBadge.setTextColor(getResources().getColor(R.color.red_error));
                }
            }
            if (tvCorrectionFeedback != null) {
                tvCorrectionFeedback.setText(feedback);
                if (score >= 90) {
                    tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.emerald));
                } else if (score >= 70) {
                    tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.gold_primary));
                } else {
                    tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.red_error));
                }
            }
            if (tvUserRecitation != null && userText != null && !userText.trim().isEmpty()) {
                applyRecitationColors(userText);
            }
            if (layoutActionPeriksaHafalan != null) {
                layoutActionPeriksaHafalan.setVisibility(View.GONE);
            }
        } else {
            if (cardHafalResult != null) cardHafalResult.setVisibility(View.GONE);
        }
    }

    private void setupRecordedAudioFile() {
        File dir = new File(getFilesDir(), "hafalan_audio");
        if (!dir.exists()) dir.mkdirs();
        File m4aFile = new File(dir, "recitation_s" + surahNumber + "_a" + ayahNumber + ".m4a");

        // Migrasikan file dari cache dir lama jika ada
        File oldCacheFile = new File(new File(getCacheDir(), "hafalan_audio"), "recitation_s" + surahNumber + "_a" + ayahNumber + ".m4a");
        if (!m4aFile.exists() && oldCacheFile.exists() && oldCacheFile.length() > 1024) {
            try {
                oldCacheFile.renameTo(m4aFile);
            } catch (Exception ignored) {}
        }
        recordedAudioFile = m4aFile;
    }

    private void updateTerusanUi() {
        if (btnToggleTerusan == null) return;
        if (isTerusan) {
            btnToggleTerusan.setText("🔁 Mode Terusan: Ya (Auto)");
            btnToggleTerusan.setTextColor(0xFF10B981);
            btnToggleTerusan.setBackgroundResource(R.drawable.bg_chip_action_active);
        } else {
            btnToggleTerusan.setText("🔁 Mode Terusan: Tidak");
            btnToggleTerusan.setTextColor(getResources().getColor(R.color.text_secondary));
            btnToggleTerusan.setBackgroundResource(R.drawable.bg_chip_action);
        }
    }

    private void initSilenceTimeout() {
        silenceTimeoutRunnable = () -> {
            if (isRecordingSessionActive || isListening) {
                stopListeningAndRecording();
                tvMicStatus.setText("⏱️ Rekaman otomatis dihentikan (tidak ada suara > 2 menit).");
                Toast.makeText(HafalanActivity.this, "Rekaman dihentikan: tidak ada suara lebih dari 2 menit", Toast.LENGTH_LONG).show();
            }
        };
    }

    private void resetSilenceTimeout() {
        if (silenceTimeoutHandler != null && silenceTimeoutRunnable != null) {
            silenceTimeoutHandler.removeCallbacks(silenceTimeoutRunnable);
            if (isRecordingSessionActive) {
                silenceTimeoutHandler.postDelayed(silenceTimeoutRunnable, SILENCE_TIMEOUT_MS);
            }
        }
    }

    private void cancelSilenceTimeout() {
        if (silenceTimeoutHandler != null && silenceTimeoutRunnable != null) {
            silenceTimeoutHandler.removeCallbacks(silenceTimeoutRunnable);
        }
    }

    private boolean isRecitationCompleteAndCorrect(String spokenText) {
        if (currentAyah == null || spokenText == null || spokenText.trim().isEmpty()) return false;
        String refNorm = normAr(currentAyah.getArabic());
        String spkNorm = normAr(spokenText);
        String[] refWords = refNorm.split("\\s+");
        String[] spkWords = spkNorm.isEmpty() ? new String[0] : spkNorm.split("\\s+");
        if (refWords.length == 0 || spkWords.length == 0) return false;

        boolean[] refMatched = new boolean[refWords.length];
        boolean[] spkMatched = new boolean[spkWords.length];

        for (int i = 0; i < refWords.length; i++) {
            float bestSim = -1f;
            int bestJ = -1;
            for (int j = 0; j < spkWords.length; j++) {
                if (spkMatched[j]) continue;
                float sim = similarity(refWords[i], spkWords[j]);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestJ = j;
                }
            }
            if (bestJ >= 0 && bestSim >= AMBANG_MIRIP) {
                refMatched[i] = true;
                spkMatched[bestJ] = true;
            }
        }

        int wrongCount = 0;
        for (int j = 0; j < spkWords.length; j++) {
            if (!spkMatched[j]) wrongCount++;
        }
        int missingCount = 0;
        for (boolean m : refMatched) {
            if (!m) missingCount++;
        }

        return (wrongCount == 0 && missingCount == 0 && refWords.length > 0);
    }

    private void handleAyahCompleteAndCorrect(String text) {
        if (isTransitioningToNext) return;
        isTransitioningToNext = true;

        // 1. Hentikan rekaman audio jika ada yang aktif
        if (isMediaRecording) {
            stopUserAudioRecording();
        }

        // 2. Simpan progress hafalan seketika
        PreferenceManager.markHafalan(this, surahNumber, ayahNumber);

        // 3. Evaluasi dan tampilkan hasil bacaan 100% sempurna
        evaluateRecitation(text);

        // 4. Batalkan timeout hening selama jeda transisi
        cancelSilenceTimeout();
        if (currentAyah != null) {
            String[] words = currentAyah.getArabic().trim().split("\\s+");
            lastLiveWordIndex = words.length;
            updateTargetArabicKaraoke(lastLiveWordIndex);
        }

        // 5. Lanjut langsung ke ayat berikutnya tanpa tekan rekam lagi
        if (ayahNumber < ayahs.size()) {
            final int nextAyah = ayahNumber + 1;
            tvMicStatus.setText("🌟 Ayat " + ayahNumber + " selesai & benar! Lanjut ke Ayat " + nextAyah + "...");
            if (tvLiveRecordingIndicator != null) {
                tvLiveRecordingIndicator.setText("✓ Benar & Tersimpan");
                tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
            }

            autoNextHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!isRecordingSessionActive) {
                    isTransitioningToNext = false;
                    return;
                }

                ayahNumber++;
                displayAyah();
                isTransitioningToNext = false;

                // Langsung aktifkan perekaman untuk ayat berikutnya tanpa tekan rekam lagi
                startListeningAndRecording();
            }, 1400);
        } else {
            // Seluruh ayat dalam surah selesai
            isTransitioningToNext = false;
            isRecordingSessionActive = false;
            isListening = false;
            ivMicIcon.setImageResource(R.drawable.ic_mic);
            tvMicStatus.setText("🎉 Masya Allah! Seluruh ayat dalam surah ini telah selesai disetorkan.");
            if (tvLiveRecordingIndicator != null) {
                tvLiveRecordingIndicator.setText("🎉 Selesai Surah");
                tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
            }
            Toast.makeText(this, "🎉 Masya Allah! Semua ayat di surah ini selesai disetorkan!", Toast.LENGTH_LONG).show();
        }
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    resetSilenceTimeout();
                    tvMicStatus.setText("🎙️ Mendengarkan... Silakan baca ayat ini");
                    if (tvLiveRecordingIndicator != null) {
                        tvLiveRecordingIndicator.setText(isTerusan ? "● Siap Mendengarkan (Terusan)" : "● Siap Mendengarkan");
                        tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
                    }
                }
                @Override public void onBeginningOfSpeech() {
                    resetSilenceTimeout();
                    tvMicStatus.setText("🎙️ Suara terdeteksi, teruskan membaca...");
                    if (tvLiveRecordingIndicator != null) {
                        tvLiveRecordingIndicator.setText("● Merekam...");
                        tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.red_error));
                    }
                }
                @Override public void onRmsChanged(float rmsdB) {
                    if (rmsdB > 2.0f) resetSilenceTimeout();
                }
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    if (isTerusan && isRecordingSessionActive && lastSpokenText != null && !lastSpokenText.trim().isEmpty() && isRecitationCompleteAndCorrect(lastSpokenText)) {
                        handleAyahCompleteAndCorrect(lastSpokenText);
                        return;
                    }

                    if (isRecordingSessionActive) {
                        tvMicStatus.setText("🎙️ Jeda nafas terdeteksi... Silakan lanjutkan atau ketuk 'Periksa'");
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("⏳ Jeda Nafas");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
                        }
                    } else {
                        tvMicStatus.setText("⏳ Selesai mendengarkan. Memproses bacaan...");
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("Selesai");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
                        }
                        if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                            if (layoutActionPeriksaHafalan != null) {
                                layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
                            }
                            evaluateRecitation(lastSpokenText);
                        }
                    }
                }
                @Override public void onError(int error) {
                    if (isTransitioningToNext) return;

                    // 1. Error audio hardware / permission: hentikan sesi agar tidak retry loop tanpa henti
                    if (error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        cancelSilenceTimeout();
                        isListening = false;
                        isRecordingSessionActive = false;
                        ivMicIcon.setImageResource(R.drawable.ic_mic);
                        tvMicStatus.setText("Mikrofon audio tidak dapat diakses atau izin ditolak. Silakan coba lagi.");
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("Error Mic");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.red_error));
                        }
                        return;
                    }

                    // 2. Selama sesi setor hafalan masih aktif (user belum stop manual):
                    // Lanjutkan mendengarkan untuk jeda jeda nafas / no match
                    if (isRecordingSessionActive) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (isRecordingSessionActive && !isTransitioningToNext) {
                                try {
                                    if (speechRecognizer != null) {
                                        speechRecognizer.cancel();
                                    }
                                } catch (Exception ignored) {}
                                restartListeningIntent();
                            }
                        }, 350);
                        return;
                    }

                    cancelSilenceTimeout();
                    isListening = false;
                    isRecordingSessionActive = false;
                    ivMicIcon.setImageResource(R.drawable.ic_mic);

                    // 3. Jika teks bacaan sudah tertangkap selama berbicara (onPartialResults), gunakan teks tersebut
                    if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                        if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
                        if (tvLiveSpeech != null) tvLiveSpeech.setText(lastSpokenText);
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("✓ Terekam");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
                        }
                        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
                        tvMicStatus.setText("✅ Suara berhasil terdeteksi.");
                        highlightLiveRecitedWords(lastSpokenText);

                        if (isTerusan && isRecitationCompleteAndCorrect(lastSpokenText)) {
                            handleAyahCompleteAndCorrect(lastSpokenText);
                        } else {
                            evaluateRecitation(lastSpokenText);
                        }
                        return;
                    }

                    String msg;
                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO:          msg = "Kesalahan mikrofon audio, periksa izin mikrofon"; break;
                        case SpeechRecognizer.ERROR_NO_MATCH:       msg = "Suara belum terdeteksi. Silakan dekatkan mic dan baca lebih lantang"; break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "Waktu membaca habis, silakan ketuk mikrofon untuk mengulang"; break;
                        case SpeechRecognizer.ERROR_NETWORK:        msg = "Periksa koneksi internet untuk pengenal suara Google"; break;
                        default:                                     msg = "Ketuk mikrofon untuk mengulang setoran"; break;
                    }
                    tvMicStatus.setText(msg);

                    if (tvLiveRecordingIndicator != null) {
                        tvLiveRecordingIndicator.setText("Berhenti");
                        tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.text_muted));
                    }
                }
                @Override public void onResults(Bundle results) {
                    resetSilenceTimeout();
                    ArrayList<String> matches = results != null ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (matches != null && !matches.isEmpty()) {
                        String seg = matches.get(0);
                        if (seg != null && !seg.trim().isEmpty()) {
                            if (accumulatedSpeech.length() > 0) accumulatedSpeech.append(" ");
                            accumulatedSpeech.append(seg.trim());
                            lastSpokenText = accumulatedSpeech.toString().trim();
                            if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
                            if (tvLiveSpeech != null) tvLiveSpeech.setText(lastSpokenText);
                            highlightLiveRecitedWords(lastSpokenText);
                            if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
                        }
                    }

                    // JIKA MODE TERUSAN AKTIF & AYAT SUDAH SELESAI SERTA BENAR:
                    if (isTerusan && isRecordingSessionActive && isRecitationCompleteAndCorrect(lastSpokenText)) {
                        handleAyahCompleteAndCorrect(lastSpokenText);
                        return;
                    }

                    if (isRecordingSessionActive) {
                        // User is still reciting! Automatically continue listening for next segment
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (isRecordingSessionActive && !isTransitioningToNext) {
                                restartListeningIntent();
                            }
                        }, 300);
                        return;
                    }

                    isListening = false;
                    cancelSilenceTimeout();
                    ivMicIcon.setImageResource(R.drawable.ic_mic);

                    if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                        if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
                        if (tvLiveSpeech != null) tvLiveSpeech.setText(lastSpokenText);
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("✓ Terekam");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
                        }
                        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
                        tvMicStatus.setText("✅ Suara berhasil terdeteksi.");
                        highlightLiveRecitedWords(lastSpokenText);
                        evaluateRecitation(lastSpokenText);
                    } else {
                        tvMicStatus.setText("Perangkat belum mengenali teks bacaan. Silakan coba dekatkan mikrofon.");
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    resetSilenceTimeout();
                    ArrayList<String> partials = partialResults != null ? partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) : null;
                    if (partials != null && !partials.isEmpty()) {
                        String live = partials.get(0);
                        if (live != null && !live.trim().isEmpty()) {
                            String fullLive = (accumulatedSpeech.length() > 0 ? (accumulatedSpeech + " ") : "") + live.trim();
                            lastSpokenText = fullLive.trim();
                            if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
                            if (tvLiveSpeech != null) tvLiveSpeech.setText(fullLive);
                            tvMicStatus.setText("🎙️ Mengikuti suara: " + fullLive);
                            highlightLiveRecitedWords(fullLive);

                            // Jika pas bacaan kata terakhir selesai dan benar:
                            if (isTerusan && isRecordingSessionActive && isRecitationCompleteAndCorrect(fullLive.trim())) {
                                handleAyahCompleteAndCorrect(fullLive.trim());
                            }
                        }
                    }
                }
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        } else {
            tvMicStatus.setText("Layanan pengenal suara Google belum aktif di perangkat ini.");
        }
    }

    private void checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQ_CODE);
        } else {
            startListeningAndRecording();
        }
    }

    private void checkPermissionAndStartUserRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQ_CODE + 1);
        } else {
            startUserAudioRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == PERMISSION_REQ_CODE) {
                startListeningAndRecording();
            } else if (requestCode == PERMISSION_REQ_CODE + 1) {
                startUserAudioRecording();
            }
        } else {
            Toast.makeText(this, "Izin mikrofon diperlukan", Toast.LENGTH_SHORT).show();
        }
    }

    private void startListeningAndRecording() {
        stopAllAudio();
        if (isMediaRecording) {
            stopUserAudioRecording();
        }

        lastSpokenText = "";
        accumulatedSpeech.setLength(0);
        isRecordingSessionActive = true;
        isTransitioningToNext = false;
        resetSilenceTimeout();

        if (cardLiveSpeech != null) {
            cardLiveSpeech.setVisibility(View.VISIBLE);
            if (tvLiveSpeech != null) tvLiveSpeech.setText("...");
            if (tvLiveRecordingIndicator != null) {
                tvLiveRecordingIndicator.setText(isTerusan ? "● Mendengarkan (Terusan)..." : "● Mendengarkan Suara...");
                tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
            }
        }
        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.GONE);
        if (cardHafalResult != null) cardHafalResult.setVisibility(View.GONE);

        // Reset target Arabic text display before recording
        lastLiveWordIndex = -1;
        updateTargetArabicKaraoke(-1);

        // SpeechRecognizer eksklusif: tidak ada MediaRecorder paralel yang memblokir mikrofon!
        if (speechRecognizer == null) initSpeechRecognizer();
        restartListeningIntent();

        ivMicIcon.setImageResource(R.drawable.ic_pause);
        tvMicStatus.setText(isTerusan ? "🎙️ Mode Terusan: Silakan baca ayat ini..." : "🎙️ Mendengarkan bacaan... Karaoke real-time aktif mengikuti suara Anda");
        updateAudioComparisonUi();
    }

    private void startUserAudioRecording() {
        if (isListening || isRecordingSessionActive) {
            stopListeningAndRecording();
        }
        stopAllAudio();

        try {
            setupRecordedAudioFile();
            if (recordedAudioFile != null && recordedAudioFile.exists()) {
                recordedAudioFile.delete();
            }
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setAudioEncodingBitRate(96000);
            mediaRecorder.setOutputFile(recordedAudioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            isMediaRecording = true;

            if (ivRecordIcon != null) ivRecordIcon.setImageResource(R.drawable.ic_pause);
            if (tvRecordLabel != null) {
                tvRecordLabel.setText("Simpan");
                tvRecordLabel.setTextColor(getResources().getColor(R.color.red_error));
            }
            if (tvAudioPlayStatus != null) {
                tvAudioPlayStatus.setText("🔴 Sedang merekam suara Anda... Ketuk 'Simpan' jika selesai membaca.");
            }
            Toast.makeText(this, "🔴 Merekam suara... Silakan baca ayat ini", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            isMediaRecording = false;
            if (mediaRecorder != null) {
                try { mediaRecorder.release(); } catch (Exception ignored) {}
                mediaRecorder = null;
            }
            Toast.makeText(this, "Gagal memulai perekam audio: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        updateAudioComparisonUi();
    }

    private void stopUserAudioRecording() {
        if (isMediaRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {
            } finally {
                try { mediaRecorder.release(); } catch (Exception ignored) {}
                mediaRecorder = null;
                isMediaRecording = false;
            }
        }
        setupRecordedAudioFile();
        if (recordedAudioFile != null && recordedAudioFile.exists() && recordedAudioFile.length() < 1024) {
            try { recordedAudioFile.delete(); } catch (Exception ignored) {}
        }
        if (ivRecordIcon != null) ivRecordIcon.setImageResource(R.drawable.ic_mic);
        if (tvRecordLabel != null) {
            tvRecordLabel.setText("Rekam Ulang");
            tvRecordLabel.setTextColor(getResources().getColor(R.color.gold_primary));
        }
        if (recordedAudioFile != null && recordedAudioFile.exists() && recordedAudioFile.length() >= 1024) {
            Toast.makeText(this, "✓ Rekaman suara berhasil disimpan!", Toast.LENGTH_SHORT).show();
            if (tvAudioPlayStatus != null) {
                tvAudioPlayStatus.setText("✓ Rekaman tersimpan! Ketuk 'Rekaman Saya' untuk memutar, atau 'Suara Qari' sebagai pembanding.");
            }
        }
        updateAudioComparisonUi();
    }

    private void updateAudioComparisonUi() {
        if (layoutAudioComparison != null) {
            layoutAudioComparison.setVisibility(View.VISIBLE);
        }
        setupRecordedAudioFile();
        boolean hasRecording = (recordedAudioFile != null && recordedAudioFile.exists() && recordedAudioFile.length() > 1024);

        if (isMediaRecording) {
            if (ivRecordIcon != null) ivRecordIcon.setImageResource(R.drawable.ic_pause);
            if (tvRecordLabel != null) {
                tvRecordLabel.setText("Simpan");
                tvRecordLabel.setTextColor(getResources().getColor(R.color.red_error));
            }
            if (tvAudioPlayStatus != null) {
                tvAudioPlayStatus.setText("🔴 Sedang merekam suara Anda... Ketuk 'Simpan' jika selesai membaca.");
            }
        } else {
            if (ivRecordIcon != null) ivRecordIcon.setImageResource(R.drawable.ic_mic);
            if (tvRecordLabel != null) {
                tvRecordLabel.setText(hasRecording ? "Rekam Ulang" : "Rekam Suara");
                tvRecordLabel.setTextColor(getResources().getColor(R.color.gold_primary));
            }

            if (hasRecording) {
                if (ivUserPlayIcon != null) {
                    ivUserPlayIcon.setImageResource(isPlayingUserAudio ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
                }
                if (tvUserPlayLabel != null) {
                    tvUserPlayLabel.setText(isPlayingUserAudio ? "Jeda" : "Rekaman Saya");
                    tvUserPlayLabel.setTextColor(getResources().getColor(R.color.emerald));
                }
                if (tvAudioPlayStatus != null && !isPlayingUserAudio && !isPlayingQariAudio) {
                    tvAudioPlayStatus.setText("✓ Rekaman tersimpan. Ketuk 'Rekaman Saya' untuk mendengarkan kembali.");
                }
            } else {
                if (ivUserPlayIcon != null) ivUserPlayIcon.setImageResource(R.drawable.ic_play_arrow);
                if (tvUserPlayLabel != null) {
                    tvUserPlayLabel.setText("Rekaman Saya");
                    tvUserPlayLabel.setTextColor(getResources().getColor(R.color.text_muted));
                }
                if (tvAudioPlayStatus != null && !isPlayingUserAudio && !isPlayingQariAudio) {
                    tvAudioPlayStatus.setText("Ketuk 'Rekam Suara' untuk merekam, atau tombol mic di atas untuk Setor Hafalan AI.");
                }
            }
        }
    }

    private void restartListeningIntent() {
        if (!isRecordingSessionActive || speechRecognizer == null || isTransitioningToNext) return;
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
            speechRecognizer.startListening(intent);
            isListening = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopListeningAndRecording() {
        cancelSilenceTimeout();
        isRecordingSessionActive = false;
        isListening = false;
        isTransitioningToNext = false;
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        ivMicIcon.setImageResource(R.drawable.ic_mic);
        tvMicStatus.setText("Selesai mendengarkan.");
        if (tvLiveRecordingIndicator != null) {
            tvLiveRecordingIndicator.setText("Selesai");
            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
        }
        if (layoutActionPeriksaHafalan != null) {
            layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
        }
        if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
            evaluateRecitation(lastSpokenText.trim());
        }
    }

    // ── ARABIC NORMALIZATION (update.md §13) ──

    private String normAr(String text) {
        if (text == null) return "";
        // Remove harakat/diacritics
        String s = text.replaceAll("[\u064B-\u065F\u0670\u06D6-\u06ED]", "");
        // Normalize alef variants → ا
        s = s.replaceAll("[\u0623\u0625\u0622\u0671]", "\u0627");
        // ى , ئ → ي
        s = s.replaceAll("[\u0649\u0626]", "\u064A");
        // ة → ه
        s = s.replace('\u0629', '\u0647');
        // ؤ → و
        s = s.replace('\u0624', '\u0648');
        // Keep only Arabic letters + whitespace
        s = s.replaceAll("[^\u0621-\u064A\\s]", "");
        return s.trim();
    }

    // ── LEVENSHTEIN (edit distance) ──

    private int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) dp[i][j] = dp[i-1][j-1];
                else dp[i][j] = 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
            }
        }
        return dp[la][lb];
    }

    private float similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1f;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1f;
        return 1f - (float) levenshtein(a, b) / maxLen;
    }

    // ── WORD CLASSIFICATION ──

    private static final int CORRECT  = 0;
    private static final int NEAR     = 1;
    private static final int WRONG    = 2;
    private static final int MISSING  = 3;

    // ── LIVE ARABIC WORD TRACKING (Menyorot kata ayat secara real-time saat dibaca) ──

    private void highlightLiveRecitedWords(String spoken) {
        if (currentAyah == null || tvTargetArabic == null || spoken == null || spoken.trim().isEmpty()) return;

        int foundIdx = RecitationEvaluator.findSpokenWordIndex(currentAyah.getArabic(), spoken, lastLiveWordIndex);
        if (foundIdx >= 0) {
            lastLiveWordIndex = Math.max(lastLiveWordIndex, foundIdx);
        } else {
            // Coba periksa token-token kata terakhir secara individual
            String[] tokens = spoken.trim().split("\\s+");
            for (int t = tokens.length - 1; t >= Math.max(0, tokens.length - 3); t--) {
                int idx = RecitationEvaluator.findSpokenWordIndex(currentAyah.getArabic(), tokens[t], lastLiveWordIndex);
                if (idx >= 0) {
                    lastLiveWordIndex = Math.max(lastLiveWordIndex, idx);
                    break;
                }
            }
        }
        updateTargetArabicKaraoke(lastLiveWordIndex);
    }

    private void updateTargetArabicKaraoke(int activeWordIndex) {
        if (currentAyah == null || tvTargetArabic == null) return;
        String arabicRef = currentAyah.getArabic().trim();
        String[] rawWords = arabicRef.split("\\s+");

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (int i = 0; i < rawWords.length; i++) {
            if (i > 0) ssb.append("  ");
            int start = ssb.length();
            if (isHidden && i > activeWordIndex) {
                // Dalam mode sembunyikan ayat: kata yang belum dibaca disamarkan titik-titik bertingkat
                ssb.append("••••");
            } else {
                ssb.append(rawWords[i]);
            }
            int end = ssb.length();

            if (i < activeWordIndex) {
                // Kata yang sudah diucapkan dan cocok: HIJAU EMERALD
                ssb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (i == activeWordIndex) {
                // Kata yang sedang aktif diucapkan saat ini: CYAN MENYALA + BACKGROUND HIGHLIGHT + BOLD
                ssb.setSpan(new ForegroundColorSpan(0xFF00FFD5), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new BackgroundColorSpan(0x4400FFD5), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Belum diucapkan
                if (isHidden) {
                    ssb.setSpan(new ForegroundColorSpan(0xFF64748B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    ssb.setSpan(new ForegroundColorSpan(0xFFE2E8F0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        tvTargetArabic.setText(ssb);
    }

    // ── EVALUATE RECITATION DENGAN WARNA MERAH UNTUK KATA SALAH ──

    private void evaluateRecitation(String userText) {
        if (userText == null || userText.trim().isEmpty() || currentAyah == null) return;

        cardHafalResult.setVisibility(View.VISIBLE);
        if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
        if (tvLiveSpeech != null) tvLiveSpeech.setText(userText);
        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);

        String refNorm = normAr(currentAyah.getArabic());
        String spkNorm = normAr(userText);

        String[] refWords = refNorm.split("\\s+");
        String[] spkWords = spkNorm.isEmpty() ? new String[0] : spkNorm.split("\\s+");

        boolean[] refMatched = new boolean[refWords.length];
        boolean[] spkMatched = new boolean[spkWords.length];

        int correctCount = 0;
        int nearCount = 0;

        for (int i = 0; i < refWords.length; i++) {
            float bestSim = -1f;
            int bestJ = -1;
            for (int j = 0; j < spkWords.length; j++) {
                if (spkMatched[j]) continue;
                float sim = similarity(refWords[i], spkWords[j]);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestJ = j;
                }
            }
            if (bestJ >= 0 && bestSim >= AMBANG_MIRIP) {
                refMatched[i] = true;
                spkMatched[bestJ] = true;
                if (bestSim == 1f) {
                    correctCount++;
                } else {
                    nearCount++;
                }
            }
        }

        int wrongCount = 0;
        for (int j = 0; j < spkWords.length; j++) {
            if (!spkMatched[j]) wrongCount++;
        }

        int missingCount = 0;
        for (boolean m : refMatched) {
            if (!m) missingCount++;
        }

        int total = refWords.length;
        float rawScore = total > 0 ? (correctCount + nearCount * BOBOT_NEAR - (wrongCount + missingCount) * PENALTI_WRONG) / total * 100f : 0f;
        int score = (int) Math.max(0, Math.min(100, rawScore));

        // Display score
        String scoreText = score + "%";
        tvScoreBadge.setText(scoreText);
        String feedback;
        if (score >= 90) {
            tvScoreBadge.setTextColor(getResources().getColor(R.color.emerald));
            feedback = "🌟 Masya Allah! Hafalan sangat sempurna.";
            tvCorrectionFeedback.setText(feedback);
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.emerald));
        } else if (score >= 70) {
            tvScoreBadge.setTextColor(getResources().getColor(R.color.gold_primary));
            feedback = "👍 Hampir sempurna! Perhatikan kata-kata yang ditandai merah.";
            tvCorrectionFeedback.setText(feedback);
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.gold_primary));
        } else {
            tvScoreBadge.setTextColor(getResources().getColor(R.color.red_error));
            feedback = "✍️ Terus berlatih! Perbaiki kata-kata yang berwarna merah.";
            tvCorrectionFeedback.setText(feedback);
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.red_error));
        }

        // Terapkan penanda warna merah & hijau pada bacaan user & acuan
        applyRecitationColors(userText);

        // Simpan progress hafalan jika 100% sempurna
        if (wrongCount == 0 && missingCount == 0 && total > 0) {
            PreferenceManager.markHafalan(this, surahNumber, ayahNumber);
            feedback = "✅ Masya Allah! Hafalan 100% sempurna — progres tersimpan!";
            tvCorrectionFeedback.setText(feedback);
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.emerald));
        }

        // Simpan hasil evaluasi hafalan per ayat secara permanen
        PreferenceManager.saveHafalanEvaluation(this, surahNumber, ayahNumber, score, scoreText, feedback, userText);

        // Update UI tombol pemutar rekaman
        updateAudioComparisonUi();
    }

    private void applyRecitationColors(String userText) {
        if (userText == null || userText.trim().isEmpty() || currentAyah == null) return;

        String refNorm = normAr(currentAyah.getArabic());
        String spkNorm = normAr(userText);

        String[] refWords = refNorm.split("\\s+");
        String[] spkWords = spkNorm.isEmpty() ? new String[0] : spkNorm.split("\\s+");
        String[] spkRawWords = userText.trim().split("\\s+");
        String[] refRawWords = currentAyah.getArabic().trim().split("\\s+");

        boolean[] refMatched = new boolean[refWords.length];
        boolean[] spkMatched = new boolean[spkWords.length];
        int[] spkClass = new int[spkWords.length];
        for (int j = 0; j < spkWords.length; j++) {
            spkClass[j] = WRONG;
        }

        for (int i = 0; i < refWords.length; i++) {
            float bestSim = -1f;
            int bestJ = -1;
            for (int j = 0; j < spkWords.length; j++) {
                if (spkMatched[j]) continue;
                float sim = similarity(refWords[i], spkWords[j]);
                if (sim > bestSim) {
                    bestSim = sim;
                    bestJ = j;
                }
            }
            if (bestJ >= 0 && bestSim >= AMBANG_MIRIP) {
                refMatched[i] = true;
                spkMatched[bestJ] = true;
                if (bestSim == 1f) {
                    spkClass[bestJ] = CORRECT;
                } else {
                    spkClass[bestJ] = NEAR;
                }
            }
        }

        // Highlight User's Recited Words: HIJAU jika benar, MERAH TEBAL jika salah
        if (tvUserRecitation != null) {
            SpannableStringBuilder userSsb = new SpannableStringBuilder();
            for (int j = 0; j < spkRawWords.length; j++) {
                if (j > 0) userSsb.append("  ");
                int start = userSsb.length();
                userSsb.append(spkRawWords[j]);
                int end = userSsb.length();

                int classification = (j < spkClass.length) ? spkClass[j] : WRONG;
                if (classification == CORRECT) {
                    userSsb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (classification == NEAR) {
                    userSsb.setSpan(new ForegroundColorSpan(0xFF38BDF8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    userSsb.setSpan(new ForegroundColorSpan(0xFFFF2B2B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    userSsb.setSpan(new BackgroundColorSpan(0x33FF0000), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    userSsb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            tvUserRecitation.setText(userSsb);
        }

        // Highlight Target Verse: Hijau jika cocok, MERAH TEBAL jika salah/terlewat
        if (!isHidden && tvTargetArabic != null) {
            SpannableStringBuilder targetSsb = new SpannableStringBuilder();
            for (int i = 0; i < refRawWords.length; i++) {
                if (i > 0) targetSsb.append("  ");
                int start = targetSsb.length();
                targetSsb.append(refRawWords[i]);
                int end = targetSsb.length();

                if (i < refMatched.length && refMatched[i]) {
                    targetSsb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    targetSsb.setSpan(new ForegroundColorSpan(0xFFFF2B2B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    targetSsb.setSpan(new BackgroundColorSpan(0x33FF0000), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    targetSsb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            tvTargetArabic.setText(targetSsb);
        }
    }

    // ── AUDIO PLAYBACK METHODS ──

    private void playUserRecordedAudio() {
        if (isPlayingUserAudio) {
            stopUserPlayback();
            return;
        }
        stopAllAudio();
        setupRecordedAudioFile();
        if (recordedAudioFile == null || !recordedAudioFile.exists() || recordedAudioFile.length() < 1024) {
            Toast.makeText(this, "Belum ada rekaman suara untuk ayat ini. Ketuk tombol 'Rekam Suara' di sebelah kiri untuk merekam.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            userPlayer = new MediaPlayer();
            userPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            userPlayer.setDataSource(recordedAudioFile.getAbsolutePath());
            userPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isPlayingUserAudio = true;
                if (ivUserPlayIcon != null) ivUserPlayIcon.setImageResource(R.drawable.ic_pause);
                if (tvUserPlayLabel != null) tvUserPlayLabel.setText("Jeda");
                if (tvAudioPlayStatus != null) tvAudioPlayStatus.setText("▶️ Sedang memutar rekaman suara Anda...");
            });
            userPlayer.setOnCompletionListener(mp -> stopUserPlayback());
            userPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(HafalanActivity.this, "Gagal memutar rekaman suara", Toast.LENGTH_SHORT).show();
                stopUserPlayback();
                return true;
            });
            userPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Gagal memutar rekaman: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopUserPlayback();
        }
    }

    private void stopUserPlayback() {
        if (userPlayer != null) {
            try {
                if (userPlayer.isPlaying()) userPlayer.stop();
                userPlayer.release();
            } catch (Exception ignored) {}
            userPlayer = null;
        }
        isPlayingUserAudio = false;
        if (ivUserPlayIcon != null) ivUserPlayIcon.setImageResource(R.drawable.ic_play_arrow);
        if (tvUserPlayLabel != null) tvUserPlayLabel.setText("Rekaman Saya");
        if (tvAudioPlayStatus != null) tvAudioPlayStatus.setText("Ketuk untuk mendengarkan kembali");
    }

    private void playQariAudio() {
        stopAllAudio();
        String qariId   = PreferenceManager.getQariId(this);
        String qariName = PreferenceManager.getQariName(this);
        String primaryUrl = String.format(Locale.US, "https://mirrors.quranicaudio.com/everyayah/%s/%03d%03d.mp3",
                qariId, surahNumber, currentAyah.getNumberInSurah());
        String fallbackUrl = String.format(Locale.US, "https://everyayah.com/data/%s/%03d%03d.mp3",
                qariId, surahNumber, currentAyah.getNumberInSurah());
        startQariPlayer(primaryUrl, fallbackUrl, qariName);
    }

    private void startQariPlayer(String url, String fallbackUrl, String qariName) {
        try {
            qariPlayer = new MediaPlayer();
            qariPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            qariPlayer.setDataSource(url);
            qariPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isPlayingQariAudio = true;
                ivQariPlayIcon.setImageResource(R.drawable.ic_pause);
                tvQariPlayLabel.setText("Jeda");
                tvAudioPlayStatus.setText("🎧 Sedang memutar suara " + qariName + "...");
            });
            qariPlayer.setOnCompletionListener(mp -> stopQariPlayback());
            qariPlayer.setOnErrorListener((mp, what, extra) -> {
                if (fallbackUrl != null && !url.equals(fallbackUrl)) {
                    stopQariPlayback();
                    startQariPlayer(fallbackUrl, null, qariName);
                    return true;
                }
                Toast.makeText(HafalanActivity.this, "Gagal memutar audio qari", Toast.LENGTH_SHORT).show();
                stopQariPlayback();
                return true;
            });
            qariPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Gagal memutar audio qari: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            stopQariPlayback();
        }
    }

    private void stopQariPlayback() {
        if (qariPlayer != null) {
            try {
                if (qariPlayer.isPlaying()) qariPlayer.stop();
                qariPlayer.release();
            } catch (Exception ignored) {}
            qariPlayer = null;
        }
        isPlayingQariAudio = false;
        ivQariPlayIcon.setImageResource(R.drawable.ic_play_arrow);
        tvQariPlayLabel.setText("Suara Qari");
        tvAudioPlayStatus.setText("Ketuk untuk mendengarkan kembali");
    }

    private void stopAllAudio() {
        stopUserPlayback();
        stopQariPlayback();
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoNextHandler.removeCallbacksAndMessages(null);
        cancelSilenceTimeout();
        isTransitioningToNext = false;
        if (isMediaRecording) stopUserAudioRecording();
        stopListeningAndRecording();
        stopAllAudio();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        autoNextHandler.removeCallbacksAndMessages(null);
        cancelSilenceTimeout();
        isTransitioningToNext = false;
        if (isMediaRecording) stopUserAudioRecording();
        stopListeningAndRecording();
        stopAllAudio();
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }
}

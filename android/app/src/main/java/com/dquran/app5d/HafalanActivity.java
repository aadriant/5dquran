package com.dquran.app5d;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
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
    private FrameLayout btnMic;
    private ImageView ivMicIcon;
    private TextView tvMicStatus;

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

    // Audio Playback Section (User Recording vs Qari)
    private View layoutAudioComparison;
    private View btnPlayUserRecording;
    private ImageView ivUserPlayIcon;
    private TextView tvUserPlayLabel;

    private View btnListenCorrect;
    private ImageView ivQariPlayIcon;
    private TextView tvQariPlayLabel;
    private TextView tvAudioPlayStatus;

    // Speech & Media Recording
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private boolean isHidden = false;

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
        tvWordDiff           = findViewById(R.id.tv_word_diff);

        // Audio Comparison Section
        layoutAudioComparison = findViewById(R.id.layout_audio_comparison);
        btnPlayUserRecording  = findViewById(R.id.btn_play_user_recording);
        ivUserPlayIcon        = findViewById(R.id.iv_user_play_icon);
        tvUserPlayLabel       = findViewById(R.id.tv_user_play_label);

        btnListenCorrect  = findViewById(R.id.btn_listen_correct);
        ivQariPlayIcon    = findViewById(R.id.iv_qari_play_icon);
        tvQariPlayLabel   = findViewById(R.id.tv_qari_play_label);
        tvAudioPlayStatus = findViewById(R.id.tv_audio_play_status);

        findViewById(R.id.btn_back_hafal).setOnClickListener(v -> finish());

        findViewById(R.id.btn_prev_ayah).setOnClickListener(v -> {
            if (ayahNumber > 1) {
                stopAllAudio();
                ayahNumber--;
                displayAyah();
            }
        });

        findViewById(R.id.btn_next_ayah).setOnClickListener(v -> {
            if (ayahNumber < ayahs.size()) {
                stopAllAudio();
                ayahNumber++;
                displayAyah();
            }
        });

        btnToggleHide.setOnClickListener(v -> {
            isHidden = !isHidden;
            PreferenceManager.setHafalanDenganContoh(this, !isHidden);
            if (isHidden) {
                tvTargetArabic.setText("•••••••••••••••••••••••••");
                tvTargetArabic.setTextColor(getResources().getColor(R.color.text_muted));
                btnToggleHide.setText("👁️ Tampilkan");
            } else {
                tvTargetArabic.setText(currentAyah.getArabic());
                tvTargetArabic.setTextColor(getResources().getColor(R.color.gold_primary));
                btnToggleHide.setText("👁️ Sembunyikan");
            }
        });

        btnMic.setOnClickListener(v -> {
            if (isListening || isMediaRecording) {
                stopListeningAndRecording();
            } else {
                checkPermissionAndStartListening();
            }
        });

        if (btnPeriksaHafalan != null) {
            btnPeriksaHafalan.setOnClickListener(v -> {
                if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                    evaluateRecitation(lastSpokenText.trim());
                } else {
                    Toast.makeText(this, "Silakan rekam suara hafalan Anda terlebih dahulu", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnPlayUserRecording.setOnClickListener(v -> {
            if (isPlayingUserAudio) stopUserPlayback();
            else playUserRecordedAudio();
        });

        btnListenCorrect.setOnClickListener(v -> {
            if (isPlayingQariAudio) stopQariPlayback();
            else playQariAudio();
        });
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
        if (isHidden) {
            tvTargetArabic.setText("•••••••••••••••••••••••••");
            tvTargetArabic.setTextColor(getResources().getColor(R.color.text_muted));
            btnToggleHide.setText("👁️ Tampilkan");
        } else {
            tvTargetArabic.setText(currentAyah.getArabic());
            tvTargetArabic.setTextColor(getResources().getColor(R.color.gold_primary));
            btnToggleHide.setText("👁️ Sembunyikan");
        }

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

        // Check if an existing recording exists for this ayah
        setupRecordedAudioFile();
        if (recordedAudioFile.exists() && recordedAudioFile.length() > 0) {
            layoutAudioComparison.setVisibility(View.VISIBLE);
            tvAudioPlayStatus.setText("Ada rekaman sebelumnya. Ketuk 'Rekaman Saya' untuk mendengarkan.");
        } else {
            layoutAudioComparison.setVisibility(View.GONE);
        }
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

    private void setupRecordedAudioFile() {
        File dir = new File(getCacheDir(), "hafalan_audio");
        if (!dir.exists()) dir.mkdirs();
        recordedAudioFile = new File(dir, "recitation_s" + surahNumber + "_a" + ayahNumber + ".m4a");
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    tvMicStatus.setText("🎙️ Mendengarkan... Silakan baca ayat ini");
                    if (tvLiveRecordingIndicator != null) {
                        tvLiveRecordingIndicator.setText("● Siap Mendengarkan");
                        tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
                    }
                }
                @Override public void onBeginningOfSpeech() {
                    tvMicStatus.setText("🎙️ Suara terdeteksi, teruskan membaca...");
                    if (tvLiveRecordingIndicator != null) {
                        tvLiveRecordingIndicator.setText("● Merekam...");
                        tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.red_error));
                    }
                }
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    tvMicStatus.setText("⏳ Selesai merekam. Memproses bacaan...");
                    stopMediaRecordingOnly();
                    if (tvLiveRecordingIndicator != null) {
                        tvLiveRecordingIndicator.setText("Selesai Rekam");
                        tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
                    }
                    if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                        if (layoutActionPeriksaHafalan != null) {
                            layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
                        }
                        evaluateRecitation(lastSpokenText);
                    }
                }
                @Override public void onError(int error) {
                    isListening = false;
                    stopMediaRecordingOnly();
                    ivMicIcon.setImageResource(R.drawable.ic_mic);

                    // 1. Jika teks bacaan sudah tertangkap selama berbicara (onPartialResults), gunakan teks tersebut
                    if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                        if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
                        if (tvLiveSpeech != null) tvLiveSpeech.setText(lastSpokenText);
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("✓ Terekam");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
                        }
                        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
                        tvMicStatus.setText("✅ Suara berhasil direkam.");
                        highlightLiveRecitedWords(lastSpokenText);
                        evaluateRecitation(lastSpokenText);
                        return;
                    }

                    // 2. Jika ada file rekaman suara yang tersimpan di perangkat
                    if (recordedAudioFile != null && recordedAudioFile.exists() && recordedAudioFile.length() > 1000) {
                        layoutAudioComparison.setVisibility(View.VISIBLE);
                        tvAudioPlayStatus.setText("Rekaman suara Anda tersimpan dan dapat didengarkan.");
                        tvMicStatus.setText("🎙️ Rekaman tersimpan. Ketuk 'Rekaman Saya' di bawah untuk mendengarkan.");
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("Tersimpan");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.gold_primary));
                        }
                        return;
                    }

                    String msg;
                    switch (error) {
                        case SpeechRecognizer.ERROR_AUDIO:          msg = "Kesalahan mikrofon audio, periksa izin mikrofon"; break;
                        case SpeechRecognizer.ERROR_NO_MATCH:       msg = "Suara belum terdeteksi. Silakan dekatkan mic dan baca lebih lantang"; break;
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: msg = "Waktu membaca habis, silakan ketuk lagi"; break;
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
                    isListening = false;
                    ivMicIcon.setImageResource(R.drawable.ic_mic);

                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        lastSpokenText = matches.get(0);
                    }

                    if (lastSpokenText != null && !lastSpokenText.trim().isEmpty()) {
                        if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
                        if (tvLiveSpeech != null) tvLiveSpeech.setText(lastSpokenText);
                        if (tvLiveRecordingIndicator != null) {
                            tvLiveRecordingIndicator.setText("✓ Terekam");
                            tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.emerald));
                        }
                        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);
                        tvMicStatus.setText("✅ Suara berhasil direkam.");
                        highlightLiveRecitedWords(lastSpokenText);
                        evaluateRecitation(lastSpokenText);
                    } else {
                        tvMicStatus.setText("Perangkat belum mengenali teks bacaan. Silakan coba dekatkan mikrofon.");
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {
                    // Real-time voice tracking with Arabic text following microphone
                    ArrayList<String> partials = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partials != null && !partials.isEmpty()) {
                        String live = partials.get(0);
                        if (live != null && !live.trim().isEmpty()) {
                            lastSpokenText = live.trim();
                            if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
                            if (tvLiveSpeech != null) tvLiveSpeech.setText(live);
                            tvMicStatus.setText("🎙️ Mengikuti suara: " + live);
                            highlightLiveRecitedWords(live);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListeningAndRecording();
        } else {
            Toast.makeText(this, "Izin mikrofon diperlukan untuk setor hafalan", Toast.LENGTH_SHORT).show();
        }
    }

    private void startListeningAndRecording() {
        stopAllAudio();

        lastSpokenText = "";
        if (cardLiveSpeech != null) {
            cardLiveSpeech.setVisibility(View.VISIBLE);
            if (tvLiveSpeech != null) tvLiveSpeech.setText("...");
            if (tvLiveRecordingIndicator != null) {
                tvLiveRecordingIndicator.setText("● Merekam...");
                tvLiveRecordingIndicator.setTextColor(getResources().getColor(R.color.red_error));
            }
        }
        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.GONE);
        if (cardHafalResult != null) cardHafalResult.setVisibility(View.GONE);

        // Reset target Arabic text display before recording
        if (!isHidden && currentAyah != null) {
            tvTargetArabic.setText(currentAyah.getArabic());
            tvTargetArabic.setTextColor(getResources().getColor(R.color.gold_primary));
        }

        // Start SpeechRecognizer with exclusive microphone access (no MediaRecorder conflict)
        if (speechRecognizer == null) initSpeechRecognizer();
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            intent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ar-SA", "ar-EG", "ar"});
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L);
            try {
                speechRecognizer.startListening(intent);
                isListening = true;
            } catch (Exception e) {
                e.printStackTrace();
                isListening = false;
            }
        }

        ivMicIcon.setImageResource(R.drawable.ic_pause);
        tvMicStatus.setText("🎙️ Mendengarkan suara hafalan Anda...");
    }

    private void stopMediaRecordingOnly() {
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
    }

    private void stopListeningAndRecording() {
        stopMediaRecordingOnly();
        if (speechRecognizer != null && isListening) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
            isListening = false;
        }
        ivMicIcon.setImageResource(R.drawable.ic_mic);
        tvMicStatus.setText("Memproses rekaman suara...");
        if (tvLiveRecordingIndicator != null) {
            tvLiveRecordingIndicator.setText("Selesai Rekam");
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
        String arabicRef = currentAyah.getArabic().trim();
        String[] rawWords = arabicRef.split("\\s+");
        String[] refNorm = normAr(arabicRef).split("\\s+");
        String[] spkNorm = normAr(spoken).split("\\s+");

        int matchedUpTo = -1;
        int spkIdx = 0;
        for (int ri = 0; ri < refNorm.length && spkIdx < spkNorm.length; ri++) {
            if (refNorm[ri].isEmpty()) continue;
            float sim = similarity(refNorm[ri], spkNorm[spkIdx]);
            if (sim >= AMBANG_MIRIP) {
                matchedUpTo = ri;
                spkIdx++;
            } else if (spkIdx + 1 < spkNorm.length) {
                float sim2 = similarity(refNorm[ri], spkNorm[spkIdx + 1]);
                if (sim2 >= AMBANG_MIRIP) {
                    matchedUpTo = ri;
                    spkIdx += 2;
                }
            }
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        for (int i = 0; i < rawWords.length; i++) {
            if (i > 0) ssb.append("  ");
            int start = ssb.length();
            ssb.append(rawWords[i]);
            int end = ssb.length();

            if (i < matchedUpTo) {
                // Kata yang sudah diucapkan dan cocok: HIJAU
                ssb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (i == matchedUpTo) {
                // Kata yang sedang aktif diucapkan: CYAN MENYALA + BOLD
                ssb.setSpan(new ForegroundColorSpan(0xFF38BDF8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new BackgroundColorSpan(0x3338BDF8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // Belum diucapkan
                if (isHidden) {
                    ssb.setSpan(new ForegroundColorSpan(0x33FFFFFF), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else {
                    ssb.setSpan(new ForegroundColorSpan(0xFFE2E8F0), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        tvTargetArabic.setText(ssb);
    }

    // ── EVALUATE RECITATION DENGAN WARNA MERAH UNTUK KATA SALAH ──

    private void evaluateRecitation(String userText) {
        if (userText == null || userText.trim().isEmpty()) return;

        cardHafalResult.setVisibility(View.VISIBLE);
        if (cardLiveSpeech != null) cardLiveSpeech.setVisibility(View.VISIBLE);
        if (tvLiveSpeech != null) tvLiveSpeech.setText(userText);
        if (layoutActionPeriksaHafalan != null) layoutActionPeriksaHafalan.setVisibility(View.VISIBLE);

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
            spkClass[j] = WRONG; // Default is wrong/unmatched
        }

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
                    spkClass[bestJ] = CORRECT;
                    correctCount++;
                } else {
                    spkClass[bestJ] = NEAR;
                    nearCount++;
                }
            }
        }

        int wrongCount = 0;
        for (int j = 0; j < spkWords.length; j++) {
            if (!spkMatched[j]) {
                spkClass[j] = WRONG;
                wrongCount++;
            }
        }

        int missingCount = 0;
        for (boolean m : refMatched) {
            if (!m) missingCount++;
        }

        int total = refWords.length;
        float rawScore = total > 0 ? (correctCount + nearCount * BOBOT_NEAR - (wrongCount + missingCount) * PENALTI_WRONG) / total * 100f : 0f;
        int score = (int) Math.max(0, Math.min(100, rawScore));

        // Display score
        tvScoreBadge.setText(score + "%");
        if (score >= 90) {
            tvScoreBadge.setTextColor(getResources().getColor(R.color.emerald));
            tvCorrectionFeedback.setText("🌟 Masya Allah! Hafalan sangat sempurna.");
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.emerald));
        } else if (score >= 70) {
            tvScoreBadge.setTextColor(getResources().getColor(R.color.gold_primary));
            tvCorrectionFeedback.setText("👍 Hampir sempurna! Perhatikan kata-kata yang ditandai merah.");
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.gold_primary));
        } else {
            tvScoreBadge.setTextColor(getResources().getColor(R.color.red_error));
            tvCorrectionFeedback.setText("✍️ Terus berlatih! Perbaiki kata-kata yang berwarna merah.");
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.red_error));
        }

        // Highlight User's Recited Words: GREEN for correct, RED for wrong/unmatched
        SpannableStringBuilder userSsb = new SpannableStringBuilder();
        for (int j = 0; j < spkRawWords.length; j++) {
            if (j > 0) userSsb.append("  ");
            int start = userSsb.length();
            userSsb.append(spkRawWords[j]);
            int end = userSsb.length();

            int classification = (j < spkClass.length) ? spkClass[j] : WRONG;
            if (classification == CORRECT) {
                userSsb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // emerald green
            } else if (classification == NEAR) {
                userSsb.setSpan(new ForegroundColorSpan(0xFF38BDF8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // cyan
            } else {
                // WARNA MERAH UNTUK KATA YANG SALAH
                userSsb.setSpan(new ForegroundColorSpan(0xFFFF2B2B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                userSsb.setSpan(new BackgroundColorSpan(0x33FF0000), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                userSsb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        tvUserRecitation.setText(userSsb);

        // Highlight Target Verse: Green if matched, RED if missed/wrong
        if (tvWordDiff != null) {
            SpannableStringBuilder targetSsb = new SpannableStringBuilder();
            for (int i = 0; i < refRawWords.length; i++) {
                if (i > 0) targetSsb.append("  ");
                int start = targetSsb.length();
                targetSsb.append(refRawWords[i]);
                int end = targetSsb.length();

                if (i < refMatched.length && refMatched[i]) {
                    targetSsb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // matched green
                } else {
                    // WARNA MERAH UNTUK KATA YANG TIDAK COCOK / TERLEWAT
                    targetSsb.setSpan(new ForegroundColorSpan(0xFFFF2B2B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    targetSsb.setSpan(new BackgroundColorSpan(0x33FF0000), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    targetSsb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            tvWordDiff.setText(targetSsb);
            tvWordDiff.setVisibility(View.VISIBLE);
        }

        // Save progress: only if perfect (wrong == 0 && missing == 0)
        if (wrongCount == 0 && missingCount == 0 && total > 0) {
            PreferenceManager.markHafalan(this, surahNumber, ayahNumber);
            tvCorrectionFeedback.setText("✅ Masya Allah! Hafalan 100% sempurna — progres tersimpan!");
            tvCorrectionFeedback.setTextColor(getResources().getColor(R.color.emerald));
        }

        // Show audio comparison
        if (recordedAudioFile != null && recordedAudioFile.exists() && recordedAudioFile.length() > 0) {
            layoutAudioComparison.setVisibility(View.VISIBLE);
            tvAudioPlayStatus.setText("Rekaman tersimpan! Ketuk 'Rekaman Saya' untuk mendengarkan kembali.");
        }
    }

    // ── AUDIO PLAYBACK METHODS ──

    private void playUserRecordedAudio() {
        stopAllAudio();
        if (recordedAudioFile == null || !recordedAudioFile.exists() || recordedAudioFile.length() == 0) {
            Toast.makeText(this, "Belum ada file rekaman suara Anda", Toast.LENGTH_SHORT).show();
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
                ivUserPlayIcon.setImageResource(R.drawable.ic_pause);
                tvUserPlayLabel.setText("Jeda");
                tvAudioPlayStatus.setText("▶️ Sedang memutar rekaman suara Anda sendiri...");
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
        ivUserPlayIcon.setImageResource(R.drawable.ic_play_arrow);
        tvUserPlayLabel.setText("Rekaman Saya");
        tvAudioPlayStatus.setText("Ketuk untuk mendengarkan kembali");
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
    protected void onDestroy() {
        super.onDestroy();
        stopListeningAndRecording();
        stopAllAudio();
        if (speechRecognizer != null) speechRecognizer.destroy();
    }
}

package com.dquran.app5d;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class TulisActivity extends AppCompatActivity {

    // Scoring constants
    private static final float BOBOT_NEAR    = 0.75f;
    private static final float PENALTI_WRONG = 0.25f;
    private static final float AMBANG_MIRIP  = 0.6f;

    private int surahNumber = 1;
    private int ayahNumber  = 1;
    private List<AyahModel> ayahs;
    private AyahModel currentAyah;
    private String surahName = "";

    // Header views
    private TextView tvTulisTitle, tvTulisSubtitle, tvTulisAyahBadge;
    private TextView btnToggleContohTulis;

    // Keyboard warning views
    private View cardKeyboardWarning;
    private ImageButton btnDismissKbWarning;
    private TextView btnOpenKbSettings, btnInstallGboard;

    // Contoh card views
    private TextView tvContohModeBadge, tvTargetArabic, tvTargetIndo;
    private View layoutTanpaContohNotice;

    // Input views
    private EditText etArabicInput;
    private TextView tvTypingCharCount;
    private TextView btnClearInput, btnSimpanInput, btnPeriksaKetikan;
    private boolean isRestoringDraft = false;

    // Check panel views
    private View cardCheckPanel;
    private TextView tvCheckScore, tvCheckStats, tvCheckWordDiff, tvCheckFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tulis);

        surahNumber = getIntent().getIntExtra("surah_number", 1);
        ayahNumber  = getIntent().getIntExtra("ayah_number", 1);

        initViews();
        loadAyahs();
        checkArabicKeyboard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTypeface();
        checkArabicKeyboard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentAyahDraft();
    }

    private void saveCurrentAyahDraft() {
        if (etArabicInput != null) {
            String text = etArabicInput.getText().toString();
            PreferenceManager.setTulisDraft(this, surahNumber, ayahNumber, text);
        }
    }

    private void initViews() {
        tvTulisTitle         = findViewById(R.id.tv_tulis_title);
        tvTulisSubtitle      = findViewById(R.id.tv_tulis_subtitle);
        tvTulisAyahBadge     = findViewById(R.id.tv_tulis_ayah_badge);
        btnToggleContohTulis = findViewById(R.id.btn_toggle_contoh_tulis);

        cardKeyboardWarning  = findViewById(R.id.card_keyboard_warning);
        btnDismissKbWarning  = findViewById(R.id.btn_dismiss_kb_warning);
        btnOpenKbSettings    = findViewById(R.id.btn_open_kb_settings);
        btnInstallGboard     = findViewById(R.id.btn_install_gboard);

        tvContohModeBadge        = findViewById(R.id.tv_contoh_mode_badge);
        tvTargetArabic           = findViewById(R.id.tv_target_arabic);
        tvTargetIndo             = findViewById(R.id.tv_target_indo);
        layoutTanpaContohNotice  = findViewById(R.id.layout_tanpa_contoh_notice);

        etArabicInput       = findViewById(R.id.et_arabic_input);
        tvTypingCharCount   = findViewById(R.id.tv_typing_char_count);
        btnClearInput       = findViewById(R.id.btn_clear_input);
        btnSimpanInput      = findViewById(R.id.btn_simpan_input);
        btnPeriksaKetikan   = findViewById(R.id.btn_periksa_ketikan);

        cardCheckPanel      = findViewById(R.id.card_check_panel);
        tvCheckScore        = findViewById(R.id.tv_check_score);
        tvCheckStats        = findViewById(R.id.tv_check_stats);
        tvCheckWordDiff     = findViewById(R.id.tv_check_word_diff);
        tvCheckFeedback     = findViewById(R.id.tv_check_feedback);

        findViewById(R.id.btn_back_tulis).setOnClickListener(v -> {
            saveCurrentAyahDraft();
            finish();
        });

        findViewById(R.id.btn_tulis_prev).setOnClickListener(v -> {
            if (ayahNumber > 1) {
                saveCurrentAyahDraft();
                ayahNumber--;
                displayAyah();
            }
        });

        findViewById(R.id.btn_tulis_next).setOnClickListener(v -> {
            if (ayahNumber < ayahs.size()) {
                saveCurrentAyahDraft();
                ayahNumber++;
                displayAyah();
            }
        });

        btnToggleContohTulis.setOnClickListener(v -> {
            boolean cur = PreferenceManager.isTulisDenganContoh(this);
            PreferenceManager.setTulisDenganContoh(this, !cur);
            updateContohUI(!cur);
            Toast.makeText(this, !cur ? "Panduan teks acuan ditampilkan" : "Teks acuan disembunyikan (Mode Uji Ingatan)", Toast.LENGTH_SHORT).show();
        });

        if (btnDismissKbWarning != null) {
            btnDismissKbWarning.setOnClickListener(v -> cardKeyboardWarning.setVisibility(View.GONE));
        }

        if (btnOpenKbSettings != null) {
            btnOpenKbSettings.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(this, "Buka Pengaturan HP > Sistem > Bahasa & Masukan > Keyboard", Toast.LENGTH_LONG).show();
                }
            });
        }

        if (btnInstallGboard != null) {
            btnInstallGboard.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.inputmethod.latin")));
                } catch (Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin")));
                }
            });
        }

        btnClearInput.setOnClickListener(v -> {
            etArabicInput.setText("");
            PreferenceManager.clearTulisDraft(this, surahNumber, ayahNumber);
            if (cardCheckPanel != null) cardCheckPanel.setVisibility(View.GONE);
            Toast.makeText(this, "Ketikan ayat ini telah dibersihkan", Toast.LENGTH_SHORT).show();
        });

        if (btnSimpanInput != null) {
            btnSimpanInput.setOnClickListener(v -> {
                saveCurrentAyahDraft();
                Toast.makeText(this, "💾 Ketikan Ayat " + ayahNumber + " berhasil disimpan!", Toast.LENGTH_SHORT).show();
            });
        }

        btnPeriksaKetikan.setOnClickListener(v -> periksaKetikan());

        etArabicInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int len = s != null ? s.toString().trim().length() : 0;
                tvTypingCharCount.setText(len + " huruf");
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isRestoringDraft) {
                    saveCurrentAyahDraft();
                }
            }
        });

        applyTypeface();
    }

    private void applyTypeface() {
        Typeface tf = PreferenceManager.getMushafTypeface(this);
        if (tvTargetArabic != null) {
            tvTargetArabic.setTypeface(tf);
            tvTargetArabic.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvTargetArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvTargetArabic.setGravity(android.view.Gravity.RIGHT | android.view.Gravity.END);
            tvTargetArabic.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        }
        if (etArabicInput != null) {
            etArabicInput.setTypeface(tf);
            etArabicInput.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            etArabicInput.setTextDirection(View.TEXT_DIRECTION_RTL);
            etArabicInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.RIGHT | android.view.Gravity.END);
            etArabicInput.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        }
        if (tvCheckWordDiff != null) {
            tvCheckWordDiff.setTypeface(tf);
            tvCheckWordDiff.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            tvCheckWordDiff.setTextDirection(View.TEXT_DIRECTION_RTL);
            tvCheckWordDiff.setGravity(android.view.Gravity.RIGHT | android.view.Gravity.END);
            tvCheckWordDiff.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        }
    }

    private void checkArabicKeyboard() {
        boolean hasArabic = isArabicKeyboardAvailable();
        if (cardKeyboardWarning != null) {
            cardKeyboardWarning.setVisibility(hasArabic ? View.GONE : View.VISIBLE);
        }
    }

    private boolean isArabicKeyboardAvailable() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) return true;
            List<InputMethodInfo> imiList = imm.getEnabledInputMethodList();
            if (imiList == null || imiList.isEmpty()) return true;

            for (InputMethodInfo imi : imiList) {
                int subtypeCount = imi.getSubtypeCount();
                for (int i = 0; i < subtypeCount; i++) {
                    InputMethodSubtype subtype = imi.getSubtypeAt(i);
                    String locale = subtype.getLocale();
                    if (locale != null && locale.toLowerCase().startsWith("ar")) {
                        return true;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        String tag = subtype.getLanguageTag();
                        if (tag != null && tag.toLowerCase().startsWith("ar")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
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
        tvTulisTitle.setText("Mengetik: " + displayName);
        tvTulisAyahBadge.setText(displayName + " : Ayat " + ayahNumber);

        tvTargetArabic.setText(currentAyah.getArabic());
        tvTargetIndo.setText(currentAyah.getIndo());

        boolean denganContoh = PreferenceManager.isTulisDenganContoh(this);
        updateContohUI(denganContoh);

        // Restore saved draft for this ayah
        String savedDraft = PreferenceManager.getTulisDraft(this, surahNumber, ayahNumber);
        isRestoringDraft = true;
        etArabicInput.setText(savedDraft != null ? savedDraft : "");
        isRestoringDraft = false;
        if (savedDraft != null && !savedDraft.isEmpty()) {
            etArabicInput.setSelection(savedDraft.length());
        }

        if (cardCheckPanel != null) cardCheckPanel.setVisibility(View.GONE);

        // Save last tulis position
        PreferenceManager.setLastTulis(this, surahNumber, surahName, ayahNumber);
    }

    private void updateContohUI(boolean denganContoh) {
        if (denganContoh) {
            tvTargetArabic.setVisibility(View.VISIBLE);
            layoutTanpaContohNotice.setVisibility(View.GONE);
            btnToggleContohTulis.setText("👁️ Contoh: Ya");
            tvContohModeBadge.setText("Dengan Contoh");
            tvContohModeBadge.setTextColor(getResources().getColor(R.color.emerald));
        } else {
            tvTargetArabic.setVisibility(View.GONE);
            layoutTanpaContohNotice.setVisibility(View.VISIBLE);
            btnToggleContohTulis.setText("👁️ Contoh: Tidak");
            tvContohModeBadge.setText("Tanpa Contoh");
            tvContohModeBadge.setTextColor(getResources().getColor(R.color.gold_primary));
        }
    }

    // ── ARABIC NORMALIZATION & WORD ALIGNMENT ──

    private String normAr(String text) {
        if (text == null) return "";
        String s = text.replaceAll("[\u064B-\u065F\u0670\u06D6-\u06ED]", "");
        s = s.replaceAll("[\u0623\u0625\u0622\u0671]", "\u0627");
        s = s.replaceAll("[\u0649\u0626]", "\u064A");
        s = s.replace('\u0629', '\u0647');
        s = s.replace('\u0624', '\u0648');
        s = s.replaceAll("[^\u0621-\u064A\\s]", "");
        return s.trim();
    }

    private int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++)
            for (int j = 1; j <= lb; j++)
                if (a.charAt(i-1) == b.charAt(j-1)) dp[i][j] = dp[i-1][j-1];
                else dp[i][j] = 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
        return dp[la][lb];
    }

    private float similarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) return 1f;
        return 1f - (float) levenshtein(a, b) / maxLen;
    }

    private static final int CORRECT = 0, NEAR = 1, WRONG = 2, MISSING = 3;

    private int[] alignWords(String[] refWords, String[] spkWords) {
        int[] result = new int[refWords.length];
        boolean[] used = new boolean[spkWords.length];
        for (int i = 0; i < refWords.length; i++) {
            float bestSim = -1; int bestJ = -1;
            for (int j = 0; j < spkWords.length; j++) {
                if (used[j]) continue;
                float sim = similarity(refWords[i], spkWords[j]);
                if (sim > bestSim) { bestSim = sim; bestJ = j; }
            }
            if (bestJ < 0) result[i] = MISSING;
            else if (bestSim == 1f) { result[i] = CORRECT; used[bestJ] = true; }
            else if (bestSim >= AMBANG_MIRIP) { result[i] = NEAR; used[bestJ] = true; }
            else result[i] = WRONG;
        }
        return result;
    }

    // ── PERIKSA KETIKAN ──

    private void periksaKetikan() {
        String rawUserText = etArabicInput.getText().toString();
        String userText = rawUserText.trim();
        if (userText.isEmpty()) {
            Toast.makeText(this, "Silakan ketik teks ayat terlebih dahulu menggunakan keyboard Arab", Toast.LENGTH_LONG).show();
            return;
        }

        if (cardCheckPanel == null) return;

        String refNorm = normAr(currentAyah.getArabic());
        String spkNorm = normAr(userText);
        String[] refWords = refNorm.split("\\s+");
        String[] spkWords = spkNorm.isEmpty() ? new String[0] : spkNorm.split("\\s+");
        String[] spkRawWords = userText.split("\\s+");
        String[] refRawWords = currentAyah.getArabic().trim().split("\\s+");

        boolean[] refMatched = new boolean[refWords.length];
        boolean[] spkMatched = new boolean[spkWords.length];
        int[] spkClass = new int[spkWords.length];
        for (int j = 0; j < spkWords.length; j++) {
            spkClass[j] = WRONG;
        }

        int correct = 0, near = 0;
        for (int i = 0; i < refWords.length; i++) {
            float bestSim = -1; int bestJ = -1;
            for (int j = 0; j < spkWords.length; j++) {
                if (spkMatched[j]) continue;
                float sim = similarity(refWords[i], spkWords[j]);
                if (sim > bestSim) { bestSim = sim; bestJ = j; }
            }
            if (bestJ >= 0 && bestSim >= AMBANG_MIRIP) {
                refMatched[i] = true;
                spkMatched[bestJ] = true;
                if (bestSim == 1f) {
                    spkClass[bestJ] = CORRECT;
                    correct++;
                } else {
                    spkClass[bestJ] = NEAR;
                    near++;
                }
            }
        }

        int wrong = 0;
        for (int j = 0; j < spkWords.length; j++) {
            if (!spkMatched[j]) {
                spkClass[j] = WRONG;
                wrong++;
            }
        }

        int missing = 0;
        for (boolean m : refMatched) {
            if (!m) missing++;
        }

        int total = refWords.length;
        float rawScore = total > 0 ? (correct + near * BOBOT_NEAR - (wrong + missing) * PENALTI_WRONG) / total * 100f : 0f;
        int score = (int) Math.max(0, Math.min(100, rawScore));

        tvCheckScore.setText(score + "%");
        tvCheckStats.setText("✅ Benar: " + correct + "  🔵 Mirip: " + near + "  ❌ Salah: " + wrong + "  ⚫ Terlewat: " + missing);

        // Feedback text
        if (score == 100) {
            tvCheckFeedback.setText("🌟 Masya Allah! Ketikan Anda 100% sempurna tanpa kesalahan.");
            tvCheckFeedback.setTextColor(getResources().getColor(R.color.emerald));
        } else if (score >= 80) {
            tvCheckFeedback.setText("👍 Sangat Baik! Tulisan Anda hampir sempurna, perhatikan kata-kata bertanda merah.");
            tvCheckFeedback.setTextColor(getResources().getColor(R.color.gold_primary));
        } else if (score >= 60) {
            tvCheckFeedback.setText("📖 Cukup Baik! Perbaiki kata-kata yang berwarna merah.");
            tvCheckFeedback.setTextColor(getResources().getColor(R.color.cyan_accent));
        } else {
            tvCheckFeedback.setText("✍️ Terus berlatih! Huruf/kata yang berwarna merah salah dan perlu dikoreksi.");
            tvCheckFeedback.setTextColor(getResources().getColor(R.color.red_error));
        }

        // 1. Highlight user's typing in etArabicInput (WARNA MERAH UNTUK KATA YANG SALAH)
        SpannableStringBuilder inputSsb = new SpannableStringBuilder();
        for (int j = 0; j < spkRawWords.length; j++) {
            if (j > 0) inputSsb.append(" ");
            int start = inputSsb.length();
            inputSsb.append(spkRawWords[j]);
            int end = inputSsb.length();

            int classification = (j < spkClass.length) ? spkClass[j] : WRONG;
            if (classification == CORRECT) {
                inputSsb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // hijau
            } else if (classification == NEAR) {
                inputSsb.setSpan(new ForegroundColorSpan(0xFF38BDF8), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); // biru/cyan
            } else {
                // WARNA MERAH UNTUK KATA YANG SALAH
                inputSsb.setSpan(new ForegroundColorSpan(0xFFFF2B2B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                inputSsb.setSpan(new BackgroundColorSpan(0x33FF0000), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                inputSsb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        etArabicInput.setText(inputSsb);
        etArabicInput.setSelection(inputSsb.length());

        // 2. Word diff display: Target verse words (Hijau = Cocok, Merah = Salah / Terlewat)
        SpannableStringBuilder diffSsb = new SpannableStringBuilder();
        for (int i = 0; i < refRawWords.length; i++) {
            if (i > 0) diffSsb.append(" ");
            int start = diffSsb.length();
            diffSsb.append(refRawWords[i]);
            int end = diffSsb.length();

            if (i < refMatched.length && refMatched[i]) {
                diffSsb.setSpan(new ForegroundColorSpan(0xFF10B981), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                // WARNA MERAH UNTUK KATA YANG SALAH ATAU TERLEWAT
                diffSsb.setSpan(new ForegroundColorSpan(0xFFFF2B2B), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                diffSsb.setSpan(new BackgroundColorSpan(0x33FF0000), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                diffSsb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        tvCheckWordDiff.setText(diffSsb);

        cardCheckPanel.setVisibility(View.VISIBLE);

        // Record progress if acceptable score
        if (score >= 70) {
            PreferenceManager.markTulisan(this, surahNumber, ayahNumber);
        }
    }
}

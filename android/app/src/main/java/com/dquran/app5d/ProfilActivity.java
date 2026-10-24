package com.dquran.app5d;

import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ProfilActivity extends AppCompatActivity {

    private static final String TAG = "ProfilActivity";
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_CHOOSE_ACCOUNT = 9002;
    private static final int TOTAL_AYAT = 6236;

    private GoogleSignInClient mGoogleSignInClient;

    private View layoutNotLoggedIn;
    private View layoutLoggedIn;
    private ImageView ivUserAvatar;
    private TextView tvUserName;
    private TextView tvUserEmail;

    private TextView tvStatBaca;
    private TextView tvStatTulis;
    private TextView tvStatHafalan;
    private View barBaca;
    private View barTulis;
    private View barHafalan;

    private TextView tvLastRead;
    private TextView tvLastTulis;
    private TextView tvLastHafalan;

    // PDF Export & Audio views
    private TextView tvBadgePdfTulis;
    private TextView tvBadgePlayRecordings;
    private ProgressDialog pdfProgressDialog;

    // User Recordings Playlist Player
    private MediaPlayer allRecordingsPlayer;
    private Dialog allRecordingsDialog;
    private List<File> userAudioFiles = new ArrayList<>();
    private int currentAudioIndex = 0;
    private boolean isAudioPlaying = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profil);

        setupGoogleSignIn();
        initViews();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        ImageButton btnSettings = findViewById(R.id.btn_profil_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        layoutNotLoggedIn = findViewById(R.id.layout_not_logged_in);
        layoutLoggedIn    = findViewById(R.id.layout_logged_in);
        ivUserAvatar      = findViewById(R.id.iv_user_avatar);
        tvUserName        = findViewById(R.id.tv_user_name);
        tvUserEmail       = findViewById(R.id.tv_user_email);

        tvStatBaca        = findViewById(R.id.tv_profil_stat_baca);
        tvStatTulis       = findViewById(R.id.tv_profil_stat_tulis);
        tvStatHafalan     = findViewById(R.id.tv_profil_stat_hafalan);
        barBaca           = findViewById(R.id.bar_profil_baca);
        barTulis          = findViewById(R.id.bar_profil_tulis);
        barHafalan        = findViewById(R.id.bar_profil_hafalan);

        tvLastRead        = findViewById(R.id.tv_profil_last_read);
        tvLastTulis       = findViewById(R.id.tv_profil_last_tulis);
        tvLastHafalan     = findViewById(R.id.tv_profil_last_hafalan);

        tvBadgePdfTulis        = findViewById(R.id.tv_badge_pdf_tulis);
        tvBadgePlayRecordings  = findViewById(R.id.tv_badge_play_recordings);
    }

    private void setupListeners() {
        // Google Sign-In button
        findViewById(R.id.btn_google_signin).setOnClickListener(v -> signInWithGoogle());

        // Device Google account direct picker
        View btnDevice = findViewById(R.id.btn_choose_device_account);
        if (btnDevice != null) {
            btnDevice.setOnClickListener(v -> chooseDeviceGoogleAccount());
        }

        // Show SHA-1 info dialog
        View btnSha1 = findViewById(R.id.btn_show_sha1);
        if (btnSha1 != null) {
            btnSha1.setOnClickListener(v -> showSha1InfoDialog());
        }

        // Manual name sign-in / guest
        findViewById(R.id.btn_manual_name_signin).setOnClickListener(v -> showManualNameDialog());

        // Edit profile name
        findViewById(R.id.btn_edit_profile).setOnClickListener(v -> showManualNameDialog());

        // Sync web button
        View btnSyncWeb = findViewById(R.id.btn_sync_web);
        if (btnSyncWeb != null) {
            btnSyncWeb.setOnClickListener(v -> {
                Toast.makeText(this, "🔄 Menyinkronkan profil dengan web...", Toast.LENGTH_SHORT).show();
                SyncManager.syncWithWeb(this, new SyncManager.OnSyncCallback() {
                    @Override
                    public void onSuccess(String message) {
                        Toast.makeText(ProfilActivity.this, "✅ " + message, Toast.LENGTH_SHORT).show();
                        updateUI();
                    }
                    @Override
                    public void onError(String error) {
                        Toast.makeText(ProfilActivity.this, "⚠️ " + error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }

        // Logout
        findViewById(R.id.btn_logout).setOnClickListener(v -> logout());

        // Pengaturan shortcut
        findViewById(R.id.btn_profil_open_settings).setOnClickListener(v -> showSettingsDialog());

        // Chat & Pelaporan shortcut
        View btnChatFeedback = findViewById(R.id.btn_profil_chat_feedback);
        if (btnChatFeedback != null) {
            btnChatFeedback.setOnClickListener(v -> {
                Intent intent = new Intent(ProfilActivity.this, ChatFeedbackActivity.class);
                startActivity(intent);
            });
        }

        // Click on Last Activity rows
        findViewById(R.id.row_last_read).setOnClickListener(v -> {
            int surah = PreferenceManager.getLastReadSurah(this);
            int ayah  = PreferenceManager.getLastReadAyah(this);
            Intent intent = new Intent(this, SurahActivity.class);
            intent.putExtra("surah_number", surah);
            intent.putExtra("target_ayah", ayah);
            startActivity(intent);
        });

        findViewById(R.id.row_last_tulis).setOnClickListener(v -> {
            int surah = PreferenceManager.getLastTulisSurah(this);
            if (surah == 0) {
                Toast.makeText(this, "Belum ada riwayat Tulis", Toast.LENGTH_SHORT).show();
                return;
            }
            int ayah = PreferenceManager.getLastTulisAyah(this);
            Intent intent = new Intent(this, TulisActivity.class);
            intent.putExtra("surah_number", surah);
            intent.putExtra("ayah_number", ayah);
            startActivity(intent);
        });

        findViewById(R.id.row_last_hafalan).setOnClickListener(v -> {
            int surah = PreferenceManager.getLastHafalanSurah(this);
            if (surah == 0) {
                Toast.makeText(this, "Belum ada riwayat Hafalan", Toast.LENGTH_SHORT).show();
                return;
            }
            int ayah = PreferenceManager.getLastHafalanAyah(this);
            Intent intent = new Intent(this, HafalanActivity.class);
            intent.putExtra("surah_number", surah);
            intent.putExtra("ayah_number", ayah);
            startActivity(intent);
        });

        // Export PDF listener
        View btnPdfTulis = findViewById(R.id.btn_export_pdf_tulis);
        if (btnPdfTulis != null) {
            btnPdfTulis.setOnClickListener(v -> exportPdf(false));
        }

        // Play All Recordings listener
        View btnPlayAll = findViewById(R.id.btn_play_all_recordings);
        if (btnPlayAll != null) {
            btnPlayAll.setOnClickListener(v -> playAllHafalanRecordings());
        }
    }

    private void signInWithGoogle() {
        if (mGoogleSignInClient == null) return;
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void chooseDeviceGoogleAccount() {
        try {
            Intent intent = AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    new String[]{"com.google"},
                    null,
                    null,
                    null,
                    null
            );
            startActivityForResult(intent, RC_CHOOSE_ACCOUNT);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Tidak ada akun Google yang terdeteksi di HP ini", Toast.LENGTH_SHORT).show();
            showManualNameDialog();
        } catch (Exception e) {
            Log.e(TAG, "Gagal membuka pemilih akun: " + e.getMessage());
            showManualNameDialog();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    String name = account.getDisplayName();
                    String email = account.getEmail();
                    String photoUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : "";
                    String id = account.getId();

                    PreferenceManager.setUserProfile(this, name, email, photoUrl, id);
                    Toast.makeText(this, "Selamat datang, " + name, Toast.LENGTH_SHORT).show();
                    updateUI();
                    triggerSyncAfterLogin();
                }
            } catch (ApiException e) {
                int code = e.getStatusCode();
                Log.w(TAG, "signInResult:failed code=" + code);
                handleSignInError(code);
            }
        } else if (requestCode == RC_CHOOSE_ACCOUNT) {
            if (resultCode == RESULT_OK && data != null) {
                String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                if (accountName != null && !accountName.trim().isEmpty()) {
                    String derivedName = extractNameFromEmail(accountName);
                    PreferenceManager.setUserProfile(this, derivedName, accountName, "", "google_device");
                    Toast.makeText(this, "Selamat datang, " + derivedName, Toast.LENGTH_SHORT).show();
                    updateUI();
                    triggerSyncAfterLogin();
                }
            }
        }
    }

    private void triggerSyncAfterLogin() {
        SyncManager.syncWithWeb(this, new SyncManager.OnSyncCallback() {
            @Override
            public void onSuccess(String message) {
                Toast.makeText(ProfilActivity.this, "✅ " + message, Toast.LENGTH_SHORT).show();
                updateUI();
            }
            @Override
            public void onError(String error) {
                Log.w(TAG, "Sync note: " + error);
            }
        });
    }

    private void handleSignInError(int statusCode) {
        if (statusCode == 12501) {
            // Pengguna membatalkan pemilihan akun
            return;
        }

        if (statusCode == 10 || statusCode == 12500) {
            new AlertDialog.Builder(this)
                    .setTitle("Masuk Akun Google")
                    .setMessage("Google Sign-In resmi memerlukan sidik jari sertifikat (SHA-1) terdaftar di Google Cloud Console.\n\n"
                            + "💡 Solusi Mudah: Anda dapat langsung memilih Akun Google yang sudah aktif di HP ini tanpa perlu mendaftar ke Google Console.")
                    .setPositiveButton("Pilih Akun di HP", (d, w) -> chooseDeviceGoogleAccount())
                    .setNeutralButton("Salin Data SHA-1", (d, w) -> copySha1Info())
                    .setNegativeButton("Nama Manual", (d, w) -> showManualNameDialog())
                    .show();
            return;
        }

        String msg;
        if (statusCode == 7) {
            msg = "Koneksi jaringan gagal. Periksa koneksi internet Anda.";
        } else {
            msg = "Gagal masuk (" + statusCode + "). Anda dapat memilih akun Google di HP atau memasukkan nama secara manual.";
        }

        new AlertDialog.Builder(this)
                .setTitle("Info Akun Google")
                .setMessage(msg)
                .setPositiveButton("Pilih Akun di HP", (d, w) -> chooseDeviceGoogleAccount())
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void showSha1InfoDialog() {
        String info = "Data untuk didaftarkan ke Google Cloud Console / Firebase:\n\n"
                + "• Package Name:\ncom.dquran.app5d\n\n"
                + "• SHA-1 Fingerprint:\n91:80:AB:82:F6:D2:B6:0F:93:4B:BA:28:AE:3E:3A:42:5F:80:BE:9B\n\n"
                + "• SHA-256 Fingerprint:\n98:34:C4:28:78:A6:65:61:CF:AC:5A:5E:79:85:60:70:48:B6:4D:86:E8:52:AF:FC:04:26:C2:23:71:E3:C2:10";

        new AlertDialog.Builder(this)
                .setTitle("Info SHA-1 Google Console")
                .setMessage(info)
                .setPositiveButton("Salin ke Clipboard", (d, w) -> copySha1Info())
                .setNeutralButton("Pilih Akun di HP", (d, w) -> chooseDeviceGoogleAccount())
                .setNegativeButton("Tutup", null)
                .show();
    }

    private void copySha1Info() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("SHA1_Google_Console",
                "Package Name: com.dquran.app5d\nSHA-1: 91:80:AB:82:F6:D2:B6:0F:93:4B:BA:28:AE:3E:3A:42:5F:80:BE:9B");
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Data SHA-1 berhasil disalin ke clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private String extractNameFromEmail(String email) {
        if (email == null || !email.contains("@")) return "Pengguna 5D Quran";
        String prefix = email.substring(0, email.indexOf('@'));
        String[] parts = prefix.split("[._\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            String clean = p.replaceAll("[0-9]", "").trim();
            if (!clean.isEmpty()) {
                sb.append(Character.toUpperCase(clean.charAt(0)));
                if (clean.length() > 1) {
                    sb.append(clean.substring(1).toLowerCase());
                }
                sb.append(" ");
            }
        }
        String res = sb.toString().trim();
        return res.isEmpty() ? prefix : res;
    }

    private void showManualNameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nama Profil");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 10);

        final EditText etName = new EditText(this);
        etName.setHint("Nama Lengkap");
        etName.setText(PreferenceManager.getUserName(this));
        layout.addView(etName);

        final EditText etEmail = new EditText(this);
        etEmail.setHint("Email (opsional)");
        etEmail.setText(PreferenceManager.getUserEmail(this));
        layout.addView(etEmail);

        builder.setView(layout);
        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) name = "Pengguna 5D Quran";
            String email = etEmail.getText().toString().trim();
            PreferenceManager.setUserProfile(this, name, email, "", "manual");
            Toast.makeText(this, "Profil berhasil disimpan", Toast.LENGTH_SHORT).show();
            updateUI();
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void logout() {
        if (mGoogleSignInClient != null) {
            mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
                PreferenceManager.logoutUser(this);
                Toast.makeText(this, "Berhasil keluar", Toast.LENGTH_SHORT).show();
                updateUI();
            });
        } else {
            PreferenceManager.logoutUser(this);
            Toast.makeText(this, "Berhasil keluar", Toast.LENGTH_SHORT).show();
            updateUI();
        }
    }

    private void updateUI() {
        boolean loggedIn = PreferenceManager.isLoggedIn(this);
        if (loggedIn) {
            layoutNotLoggedIn.setVisibility(View.GONE);
            layoutLoggedIn.setVisibility(View.VISIBLE);

            String name = PreferenceManager.getUserName(this);
            String email = PreferenceManager.getUserEmail(this);
            String photo = PreferenceManager.getUserPhotoUrl(this);

            tvUserName.setText(name);
            tvUserEmail.setText(email.isEmpty() ? "Akun Lokal" : email);

            loadAvatar(photo, ivUserAvatar);
        } else {
            layoutNotLoggedIn.setVisibility(View.VISIBLE);
            layoutLoggedIn.setVisibility(View.GONE);
        }

        updateKemajuanKriteria();
        updateAktivitas();
    }

    private void updateKemajuanKriteria() {
        int baca    = PreferenceManager.getProgressBaca(this);
        int tulis   = PreferenceManager.getProgressTulisan(this);
        int hafalan = PreferenceManager.getProgressHafalan(this);

        tvStatBaca.setText(formatStat(baca));
        tvStatTulis.setText(formatStat(tulis));
        tvStatHafalan.setText(formatStat(hafalan));

        updateBar(barBaca,    baca);
        updateBar(barTulis,   tulis);
        updateBar(barHafalan, hafalan);

        if (tvBadgePdfTulis != null) {
            tvBadgePdfTulis.setText(tulis > 0
                    ? tulis + " ayat tersimpan • Siap dijadikan Mushaf Al-Quran"
                    : "Format Mushaf dari ayat yang telah Anda ketik");
        }

        if (tvBadgePlayRecordings != null) {
            int recCount = getUserHafalanAudioFiles().size();
            tvBadgePlayRecordings.setText(recCount > 0
                    ? recCount + " rekaman suara tersedia • Ketuk untuk memutar seluruhnya"
                    : "Belum ada rekaman setoran hafalan");
        }
    }

    private String formatStat(int count) {
        float pct = (float) count / TOTAL_AYAT * 100f;
        return String.format(java.util.Locale.US, "%,d / %,d (%.1f%%)", count, TOTAL_AYAT, pct);
    }

    private void updateBar(final View bar, final int count) {
        if (bar == null) return;
        final View parent = (View) bar.getParent();
        if (parent == null) return;
        parent.post(() -> {
            int parentWidth = parent.getWidth();
            if (parentWidth <= 0) return;
            float ratio = (float) count / TOTAL_AYAT;
            int barWidth = (int) (parentWidth * Math.min(1.0f, ratio));
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.width = Math.max(0, barWidth);
            bar.setLayoutParams(lp);
        });
    }

    private void updateAktivitas() {
        int surahRead = PreferenceManager.getLastReadSurah(this);
        String nameRead = PreferenceManager.getLastReadSurahName(this);
        int ayahRead = PreferenceManager.getLastReadAyah(this);
        tvLastRead.setText(nameRead + ": " + ayahRead);

        int surahTulis = PreferenceManager.getLastTulisSurah(this);
        if (surahTulis == 0) {
            tvLastTulis.setText("Belum ada");
        } else {
            tvLastTulis.setText(PreferenceManager.getLastTulisSurahName(this) + ": " + PreferenceManager.getLastTulisAyah(this));
        }

        int surahHafalan = PreferenceManager.getLastHafalanSurah(this);
        if (surahHafalan == 0) {
            tvLastHafalan.setText("Belum ada");
        } else {
            tvLastHafalan.setText(PreferenceManager.getLastHafalanSurahName(this) + ": " + PreferenceManager.getLastHafalanAyah(this));
        }
    }

    private void loadAvatar(String photoUrl, ImageView imageView) {
        if (photoUrl == null || photoUrl.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_person);
            return;
        }
        new Thread(() -> {
            try {
                URL url = new URL(photoUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoInput(true);
                conn.connect();
                InputStream input = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                runOnUiThread(() -> {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        imageView.setImageResource(R.drawable.ic_person);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> imageView.setImageResource(R.drawable.ic_person));
            }
        }).start();
    }

    private void showSettingsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_settings);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        RadioGroup rgQari = dialog.findViewById(R.id.rg_qari);
        String currentQariId = PreferenceManager.getQariId(this);
        if ("Abdurrahmaan_As-Sudais_192kbps".equals(currentQariId)) {
            ((RadioButton) dialog.findViewById(R.id.rb_qari_sudais)).setChecked(true);
        } else if ("Ghamadi_40kbps".equals(currentQariId)) {
            ((RadioButton) dialog.findViewById(R.id.rb_qari_ghamdi)).setChecked(true);
        } else if ("Husary_128kbps".equals(currentQariId)) {
            ((RadioButton) dialog.findViewById(R.id.rb_qari_husary)).setChecked(true);
        } else if ("Minshawy_Murattal_128kbps".equals(currentQariId)) {
            ((RadioButton) dialog.findViewById(R.id.rb_qari_minshawi)).setChecked(true);
        } else {
            ((RadioButton) dialog.findViewById(R.id.rb_qari_alafasy)).setChecked(true);
        }

        // Huruf Mushaf selection
        RadioGroup rgHurufMushaf = dialog.findViewById(R.id.rg_huruf_mushaf);
        String currentHuruf = PreferenceManager.getHurufMushaf(this);
        if ("naskh".equals(currentHuruf)) {
            ((RadioButton) dialog.findViewById(R.id.rb_mushaf_naskh)).setChecked(true);
        } else if ("indopak".equals(currentHuruf)) {
            ((RadioButton) dialog.findViewById(R.id.rb_mushaf_indopak)).setChecked(true);
        } else {
            ((RadioButton) dialog.findViewById(R.id.rb_mushaf_madani)).setChecked(true);
        }

        RadioGroup rgFontSize = dialog.findViewById(R.id.rg_font_size);
        int currentSize = PreferenceManager.getArabicFontSize(this);
        if (currentSize == 22) {
            ((RadioButton) dialog.findViewById(R.id.rb_size_sedang)).setChecked(true);
        } else if (currentSize == 32) {
            ((RadioButton) dialog.findViewById(R.id.rb_size_xbesar)).setChecked(true);
        } else if (currentSize == 38) {
            ((RadioButton) dialog.findViewById(R.id.rb_size_jumbo)).setChecked(true);
        } else {
            ((RadioButton) dialog.findViewById(R.id.rb_size_besar)).setChecked(true);
        }

        // Tampilan Ayat selection
        RadioGroup rgTampilanAyat = dialog.findViewById(R.id.rg_tampilan_ayat);
        if (PreferenceManager.isArabSaja(this)) {
            ((RadioButton) dialog.findViewById(R.id.rb_tampilan_arab)).setChecked(true);
        } else {
            ((RadioButton) dialog.findViewById(R.id.rb_tampilan_lengkap)).setChecked(true);
        }

        // Contoh Tulis
        RadioGroup rgContohTulis = dialog.findViewById(R.id.rg_contoh_tulis);
        boolean tulisContoh = PreferenceManager.isTulisDenganContoh(this);
        if (tulisContoh) {
            ((RadioButton) dialog.findViewById(R.id.rb_tulis_dengan_contoh)).setChecked(true);
        } else {
            ((RadioButton) dialog.findViewById(R.id.rb_tulis_tanpa_contoh)).setChecked(true);
        }

        // Contoh Hafalan
        RadioGroup rgContohHafalan = dialog.findViewById(R.id.rg_contoh_hafalan);
        boolean hafalContoh = PreferenceManager.isHafalanDenganContoh(this);
        if (hafalContoh) {
            ((RadioButton) dialog.findViewById(R.id.rb_hafal_dengan_contoh)).setChecked(true);
        } else {
            ((RadioButton) dialog.findViewById(R.id.rb_hafal_tanpa_contoh)).setChecked(true);
        }

        dialog.findViewById(R.id.btn_cancel_settings).setOnClickListener(v -> dialog.dismiss());

        dialog.findViewById(R.id.btn_save_settings).setOnClickListener(v -> {
            int qariCheckId = rgQari.getCheckedRadioButtonId();
            if (qariCheckId == R.id.rb_qari_sudais) {
                PreferenceManager.setQari(this, "Abdurrahmaan_As-Sudais_192kbps", "Syaikh Abdul Rahman As-Sudais");
            } else if (qariCheckId == R.id.rb_qari_ghamdi) {
                PreferenceManager.setQari(this, "Ghamadi_40kbps", "Syaikh Saad Al-Ghamdi");
            } else if (qariCheckId == R.id.rb_qari_husary) {
                PreferenceManager.setQari(this, "Husary_128kbps", "Syaikh Mahmud Khalil Al-Husary");
            } else if (qariCheckId == R.id.rb_qari_minshawi) {
                PreferenceManager.setQari(this, "Minshawy_Murattal_128kbps", "Syaikh Muhammad Siddiq Al-Minshawi");
            } else {
                PreferenceManager.setQari(this, "Alafasy_128kbps", "Syaikh Mishari Rashid Alafasy");
            }

            // Save Huruf Mushaf
            int hurufCheckId = rgHurufMushaf.getCheckedRadioButtonId();
            if (hurufCheckId == R.id.rb_mushaf_naskh) {
                PreferenceManager.setHurufMushaf(this, "naskh");
            } else if (hurufCheckId == R.id.rb_mushaf_indopak) {
                PreferenceManager.setHurufMushaf(this, "indopak");
            } else {
                PreferenceManager.setHurufMushaf(this, "madani");
            }

            int sizeCheckId = rgFontSize.getCheckedRadioButtonId();
            if (sizeCheckId == R.id.rb_size_sedang) {
                PreferenceManager.setArabicFontSize(this, 22);
            } else if (sizeCheckId == R.id.rb_size_xbesar) {
                PreferenceManager.setArabicFontSize(this, 32);
            } else if (sizeCheckId == R.id.rb_size_jumbo) {
                PreferenceManager.setArabicFontSize(this, 38);
            } else {
                PreferenceManager.setArabicFontSize(this, 26);
            }

            // Save Tampilan Ayat
            int tampilanCheckId = rgTampilanAyat.getCheckedRadioButtonId();
            if (tampilanCheckId == R.id.rb_tampilan_arab) {
                PreferenceManager.setTampilanAyat(this, "arab");
            } else {
                PreferenceManager.setTampilanAyat(this, "lengkap");
            }

            // Save contoh preferences
            boolean isTulisChecked = rgContohTulis.getCheckedRadioButtonId() == R.id.rb_tulis_dengan_contoh;
            PreferenceManager.setTulisDenganContoh(this, isTulisChecked);

            boolean isHafalChecked = rgContohHafalan.getCheckedRadioButtonId() == R.id.rb_hafal_dengan_contoh;
            PreferenceManager.setHafalanDenganContoh(this, isHafalChecked);

            // Sync with web if logged in
            if (PreferenceManager.isLoggedIn(this)) {
                triggerSyncAfterLogin();
            }

            Toast.makeText(this, "✅ Pengaturan berhasil disimpan", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ── CETAK / EXPORT DOKUMEN MUSHAF PDF ──

    private void exportPdf(boolean isHafalan) {
        if (pdfProgressDialog != null && pdfProgressDialog.isShowing()) {
            return;
        }

        pdfProgressDialog = new ProgressDialog(this);
        pdfProgressDialog.setTitle(isHafalan ? "Cetak PDF Hafalan" : "Cetak PDF Tulisan");
        pdfProgressDialog.setMessage("Menyiapkan lembaran mushaf Al-Quran...");
        pdfProgressDialog.setCancelable(false);
        pdfProgressDialog.show();

        MushafPdfGenerator.OnPdfGenerateListener listener = new MushafPdfGenerator.OnPdfGenerateListener() {
            @Override
            public void onProgress(String status) {
                if (pdfProgressDialog != null && pdfProgressDialog.isShowing()) {
                    pdfProgressDialog.setMessage(status);
                }
            }

            @Override
            public void onSuccess(File pdfFile, int totalAyahs, int totalPages) {
                if (pdfProgressDialog != null && pdfProgressDialog.isShowing()) {
                    pdfProgressDialog.dismiss();
                }

                String title = isHafalan ? "🌟 Mushaf Hafalan Siap!" : "🌟 Mushaf Tulisan Siap!";
                String msg = totalAyahs + " ayat berhasil disusun ke dalam format Mushaf Al-Quran standar siap cetak.\n\n"
                        + "Nama Berkas:\n" + pdfFile.getName() + "\n\n"
                        + "Lokasi Penyimpanan:\nFolder Dokumen/5DQuran";

                new AlertDialog.Builder(ProfilActivity.this)
                        .setTitle(title)
                        .setMessage(msg)
                        .setPositiveButton("Buka Dokumen PDF", (d, w) -> openPdfFile(pdfFile))
                        .setNeutralButton("Bagikan / Cetak", (d, w) -> sharePdfFile(pdfFile))
                        .setNegativeButton("Tutup", null)
                        .show();
            }

            @Override
            public void onEmpty(String message) {
                if (pdfProgressDialog != null && pdfProgressDialog.isShowing()) {
                    pdfProgressDialog.dismiss();
                }
                new AlertDialog.Builder(ProfilActivity.this)
                        .setTitle("Belum Ada Ayat")
                        .setMessage(message)
                        .setPositiveButton("Mengerti", null)
                        .show();
            }

            @Override
            public void onError(Exception e) {
                if (pdfProgressDialog != null && pdfProgressDialog.isShowing()) {
                    pdfProgressDialog.dismiss();
                }
                Toast.makeText(ProfilActivity.this, "Gagal membuat PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };

        if (isHafalan) {
            MushafPdfGenerator.generateHafalanPdf(this, listener);
        } else {
            MushafPdfGenerator.generateTulisanPdf(this, listener);
        }
    }

    private void openPdfFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Berkas PDF tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(intent, "Buka Dokumen Mushaf PDF"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Tidak ada aplikasi pembuka PDF di HP ini. File tersimpan di folder Documents/5DQuran/" + file.getName(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePdfFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "Berkas PDF tidak ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
            intent.putExtra(Intent.EXTRA_TEXT, "Dokumen Mushaf Al-Quran Pribadi — Dihasilkan dari Aplikasi 5D Quran");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Bagikan Dokumen Mushaf PDF"));
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membagikan PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAndReleaseAudioPlayer();
    }

    // ── PEMUTAR REKAMAN HAFALAN USER KESELURUHAN ──

    private List<File> getUserHafalanAudioFiles() {
        java.util.Map<String, File> uniqueMap = new java.util.HashMap<>();
        File dir = new File(getCacheDir(), "hafalan_audio");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                Pattern pattern = Pattern.compile("^recitation_s(\\d+)_a(\\d+)\\.(m4a|wav)$");
                for (File f : files) {
                    if (f.isFile() && f.length() > 500) {
                        Matcher m = pattern.matcher(f.getName());
                        if (m.matches()) {
                            String key = m.group(1) + "_" + m.group(2);
                            File existing = uniqueMap.get(key);
                            if (existing == null || f.lastModified() > existing.lastModified()) {
                                uniqueMap.put(key, f);
                            }
                        }
                    }
                }
            }
        }

        List<File> result = new ArrayList<>(uniqueMap.values());

        // Urutkan berdasarkan nomor surat ASC, lalu nomor ayat ASC
        Collections.sort(result, (f1, f2) -> {
            int[] p1 = parseSurahAyah(f1.getName());
            int[] p2 = parseSurahAyah(f2.getName());
            if (p1[0] != p2[0]) return Integer.compare(p1[0], p2[0]);
            return Integer.compare(p1[1], p2[1]);
        });

        return result;
    }

    private int[] parseSurahAyah(String fileName) {
        Matcher m = Pattern.compile("^recitation_s(\\d+)_a(\\d+)\\.(m4a|wav)$").matcher(fileName);
        if (m.matches()) {
            try {
                return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
            } catch (Exception ignored) {}
        }
        return new int[]{0, 0};
    }

    private void playAllHafalanRecordings() {
        userAudioFiles = getUserHafalanAudioFiles();
        if (userAudioFiles.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Belum Ada Rekaman")
                    .setMessage("Anda belum memiliki rekaman suara hafalan yang tersimpan di HP ini.\n\nSilakan setor hafalan terlebih dahulu di menu Dihafal.")
                    .setPositiveButton("Mengerti", null)
                    .show();
            return;
        }

        showUserRecordingsPlayerDialog(userAudioFiles);
    }

    private void showUserRecordingsPlayerDialog(List<File> files) {
        stopAndReleaseAudioPlayer();

        allRecordingsDialog = new Dialog(this);
        allRecordingsDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        allRecordingsDialog.setContentView(R.layout.dialog_user_recordings_player);
        if (allRecordingsDialog.getWindow() != null) {
            allRecordingsDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.94);
            allRecordingsDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvCounter = allRecordingsDialog.findViewById(R.id.tv_player_counter);
        TextView tvBadge = allRecordingsDialog.findViewById(R.id.tv_player_surah_badge);
        TextView tvArabic = allRecordingsDialog.findViewById(R.id.tv_player_arabic);
        TextView tvStatus = allRecordingsDialog.findViewById(R.id.tv_player_status);
        ImageView ivToggle = allRecordingsDialog.findViewById(R.id.iv_player_toggle_icon);
        View btnPrev = allRecordingsDialog.findViewById(R.id.btn_player_prev);
        View btnToggle = allRecordingsDialog.findViewById(R.id.btn_player_toggle);
        View btnNext = allRecordingsDialog.findViewById(R.id.btn_player_next);
        ImageButton btnClose = allRecordingsDialog.findViewById(R.id.btn_player_close);

        tvArabic.setTypeface(PreferenceManager.getMushafTypeface(this));

        currentAudioIndex = 0;

        btnPrev.setOnClickListener(v -> {
            if (currentAudioIndex > 0) {
                playAudioItem(currentAudioIndex - 1, tvCounter, tvBadge, tvArabic, tvStatus, ivToggle);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentAudioIndex < files.size() - 1) {
                playAudioItem(currentAudioIndex + 1, tvCounter, tvBadge, tvArabic, tvStatus, ivToggle);
            }
        });

        btnToggle.setOnClickListener(v -> {
            if (allRecordingsPlayer != null) {
                if (isAudioPlaying) {
                    allRecordingsPlayer.pause();
                    isAudioPlaying = false;
                    ivToggle.setImageResource(R.drawable.ic_play_arrow);
                    tvStatus.setText("⏸️ Rekaman dijeda");
                } else {
                    allRecordingsPlayer.start();
                    isAudioPlaying = true;
                    ivToggle.setImageResource(R.drawable.ic_pause);
                    tvStatus.setText("▶️ Sedang memutar rekaman suara Anda...");
                }
            }
        });

        btnClose.setOnClickListener(v -> allRecordingsDialog.dismiss());
        allRecordingsDialog.setOnDismissListener(dialog -> stopAndReleaseAudioPlayer());

        allRecordingsDialog.show();
        playAudioItem(0, tvCounter, tvBadge, tvArabic, tvStatus, ivToggle);
    }

    private void playAudioItem(int index, TextView tvCounter, TextView tvBadge, TextView tvArabic, TextView tvStatus, ImageView ivToggle) {
        if (userAudioFiles == null || index < 0 || index >= userAudioFiles.size()) return;
        currentAudioIndex = index;
        File audioFile = userAudioFiles.get(index);
        int[] sa = parseSurahAyah(audioFile.getName());
        int surah = sa[0];
        int ayah = sa[1];

        String surahName = QuranRepository.getSurahName(this, surah);
        tvCounter.setText((index + 1) + " / " + userAudioFiles.size());
        tvBadge.setText("Surat " + surahName + " : Ayat " + ayah);

        // Ambil teks ayat Al-Quran
        List<AyahModel> ayahs = QuranRepository.getAyahs(this, surah);
        if (ayahs != null && ayah >= 1 && ayah <= ayahs.size()) {
            tvArabic.setText(ayahs.get(ayah - 1).getArabic());
        } else {
            tvArabic.setText("");
        }

        stopAudioPlaybackOnly();

        try {
            allRecordingsPlayer = new MediaPlayer();
            allRecordingsPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            allRecordingsPlayer.setDataSource(audioFile.getAbsolutePath());
            allRecordingsPlayer.setOnPreparedListener(mp -> {
                mp.start();
                isAudioPlaying = true;
                ivToggle.setImageResource(R.drawable.ic_pause);
                tvStatus.setText("▶️ Sedang memutar (" + (currentAudioIndex + 1) + " dari " + userAudioFiles.size() + ")...");
            });

            allRecordingsPlayer.setOnCompletionListener(mp -> {
                isAudioPlaying = false;
                ivToggle.setImageResource(R.drawable.ic_play_arrow);
                // Otomatis lanjut ke rekaman ayat berikutnya
                if (currentAudioIndex + 1 < userAudioFiles.size()) {
                    tvStatus.setText("Lanjut ke ayat berikutnya...");
                    tvStatus.postDelayed(() -> {
                        if (allRecordingsDialog != null && allRecordingsDialog.isShowing()) {
                            playAudioItem(currentAudioIndex + 1, tvCounter, tvBadge, tvArabic, tvStatus, ivToggle);
                        }
                    }, 500);
                } else {
                    tvStatus.setText("✅ Seluruh rekaman (" + userAudioFiles.size() + " ayat) telah selesai diputar.");
                }
            });

            allRecordingsPlayer.setOnErrorListener((mp, what, extra) -> {
                tvStatus.setText("⚠️ Terjadi kesalahan memutar berkas ini.");
                isAudioPlaying = false;
                ivToggle.setImageResource(R.drawable.ic_play_arrow);
                return true;
            });

            allRecordingsPlayer.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            tvStatus.setText("Gagal memutar rekaman: " + e.getMessage());
        }
    }

    private void stopAudioPlaybackOnly() {
        if (allRecordingsPlayer != null) {
            try {
                if (allRecordingsPlayer.isPlaying()) {
                    allRecordingsPlayer.stop();
                }
                allRecordingsPlayer.release();
            } catch (Exception ignored) {}
            allRecordingsPlayer = null;
        }
        isAudioPlaying = false;
    }

    private void stopAndReleaseAudioPlayer() {
        stopAudioPlaybackOnly();
        if (allRecordingsDialog != null && allRecordingsDialog.isShowing()) {
            try {
                allRecordingsDialog.dismiss();
            } catch (Exception ignored) {}
            allRecordingsDialog = null;
        }
    }
}

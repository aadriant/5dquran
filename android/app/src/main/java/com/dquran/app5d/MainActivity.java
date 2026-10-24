package com.dquran.app5d;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Locale;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int TOTAL_AYAT = PreferenceManager.TOTAL_AYAT;

    private RecyclerView rvSurahs;
    private SurahAdapter adapter;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private TextView tvCount;

    // Tabs Urutan (Surat, Juz, Halaman, Turunnya Surat)
    private TextView tabOrderSurah, tabOrderJuz, tabOrderPage, tabOrderTurun;
    private String currentOrderMode = "surah";

    // 3 Lanjutkan cards
    private View cardLastRead;
    private TextView tvLastReadSurah;
    private View cardLastTulis;
    private TextView tvLastTulisSurah;
    private View cardLastHafalan;
    private TextView tvLastHafalanSurah;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupData();
        setupSearch();
        setupLastRead();
        setupFavorites();

        // ── Auto-redirect ke halaman terakhir saat pertama membuka aplikasi ──
        checkAutoRedirect(savedInstanceState);
    }

    private void checkAutoRedirect(Bundle savedInstanceState) {
        boolean noRedirect = getIntent().getBooleanExtra("no_redirect", false);
        if (noRedirect || savedInstanceState != null) return;

        String lastAct = PreferenceManager.getLastActivity(this);
        if ("tulis".equals(lastAct)) {
            int s = PreferenceManager.getLastTulisSurah(this);
            int a = PreferenceManager.getLastTulisAyah(this);
            if (s > 0) {
                Intent intent = new Intent(this, TulisActivity.class);
                intent.putExtra("surah_number", s);
                intent.putExtra("ayah_number", a);
                startActivity(intent);
                return;
            }
        } else if ("hafalan".equals(lastAct)) {
            int s = PreferenceManager.getLastHafalanSurah(this);
            int a = PreferenceManager.getLastHafalanAyah(this);
            if (s > 0) {
                Intent intent = new Intent(this, HafalanActivity.class);
                intent.putExtra("surah_number", s);
                intent.putExtra("ayah_number", a);
                startActivity(intent);
                return;
            }
        } else if ("baca".equals(lastAct)) {
            int s = PreferenceManager.getLastReadSurah(this);
            int a = PreferenceManager.getLastReadAyah(this);
            if (s > 0) {
                Intent intent = new Intent(this, SurahActivity.class);
                intent.putExtra("surah_number", s);
                intent.putExtra("target_ayah", a);
                startActivity(intent);
                return;
            }
        } else {
            // Jika lastAct belum tercatat, periksa aktivitas mana yang punya riwayat
            int sh = PreferenceManager.getLastHafalanSurah(this);
            int st = PreferenceManager.getLastTulisSurah(this);
            int sr = PreferenceManager.getLastReadSurah(this);
            if (sh > 0) {
                Intent intent = new Intent(this, HafalanActivity.class);
                intent.putExtra("surah_number", sh);
                intent.putExtra("ayah_number", PreferenceManager.getLastHafalanAyah(this));
                startActivity(intent);
            } else if (st > 0) {
                Intent intent = new Intent(this, TulisActivity.class);
                intent.putExtra("surah_number", st);
                intent.putExtra("ayah_number", PreferenceManager.getLastTulisAyah(this));
                startActivity(intent);
            } else if (sr > 0) {
                Intent intent = new Intent(this, SurahActivity.class);
                intent.putExtra("surah_number", sr);
                intent.putExtra("target_ayah", PreferenceManager.getLastReadAyah(this));
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLastReadView();
    }

    private void initViews() {
        rvSurahs       = findViewById(R.id.rv_surahs);
        etSearch       = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        tvCount        = findViewById(R.id.tv_count);

        cardLastRead       = findViewById(R.id.card_last_read);
        tvLastReadSurah    = findViewById(R.id.tv_last_read_surah);
        cardLastTulis      = findViewById(R.id.card_last_tulis);
        tvLastTulisSurah   = findViewById(R.id.tv_last_tulis_surah);
        cardLastHafalan    = findViewById(R.id.card_last_hafalan);
        tvLastHafalanSurah = findViewById(R.id.tv_last_hafalan_surah);

        // Tab Urutan
        tabOrderSurah = findViewById(R.id.tab_order_surah);
        tabOrderJuz   = findViewById(R.id.tab_order_juz);
        tabOrderPage  = findViewById(R.id.tab_order_page);
        tabOrderTurun = findViewById(R.id.tab_order_turun);

        if (tabOrderSurah != null) tabOrderSurah.setOnClickListener(v -> setOrderMode("surah"));
        if (tabOrderJuz != null)   tabOrderJuz.setOnClickListener(v -> setOrderMode("juz"));
        if (tabOrderPage != null)  tabOrderPage.setOnClickListener(v -> setOrderMode("page"));
        if (tabOrderTurun != null) tabOrderTurun.setOnClickListener(v -> setOrderMode("turun"));

        ImageButton btnProfile = findViewById(R.id.btn_profile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ProfilActivity.class);
                startActivity(intent);
            });
        }

        ImageButton btnChat = findViewById(R.id.btn_chat_feedback);
        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ChatFeedbackActivity.class);
                startActivity(intent);
            });
        }

        ImageButton btnAbout = findViewById(R.id.btn_about);
        btnAbout.setOnClickListener(v -> showAboutDialog());

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    private void setupData() {
        rvSurahs.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SurahAdapter(this, null, item -> {
            Intent intent = new Intent(MainActivity.this, SurahActivity.class);
            intent.putExtra("surah_number", item.getTargetSurah());
            intent.putExtra("target_ayah", item.getTargetAyah());
            startActivity(intent);
        });

        rvSurahs.setAdapter(adapter);

        String savedMode = PreferenceManager.getMainOrderMode(this);
        setOrderMode(savedMode != null ? savedMode : "surah");
    }

    private void setOrderMode(String mode) {
        currentOrderMode = mode;
        PreferenceManager.setMainOrderMode(this, mode);

        int activeBg = R.drawable.bg_chip_tab_active;
        int normalBg = R.drawable.bg_chip_action;
        int activeColor = getResources().getColor(R.color.gold_primary);
        int normalColor = getResources().getColor(R.color.text_muted);

        if (tabOrderSurah != null) {
            tabOrderSurah.setBackgroundResource("surah".equals(mode) ? activeBg : normalBg);
            tabOrderSurah.setTextColor("surah".equals(mode) ? activeColor : normalColor);
        }
        if (tabOrderJuz != null) {
            tabOrderJuz.setBackgroundResource("juz".equals(mode) ? activeBg : normalBg);
            tabOrderJuz.setTextColor("juz".equals(mode) ? activeColor : normalColor);
        }
        if (tabOrderPage != null) {
            tabOrderPage.setBackgroundResource("page".equals(mode) ? activeBg : normalBg);
            tabOrderPage.setTextColor("page".equals(mode) ? activeColor : normalColor);
        }
        if (tabOrderTurun != null) {
            tabOrderTurun.setBackgroundResource("turun".equals(mode) ? activeBg : normalBg);
            tabOrderTurun.setTextColor("turun".equals(mode) ? activeColor : normalColor);
        }

        List<QuranItem> items;
        if ("juz".equals(mode)) {
            items = QuranRepository.getItemsByJuz(this);
        } else if ("page".equals(mode)) {
            items = QuranRepository.getItemsByPage(this);
        } else if ("turun".equals(mode)) {
            items = QuranRepository.getItemsByRevelation(this);
        } else {
            items = QuranRepository.getItemsBySurah(this);
        }

        if (adapter != null) {
            adapter.setItems(items);
            if (etSearch != null && etSearch.getText().length() > 0) {
                adapter.filter(etSearch.getText().toString());
            }
        }
        updateCount();
    }

    private void setupLastRead() {
        updateLastReadView();

        // Lanjut Baca
        cardLastRead.setOnClickListener(v -> {
            int surah = PreferenceManager.getLastReadSurah(this);
            int ayah  = PreferenceManager.getLastReadAyah(this);
            Intent intent = new Intent(this, SurahActivity.class);
            intent.putExtra("surah_number", surah);
            intent.putExtra("target_ayah", ayah);
            startActivity(intent);
        });

        // Lanjut Tulis
        if (cardLastTulis != null) {
            cardLastTulis.setOnClickListener(v -> {
                int surah = PreferenceManager.getLastTulisSurah(this);
                if (surah == 0) { Toast.makeText(this, "Belum ada riwayat Tulis", Toast.LENGTH_SHORT).show(); return; }
                int ayah = PreferenceManager.getLastTulisAyah(this);
                Intent intent = new Intent(this, TulisActivity.class);
                intent.putExtra("surah_number", surah);
                intent.putExtra("ayah_number", ayah);
                startActivity(intent);
            });
        }

        // Lanjut Hafalan
        if (cardLastHafalan != null) {
            cardLastHafalan.setOnClickListener(v -> {
                int surah = PreferenceManager.getLastHafalanSurah(this);
                if (surah == 0) { Toast.makeText(this, "Belum ada riwayat Hafalan", Toast.LENGTH_SHORT).show(); return; }
                int ayah = PreferenceManager.getLastHafalanAyah(this);
                Intent intent = new Intent(this, HafalanActivity.class);
                intent.putExtra("surah_number", surah);
                intent.putExtra("ayah_number", ayah);
                startActivity(intent);
            });
        }
    }

    private void updateLastReadView() {
        // Baca
        int surahRead = PreferenceManager.getLastReadSurah(this);
        String nameRead = PreferenceManager.getLastReadSurahName(this);
        int ayahRead = PreferenceManager.getLastReadAyah(this);
        if (tvLastReadSurah != null)
            tvLastReadSurah.setText(nameRead + " · Ayat " + ayahRead);

        // Tulis
        int surahTulis = PreferenceManager.getLastTulisSurah(this);
        if (tvLastTulisSurah != null) {
            if (surahTulis == 0) {
                tvLastTulisSurah.setText("Belum ada riwayat");
            } else {
                tvLastTulisSurah.setText(PreferenceManager.getLastTulisSurahName(this)
                        + " · Ayat " + PreferenceManager.getLastTulisAyah(this));
            }
        }

        // Hafalan
        int surahHafalan = PreferenceManager.getLastHafalanSurah(this);
        if (tvLastHafalanSurah != null) {
            if (surahHafalan == 0) {
                tvLastHafalanSurah.setText("Belum ada riwayat");
            } else {
                tvLastHafalanSurah.setText(PreferenceManager.getLastHafalanSurahName(this)
                        + " · Ayat " + PreferenceManager.getLastHafalanAyah(this));
            }
        }
    }

    private void setupFavorites() {
        // 1. Al-Mulk (67)
        findViewById(R.id.fav_al_mulk).setOnClickListener(v -> openSurahDirectly(67));
        // 2. Al-Kahfi (18)
        findViewById(R.id.fav_al_kahf).setOnClickListener(v -> openSurahDirectly(18));
        // 3. Al-Insyiqaq (84)
        findViewById(R.id.fav_al_insyiqaq).setOnClickListener(v -> openSurahDirectly(84));
        // 4. Yasin (36)
        findViewById(R.id.fav_yasin).setOnClickListener(v -> openSurahDirectly(36));
        // 5. Ar-Rahman (55)
        findViewById(R.id.fav_ar_rahman).setOnClickListener(v -> openSurahDirectly(55));
    }

    private void openSurahDirectly(int surahNumber) {
        Intent intent = new Intent(this, SurahActivity.class);
        intent.putExtra("surah_number", surahNumber);
        startActivity(intent);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (adapter != null) {
                    adapter.filter(s.toString());
                }
                updateCount();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));
    }

    private void updateCount() {
        if (adapter == null || tvCount == null) return;
        String query = etSearch != null ? etSearch.getText().toString().trim() : "";
        if (!query.isEmpty()) {
            tvCount.setText(adapter.getDisplayCount() + " hasil ditemukan");
            return;
        }

        if ("juz".equals(currentOrderMode)) {
            tvCount.setText("30 Juz");
        } else if ("page".equals(currentOrderMode)) {
            tvCount.setText("604 Halaman Mushaf");
        } else if ("turun".equals(currentOrderMode)) {
            tvCount.setText("114 Surah (Urutan Pewahyuan / Nuzulul Quran)");
        } else {
            tvCount.setText("114 Surah");
        }
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

        // Qari selection
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

        // Font size selection
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

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }

            Toast.makeText(this, "✅ Pengaturan berhasil disimpan", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showAboutDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width  = (int) (getResources().getDisplayMetrics().widthPixels * 0.94);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
            dialog.getWindow().setLayout(width, height);
        }
        View btnClose = dialog.findViewById(R.id.btn_close_about);
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}

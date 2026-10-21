/* ============================================================
   5D Quran — tulis.js (Writing Practice Logic)
   - Per-ayat writing with optional reference display
   - Auto-save to localStorage
   - Navigation between ayats
   - Download all writings
   ============================================================ */

// Tandai timestamp saat user balik ke beranda (agar app.js skip auto-redirect)
document.addEventListener('click', e => {
  const a = e.target.closest('a[href]');
  if (a && (a.href.endsWith('index.html') || a.getAttribute('href') === 'index.html')) {
    try { localStorage.setItem('5dq_ts_balik', String(Date.now())); } catch(_) {}
  }
});

const API_BASE = 'https://api.alquran.cloud/v1';
const EDITIONS = 'quran-uthmani,id.indonesian';
const STORAGE_PREFIX = '5dquran_tulis';

let surahId   = 1;
let ayatNum   = 1;
let surahData = null;      // full Arabic edition
let indoData  = null;      // Indonesian edition
let totalAyat = 0;
let saveTimer = null;
let isDirty   = false;

// ── DOM REFS ──
const tiSurahInfo    = document.getElementById('ti-surah-info');
const tiProgress     = document.getElementById('ti-progress');
const tiAyatNum      = document.getElementById('ti-ayat-num');
const tiAyatTotal    = document.getElementById('ti-ayat-total');
const tulisLoading   = document.getElementById('tulis-loading');
const tulisPanel     = document.getElementById('tulis-panel');
const refBadge       = document.getElementById('ref-badge');
const refArabic      = document.getElementById('ref-arabic');
const refTrans       = document.getElementById('ref-trans');
const refArabicWrap  = document.getElementById('ref-arabic-wrap');
const toggleRef      = document.getElementById('toggle-ref');
const writingArea    = document.getElementById('writing-area');
const saveStatus     = document.getElementById('save-status');
const btnSave        = document.getElementById('btn-save');
const btnClear       = document.getElementById('btn-clear');
const btnPrev        = document.getElementById('btn-tulis-prev');
const btnNext        = document.getElementById('btn-tulis-next');
const ayatPicker     = document.getElementById('ayat-picker');
const savedHistory   = document.getElementById('saved-history');
const shHistContent  = document.getElementById('sh-history-content');
const toastEl        = document.getElementById('toast');
const btnBackSurah   = document.getElementById('btn-back-surah');
const btnDownloadAll = document.getElementById('btn-download-all');

// ══════════════════════════════════════════
// KEYBOARD ARAB — deteksi & panduan pasang
// ══════════════════════════════════════════
/* Deteksi apakah pengguna bisa mengetik huruf Arab.
   Trik tanpa API khusus: cek 'Arabic' ada di daftar bahasa keyboard
   (Keyboard API, Chrome) atau deteksi input pertama. */
// ══════════════════════════════════════════
// MODE CONTOH (setting profil: ya / tidak)
// ══════════════════════════════════════════
function modeContohAyat() {
  try {
    if (typeof Auth !== 'undefined' && Auth.setting) {
      return Auth.setting().contoh || 'ya';
    }
  } catch (e) {}
  return 'ya';
}

function keyboardArabTersedia() {
  if (navigator.keyboard && navigator.keyboard.getLayoutMap) return true; // ada API keyboard
  return /Android|iPhone|iPad|Mac|Windows|Linux/i.test(navigator.userAgent); // perangkat umum bisa pasang
}
function tampilkanPanduanKeyboard() {
  const ua = navigator.userAgent;
  let langkah;
  if (/Android/i.test(ua)) {
    langkah = ['Buka <b>Setelan</b> Android', 'Pilih <b>Sistem → Bahasa &amp; masukan</b>',
               'Ketuk <b>Keyboard di layar → Gboard → Bahasa</b>', 'Tambah <b>العربية (Arab)</b>',
               'Saat mengetik, tekan ikon 🌐 untuk berpindah ke keyboard Arab'];
  } else if (/iPhone|iPad/i.test(ua)) {
    langkah = ['Buka <b>Setelan</b> iOS', 'Pilih <b>Umum → Keyboard → Keyboard</b>',
               'Ketuk <b>Tambahkan Keyboard Baru</b>', 'Pilih <b>Arab</b>',
               'Saat mengetik, tahan ikon 🌐 untuk berpindah keyboard'];
  } else if (/Mac/i.test(ua)) {
    langkah = ['Buka <b>System Settings → Keyboard → Input Sources</b>',
               'Klik <b>+</b> lalu pilih <b>Arabic</b>', 'Tambahkan, lalu gunakan <b>Ctrl+Spasi</b> untuk berpindah'];
  } else {
    langkah = ['Buka <b>Pengaturan → Waktu &amp; Bahasa → Bahasa</b> (Windows)',
               'Tambah bahasa <b>العربية</b>', 'Aktifkan layout keyboard Arab',
               'Berpindah dengan <b>Win + Spasi</b>'];
  }
  const html = `
    <div class="about-modal" role="dialog" aria-modal="true" aria-label="Cara pasang keyboard Arab" style="max-width:440px">
      <button class="about-close" id="kb-close" aria-label="Tutup">&times;</button>
      <div class="about-header">
        <h2 class="about-title">⌨️ Pasang Keyboard Arab</h2>
        <p class="about-tagline">Ikuti langkah berikut di perangkat Anda</p>
      </div>
      <ol class="kb-steps">${langkah.map(l => '<li>' + l + '</li>').join('')}</ol>
      <p class="kb-tip">Setelah aktif, kembali ke halaman ini dan ketik ayat dengan keyboard Arab.</p>
    </div>`;
  const ov = document.createElement('div');
  ov.id = 'kb-modal-overlay';
  ov.style.cssText = 'position:fixed;inset:0;z-index:9999;background:rgba(4,4,10,.82);backdrop-filter:blur(6px);display:flex;align-items:center;justify-content:center;padding:1rem';
  ov.innerHTML = html;
  document.body.appendChild(ov);
  const tutup = () => ov.remove();
  document.getElementById('kb-close').addEventListener('click', tutup);
  ov.addEventListener('click', e => { if (e.target === ov) tutup(); });
}

// ══════════════════════════════════════════
// INIT
// ══════════════════════════════════════════
document.addEventListener('DOMContentLoaded', async () => {
  const params = new URLSearchParams(window.location.search);
  surahId  = parseInt(params.get('surah')) || 1;
  ayatNum  = parseInt(params.get('ayat'))  || 1;
  if (surahId < 1)   surahId = 1;
  if (surahId > 114) surahId = 114;

  // Halaman tulis wajib login: progres "tulisan" dan riwayat "Lanjut Tulis"
  // disimpan per profil di server. gerbang() sekaligus memuat sesi (Auth.init);
  // tanpa ini Auth.me kosong dan Auth.simpanMark() tidak menyimpan apa pun.
  if (typeof gerbang === 'function' && !(await gerbang('tulis.html'))) return;

  btnBackSurah.href = `surah.html?id=${surahId}`;
  btnBackSurah.addEventListener('click', (e) => {
    sessionStorage.setItem('5dquran_mode', 'baca');
  });

  loadSurahData();
  initControls();
});

function stripBismillahPrefix(text) {
  if (!text) return '';
  const trimmed = text.replace(/\ufeff/g, '').trim();
  const words = trimmed.split(/\s+/);
  if (words.length >= 5 && words[0].includes('ب') && words[1].includes('ل') && words[2].includes('ح') && words[3].includes('ح')) {
    return words.slice(4).join(' ').trim();
  }
  return trimmed;
}

// ══════════════════════════════════════════
// LOAD DATA
// ══════════════════════════════════════════
async function loadSurahData() {
  try {
    const res  = await fetch(`${API_BASE}/surah/${surahId}/editions/${EDITIONS}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    surahData  = json.data[0]; // Arabic
    indoData   = json.data[1]; // Indonesian
    totalAyat  = surahData.ayahs.length;

    // Strip Bismillah from Ayah 1 for Surahs 2..8 and 10..114
    if (surahId !== 1 && surahId !== 9 && surahData && surahData.ayahs && surahData.ayahs.length > 0) {
      surahData.ayahs[0].text = stripBismillahPrefix(surahData.ayahs[0].text);
    }

    if (ayatNum < 1)          ayatNum = 1;
    if (ayatNum > totalAyat)  ayatNum = totalAyat;

    renderSurahInfo();
    buildAyatPicker();
    renderAyat(ayatNum);
    showPanel();
  } catch (err) {
    console.error('Failed to load surah:', err);
    tulisLoading.innerHTML = `<div class="error-icon">⚠️</div><p>Gagal memuat data. Periksa koneksi internet.</p>
      <button class="btn-retry" onclick="loadSurahData()">Coba Lagi</button>`;
  }
}

function renderSurahInfo() {
  document.title = `Tulis — ${surahData.englishName} — 5D Quran`;
  const revType = surahData.revelationType === 'Meccan' ? 'Makkiyah' : 'Madaniyah';

  tiSurahInfo.innerHTML = `
    <span class="ti-number">${surahData.number}</span>
    <span class="ti-arabic">${surahData.name}</span>
    <span class="ti-name">${surahData.englishName}</span>
    <span class="ti-badge">${revType}</span>
  `;

  tiAyatTotal.textContent = totalAyat;
  tiProgress.style.display = 'flex';

  const bcSurah = document.getElementById('bc-surah');
  if (bcSurah) {
    bcSurah.textContent = `Surat ${surahData.number}. ${surahData.englishName}`;
    bcSurah.href = `surah.html?id=${surahId}`;
    bcSurah.addEventListener('click', () => {
      sessionStorage.setItem('5dquran_mode', 'baca');
    });
  }
}

function buildAyatPicker() {
  ayatPicker.innerHTML = '';
  for (let i = 1; i <= totalAyat; i++) {
    const opt = document.createElement('option');
    opt.value = i;
    opt.textContent = `Ayat ${i}`;
    opt.style.backgroundColor = '#16162a';
    opt.style.color = '#f1f5f9';
    if (i === ayatNum) opt.selected = true;
    ayatPicker.appendChild(opt);
  }
}

// ══════════════════════════════════════════
// RENDER AYAT
// ══════════════════════════════════════════
function renderAyat(num) {
  if (!surahData) return;
  ayatNum = num;

  const arabicAyah = surahData.ayahs[ayatNum - 1];
  const indoAyah   = indoData  ? indoData.ayahs[ayatNum - 1] : null;

  // Update reference
  refBadge.textContent    = `Ayat ${arabicAyah.numberInSurah}`;
  refArabic.textContent   = arabicAyah.text;
  refTrans.textContent    = indoAyah ? indoAyah.text : '';

  // Update progress
  tiAyatNum.textContent   = ayatNum;
  ayatPicker.value        = ayatNum;

  const bcCurrent = document.getElementById('bc-current');
  if (bcCurrent) {
    bcCurrent.textContent = `✍️ Latihan Menulis Ayat ${ayatNum}`;
  }

  // ── Mode "Tanpa Contoh": sembunyikan ayat contoh ──
  const modeContoh = modeContohAyat();
  const wrapRef    = document.getElementById('ref-arabic-wrap');
  const noteTanpa  = document.getElementById('tanpa-contoh-note');
  if (modeContoh === 'tidak') {
    if (wrapRef)   wrapRef.classList.add('hidden');
    if (noteTanpa) noteTanpa.style.display = 'block';
  } else {
    if (wrapRef)   wrapRef.classList.remove('hidden');
    if (noteTanpa) noteTanpa.style.display = 'none';
  }

  // Update nav buttons
  btnPrev.disabled = ayatNum <= 1;
  btnNext.disabled = ayatNum >= totalAyat;
  btnNext.textContent = ayatNum < totalAyat ? 'Ayat Selanjutnya' : 'Selesai ✓';

  // Load saved writing — server dulu (sinkron antar perangkat), lalu lokal
  const savedLokal = getSaved(surahId, ayatNum);
  writingArea.value = savedLokal ? savedLokal.text : '';
  isDirty = false;
  updateSaveStatus(savedLokal ? `Tersimpan: ${formatDate(savedLokal.savedAt)}` : '', savedLokal ? 'saved' : '');
  ambilTulisanServer(surahId, ayatNum).then(rec => {
    if (!rec) return;
    // Hanya timpa bila bidang masih kosong / belum diubah pengguna
    if (isDirty || writingArea.value.trim()) return;
    writingArea.value = rec.text;
    updateSaveStatus(`Tersimpan di server: ${formatDate(rec.savedAt)}`, 'saved');
  });

  // Load history
  renderHistory();

  // Sembunyikan hasil periksa saat pindah ayat
  const cp = document.getElementById('check-panel');
  if (cp) cp.style.display = 'none';

  // Update URL without reload
  const newUrl = `tulis.html?surah=${surahId}&ayat=${ayatNum}`;
  history.replaceState(null, '', newUrl);

  // Simpan posisi terakhir latihan tulis (untuk kartu "Lanjut Tulis")
  if (typeof Auth !== 'undefined' && Auth.simpanMark) {
    Auth.simpanMark('terakhir_tulis', { surah: surahId, ayat: ayatNum, nama: surahData.englishName || '' });
  }
  // localStorage fallback (pengguna belum login)
  try {
    const all = JSON.parse(localStorage.getItem('5dquran_terakhir') || '{}');
    all['terakhir_tulis'] = { surah: surahId, ayat: ayatNum, nama: surahData.englishName || '', ts: Date.now() };
    localStorage.setItem('5dquran_terakhir', JSON.stringify(all));
    localStorage.setItem('5dq_terakhir_halaman', JSON.stringify({
      jenis: 'tulis', surah: surahId, ayat: ayatNum, nama: surahData.englishName || '',
      url: `tulis.html?surah=${surahId}&ayat=${ayatNum}`, ts: Date.now()
    }));
  } catch (e) {}
}

// ══════════════════════════════════════════
// SAVE / LOAD
// ══════════════════════════════════════════
function saveCurrentAyat() {
  const text = writingArea.value.trim();
  if (!text) {
    showToast('Belum ada tulisan untuk disimpan');
    return;
  }
  const arabicAyah = surahData.ayahs[ayatNum - 1];
  const record = {
    surahId,
    surahName:   surahData.englishName,
    surahArabic: surahData.name,
    ayatNum,
    arabic:      arabicAyah.text,
    text,
    savedAt:     new Date().toISOString(),
  };

  // Save current version
  const key = buildKey(surahId, ayatNum);
  localStorage.setItem(key, JSON.stringify(record));

  // Also append to history list
  const histKey = buildHistKey(surahId, ayatNum);
  let history = [];
  try { history = JSON.parse(localStorage.getItem(histKey) || '[]'); } catch {}
  history.unshift(record);
  if (history.length > 5) history = history.slice(0, 5); // keep last 5
  localStorage.setItem(histKey, JSON.stringify(history));

  isDirty = false;
  updateSaveStatus(`✓ Tersimpan: ${formatDate(record.savedAt)}`, 'saved');
  renderHistory();

  // Simpan ke server supaya tulisan sama di semua perangkat
  simpanTulisanServer(record)
    .then(ok => showToast(ok ? '✓ Ayat berhasil disimpan!' : '⚠ Tersimpan lokal; gagal ke server'))
    .catch(() => showToast('⚠ Tersimpan lokal; gagal ke server'));
}

/* Kirim teks tulisan ke database (per profil), bukan hanya localStorage. */
async function simpanTulisanServer(record) {
  if (typeof Auth === 'undefined' || !Auth.sudahLogin()) return false;
  try {
    const r = await fetch('api.php?a=tulisan', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        surah: record.surahId, ayat: record.ayatNum,
        teks: record.text, nama: record.surahName || '',
        diperiksa: !!record.diperiksa,
      }),
    });
    const d = await r.json();
    return !!(d && d.ok);
  } catch (e) { return false; }
}

/* Ambil tulisan dari server; null bila belum ada. */
async function ambilTulisanServer(sid, anum) {
  if (typeof Auth === 'undefined' || !Auth.sudahLogin()) return null;
  try {
    const r = await fetch(`api.php?a=tulisan&surah=${sid}&ayat=${anum}`, { credentials: 'same-origin' });
    const d = await r.json();
    if (d && d.ok && d.data && d.data.teks) {
      return { text: d.data.teks, savedAt: d.data.updated, dariServer: true };
    }
  } catch (e) {}
  return null;
}

/* Daftar semua tulisan tersimpan (untuk unduh) — dari server. */
async function daftarTulisanServer() {
  if (typeof Auth === 'undefined' || !Auth.sudahLogin()) return [];
  try {
    const r = await fetch('api.php?a=tulisan', { credentials: 'same-origin' });
    const d = await r.json();
    return (d && d.ok && Array.isArray(d.data)) ? d.data : [];
  } catch (e) { return []; }
}

function getSaved(sid, anum) {
  try {
    const data = localStorage.getItem(buildKey(sid, anum));
    return data ? JSON.parse(data) : null;
  } catch { return null; }
}

function buildKey(sid, anum)     { return `${STORAGE_PREFIX}_${sid}_${anum}`; }
function buildHistKey(sid, anum) { return `${STORAGE_PREFIX}_hist_${sid}_${anum}`; }

// ══════════════════════════════════════════
// HISTORY
// ══════════════════════════════════════════
function renderHistory() {
  let history = [];
  try { history = JSON.parse(localStorage.getItem(buildHistKey(surahId, ayatNum)) || '[]'); } catch {}

  if (!history.length) {
    savedHistory.style.display = 'none';
    return;
  }

  savedHistory.style.display = 'block';
  shHistContent.innerHTML = '';
  history.forEach(entry => {
    const div = document.createElement('div');
    div.className = 'history-entry';
    div.innerHTML = `
      <div class="history-text">${escapeHtml(entry.text)}</div>
      <div class="history-meta">💾 ${formatDate(entry.savedAt)}</div>
    `;
    shHistContent.appendChild(div);
  });
}

// ══════════════════════════════════════════
// DOWNLOAD ALL
// ══════════════════════════════════════════
async function downloadAllWritings() {
  const daftar = await daftarTulisanServer();

  if (!daftar.length) {
    showToast('Belum ada tulisan tersimpan');
    return;
  }

  let content = '5D QURAN — KUMPULAN TULISAN\n';
  content += '═'.repeat(50) + '\n\n';

  daftar.forEach(e => {
    const nama = e.nama || ('Surat ' + e.surah);
    content += `📖 ${nama} — Ayat ${e.ayat}\n`;
    content += `Tulisan: ${e.teks}\n`;
    content += `Waktu  : ${formatDate(e.updated)}\n`;
    content += '─'.repeat(40) + '\n\n';
  });

  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement('a');
  a.href     = url;
  a.download = `5DQuran-Tulisan-${new Date().toISOString().slice(0,10)}.txt`;
  a.click();
  URL.revokeObjectURL(url);
  showToast('✓ File berhasil diunduh!');
}

// ══════════════════════════════════════════
// CONTROLS
// ══════════════════════════════════════════
function initControls() {
  // ── Keyboard Arab: tombol panduan ──
  const btnKb = document.getElementById('btn-pasang-kb');
  if (btnKb) btnKb.addEventListener('click', tampilkanPanduanKeyboard);
  const btnUncover = document.getElementById('btn-uncover');
  if (btnUncover) btnUncover.addEventListener('click', () => {
    refArabicWrap.classList.remove('hidden');
    document.getElementById('tanpa-contoh-note').style.display = 'none';
  });

  // Toggle reference
  toggleRef.addEventListener('change', () => {
    refArabicWrap.classList.toggle('hidden', !toggleRef.checked);
  });

  // Auto-save on typing (debounced) + peringatan keyboard non-Arab
  writingArea.addEventListener('input', () => {
    isDirty = true;
    updateSaveStatus('Belum disimpan...', 'unsaved');
    // Peringatkan bila mengetik huruf Latin (keyboard Arab belum aktif)
    const val = writingArea.value.trim();
    const adaArab = /[\u0621-\u064A]/.test(val);
    const adaLatin = /[A-Za-z]/.test(val);
    const warn = document.getElementById('keyboard-warn');
    if (warn) warn.style.display = (adaLatin && !adaArab) ? 'flex' : 'none';
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      if (isDirty && writingArea.value.trim()) saveCurrentAyat();
    }, 3000); // auto-save after 3s idle
  });

  // Manual save
  btnSave.addEventListener('click', () => {
    clearTimeout(saveTimer);
    saveCurrentAyat();
  });

  // Periksa tulisan (kriteria 3)
  const btnCheck = document.getElementById('btn-check');
  if (btnCheck) btnCheck.addEventListener('click', periksaTulisan);

  // Clear
  btnClear.addEventListener('click', () => {
    if (!writingArea.value.trim()) return;
    if (confirm('Hapus tulisan ini?')) {
      writingArea.value = '';
      isDirty = false;
      updateSaveStatus('Tulisan dihapus', '');
    }
  });

  // Prev ayat
  btnPrev.addEventListener('click', () => {
    if (ayatNum > 1) {
      autoSaveIfDirty();
      renderAyat(ayatNum - 1);
    }
  });

  // Next ayat
  btnNext.addEventListener('click', () => {
    if (ayatNum < totalAyat) {
      autoSaveIfDirty();
      renderAyat(ayatNum + 1);
    } else {
      // All done
      showToast('🎉 Selesai! Semua ayat telah ditulis');
      window.location.href = `surah.html?id=${surahId}`;
    }
  });

  // Picker
  ayatPicker.addEventListener('change', () => {
    autoSaveIfDirty();
    renderAyat(parseInt(ayatPicker.value));
  });

  // Keyboard shortcuts
  document.addEventListener('keydown', e => {
    if (e.ctrlKey && e.key === 's') {
      e.preventDefault();
      clearTimeout(saveTimer);
      saveCurrentAyat();
    }
    if (e.altKey && e.key === 'ArrowRight' && ayatNum < totalAyat) {
      autoSaveIfDirty(); renderAyat(ayatNum + 1);
    }
    if (e.altKey && e.key === 'ArrowLeft' && ayatNum > 1) {
      autoSaveIfDirty(); renderAyat(ayatNum - 1);
    }
  });

  // Download
  btnDownloadAll.addEventListener('click', downloadAllWritings);
}

function autoSaveIfDirty() {
  if (isDirty && writingArea.value.trim()) {
    clearTimeout(saveTimer);
    saveCurrentAyat();
  }
}

// ══════════════════════════════════════════
// UI HELPERS
// ══════════════════════════════════════════
function showPanel() {
  tulisLoading.style.display = 'none';
  tulisPanel.style.display   = 'flex';
}

function updateSaveStatus(msg, cls) {
  saveStatus.textContent  = msg;
  saveStatus.className    = `save-status ${cls}`;
}

function showToast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.add('show');
  setTimeout(() => toastEl.classList.remove('show'), 2200);
}

function formatDate(isoString) {
  if (!isoString) return '';
  const d = new Date(isoString);
  return d.toLocaleString('id-ID', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
}

function escapeHtml(str) {
  return str.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

// ══════════════════════════════════════════
// PERIKSA TULISAN  (kriteria 3)
// Normalisasi Arab + alignment berbobot, sama seperti halaman hafalan,
// supaya penilaian konsisten. Tulisan dihitung 1 bila 0 salah & 0 terlewat.
// ══════════════════════════════════════════
function normAr(s) {
  return (s || '')
    /* PENTING — urutan: peta karakter di luar rentang \u0621-\u064A harus
       dijalankan SEBELUM filter, karena filter akan membuangnya.
       alef wasla \u0671 ('ٱ') -→ 'ا' : supaya 'ٱللَّهُ' == 'الله' */
    .replace(/\u0671/g, '\u0627')
    /* superscript alef \u0670: DIBUANG, bukan jadi 'ا'.
       'ٱلرَّحۡمَٰنُ' → 'الرحمان' salah; dibuang → 'الرحمن' benar. */
    .replace(/\u0670/g, '')
    .replace(/[^\u0621-\u064A\s]/g, '')     /* buang harakat \u064B-\u065F serta tanda lain */
    .replace(/\u0640/g, '')                    /* tatweel */
    .replace(/[\u0622\u0623\u0625]/g, '\u0627')
    .replace(/\u0649/g, '\u064A')
    .replace(/\u0629/g, '\u0647')
    .replace(/\u0624/g, '\u0648')
    .replace(/\u0626/g, '\u064A')
    .replace(/\s+/g, ' ').trim();
}
function alignAr(ref, spk) {
  const R = normAr(ref).split(' ').filter(Boolean);
  const S = normAr(spk).split(' ').filter(Boolean);
  const m = R.length, n = S.length;
  const d = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = 1; i <= m; i++) d[i][0] = i;
  for (let j = 1; j <= n; j++) d[0][j] = j;
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      const sama = R[i - 1] === S[j - 1];
      const mirip = !sama && simMirip(R[i - 1], S[j - 1]) >= 0.6;
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + (sama ? 0 : mirip ? 0.4 : 1));
    }
  }
  // Telusuri kembali untuk klasifikasi kata
  let i = m, j = n, correct = 0, near = 0, wrong = 0, missing = 0;
  const res = [];
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0) {
      const sama = R[i - 1] === S[j - 1];
      const mirip = !sama && simMirip(R[i - 1], S[j - 1]) >= 0.6;
      const biaya = sama ? 0 : mirip ? 0.4 : 1;
      if (d[i][j] === d[i - 1][j - 1] + biaya) {
        if (sama) { correct++; res.unshift({ st: 'correct', ref: R[i - 1], spk: S[j - 1] }); }
        else if (mirip) { near++; res.unshift({ st: 'near', ref: R[i - 1], spk: S[j - 1] }); }
        else { wrong++; res.unshift({ st: 'wrong', ref: R[i - 1], spk: S[j - 1] }); }
        i--; j--; continue;
      }
    }
    if (i > 0 && d[i][j] === d[i - 1][j] + 1) { missing++; res.unshift({ st: 'missing', ref: R[i - 1], spk: '' }); i--; }
    else { j--; }
  }
  const total = m;
  const skor = correct + near * 0.75 - wrong * 0.25;
  return { res, correct, near, wrong, missing, total, akurasi: total ? Math.max(0, Math.round(skor / total * 100)) : 0 };
}
// Kemiripan sederhana (rasio huruf sama berurutan) untuk kata Arab pendek.
function simMirip(a, b) {
  if (a === b) return 1;
  if (!a || !b) return 0;
  let sama = 0;
  const n = Math.min(a.length, b.length);
  for (let k = 0; k < n; k++) if (a[k] === b[k]) sama++;
  return sama / Math.max(a.length, b.length);
}

function periksaTulisan() {
  const tulis = writingArea.value.trim();
  if (!tulis) { showToast('Tulis dulu ayatnya, baru diperiksa'); return; }
  const arabicAyah = surahData.ayahs[ayatNum - 1];
  const { res, correct, near, wrong, missing, total, akurasi } = alignAr(arabicAyah.text, tulis);
  const sempurna = (wrong === 0 && missing === 0);

  document.getElementById('tc-score').textContent = akurasi + '%';
  document.getElementById('tc-score').style.color = sempurna ? '#4ade80' : (akurasi >= 70 ? '#fbbf24' : '#f87171');
  document.getElementById('tc-title').textContent = sempurna ? '✓ Tulisan Sempurna' : 'Hasil Periksa';
  document.getElementById('tc-stats').innerHTML =
    '<span class="tc-b">Benar <b>' + correct + '</b></span>' +
    '<span class="tc-n">Mirip <b>' + near + '</b></span>' +
    '<span class="tc-w">Salah <b>' + wrong + '</b></span>' +
    '<span class="tc-m">Terlewat <b>' + missing + '</b></span>';
  document.getElementById('tc-diff').innerHTML = res.map(e => {
    const c = e.st === 'correct' ? 'w-correct' : e.st === 'near' ? 'w-near' : e.st === 'wrong' ? 'w-wrong' : 'w-missing';
    return '<span class="' + c + '">' + escapeHtml(e.ref) + '</span>';
  }).join(' ');
  document.getElementById('check-panel').style.display = 'block';

  // Kriteria 3: tulisan dihitung 1 hanya bila 0 salah & 0 terlewat.
  if (sempurna && typeof Auth !== 'undefined' && Auth.simpanMark) {
    Auth.simpanMark('tulisan', { surah: surahId, ayat: ayatNum, nama: surahData.englishName || '' });
    // Simpan juga hasil ketikannya supaya terbuka sama di perangkat lain
    simpanTulisanServer({ surahId, ayatNum, text: tulis, surahName: surahData.englishName || '', diperiksa: true });
  }
}

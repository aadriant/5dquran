/* ============================================================
   5D Quran — app.js (Homepage Logic)
   Fetches surah list from AlQuran.cloud API
   ============================================================ */

const API_BASE = 'https://api.alquran.cloud/v1';
let allSurahs = [];
let searchQuery = '';

// ── DATA SURAT (nama Arab, latin, arti bahasa Indonesia) ──
let DATA_SURAT = {};   // { "1": {arab, latin, arti, ayat, tempat}, ... }
function infoSurat(nomor) {
  return DATA_SURAT[String(nomor)] || {};
}
async function muatDataSurat() {
  try {
    const r = await fetch('data/surat-id.json');
    if (r.ok) DATA_SURAT = await r.json();
  } catch (e) { DATA_SURAT = {}; }
}

// ── DOM REFS ──
const surahGrid    = document.getElementById('surah-grid');
const loadingState = document.getElementById('loading-state');
const errorState   = document.getElementById('error-state');
const noResult     = document.getElementById('no-result');
const searchInput  = document.getElementById('search-input');
const clearBtn     = document.getElementById('clear-search');
const statShown    = document.getElementById('stat-shown');
const scrollTopBtn = document.getElementById('scroll-top');

// ── TERAKHIR DIBUKA (localStorage fallback untuk pengguna belum login) ──
const LS_KEY = '5dquran_terakhir';
function simpanTerakhirLokal(jenis, data) {
  try {
    const all = JSON.parse(localStorage.getItem(LS_KEY) || '{}');
    all[jenis] = data;
    localStorage.setItem(LS_KEY, JSON.stringify(all));
  } catch (e) {}
}
function ambilTerakhirLokal(jenis) {
  try {
    const all = JSON.parse(localStorage.getItem(LS_KEY) || '{}');
    return all[jenis] || null;
  } catch (e) { return null; }
}
// Expose agar bisa dipanggil dari halaman lain
window.simpanTerakhirLokal = simpanTerakhirLokal;
window.ambilTerakhirLokal  = ambilTerakhirLokal;

// ── INIT ──
document.addEventListener('DOMContentLoaded', async () => {
  // Muat profil dulu agar setting & 'terakhir dibaca' tersedia
  if (typeof Auth !== 'undefined') await Auth.init();
  await muatDataSurat();          // butuh nama surat Indonesia sebelum render

  // ── Buka langsung halaman terakhir saat membuka aplikasi ──
  const isBackToHome = window.location.search.includes('home=1');
  const tsNavigasiKembali = parseInt(localStorage.getItem('5dq_ts_balik') || '0', 10);
  const baruKembaliDariHalaman = (Date.now() - tsNavigasiKembali) < 4000;
  if (!isBackToHome && !baruKembaliDariHalaman) {
    try {
      const terakhirHal = JSON.parse(localStorage.getItem('5dq_terakhir_halaman') || 'null');
      if (terakhirHal && terakhirHal.url && terakhirHal.surah) {
        window.location.replace(terakhirHal.url);
        return;
      }
    } catch (e) {}

    const terakhirBaca    = (typeof Auth !== 'undefined' && Auth.me && Auth.me.terakhir && Auth.me.terakhir[0])
                            ? Auth.me.terakhir[0]
                            : ambilTerakhirLokal('terakhir');
    const terakhirTulis   = (typeof Auth !== 'undefined' && Auth.me && Auth.me.terakhir_tulis && Auth.me.terakhir_tulis[0])
                            ? Auth.me.terakhir_tulis[0]
                            : ambilTerakhirLokal('terakhir_tulis');
    const terakhirHafalan = (typeof Auth !== 'undefined' && Auth.me && Auth.me.terakhir_hafalan && Auth.me.terakhir_hafalan[0])
                            ? Auth.me.terakhir_hafalan[0]
                            : ambilTerakhirLokal('terakhir_hafalan');
    const pilihanTerakhir = [
      terakhirBaca    ? { data: terakhirBaca,    url: `surah.html?id=${terakhirBaca.surah}#ayat-${terakhirBaca.ayat}` }    : null,
      terakhirTulis   ? { data: terakhirTulis,   url: `tulis.html?surah=${terakhirTulis.surah}&ayat=${terakhirTulis.ayat}` }  : null,
      terakhirHafalan ? { data: terakhirHafalan, url: `hafalan.html?surah=${terakhirHafalan.surah}&ayat=${terakhirHafalan.ayat}` } : null,
    ].filter(Boolean);
    if (pilihanTerakhir.length > 0) {
      const terpilih = pilihanTerakhir.reduce((terbaru, item) => {
        const tsBaru  = terbaru.data.ts || terbaru.data.savedAt || 0;
        const tsItem  = item.data.ts    || item.data.savedAt    || 0;
        return tsItem > tsBaru ? item : terbaru;
      });
      if (terpilih && terpilih.data && terpilih.data.surah) {
        window.location.replace(terpilih.url);
        return;
      }
    }
  }

  loadSurahs();
  initSearch();
  initScrollTop();
  initHomeSections();
  initTombolProfil();
});

// Tombol profil di header: tampilkan nama kalau sudah masuk
function initTombolProfil() {
  const t = document.getElementById('btn-profil-teks');
  if (!t) return;
  if (Auth.sudahLogin()) {
    const p = Auth.profil();
    t.textContent = (p && p.nama ? p.nama.split(' ')[0] : 'Profil');
  } else {
    t.textContent = 'Masuk';
  }
}

// ── HALAMAN AWAL: TERAKHIR DIBACA + FAVORIT ──
const FAVORIT = [
  { nomor: 67, nama: 'Al-Mulk',    judul: 'Al-Mulk — Kerajaan' },
  { nomor: 18, nama: 'Al-Kahf',    judul: 'Al-Kahf — Gua' },
  { nomor: 84, nama: 'Al-Insyiqaq', judul: 'Al-Insyiqaq — Terbelah' },
];

function initHomeSections() {
  // Tiga kartu "Lanjutkan": baca, tulis, hafalan — gabung data server + localStorage
  const secLanjut = document.getElementById('lanjut-section');
  const gridLanjut = document.getElementById('lanjut-grid');
  if (secLanjut && gridLanjut) {
    const me = Auth.me || {};
    // Gabungkan: data server (jika login) diutamakan, fallback ke localStorage
    const ambil = (kunci) => {
      const dariServer = (me[kunci] && me[kunci][0] && me[kunci][0].surah) ? me[kunci][0] : null;
      const dariLokal  = ambilTerakhirLokal(kunci);
      return dariServer || dariLokal || null;
    };
    const kartu = [
      { kunci: 'terakhir',          label: 'Lanjut Baca',    ikon: '📖',
        url: (l) => `surah.html?id=${l.surah}#ayat-${l.ayat}`, warna: 'l-baca' },
      { kunci: 'terakhir_tulis',    label: 'Lanjut Tulis',   ikon: '✍️',
        url: (l) => `tulis.html?surah=${l.surah}&ayat=${l.ayat}`, warna: 'l-tulis' },
      { kunci: 'terakhir_hafalan',  label: 'Lanjut Hafalan', ikon: '🎤',
        url: (l) => `hafalan.html?surah=${l.surah}&ayat=${l.ayat}`, warna: 'l-hafal' },
    ];
    const html = kartu.map(k => {
      const l = ambil(k.kunci);
      const info = infoSurat(l ? l.surah : 0);
      if (!l) {
        return `<div class="lanjut-card lanjut-kosong ${k.warna}">
          <span class="lanjut-ikon">${k.ikon}</span>
          <span class="lanjut-body">
            <span class="lanjut-label">${k.label}</span>
            <span class="lanjut-surah">Belum ada riwayat</span>
          </span></div>`;
      }
      const nama = info.latin || l.nama || '';
      const arab = info.arab ? ` <span class="lanjut-arab">${info.arab}</span>` : '';
      return `<a class="lanjut-card ${k.warna}" href="${k.url(l)}">
        <span class="lanjut-ikon">${k.ikon}</span>
        <span class="lanjut-body">
          <span class="lanjut-label">${k.label}</span>
          <span class="lanjut-surah">${l.surah}. ${nama}${arab}</span>
          <span class="lanjut-ayat">Ayat ${l.ayat}</span>
        </span>
        <span class="lanjut-panah">→</span></a>`;
    }).join('');
    gridLanjut.innerHTML = html;
    secLanjut.style.display = 'block';
  }

  // Surat favorit/pilihan
  const secFav = document.getElementById('fav-section');
  const gridFav = document.getElementById('fav-grid');
  if (secFav && gridFav) {
    gridFav.innerHTML = FAVORIT.map(f => `
      <a class="fav-card" href="surah.html?id=${f.nomor}">
        <span class="fav-num">${f.nomor}</span>
        <span class="fav-nama">${f.nama}</span>
        <span class="fav-judul">${f.judul.split('—')[1] ? f.judul.split('—')[1].trim() : ''}</span>
      </a>`).join('');
    secFav.style.display = 'block';
  }
}

// ── LOAD SURAHS ──
async function loadSurahs() {
  showState('loading');
  try {
    const res = await fetch(`${API_BASE}/surah`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    allSurahs = data.data;
    renderGrid(allSurahs);
    showState('grid');
  } catch (err) {
    console.error('Failed to load surahs:', err);
    showState('error');
  }
}

// ── RENDER GRID ──
function renderGrid(surahs) {
  surahGrid.innerHTML = '';

  if (surahs.length === 0) {
    showState('no-result');
    return;
  }

  showState('grid');
  statShown.textContent = surahs.length;

  surahs.forEach((surah, idx) => {
    const card = createSurahCard(surah, idx);
    surahGrid.appendChild(card);
  });
}

function createSurahCard(surah, idx) {
  const card = document.createElement('a');
  card.className = 'surah-card';
  card.href = `surah.html?id=${surah.number}`;
  card.setAttribute('role', 'listitem');
  const info = infoSurat(surah.number);
  const latin = info.latin || surah.englishName;
  const arab  = info.arab  || buangKataSurat(surah.name);
  const arti  = info.arti  || surah.englishNameTranslation;
  card.setAttribute('aria-label', `${latin}, ${surah.numberOfAyahs} ayat`);
  card.style.animationDelay = `${Math.min(idx * 0.03, 1)}s`;
  card.addEventListener('click', () => {
    sessionStorage.setItem('5dquran_mode', 'baca');
  });

  const revealationType = surah.revelationType === 'Meccan' ? 'Makkiyah' : 'Madaniyah';
  const chipClass = surah.revelationType === 'Meccan' ? 'chip-makki' : 'chip-madani';

  card.innerHTML = `
    <div class="card-num">
      <div class="card-num-shape"></div>
      <span>${surah.number}</span>
    </div>
    <div class="card-body">
      <div class="card-name-row">
        <span class="card-latin">${latin}</span>
        <span class="card-arabic">${arab}</span>
      </div>
      <div class="card-meta">
        <span class="card-meaning">${arti}</span>
        <span class="card-dot">•</span>
        <span class="card-ayat">${surah.numberOfAyahs} Ayat</span>
      </div>
      <span class="card-chip ${chipClass}">${revealationType}</span>
    </div>
    <svg class="card-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
      <path d="M9 18l6-6-6-6"/>
    </svg>
  `;

  return card;
}

/* Buang kata 'سُورَةُ' / 'سورة' di depan nama Arab (permintaan: jangan ada kata 'surat'). */
function buangKataSurat(nama) {
  if (!nama) return '';
  return nama.replace(/^\s*سُورَةُ\s+/, '').replace(/^\s*سورة\s+/, '').replace(/^\s*سُورَة\s+/, '').trim();
}

// ── SEARCH ──
function initSearch() {
  searchInput.addEventListener('input', () => {
    searchQuery = searchInput.value.trim().toLowerCase();
    clearBtn.style.display = searchQuery ? 'block' : 'none';
    applyFilters();
  });

  clearBtn.addEventListener('click', () => {
    searchInput.value = '';
    searchQuery = '';
    clearBtn.style.display = 'none';
    searchInput.focus();
    applyFilters();
  });
}

// ── SEARCH (tombol filter Makkiyah/Madaniyah dihapus) ──
function applyFilters() {
  let result = [...allSurahs];

  if (searchQuery) {
    result = result.filter(s => {
      const info = infoSurat(s.number);
      return s.englishName.toLowerCase().includes(searchQuery) ||
        (info.latin || '').toLowerCase().includes(searchQuery) ||
        (info.arti  || '').toLowerCase().includes(searchQuery) ||
        s.englishNameTranslation.toLowerCase().includes(searchQuery) ||
        (info.arab || s.name).includes(searchQuery) ||
        String(s.number).includes(searchQuery);
    });
  }

  renderGrid(result);
}

// ── STATE MANAGER ──
function showState(state) {
  loadingState.style.display = 'none';
  errorState.style.display   = 'none';
  noResult.style.display     = 'none';
  surahGrid.style.display    = 'none';

  if (state === 'loading') {
    loadingState.style.display = 'flex';
  } else if (state === 'error') {
    errorState.style.display = 'flex';
  } else if (state === 'no-result') {
    noResult.style.display = 'flex';
    statShown.textContent = '0';
  } else if (state === 'grid') {
    surahGrid.style.display = 'grid';
  }
}

// ── SCROLL TOP ──
function initScrollTop() {
  window.addEventListener('scroll', () => {
    scrollTopBtn.style.display = window.scrollY > 400 ? 'flex' : 'none';
  });
  scrollTopBtn.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });
}

// ── ABOUT MODAL ──
function showAbout() {
  // Remove existing modal if any
  const existing = document.getElementById('about-modal-overlay');
  if (existing) existing.remove();

  const overlay = document.createElement('div');
  overlay.id = 'about-modal-overlay';
  overlay.innerHTML = `
    <div class="about-modal" id="about-modal" role="dialog" aria-modal="true" aria-label="Tentang 5D Quran">
      <button class="about-close" id="about-close" aria-label="Tutup">&times;</button>

      <!-- INTRO ANIMASI (3 tahap: logo besar → hero → isi) -->
      <div class="about-intro" id="about-intro" aria-hidden="true">
        <div class="ai-stage ai-stage-1">
          <div class="ai-logo">
            <img src="assets/icon.jpg" alt="5D Quran">
          </div>
        </div>

        <div class="ai-stage ai-stage-2">
          <div class="ai-hero">
            <span class="ai-badge"><span class="ai-dot"></span> Al-Quran Digital</span>
            <h3 class="ai-hero-title">5 Dimensi Interaksi<br>dengan <span>Al-Quran</span></h3>
            <div class="ai-pills">
              <span>📖 Dibaca</span><span>🎧 Didengarkan</span><span>🧠 Dihafal</span><span>✍️ Ditulis</span><span>🔬 Dipelajari</span>
            </div>
          </div>
        </div>

        <div class="ai-stage ai-stage-3">
          <div class="ai-about-mini">
            <div class="ai-logo ai-logo-sm">
              <img src="assets/icon.jpg" alt="5D Quran">
            </div>
            <span class="ai-mini-title">5D <em>Quran</em></span>
            <div class="ai-lines"><i></i><i></i><i></i></div>
          </div>
        </div>

        <div class="ai-tap" id="ai-tap">👆 Ketuk untuk lanjut</div>
      </div>

      <!-- Header (dipindah dari halaman utama) -->
      <div class="about-header">
        <div class="about-logo-ring">
          <img src="assets/icon.jpg" alt="5D Quran">
        </div>
        <div class="hero-badge">
          <span class="badge-dot"></span> Al-Quran Digital
        </div>
        <h1 class="about-hero-title">
          5 Dimensi Interaksi dengan<br>
          <span class="gradient-text">Al-Quran</span> Secara Mendalam
        </h1>
        <p class="about-hero-desc">
          5D Quran hadir untuk menemani perjalanan spiritualmu — membaca, mendengarkan,
          menghafal, menulis, dan mempelajari (tadabur) Al-Quran secara menyeluruh.
        </p>
        <div class="hero-pills">
          <span class="pill pill-read">📖 Dibaca</span>
          <span class="pill pill-dengar">🎧 Didengarkan</span>
          <span class="pill pill-hafal">🧠 Dihafal</span>
          <span class="pill pill-tulis">✍️ Ditulis</span>
          <span class="pill pill-pelajar">🔬 Dipelajari</span>
        </div>
      </div>

      <!-- 5D Features -->
      <div class="about-features">
        <div class="about-feat"><span class="af-icon">📖</span><div><strong>Dibaca</strong><p>Baca Al-Quran per ayat dengan terjemahan Bahasa Indonesia, mode Mushaf, dan tampilan tajweed berwarna</p></div></div>
        <div class="about-feat"><span class="af-icon">🎧</span><div><strong>Didengarkan</strong><p>Murottal lengkap surah &amp; per ayat oleh qari pilihan (Alafasy, Sudais, Minshawi, Husary, dsb.)</p></div></div>
        <div class="about-feat"><span class="af-icon">🧠</span><div><strong>Dihafal</strong><p>Setoran hafalan interaktif dengan penilaian suara (Web Speech API) dan koreksi kata per kata</p></div></div>
        <div class="about-feat"><span class="af-icon">✍️</span><div><strong>Ditulis</strong><p>Latihan menulis ayat per ayat dengan canvas, simpan ke perangkat, dan unduh hasil tulisan</p></div></div>
        <div class="about-feat"><span class="af-icon">📚</span><div><strong>Dipelajari</strong><p>Tadabur ayat: kosakata Arab, Tafsir Ibnu Katsir &amp; Jalalayn, Hadis terkait, dan refleksi mendalam</p></div></div>
      </div>

      <!-- Data Sources -->
      <div class="about-section">
        <h3 class="about-sec-title">📡 Sumber Data</h3>
        <div class="about-sources">
          <div class="about-src"><span class="src-badge">API</span><div><strong>AlQuran.cloud</strong><p>Teks Al-Quran (Uthmani), terjemahan Bahasa Indonesia, metadata surah &amp; ayat</p></div></div>
          <div class="about-src"><span class="src-badge">Audio</span><div><strong>EveryAyah.com</strong><p>Audio murottal per ayat oleh berbagai qari internasional</p></div></div>
          <div class="about-src"><span class="src-badge">Tafsir</span><div><strong>Tafsirq.com &amp; IslamicStudies.info</strong><p>Tafsir Ibnu Katsir (Bahasa Indonesia) dan Tafsir Jalalayn</p></div></div>
          <div class="about-src"><span class="src-badge">Terjemah</span><div><strong>Kemenag RI</strong><p>Terjemahan Al-Quran Bahasa Indonesia versi Kementerian Agama</p></div></div>
        </div>
      </div>

      <!-- Prayer -->
      <div class="about-doa">
        <div class="about-doa-arabic">الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ</div>
        <p class="about-doa-text">Segala puji bagi Allah, karena atas karunia-Nya aplikasi ini dapat terwujud.</p>
        <div class="about-doa-arabic about-doa-sholawat">صَلَّى اللَّهُ عَلَيْهِ وَسَلَّمَ</div>
        <p class="about-doa-text">Sholawat dan salam untuk Rasulullah Shallallahu 'Alaihi Wasallam.</p>
        <p class="about-doa-text">🤲 Mohon doa dan istighfar untuk <strong>Ari Adrianto</strong> dan keluarga, semoga diberikan istiqomah.</p>
        <p class="about-amal">🌿 Semoga aplikasi ini menjadi amal jariyah yang terus mengalir.</p>
      </div>

      <!-- Footer -->
      <div class="about-footer-note">
        <p>© 2026 5D Quran · <em>Semoga Bermanfaat Dunia &amp; Akhirat</em></p>
        <p style="color:var(--text-muted);font-size:.72rem;margin-top:.25rem">Web: dibaca, didengarkan, dihafal, ditulis, dipelajari</p>
      </div>
    </div>
  `;

  // Styles
  const style = document.createElement('style');
  style.id = 'about-modal-style';
  style.textContent = `
    #about-modal-overlay {
      position: fixed; inset: 0; z-index: 9999;
      background: rgba(0,0,0,0.8); backdrop-filter: blur(10px);
      display: flex; align-items: center; justify-content: center;
      padding: 1rem; animation: fadeInOverlay .3s ease;
    }
    @keyframes fadeInOverlay { from { opacity: 0 } to { opacity: 1 } }
    .about-modal {
      position: relative; background: #11111f;
      border: 1px solid rgba(212,168,83,0.25); border-radius: 24px;
      max-width: 560px; width: 100%; max-height: 90vh; overflow-y: auto;
      box-shadow: 0 0 60px rgba(212,168,83,0.15), 0 24px 80px rgba(0,0,0,0.7);
      animation: slideUp .35s cubic-bezier(.16,1,.3,1);
      scrollbar-width: thin; scrollbar-color: rgba(212,168,83,.2) transparent;
    }
    @keyframes slideUp { from { transform: translateY(40px); opacity: 0 } to { transform: translateY(0); opacity: 1 } }
    .about-close {
      position: sticky; top: 0; float: right; margin: 1rem 1rem 0 0;
      background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.1);
      color: #aaa; width: 32px; height: 32px; border-radius: 50%;
      font-size: 1.2rem; cursor: pointer; z-index: 10; line-height: 1;
      transition: all .2s;
    }
    .about-close:hover { background: rgba(212,168,83,.15); color: #e8bb50; border-color: rgba(212,168,83,.3); }
    .about-header { text-align: center; padding: 2rem 1.5rem 1.5rem; }
    .about-hero-title {
      font-size: 1.5rem; font-weight: 900; line-height: 1.28;
      color: #eeeef5; margin: .8rem 0 .7rem; letter-spacing: -.5px;
    }
    .about-hero-title .gradient-text { color: #e8bb50; }
    .about-hero-desc {
      font-size: .84rem; color: #9999bb; line-height: 1.6;
      margin: 0 0 1rem; max-width: 30rem; margin-left: auto; margin-right: auto;
    }
    .about-header .hero-pills { justify-content: center; }

    /* ── INTRO ANIMASI 3 TAHAP (maju dengan ketukan, tanpa timer) ── */
    .about-intro {
      position: absolute; inset: 0; z-index: 5;
      display: flex; align-items: center; justify-content: center;
      background: #11111f; border-radius: 24px;
      cursor: pointer;
    }
    .about-intro.keluar { animation: aiKeluar .4s ease forwards; }
    @keyframes aiKeluar {
      to { opacity: 0; visibility: hidden; }
    }
    .ai-stage {
      position: absolute; inset: 0;
      display: flex; align-items: center; justify-content: center;
      opacity: 0; visibility: hidden;
      transition: opacity .45s ease, transform .45s cubic-bezier(.16,1,.3,1);
    }
    .ai-stage.aktif { opacity: 1; visibility: visible; transform: none; }
    /* Tahap 1: logo besar */
    .ai-stage-1 { transform: scale(.55); }
    .ai-stage-1.aktif { animation: aiPulse 1.6s ease-in-out infinite; }
    /* Tahap 2: hero-content */
    .ai-stage-2 { transform: translateY(22px); }
    /* Tahap 3: mini about */
    .ai-stage-3 { transform: scale(.9); }
    /* Petunjuk ketuk */
    .ai-tap {
      position: absolute; bottom: 1.1rem; left: 50%; transform: translateX(-50%);
      display: inline-flex; align-items: center; gap: .4rem;
      padding: .3rem .85rem; border-radius: 100px; white-space: nowrap;
      background: rgba(212,168,83,.1); border: 1px solid rgba(212,168,83,.28);
      color: #e8bb50; font-size: .72rem; font-weight: 700; letter-spacing: .04em;
      animation: aiTap 1.6s ease-in-out infinite;
    }
    @keyframes aiTap {
      0%,100% { opacity: .55; transform: translateX(-50%) translateY(0) }
      50%     { opacity: 1;   transform: translateX(-50%) translateY(-3px) }
    }
    .ai-logo {
      width: 140px; height: 140px; border-radius: 50%;
      background: radial-gradient(circle, rgba(212,168,83,.18) 0%, transparent 70%);
      border: 1px solid rgba(212,168,83,.3);
      display: flex; align-items: center; justify-content: center;
      animation: aiPulse 1.6s ease-in-out infinite;
    }
    @keyframes aiPulse {
      0%,100% { box-shadow: 0 0 0 0 rgba(212,168,83,.28) }
      50%     { box-shadow: 0 0 0 22px rgba(212,168,83,0) }
    }
    /* Tahap 2: hero-content */
    .ai-hero { text-align: center; padding: 0 1.5rem; }
    .ai-badge {
      display: inline-flex; align-items: center; gap: .4rem;
      padding: .25rem .8rem; border-radius: 100px; margin-bottom: .9rem;
      background: rgba(212,168,83,.1); border: 1px solid rgba(212,168,83,.28);
      color: #e8bb50; font-size: .72rem; font-weight: 700; letter-spacing: .05em;
    }
    .ai-dot {
      width: 6px; height: 6px; border-radius: 50%; background: #e8bb50;
      animation: aiBlink 1.1s ease-in-out infinite;
    }
    @keyframes aiBlink { 0%,100% { opacity: 1 } 50% { opacity: .25 } }
    .ai-hero-title {
      font-size: 1.5rem; font-weight: 900; line-height: 1.25;
      color: #eeeef5; margin: 0 0 .9rem; letter-spacing: -.5px;
    }
    .ai-hero-title span { color: #e8bb50; }
    .ai-pills { display: flex; flex-wrap: wrap; gap: .4rem; justify-content: center; }
    .ai-pills span {
      padding: .3rem .7rem; border-radius: 100px; font-size: .72rem;
      background: rgba(255,255,255,.05); border: 1px solid rgba(255,255,255,.09);
      color: #b9b9d0;
    }
    /* Tahap 3: halaman about */
    .ai-about-mini { text-align: center; }
    .ai-logo-sm {
      width: 76px; height: 76px; margin: 0 auto .7rem; animation: none;
    }
    .ai-mini-title { font-size: 1.3rem; font-weight: 900; color: #eeeef5; }
    .ai-mini-title em { color: #e8bb50; font-style: normal; }
    .ai-lines { display: flex; flex-direction: column; gap: .4rem; margin-top: 1rem; align-items: center; }
    .ai-lines i {
      display: block; height: 8px; border-radius: 100px;
      background: rgba(255,255,255,.07);
      animation: aiGaris 1.1s ease-in-out infinite;
    }
    .ai-lines i:nth-child(1) { width: 170px; }
    .ai-lines i:nth-child(2) { width: 200px; animation-delay: .15s; }
    .ai-lines i:nth-child(3) { width: 140px; animation-delay: .3s; }
    @keyframes aiGaris { 0%,100% { opacity: .35 } 50% { opacity: .8 } }
    /* Hormati preferensi kurangi gerak */
    @media (prefers-reduced-motion: reduce) {
      .about-intro { display: none; }
    }
    .about-logo-ring {
      width: 80px; height: 80px; margin: 0 auto 1rem; padding: 6px;
      background: radial-gradient(circle, rgba(212,168,83,.12) 0%, transparent 70%);
      border: 1px solid rgba(212,168,83,.25); border-radius: 50%;
      display: flex; align-items: center; justify-content: center;
    }
    /* Logo icon.jpg (About + animasi intro) */
    .about-logo-ring img, .ai-logo img {
      width: 100%; height: 100%; object-fit: cover;
      border-radius: 50%; display: block;
    }
    .ai-logo { padding: 8px; }
    .ai-logo-sm { padding: 5px; }
    .about-title { font-size: 2rem; font-weight: 900; color: #eeeef5; margin: 0; }
    .about-title em { color: #e8bb50; font-style: normal; }
    .about-version {
      display: inline-block; margin: .4rem 0 .6rem;
      padding: .2rem .85rem; border-radius: 100px;
      background: rgba(212,168,83,.1); border: 1px solid rgba(212,168,83,.3);
      color: #e8bb50; font-size: .75rem; font-weight: 700; letter-spacing: .06em;
    }
    .about-tagline { color: #9999bb; font-size: .88rem; margin: 0; }
    .about-features { padding: 0 1.5rem 1.25rem; display: flex; flex-direction: column; gap: .6rem; }
    .about-feat {
      display: flex; align-items: flex-start; gap: .85rem;
      background: rgba(255,255,255,.03); border: 1px solid rgba(255,255,255,.06);
      border-radius: 12px; padding: .85rem 1rem;
    }
    .af-icon { font-size: 1.35rem; flex-shrink: 0; margin-top: .1rem; }
    .about-feat strong { display: block; font-size: .9rem; color: #e8bb50; margin-bottom: .2rem; }
    .about-feat p { font-size: .78rem; color: #7878a8; line-height: 1.5; margin: 0; }
    .about-section { padding: 0 1.5rem 1.25rem; }
    .about-sec-title { font-size: .82rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; color: #9999bb; margin: 0 0 .85rem; }
    .about-sources { display: flex; flex-direction: column; gap: .6rem; }
    .about-src {
      display: flex; align-items: flex-start; gap: .75rem;
      background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.05);
      border-radius: 10px; padding: .75rem .9rem;
    }
    .src-badge {
      flex-shrink: 0; padding: .2rem .6rem; border-radius: 6px;
      font-size: .68rem; font-weight: 800; letter-spacing: .04em;
      background: rgba(45,212,191,.1); border: 1px solid rgba(45,212,191,.25); color: #2dd4bf;
      margin-top: .15rem;
    }
    .about-src strong { display: block; font-size: .85rem; color: #eeeef5; margin-bottom: .15rem; }
    .about-src p { font-size: .75rem; color: #7878a8; line-height: 1.4; margin: 0; }
    .about-doa {
      margin: 0 1.5rem 1.25rem;
      background: linear-gradient(135deg, rgba(212,168,83,.08) 0%, rgba(251,191,36,.04) 100%);
      border: 1px solid rgba(212,168,83,.2); border-radius: 16px;
      padding: 1.25rem; text-align: center;
    }
    .about-doa-arabic { font-family: 'Amiri Quran', serif; font-size: 1.2rem; color: #e8bb50; margin-bottom: .75rem; }
    .about-doa-text { font-size: .88rem; color: #b0b0cc; line-height: 1.65; margin: 0 0 .5rem; }
    .about-doa-text strong { color: #e8bb50; }
    .about-amal { font-size: .8rem; color: #4ade80; margin: 0; font-style: italic; }
    .about-footer-note { padding: 1rem 1.5rem 1.5rem; text-align: center; border-top: 1px solid rgba(255,255,255,.06); }
    .about-footer-note p { font-size: .78rem; color: #6666aa; margin: 0; }
    .about-footer-note em { color: #e8bb50; }
    @media (max-width: 480px) {
      .about-modal { border-radius: 18px; }
      .about-header { padding: 1.5rem 1.25rem 1rem; }
      .about-features, .about-section { padding-left: 1rem; padding-right: 1rem; }
      .about-doa { margin: 0 1rem 1rem; }
    }
  `;

  if (!document.getElementById('about-modal-style')) {
    document.head.appendChild(style);
  }

  document.body.appendChild(overlay);
  document.body.style.overflow = 'hidden';

  const closeModal = () => {
    overlay.remove();
    document.body.style.overflow = '';
  };

  document.getElementById('about-close').addEventListener('click', closeModal);
  overlay.addEventListener('click', (e) => { if (e.target === overlay) closeModal(); });
  document.addEventListener('keydown', function onKey(e) {
    if (e.key === 'Escape') { closeModal(); document.removeEventListener('keydown', onKey); }
  });

  // Intro animasi: maju per ketukan (tahap 1 → 2 → 3 → tutup), tanpa timer
  const intro = document.getElementById('about-intro');
  if (intro) {
    const tahap = [...intro.querySelectorAll('.ai-stage')];
    const tap = document.getElementById('ai-tap');
    let i = 0;

    const tampil = (n) => {
      tahap.forEach((el, k) => el.classList.toggle('aktif', k === n));
      if (tap) tap.textContent = n === tahap.length - 1 ? '👆 Ketuk untuk masuk' : '👆 Ketuk untuk lanjut';
    };
    tampil(0);

    intro.addEventListener('click', (e) => {
      e.stopPropagation();
      i++;
      if (i >= tahap.length) {
        intro.classList.add('keluar');
        setTimeout(() => intro.remove(), 420);
      } else {
        tampil(i);
      }
    });

    // Papan ketik: Spasi / Enter juga memajukan
    document.addEventListener('keydown', function onTapKey(e) {
      if (!document.body.contains(intro)) { document.removeEventListener('keydown', onTapKey); return; }
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); intro.click(); }
    });
  }
}

/* ══════════════════════════════════════════════════════════
   SETTING MODAL — Qari & Huruf Mushaf
   ══════════════════════════════════════════════════════════ */
const QARI_LIST = [
  { id: 'alafasy',  name: 'Mishary Alafasy' },
  { id: 'husary',   name: 'Mahmoud Al-Husary' },
  { id: 'minshawi', name: 'Muhammad Minshawi' },
  { id: 'basit',    name: 'Abdul Basit' },
  { id: 'ajamy',    name: 'Ahmad Al-Ajamy' },
];
const MUSHAF_LIST = [
  { id: 'madani',  name: 'Madani' },
  { id: 'naskh',   name: 'Naskh' },
  { id: 'indopak', name: 'IndoPak' },
];

const TAMPILAN_LIST = [
  { id: 'arab_terjemah', name: 'Arab + Terjemah', ket: 'Tulisan Arab beserta terjemahan Indonesia' },
  { id: 'arab_saja',     name: 'Arab Saja',       ket: 'Hanya tulisan Arab tanpa terjemahan' },
];

/* Opsi untuk latihan Tulis & Hafalan:
   'ya'    = contoh ayat ditampilkan (untuk ditiru)
   'tidak' = contoh disembunyikan (murni dari hafalan) */
const CONTOH_LIST = [
  { id: 'ya',    name: 'Dengan Contoh',  ket: 'Ayat contoh ditampilkan sebagai acuan' },
  { id: 'tidak', name: 'Tanpa Contoh',   ket: 'Ayat contoh disembunyikan — murni dari hafalan' },
];

function showSettings() {
  const existing = document.getElementById('setting-modal-overlay');
  if (existing) existing.remove();

  const st = Auth.setting();
  const qariAktif     = st.qari     || 'alafasy';
  const mushafAktif   = st.mushaf   || 'madani';
  const tampilanAktif = st.tampilan || 'arab_terjemah';
  const contohAktif   = st.contoh   || 'ya';

  // Belum login: arahkan ke gerbang profil
  if (!Auth.sudahLogin()) { location.href = 'profil.html'; return; }

  const overlay = document.createElement('div');
  overlay.id = 'setting-modal-overlay';
  overlay.innerHTML = `
    <div class="about-modal" role="dialog" aria-modal="true" aria-label="Pengaturan">
      <button class="about-close" id="setting-close" aria-label="Tutup">&times;</button>
      <div class="about-header">
        <h2 class="about-title">Pengaturan</h2>
        <p class="about-tagline">Pilih tampilan ayat, qari, dan gaya huruf mushaf</p>
      </div>
      <div class="about-section">
        <h3 class="about-sec-title">📄 Tampilan Ayat</h3>
        <div class="setting-options" id="opt-tampilan">
          ${TAMPILAN_LIST.map(t => `
            <button class="setting-opt${t.id === tampilanAktif ? ' active' : ''}" data-set="tampilan" data-id="${t.id}">
              <span class="so-text"><span class="so-name">${t.name}</span><span class="so-ket">${t.ket}</span></span>
              <span class="so-check">${t.id === tampilanAktif ? '✓' : ''}</span>
            </button>`).join('')}
        </div>
      </div>
      <div class="about-section">
        <h3 class="about-sec-title">🎧 Pilihan Qari</h3>
        <div class="setting-options" id="opt-qari">
          ${QARI_LIST.map(q => `
            <button class="setting-opt${q.id === qariAktif ? ' active' : ''}" data-set="qari" data-id="${q.id}">
              <span class="so-name">${q.name}</span>
              <span class="so-check">${q.id === qariAktif ? '✓' : ''}</span>
            </button>`).join('')}
        </div>
      </div>
      <div class="about-section">
        <h3 class="about-sec-title">📖 Huruf Mushaf</h3>
        <div class="setting-options" id="opt-mushaf">
          ${MUSHAF_LIST.map(m => `
            <button class="setting-opt${m.id === mushafAktif ? ' active' : ''}" data-set="mushaf" data-id="${m.id}">
              <span class="so-name">${m.name}</span>
              <span class="so-check">${m.id === mushafAktif ? '✓' : ''}</span>
            </button>`).join('')}
        </div>
      </div>
    <div class="about-section">
        <h3 class="about-sec-title">✍️ Contoh Ayat (Tulis &amp; Hafalan)</h3>
        <div class="setting-options" id="opt-contoh">
          ${CONTOH_LIST.map(c => `
            <button class="setting-opt${c.id === contohAktif ? ' active' : ''}" data-set="contoh" data-id="${c.id}">
              <span class="so-text"><span class="so-name">${c.name}</span><span class="so-ket">${c.ket}</span></span>
              <span class="so-check">${c.id === contohAktif ? '✓' : ''}</span>
            </button>`).join('')}
        </div>
      </div>
    </div>`;

  if (!document.getElementById('setting-modal-style')) {
    const style = document.createElement('style');
    style.id = 'setting-modal-style';
    style.textContent = `
      #setting-modal-overlay {
        position: fixed; inset: 0; z-index: 9999;
        background: rgba(4,4,10,.8); backdrop-filter: blur(6px);
        display: flex; align-items: center; justify-content: center; padding: 1rem;
        animation: fadeIn .2s ease;
      }
      @keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }
      #setting-modal-overlay .about-modal { max-height: 85vh; overflow-y: auto; }
      .setting-options { display: flex; flex-direction: column; gap: .5rem; padding: 0 2rem 1.5rem; }
      .setting-opt {
        display: flex; align-items: center; justify-content: space-between;
        padding: .75rem 1rem; border-radius: 10px;
        background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.09);
        color: #ddd; font-family: var(--font-main); font-size: .92rem;
        cursor: pointer; transition: all .2s; text-align: left;
      }
      .setting-opt:hover { background: rgba(212,168,83,.1); border-color: rgba(212,168,83,.3); }
      .setting-opt.active { background: rgba(212,168,83,.16); border-color: rgba(212,168,83,.45); color: #e8bb50; font-weight: 600; }
      .setting-opt .so-check { color: #e8bb50; font-weight: 700; }
      .setting-opt .so-text { display: flex; flex-direction: column; gap: .15rem; }
      .setting-opt .so-ket { font-size: .72rem; font-weight: 400; color: #7878a8; }
      .setting-opt.active .so-ket { color: #b99a52; }
    `;
    document.head.appendChild(style);
  }

  document.body.appendChild(overlay);
  document.body.style.overflow = 'hidden';

  const closeModal = () => { overlay.remove(); document.body.style.overflow = ''; };
  document.getElementById('setting-close').addEventListener('click', closeModal);
  overlay.addEventListener('click', e => { if (e.target === overlay) closeModal(); });
  document.addEventListener('keydown', function onKey(e) {
    if (e.key === 'Escape') { closeModal(); document.removeEventListener('keydown', onKey); }
  });

  // Simpan pilihan
  overlay.querySelectorAll('.setting-opt').forEach(btn => {
    btn.addEventListener('click', () => {
      const jenis = btn.dataset.set;
      const id    = btn.dataset.id;
      Auth.simpanSetting(jenis, id);
      overlay.querySelectorAll(`[data-set="${jenis}"]`).forEach(b => {
        b.classList.toggle('active', b.dataset.id === id);
        b.querySelector('.so-check').textContent = b.dataset.id === id ? '✓' : '';
      });
    });
  });
}

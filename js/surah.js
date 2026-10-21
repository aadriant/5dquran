/* ============================================================
   5D Quran — surah.js  (v3)
   Features:
     - Baca + Muroja'ah per-ayat + Karaoke highlight
     - Mushaf (Madani / Naskh / IndoPak / Tajweed)
     - Dengarkan (full-surah audio player)
     - Tulis (redirect to writing page)
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

/* ══ RECITERS (full-surah, mp3quran.net) ════════════════════ */
const RECITERS = [
  { id:'alafasy',  name:'Mishary Alafasy',      arabic:'مشاري العفاسي',     url:'https://server8.mp3quran.net/afs/' },
  { id:'sudais',   name:'Abdurrahman As-Sudais', arabic:'عبدالرحمن السديس',  url:'https://server11.mp3quran.net/sds/' },
  { id:'husary',   name:'Mahmoud Al-Husary',     arabic:'محمود خليل الحصري', url:'https://server13.mp3quran.net/husr/' },
  { id:'minshawi', name:'Mohamed Minshawi',      arabic:'محمد المنشاوي',     url:'https://server10.mp3quran.net/minsh/' },
  { id:'ghamdi',   name:'Saad Al-Ghamdi',        arabic:'سعد الغامدي',       url:'https://server7.mp3quran.net/s_gmd/' },
  { id:'ajamy',    name:'Ahmad Al-Ajamy',         arabic:'أحمد العجمي',       url:'https://server10.mp3quran.net/ajm/' },
];

/* ══ PER-AYAT RECITERS (everyayah.com) ══════════════════════ */
const PAR_RECITERS = [
  { id:'alafasy',  name:'Alafasy',      folder:'Alafasy_128kbps' },
  { id:'husary',   name:'Al-Husary',    folder:'Husary_128kbps' },
  { id:'minshawi', name:'Minshawi',     folder:'Minshawy_Murattal_128kbps' },
  { id:'basit',    name:'Abdul Basit',  folder:'Abdul_Basit_Murattal_192kbps' },
  { id:'ajamy',    name:'Al-Ajamy',     folder:'ahmed_ibn_ali_al_ajamy_128kbps' },
];

/* ══ STATE ══════════════════════════════════════════════════ */
let surahId         = 1;
let surahData       = null;
let indoData        = null;
let tajweedData     = null;
let showTranslation = true;
let isBookmarked    = false;
let currentMode     = 'baca';
let mushafStyle     = 'madani';
let activeReciter   = null;
let parReciterId    = 'alafasy';
let isPlaying       = false;

// Per-ayat audio state
let perAyatAudio         = new Audio();
let currentPlayingAyatId = null;
let currentPlayingBtn    = null;
let wordTimings          = [];
let karaokeRAF           = null;

/* ══ DOM REFS ═══════════════════════════════════════════════ */
const shLoading      = document.getElementById('sh-loading');
const shContent      = document.getElementById('sh-content');
const shNav          = document.getElementById('sh-nav');
const shNumber       = document.getElementById('sh-number');
const shArabic       = document.getElementById('sh-arabic');
const shLatin        = document.getElementById('sh-latin');
const shMeaning      = document.getElementById('sh-meaning');
const shTypeBadge    = document.getElementById('sh-type-badge');
const shAyat         = document.getElementById('sh-ayat');
const bismillahBar   = document.getElementById('bismillah-bar');
const modeBar        = document.getElementById('mode-bar');
const mushafStyleBar = document.getElementById('mushaf-style-bar');
const tajweedLegend  = document.getElementById('tajweed-legend');
const audioSection   = document.getElementById('audio-section');
const ayatLoading    = document.getElementById('ayat-loading');
const errorState     = document.getElementById('error-state');
const ayatList       = document.getElementById('ayat-list');
const bcCurrent      = document.getElementById('bc-current');
const toastEl        = document.getElementById('toast');
const scrollTopBtn   = document.getElementById('scroll-top');
const btnBookmark    = document.getElementById('btn-bookmark');
const btnPrev        = document.getElementById('btn-prev-surah');
const btnNext        = document.getElementById('btn-next-surah');
const prevName       = document.getElementById('prev-surah-name');
const nextName       = document.getElementById('next-surah-name');
const parRecitersEl  = document.getElementById('par-reciters');

// Full-surah audio DOM
const audioEl        = document.getElementById('quran-audio');
const apPlayBtn      = document.getElementById('ap-play-btn');
const apPrevBtn      = document.getElementById('ap-prev-btn');
const apNextBtn      = document.getElementById('ap-next-btn');
const apRange        = document.getElementById('ap-range');
const apTrackFill    = document.getElementById('ap-track-fill');
const apCurTime      = document.getElementById('ap-cur-time');
const apTotalTime    = document.getElementById('ap-total-time');
const apVolRange     = document.getElementById('ap-vol-range');
const apSurahName    = document.getElementById('ap-surah-name');
const apReciterName  = document.getElementById('ap-reciter-name');
const apStatus       = document.getElementById('ap-status');
const reciterGrid    = document.getElementById('reciter-grid');

// Helper: URL audio per-ayat & full-surah dari Setting (tidak ada pemilih di halaman surat)
function reciterPerAyat() { return PAR_RECITERS.find(r => r.id === parReciterId) || PAR_RECITERS[0]; }
/* ══════════════════════════════════════════════════════════
   INIT
   ══════════════════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', async () => {
  // Halaman surat wajib login; setting diambil dari profil.
  if (typeof gerbang === 'function' && !(await gerbang('surah.html'))) return;

  const params = new URLSearchParams(window.location.search);
  surahId = parseInt(params.get('id')) || 1;
  if (surahId < 1)   surahId = 1;
  if (surahId > 114) surahId = 114;

  const savedMode = sessionStorage.getItem('5dquran_mode');
  if (savedMode === 'mushaf' || savedMode === 'dengar') {
    currentMode = savedMode;
  } else {
    currentMode = 'baca';
    sessionStorage.setItem('5dquran_mode', 'baca');
  }
  const st = Auth.setting();
  mushafStyle  = st.mushaf || 'madani';
  if (mushafStyle === 'tajweed') mushafStyle = 'madani'; // tajweed dihapus dari pilihan
  parReciterId = st.qari    || 'alafasy';
  showTranslation = (st.tampilan || 'arab_terjemah') !== 'arab_saja';

  loadSurah();
  initScrollTop();
  initControls();
  initModeTabs();
  initMushafStyleBar();
  initParReciters();
  initFullAudioPlayer();
  setupPerAyatAudio();
  checkBookmark();
});

/* Simpan posisi baca terakhir + catat ayat yang SUDAH DIBACA (kriteria 1).
   Setiap ayat yang terlihat dihitung selesai satu kali (idempoten). */
function initLastReadTracker() {
  let lastAyat = 1;
  let simpanTimer = null;
  const terlihatSudah = new Set();          // ayat yang sudah dikirim ke server
  const simpan = () => {
    const nama = (surahData && surahData.englishName) || '';
    Auth.simpanMark('terakhir', { surah: surahId, ayat: lastAyat, nama });
    // localStorage fallback (untuk pengguna belum login)
    try {
      const all = JSON.parse(localStorage.getItem('5dquran_terakhir') || '{}');
      all['terakhir'] = { surah: surahId, ayat: lastAyat, nama, ts: Date.now() };
      localStorage.setItem('5dquran_terakhir', JSON.stringify(all));
      localStorage.setItem('5dq_terakhir_halaman', JSON.stringify({
        jenis: 'baca', surah: surahId, ayat: lastAyat, nama,
        url: `surah.html?id=${surahId}#ayat-${lastAyat}`, ts: Date.now()
      }));
    } catch (e) {}
  };
  const catatBaca = (n) => {
    if (!n || terlihatSudah.has(n)) return;
    terlihatSudah.add(n);
    Auth.simpanMark('baca', { surah: surahId, ayat: n, nama: (surahData && surahData.englishName) || '' });
    // localStorage: selalu simpan posisi terbaru
    try {
      const nama = (surahData && surahData.englishName) || '';
      const all = JSON.parse(localStorage.getItem('5dquran_terakhir') || '{}');
      all['terakhir'] = { surah: surahId, ayat: n, nama, ts: Date.now() };
      localStorage.setItem('5dquran_terakhir', JSON.stringify(all));
      localStorage.setItem('5dq_terakhir_halaman', JSON.stringify({
        jenis: 'baca', surah: surahId, ayat: n, nama,
        url: `surah.html?id=${surahId}#ayat-${n}`, ts: Date.now()
      }));
    } catch (e) {}
  };
  const cards = document.querySelectorAll('.ayat-card[id^="ayat-"]');
  if (!cards.length || !('IntersectionObserver' in window)) return;
  const io = new IntersectionObserver(entries => {
    entries.filter(e => e.isIntersecting).forEach(e => {
      const n = parseInt((e.target.id || '').replace('ayat-', ''), 10);
      if (n) catatBaca(n);
    });
    const terlihat = entries.filter(e => e.isIntersecting)
      .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)[0];
    if (!terlihat) return;
    const n = parseInt((terlihat.target.id || '').replace('ayat-', ''), 10);
    if (n) lastAyat = n;
    clearTimeout(simpanTimer);
    simpanTimer = setTimeout(simpan, 600);
  }, { rootMargin: '-20% 0px -60% 0px', threshold: 0 });
  cards.forEach(c => io.observe(c));
  // Ayat pertama langsung terhitung walaupun belum di-scroll
  catatBaca(1);
  window.addEventListener('beforeunload', () => { clearTimeout(simpanTimer); simpan(); });
}


function stripBismillahPrefix(text) {
  if (!text) return '';
  const trimmed = text.replace(/\ufeff/g, '').trim();
  const words = trimmed.split(/\s+/);
  if (words.length >= 5 && words[0].includes('ب') && words[1].includes('ل') && words[2].includes('ح') && words[3].includes('ح')) {
    return words.slice(4).join(' ').trim();
  }
  return trimmed;
}

/* ══════════════════════════════════════════════════════════
   LOAD SURAH DATA
   ══════════════════════════════════════════════════════════ */
async function loadSurah() {
  showAyatState('loading');
  try {
    const res  = await fetch(`${API_BASE}/surah/${surahId}/editions/${EDITIONS}`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    surahData  = json.data[0]; // Arabic Uthmani
    indoData   = json.data[1]; // Indonesian

    // Strip Bismillah from Ayah 1 for Surahs 2..8 and 10..114 (same as printed Mushaf)
    if (surahId !== 1 && surahId !== 9 && surahData && surahData.ayahs && surahData.ayahs.length > 0) {
      surahData.ayahs[0].text = stripBismillahPrefix(surahData.ayahs[0].text);
    }

    renderSurahHero(surahData);
    renderAyatList(surahData, indoData);
    setupNavigation(surahData);
    modeBar.style.display = 'block';
    setMode(currentMode);
  } catch (err) {
    console.error('Failed to load surah:', err);
    showAyatState('error');
    shLoading.style.display = 'none';
  }
}

/* ══════════════════════════════════════════════════════════
   RENDER HERO
   ══════════════════════════════════════════════════════════ */
function renderSurahHero(surah) {
  document.title = `${surah.englishName} — 5D Quran`;
  bcCurrent.textContent = `${surah.number}. ${surah.englishName}`;
  shNumber.textContent  = surah.number;
  shArabic.textContent  = surah.name;
  shLatin.textContent   = surah.englishName;
  shMeaning.textContent = `"${surah.englishNameTranslation}"`;
  shAyat.textContent    = `${surah.numberOfAyahs} Ayat`;

  const revType = surah.revelationType === 'Meccan' ? 'Makkiyah' : 'Madaniyah';
  shTypeBadge.textContent = revType;
  shTypeBadge.className   = `sh-badge ${surah.revelationType === 'Meccan' ? 'makki' : 'madani'}`;

  // Hide separate Bismillah header for At-Taubah (9) and Al-Fatihah (1, where Ayah 1 is Bismillah)
  if (surah.number !== 9 && surah.number !== 1) {
    bismillahBar.style.display = 'block';
  } else {
    bismillahBar.style.display = 'none';
  }
  if (apSurahName)  apSurahName.textContent  = `${surah.number}. ${surah.englishName} — ${surah.name}`;

  shLoading.style.display = 'none';
  shContent.style.display = 'flex';
}

/* ══════════════════════════════════════════════════════════
   RENDER AYAT LIST
   ══════════════════════════════════════════════════════════ */
function renderAyatList(arabicEd, indoEd) {
  ayatList.innerHTML = '';
  arabicEd.ayahs.forEach((ayah, idx) => {
    const card = createAyatCard(ayah, indoEd.ayahs[idx], idx + 1);
    ayatList.appendChild(card);
  });
  showAyatState('list');
  initLastReadTracker();
  scrollKeAyatDariHash();
}

/* Kalau URL punya #ayat-N, gulir ke ayat itu (dipakai tombol Lanjutkan). */
function scrollKeAyatDariHash() {
  const m = /^#ayat-(\d+)$/.exec(location.hash);
  if (!m) return;
  const el = document.getElementById(`ayat-${m[1]}`);
  if (el) setTimeout(() => el.scrollIntoView({ behavior: 'smooth', block: 'start' }), 250);
}

function createAyatCard(arabicAyah, indoAyah, displayNum) {
  const card = document.createElement('div');
  card.className = 'ayat-card';
  card.id = `ayat-${arabicAyah.numberInSurah}`;
  card.style.animationDelay = `${Math.min(displayNum * 0.028, 1.2)}s`;

  const indoText = indoAyah ? indoAyah.text : '';

  // Build karaoke word spans from Arabic text
  const words = arabicAyah.text.split(/\s+/);
  const karaokeHtml = words.map((w, i) =>
    `<span class="ar-word" data-idx="${i}">${w}</span>`
  ).join(' ');

  card.innerHTML = `
    <div class="ayat-header">
      <div class="ayat-header-left">
        <div class="ayat-num-badge">${arabicAyah.numberInSurah}</div>
        <button class="ayat-play-btn" id="playbtn-${arabicAyah.numberInSurah}"
          data-ayat="${arabicAyah.numberInSurah}" title="Putar muroja'ah ayat ini">
          <svg class="icon-play" width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          <svg class="icon-pause" width="13" height="13" viewBox="0 0 24 24" fill="currentColor" style="display:none"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
          <span class="play-label">Putar</span>
        </button>
      </div>
      <div class="ayat-actions">
        <button class="ayat-action-btn btn-kosakata-ayat" title="Kosakata per kata">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg> Kosakata
        </button>
        <button class="ayat-action-btn" onclick="copyAyat(this,'${escapeAttr(arabicAyah.text)}','${escapeAttr(indoText)}')" title="Salin ayat">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg> Salin
        </button>
        <button class="ayat-action-btn btn-tulis-ayat" onclick="goTulis(${arabicAyah.numberInSurah})" title="Tulis ayat ini">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg> Tulis
        </button>
        <button class="ayat-action-btn btn-pelajari-ayat" onclick="goPelajari(${arabicAyah.numberInSurah})" title="Pelajari ayat ini">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg> Pelajari
        </button>
        <button class="ayat-action-btn" onclick="shareAyat('${escapeAttr(arabicAyah.text)}','${escapeAttr(indoText)}',${arabicAyah.numberInSurah})" title="Bagikan ayat">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg> Bagikan
        </button>
      </div>
    </div>
    <div class="ayat-arabic karaoke-text" id="arabic-${arabicAyah.numberInSurah}">${karaokeHtml}</div>
    <div class="ayat-trans ${showTranslation ? '' : 'hidden'}">
      <div class="ayat-trans-label">Terjemahan (ID)</div>
      <div class="ayat-trans-text">${indoText || '<em style="color:var(--text-muted)">Terjemahan tidak tersedia</em>'}</div>
    </div>
  `;

  // Bind play button
  const playBtn = card.querySelector('.ayat-play-btn');
  playBtn.addEventListener('click', () => toggleAyatPlay(playBtn, arabicAyah.numberInSurah));

  // Bind kosakata button
  const kosaBtn = card.querySelector('.btn-kosakata-ayat');
  kosaBtn.addEventListener('click', () => bukaModalKosakata(surahId, arabicAyah.numberInSurah, arabicAyah.text, indoText));

  return card;
}

function escapeAttr(str) {
  if (!str) return '';
  return str.replace(/\\/g,'\\\\').replace(/'/g,"\\'").replace(/"/g,'&quot;').replace(/\n/g,' ');
}

/* ══════════════════════════════════════════════════════════
   KOSAKATA MODAL (Word-by-Word per Ayat)
   ══════════════════════════════════════════════════════════ */
const QCOM_BASE_SURAH = 'https://api.quran.com/api/v4';
const kosaCache = {};   // { "surah:ayat": wordsData }
let kosaWordAudio = null;

async function bukaModalKosakata(sId, aNum, arabicText, indoText) {
  // Buat overlay modal
  const overlay = document.createElement('div');
  overlay.id = 'kosa-modal-overlay';
  overlay.style.cssText = 'position:fixed;inset:0;z-index:9998;background:rgba(4,4,16,.85);backdrop-filter:blur(8px);display:flex;align-items:flex-end;justify-content:center;padding:0;animation:kosaFadeIn .25s ease';
  overlay.innerHTML = `
    <div id="kosa-modal" style="background:var(--bg-card,#111128);border:1px solid rgba(212,168,83,.2);border-radius:20px 20px 0 0;width:100%;max-width:800px;max-height:88vh;display:flex;flex-direction:column;animation:kosaSlideUp .3s ease;overflow:hidden">
      <div style="display:flex;align-items:center;justify-content:space-between;padding:1rem 1.5rem;border-bottom:1px solid rgba(255,255,255,.06);flex-shrink:0">
        <div>
          <div style="font-size:.68rem;text-transform:uppercase;letter-spacing:.1em;color:rgba(212,168,83,.7);font-weight:700;margin-bottom:.2rem">Kosakata Kata per Kata</div>
          <div style="font-size:.9rem;font-weight:700;color:#f1f5f9">Surah ${sId} : Ayat ${aNum}</div>
        </div>
        <button id="kosa-close" style="background:rgba(255,255,255,.07);border:1px solid rgba(255,255,255,.1);color:#aaa;width:32px;height:32px;border-radius:50%;font-size:1.1rem;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:all .2s">&times;</button>
      </div>
      <div style="padding:1rem 1.5rem;background:rgba(212,168,83,.04);border-bottom:1px solid rgba(255,255,255,.05);flex-shrink:0">
        <div style="font-family:var(--font-quran,'Amiri Quran'),serif;font-size:1.6rem;line-height:2.2;direction:rtl;text-align:right;color:var(--gold-400,#d4a853)">${arabicText}</div>
        <div style="font-size:.82rem;color:rgba(241,245,249,.6);margin-top:.5rem;line-height:1.6;border-left:2px solid rgba(212,168,83,.3);padding-left:.75rem">${indoText}</div>
      </div>
      <div id="kosa-modal-body" style="flex:1;overflow-y:auto;padding:1rem 1.5rem">
        <div id="kosa-loading-state" style="text-align:center;padding:2.5rem;color:rgba(241,245,249,.5)">
          <div style="width:36px;height:36px;border-radius:50%;border:3px solid rgba(212,168,83,.15);border-top-color:#d4a853;animation:spin .8s linear infinite;margin:0 auto 1rem"></div>
          <p style="font-size:.85rem">Memuat kosakata...</p>
        </div>
        <div id="kosa-words-grid" style="display:none;display:none"></div>
      </div>
    </div>
  `;

  // Inject CSS animasi & layout referensi
  if (!document.getElementById('kosa-modal-style')) {
    const st = document.createElement('style');
    st.id = 'kosa-modal-style';
    st.textContent = `
      @keyframes kosaFadeIn { from{opacity:0} to{opacity:1} }
      @keyframes kosaSlideUp { from{transform:translateY(30px);opacity:0} to{transform:translateY(0);opacity:1} }
      .kosa-flow-container {
        display: flex;
        flex-direction: row-reverse;
        flex-wrap: wrap;
        justify-content: flex-start;
        align-items: flex-start;
        gap: 1.25rem 2rem;
        padding: 1.25rem 0.5rem;
        direction: rtl;
      }
      .kosa-word-item {
        display: inline-flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        cursor: pointer;
        padding: 6px 12px;
        border-radius: 10px;
        transition: all .2s;
        min-width: 60px;
      }
      .kosa-word-item:hover {
        background: rgba(212,168,83,.08);
      }
      .kosa-word-item.playing {
        background: rgba(212,168,83,.18);
        box-shadow: 0 0 12px rgba(212,168,83,.3);
      }
      .kosa-word-arabic {
        font-family: var(--font-quran,'Amiri Quran'), serif;
        font-size: 1.9rem;
        line-height: 1.5;
        color: #f8fafc;
        text-align: center;
        direction: rtl;
      }
      .kosa-word-meaning {
        font-size: 0.85rem;
        color: #94a3b8;
        text-align: center;
        margin-top: 5px;
        direction: ltr;
        line-height: 1.3;
      }
    `;
    document.head.appendChild(st);
  }

  document.body.appendChild(overlay);
  document.getElementById('kosa-close').onclick = () => { kosaWordAudio && kosaWordAudio.pause(); overlay.remove(); };
  overlay.addEventListener('click', e => { if (e.target === overlay) { kosaWordAudio && kosaWordAudio.pause(); overlay.remove(); } });

  // Load data kosakata
  const cacheKey = `${sId}:${aNum}`;
  let wordsData = kosaCache[cacheKey];

  if (!wordsData) {
    try {
      const url = `${QCOM_BASE_SURAH}/verses/by_key/${cacheKey}?language=id&words=true&word_fields=text_uthmani,translation,transliteration,word_type,char_type_name,audio_url`;
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      wordsData = json.verse.words.filter(w => w.char_type_name === 'word' || w.word_type === 'word');
      if (!wordsData.length) wordsData = json.verse.words.filter(w => w.char_type_name !== 'end');
      kosaCache[cacheKey] = wordsData;
    } catch (err) {
      // Fallback: split Arabic text
      wordsData = arabicText.split(/\s+/).map((w, i) => ({
        text_uthmani: w, transliteration: null, translation: { text: '' },
        position: i + 1, char_type_name: 'word', audio_url: null
      }));
      kosaCache[cacheKey] = wordsData;
    }
  }

  // Render kartu kata seperti gambar referensi
  const loadEl = document.getElementById('kosa-loading-state');
  const gridEl = document.getElementById('kosa-words-grid');
  if (loadEl) loadEl.style.display = 'none';
  if (gridEl) {
    gridEl.style.display = 'flex';
    gridEl.className = 'kosa-flow-container';

    gridEl.innerHTML = '';
    wordsData.forEach((w, i) => {
      const tr = w.translation ? (w.translation.text || w.translation) : '';
      const audioUrl = w.audio_url ? `https://audio.qurancdn.com/${w.audio_url}` : null;

      const card = document.createElement('div');
      card.className = 'kosa-word-item';
      card.innerHTML = `
        <div class="kosa-word-arabic">${w.text_uthmani || w.text || ''}</div>
        <div class="kosa-word-meaning">${tr || '—'}</div>
      `;

      if (audioUrl) {
        const playWord = (e) => {
          e.stopPropagation();
          if (kosaWordAudio) { kosaWordAudio.pause(); document.querySelectorAll('.kosa-word-item.playing').forEach(c=>c.classList.remove('playing')); }
          kosaWordAudio = new Audio(audioUrl);
          card.classList.add('playing');
          kosaWordAudio.play().catch(()=>{});
          kosaWordAudio.onended = () => card.classList.remove('playing');
          kosaWordAudio.onerror = () => card.classList.remove('playing');
        };
        card.addEventListener('click', playWord);
      }
      gridEl.appendChild(card);
    });
  }
}

/* ══════════════════════════════════════════════════════════
   PER-AYAT AUDIO — MUROJA'AH
   ══════════════════════════════════════════════════════════ */
function getAyatAudioUrl(sNum, aNum) {
  const s = String(sNum).padStart(3, '0');
  const a = String(aNum).padStart(3, '0');
  return `https://everyayah.com/data/${reciterPerAyat().folder}/${s}${a}.mp3`;
}

function setupPerAyatAudio() {
  perAyatAudio.addEventListener('loadedmetadata', () => {
    if (!currentPlayingAyatId) return;
    const arabicEl = document.getElementById(`arabic-${currentPlayingAyatId}`);
    if (!arabicEl) return;
    const words = [...arabicEl.querySelectorAll('.ar-word')].map(s => s.textContent);
    wordTimings = estimateWordTimings(words, perAyatAudio.duration);
    startKaraoke();
  });

  perAyatAudio.addEventListener('ended', () => {
    finishAyatPlayback();
  });

  perAyatAudio.addEventListener('error', (e) => {
    console.warn('Per-ayat audio error', e);
    showToast('⚠️ Gagal memuat audio ayat');
    finishAyatPlayback();
  });
}

function finishAyatPlayback() {
  stopKaraoke();
  if (currentPlayingBtn) {
    setAyatPlayIcon(currentPlayingBtn, false);
    currentPlayingBtn.closest('.ayat-card').classList.remove('playing-card');
  }
  // Mark all words as "read"
  if (currentPlayingAyatId) {
    const arabicEl = document.getElementById(`arabic-${currentPlayingAyatId}`);
    if (arabicEl) {
      arabicEl.querySelectorAll('.ar-word').forEach(s => {
        s.classList.remove('word-current','word-future','word-past');
        s.classList.add('word-done');
      });
    }
  }
  currentPlayingBtn    = null;
  currentPlayingAyatId = null;
  wordTimings          = [];
}

function toggleAyatPlay(btn, ayatId) {
  // Toggle current playing ayat
  if (currentPlayingAyatId === ayatId) {
    if (perAyatAudio.paused) {
      perAyatAudio.play();
      setAyatPlayIcon(btn, true);
      startKaraoke();
    } else {
      perAyatAudio.pause();
      setAyatPlayIcon(btn, false);
      stopKaraoke();
    }
    return;
  }

  // Stop previous ayat
  stopCurrentAyat();

  // Start new ayat
  currentPlayingAyatId = ayatId;
  currentPlayingBtn    = btn;

  const url = getAyatAudioUrl(surahId, ayatId);
  perAyatAudio.src = url;
  perAyatAudio.load();

  perAyatAudio.play()
    .then(() => {
      setAyatPlayIcon(btn, true);
      btn.closest('.ayat-card').classList.add('playing-card');
      btn.closest('.ayat-card').scrollIntoView({ behavior:'smooth', block:'nearest' });
    })
    .catch(err => {
      console.warn('Play failed:', err);
      showToast('⚠️ Gagal memutar audio');
      currentPlayingAyatId = null;
      currentPlayingBtn    = null;
    });
}

function stopCurrentAyat() {
  if (!perAyatAudio.paused) perAyatAudio.pause();
  stopKaraoke();
  if (currentPlayingBtn) {
    setAyatPlayIcon(currentPlayingBtn, false);
    currentPlayingBtn.closest('.ayat-card').classList.remove('playing-card');
  }
  if (currentPlayingAyatId) resetWordColors(currentPlayingAyatId);
  currentPlayingBtn    = null;
  currentPlayingAyatId = null;
  wordTimings          = [];
}

function setAyatPlayIcon(btn, playing) {
  if (!btn) return;
  btn.querySelector('.icon-play').style.display  = playing ? 'none'   : 'inline';
  btn.querySelector('.icon-pause').style.display = playing ? 'inline' : 'none';
  btn.querySelector('.play-label').textContent   = playing ? 'Jeda'   : 'Putar';
  btn.classList.toggle('playing', playing);
}

/* ══════════════════════════════════════════════════════════
   KARAOKE — WORD HIGHLIGHTING
   ══════════════════════════════════════════════════════════ */
function estimateWordTimings(words, duration) {
  // Remove Arabic diacritics for weight calculation
  const diacritics = /[\u064B-\u065F\u0610-\u061A\u0670\u06D6-\u06DC\u06DF-\u06E4\u06E7\u06E8\u06EA-\u06ED]/g;
  const weights = words.map(w => Math.max(w.replace(diacritics,'').length, 1));
  const total   = weights.reduce((a,b) => a+b, 0);

  // Reserve small lead-in (silence at start of audio)
  const leadIn  = Math.min(duration * 0.05, 0.3);
  const spoken  = duration - leadIn;
  const gapRatio = 0.07; // 7% gap between words

  let elapsed = leadIn;
  return words.map((word, i) => {
    const talkPct = (weights[i] / total) * spoken * (1 - gapRatio);
    const gapDur  = (weights[i] / total) * spoken * gapRatio;
    const start   = elapsed;
    elapsed      += talkPct + gapDur;
    return { word, start, end: elapsed };
  });
}

function startKaraoke() {
  stopKaraoke();
  const tick = () => {
    if (!perAyatAudio || perAyatAudio.paused) return;
    updateKaraokeHighlight(perAyatAudio.currentTime);
    karaokeRAF = requestAnimationFrame(tick);
  };
  karaokeRAF = requestAnimationFrame(tick);
}

function stopKaraoke() {
  if (karaokeRAF) { cancelAnimationFrame(karaokeRAF); karaokeRAF = null; }
}

function updateKaraokeHighlight(t) {
  const arabicEl = document.getElementById(`arabic-${currentPlayingAyatId}`);
  if (!arabicEl || !wordTimings.length) return;

  const spans = arabicEl.querySelectorAll('.ar-word');
  let curIdx = wordTimings.length - 1; // default last word after audio finishes
  for (let i = 0; i < wordTimings.length; i++) {
    if (t < wordTimings[i].start) { curIdx = i - 1; break; }
    if (t >= wordTimings[i].start && t < wordTimings[i].end) { curIdx = i; break; }
  }

  spans.forEach((span, i) => {
    span.classList.remove('word-past','word-current','word-future','word-done');
    if (i < curIdx)       span.classList.add('word-past');
    else if (i === curIdx) span.classList.add('word-current');
    else                  span.classList.add('word-future');
  });
}

function resetWordColors(ayatId) {
  const el = document.getElementById(`arabic-${ayatId}`);
  if (!el) return;
  el.querySelectorAll('.ar-word').forEach(s =>
    s.classList.remove('word-past','word-current','word-future','word-done')
  );
}

/* ══════════════════════════════════════════════════════════
   PER-AYAT RECITER SELECTOR
   ══════════════════════════════════════════════════════════ */
function initParReciters() {
  /* Pemilih qari dihapus dari halaman surat — qari tunggal mengikuti Setting. */
}

/* ══════════════════════════════════════════════════════════
   MODE TABS
   ══════════════════════════════════════════════════════════ */
function initModeTabs() {
  document.querySelectorAll('.mode-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const mode = btn.dataset.mode;
      const sid = surahData ? surahData.number : surahId;

      if (mode === 'tulis') {
        window.location.href = `tulis.html?surah=${sid}&ayat=1`;
        return;
      }
      if (mode === 'pelajar') {
        window.location.href = `pelajari.html?surah=${sid}&ayat=1`;
        return;
      }
      if (mode === 'hafal') {
        window.location.href = `hafalan.html?surah=${sid}`;
        return;
      }

      setMode(mode);
    });
  });
}

function setMode(mode) {
  // Hanya mode internal ('baca', 'mushaf', 'dengar') yang diizinkan di halaman surat
  if (mode !== 'mushaf' && mode !== 'dengar') {
    mode = 'baca';
  }
  currentMode = mode;
  sessionStorage.setItem('5dquran_mode', mode);

  // Update active tab
  document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
  const activeBtn = document.getElementById(`mbtn-${mode}`);
  if (activeBtn) activeBtn.classList.add('active');

  // Reset body classes
  document.body.classList.remove('mode-mushaf','mode-dengar');
  audioSection.style.display   = 'none';
  mushafStyleBar.style.display = 'none';

  switch (mode) {
    case 'baca':
      document.querySelectorAll('.ayat-trans').forEach(el =>
        el.classList.toggle('hidden', !showTranslation));
      break;

    case 'mushaf':
      document.body.classList.add('mode-mushaf');
      document.querySelectorAll('.ayat-trans').forEach(el => el.classList.add('hidden'));
      mushafStyleBar.style.display = 'block';
      applyMushafStyle(mushafStyle);
      break;

    case 'dengar':
      audioSection.style.display = 'block';
      document.body.classList.add('mode-dengar');
      document.querySelectorAll('.ayat-trans').forEach(el =>
        el.classList.toggle('hidden', !showTranslation));
      break;
  }
}

/* ══════════════════════════════════════════════════════════
   MUSHAF STYLE BAR (4 fonts + tajweed)
   ══════════════════════════════════════════════════════════ */
function initMushafStyleBar() {
  document.querySelectorAll('.msb-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.msb-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      mushafStyle = btn.dataset.mstyle;
      Auth.simpanSetting('mushaf', mushafStyle);
      applyMushafStyle(mushafStyle);
    });
  });

  // Restore saved style button
  const saved = document.querySelector(`.msb-btn[data-mstyle="${mushafStyle}"]`);
  if (saved) {
    document.querySelectorAll('.msb-btn').forEach(b => b.classList.remove('active'));
    saved.classList.add('active');
  }
}

async function applyMushafStyle(style) {
  document.body.classList.remove('mushaf-madani','mushaf-naskh','mushaf-indopak','mushaf-tajweed');
  document.body.classList.add(`mushaf-${style}`);

  if (tajweedLegend) tajweedLegend.style.display = style === 'tajweed' ? 'flex' : 'none';

  if (style === 'tajweed') {
    await loadAndApplyTajweed();
  } else {
    restoreUthmaniText();
  }
}

async function loadAndApplyTajweed() {
  if (!tajweedData) {
    showToast('⏳ Memuat data tajweed...');
    try {
      const res  = await fetch(`${API_BASE}/surah/${surahId}/quran-tajweed`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      tajweedData = json.data.ayahs;
      showToast('✓ Tajweed dimuat!');
    } catch (e) {
      showToast('⚠️ Gagal memuat data tajweed');
      return;
    }
  }

  tajweedData.forEach(ayah => {
    const el = document.getElementById(`arabic-${ayah.numberInSurah}`);
    if (el) {
      el.innerHTML = processTajweedHtml(ayah.text);
      el.classList.add('tajweed-active');
    }
  });
}

function processTajweedHtml(text) {
  // Replace <tajweed class="x"> with <span class="tj-x"> and </tajweed> with </span>
  return text
    .replace(/<tajweed class="([^"]+)">/g, (_, cls) => `<span class="tj-${cls}">`)
    .replace(/<\/tajweed>/g, '</span>');
}

function restoreUthmaniText() {
  if (!surahData) return;
  surahData.ayahs.forEach(ayah => {
    const el = document.getElementById(`arabic-${ayah.numberInSurah}`);
    if (el) {
      el.classList.remove('tajweed-active');
      const words = ayah.text.split(/\s+/);
      el.innerHTML = words.map((w, i) =>
        `<span class="ar-word" data-idx="${i}">${w}</span>`
      ).join(' ');
    }
  });
}

/* ══════════════════════════════════════════════════════════
   FULL-SURAH AUDIO PLAYER
   ══════════════════════════════════════════════════════════ */
function initFullAudioPlayer() {
  // Pakai qari dari Setting (tidak ada pemilih di halaman ini)
  const rec = RECITERS.find(r => r.id === parReciterId) || RECITERS[0];
  selectReciter(rec);

  audioEl.addEventListener('timeupdate', updateAudioProgress);
  audioEl.addEventListener('loadedmetadata', () => {
    apTotalTime.textContent = formatTime(audioEl.duration);
    apStatus.textContent    = 'Sedang diputar...';
  });
  audioEl.addEventListener('ended', () => {
    setPlayIcon(false); isPlaying = false;
    apStatus.textContent = 'Selesai ✓';
    if (surahId < 114) {
      setTimeout(() => {
        sessionStorage.setItem('5dquran_mode', 'dengar');
        window.location.href = `surah.html?id=${surahId + 1}`;
      }, 2000);
    }
  });
  audioEl.addEventListener('waiting', () => { apStatus.textContent = 'Buffering...'; });
  audioEl.addEventListener('canplay', () => { apStatus.textContent = isPlaying ? 'Sedang diputar...' : 'Siap diputar'; });
  audioEl.addEventListener('error',   () => { apStatus.textContent = '⚠️ Gagal memuat'; setPlayIcon(false); isPlaying = false; });

  apPlayBtn.addEventListener('click', () => {
    if (!activeReciter) { showToast('Pilih qari terlebih dahulu'); return; }
    if (isPlaying) {
      audioEl.pause(); isPlaying = false; setPlayIcon(false); apStatus.textContent = 'Dijeda';
    } else {
      audioEl.play().then(() => { isPlaying = true; setPlayIcon(true); apStatus.textContent = 'Sedang diputar...'; }).catch(() => {});
    }
  });

  apRange.addEventListener('input', () => {
    if (audioEl.duration) audioEl.currentTime = (apRange.value / 100) * audioEl.duration;
  });
  apVolRange.addEventListener('input', () => { audioEl.volume = apVolRange.value / 100; });
  audioEl.volume = 0.8;
}

function selectReciter(reciter) {
  activeReciter = reciter;
  if (!audioEl.paused) audioEl.pause();
  isPlaying = false; setPlayIcon(false);
  const pad = String(surahId).padStart(3,'0');
  audioEl.src = `${reciter.url}${pad}.mp3`;
  apReciterName.textContent = reciter.name;
  apStatus.textContent = 'Siap — tekan Play ▶';
  apCurTime.textContent = '0:00'; apTotalTime.textContent = '0:00';
  apTrackFill.style.width = '0%'; apRange.value = 0;
  audioEl.load();
}

function updateAudioProgress() {
  if (!audioEl.duration) return;
  const pct = (audioEl.currentTime / audioEl.duration) * 100;
  apRange.value = pct; apTrackFill.style.width = pct + '%';
  apCurTime.textContent = formatTime(audioEl.currentTime);
}

function setPlayIcon(playing) {
  apPlayBtn.querySelector('.icon-play').style.display  = playing ? 'none'  : 'block';
  apPlayBtn.querySelector('.icon-pause').style.display = playing ? 'block' : 'none';
}

function formatTime(s) {
  if (isNaN(s) || !isFinite(s)) return '0:00';
  return `${Math.floor(s/60)}:${Math.floor(s%60).toString().padStart(2,'0')}`;
}

/* ══════════════════════════════════════════════════════════
   NAVIGATION
   ══════════════════════════════════════════════════════════ */
function setupNavigation(surah) {
  shNav.style.display = 'flex';
  if (surah.number > 1) {
    btnPrev.href = `surah.html?id=${surah.number-1}`;
    prevName.textContent = `Surat ${surah.number-1}`;
    btnPrev.style.visibility = 'visible';
  } else {
    btnPrev.style.visibility = 'hidden';
  }
  if (surah.number < 114) {
    btnNext.href = `surah.html?id=${surah.number+1}`;
    nextName.textContent = `Surat ${surah.number+1}`;
    btnNext.style.visibility = 'visible';
  } else {
    btnNext.style.visibility = 'hidden';
  }
  apPrevBtn.onclick = () => {
    if (surah.number > 1) { sessionStorage.setItem('5dquran_mode','dengar'); window.location.href=`surah.html?id=${surah.number-1}`; }
  };
  apNextBtn.onclick = () => {
    if (surah.number < 114) { sessionStorage.setItem('5dquran_mode','dengar'); window.location.href=`surah.html?id=${surah.number+1}`; }
  };
}

/* ══════════════════════════════════════════════════════════
   CONTROLS, BOOKMARK, COPY, SHARE
   ══════════════════════════════════════════════════════════ */
function initControls() {
  if (btnBookmark) btnBookmark.addEventListener('click', toggleBookmark);
}

function goTulis(ayatNum) {
  window.location.href = `tulis.html?surah=${surahId}&ayat=${ayatNum}`;
}
function goPelajari(ayatNum) {
  window.location.href = `pelajari.html?surah=${surahId}&ayat=${ayatNum}`;
}

function checkBookmark() {
  isBookmarked = getBookmarks().includes(surahId);
  updateBookmarkUI();
}
function toggleBookmark() {
  let bm = getBookmarks();
  if (isBookmarked) { bm = bm.filter(id=>id!==surahId); isBookmarked=false; showToast('Tandai dihapus'); }
  else              { bm.push(surahId); isBookmarked=true; showToast('✓ Surat ditandai!'); }
  Auth.me.bookmark = bm.map(id => ({ surah: id }));
  Auth.simpanMark('bookmark', { surah: surahId, ayat: 1, hapus: !isBookmarked });
  updateBookmarkUI();
}
function getBookmarks() {
  return ((Auth.me && Auth.me.bookmark) || []).map(b => b.surah);
}
function updateBookmarkUI() {
  btnBookmark.classList.toggle('bookmarked', isBookmarked);
  btnBookmark.title = isBookmarked ? 'Hapus tanda' : 'Tandai surat ini';
}

function copyAyat(btn, arabic, indo) {
  const text = `${arabic}\n\n${indo}`;
  navigator.clipboard.writeText(text).then(() => {
    showToast('✓ Ayat disalin!');
    if (btn) { btn.style.color='var(--green)'; setTimeout(()=>{btn.style.color='';},1500); }
  }).catch(() => {
    const ta=document.createElement('textarea'); ta.value=text;
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta);
    showToast('✓ Ayat disalin!');
  });
}
function shareAyat(arabic, indo, num) {
  const sName = shLatin ? shLatin.textContent : 'Al-Quran';
  const text  = `${sName} : ${num}\n\n${arabic}\n\n${indo}\n\n— 5D Quran`;
  if (navigator.share) navigator.share({ title:`${sName} : ${num}`, text }).catch(()=>{});
  else { copyAyat(null,arabic,indo); showToast('✓ Teks disalin!'); }
}

/* ══════════════════════════════════════════════════════════
   STATE & UTILITIES
   ══════════════════════════════════════════════════════════ */
function showAyatState(state) {
  ayatLoading.style.display = 'none';
  errorState.style.display  = 'none';
  ayatList.style.display    = 'none';
  if      (state==='loading') ayatLoading.style.display = 'flex';
  else if (state==='error')   errorState.style.display  = 'flex';
  else if (state==='list')    ayatList.style.display    = 'block';
}

function showToast(msg) {
  toastEl.textContent = msg; toastEl.classList.add('show');
  setTimeout(()=>toastEl.classList.remove('show'), 2200);
}

function initScrollTop() {
  window.addEventListener('scroll', ()=>{
    scrollTopBtn.style.display = window.scrollY > 400 ? 'flex' : 'none';
  });
  scrollTopBtn.addEventListener('click', ()=> window.scrollTo({top:0, behavior:'smooth'}));
}

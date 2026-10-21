/* ============================================================
   5D Quran — pelajari.js
   Study / Tadabur Page Logic
   4 Tabs: Kosa Kata | Tafsir | Hadis | Tadabur
   ============================================================

   Data Sources:
   1. AlQuran.cloud       — Arabic text, Indonesian translation
   2. Quran.com API v4    — Word-by-word analysis, tafsir Ibn Kathir
   3. eQuran.id API v2    — Tafsir Kemenag (Indonesian)
   4. Curated local data  — Hadis, Tadabur reflection points
   ============================================================ */

const AC_BASE   = 'https://api.alquran.cloud/v1';
const QCOM_BASE = 'https://api.quran.com/api/v4';
const EQ_BASE   = 'https://equran.id/api/v2';

/* ══ STATE ══════════════════════════════════════════════════ */
let surahId   = 1;
let ayatNum   = 1;
let totalAyat = 0;
let surahInfo = null;
let arabicText = '';
let indoText   = '';
let globalAyahNum = 0;

let wordsData  = null;
let tafsirCache = {};          // { key: html }
let activeTafsirKey = 'kemenag';
let activeTab  = 'kosakata';

/* ══ TAFSIR CONFIG ══════════════════════════════════════════ */
const TAFSIRS = {
  kemenag: {
    name: 'Tafsir Kemenag RI',
    nameShort: 'Kemenag',
    lang: 'Bahasa Indonesia',
    icon: '🇮🇩',
    source: 'Kementerian Agama Republik Indonesia',
  },
  jalalayn: {
    name: 'Tafsir Al-Jalalain',
    nameShort: 'Al-Jalalain',
    lang: 'Bahasa Indonesia',
    icon: '📜',
    source: 'Imam Jalaluddin al-Mahalli & Jalaluddin as-Suyuti',
  },
  ibnukatsir: {
    name: 'Tafsir Ibnu Katsir',
    nameShort: 'Ibnu Katsir',
    lang: 'Bahasa Indonesia',
    icon: '📚',
    qcomId: 169,
    source: 'Al-Hafizh Ibnu Katsir (Tafsir Al-Qur\'an Al-\'Azhim)',
  },
};

/* ══ HADIS CURATED DATA ══════════════════════════════════════ */
// Curated for famous ayats; others get external search links
const HADIS_DATA = {
  '1:1': [
    {
      arabic: 'كُلُّ أَمْرٍ ذِي بَالٍ لَا يُبْدَأُ فِيهِ بِبِسْمِ اللَّهِ الرَّحْمَنِ الرَّحِيمِ فَهُوَ أَقْطَعُ',
      indo: 'Setiap perkara penting yang tidak dimulai dengan Bismillahirrahmanirrahim, maka ia terputus (kurang berkahnya).',
      source: 'HR. Ibnu Majah', narrator: 'Abu Hurairah r.a.', grade: 'Hadis Hasan'
    }
  ],
  '1:2': [
    {
      arabic: 'الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ أُمُّ الْقُرْآنِ وَأُمُّ الْكِتَابِ وَالسَّبْعُ الْمَثَانِي',
      indo: 'Alhamdulillahi Rabbil Alamin adalah Ummul Quran (Induk Al-Quran), Ummul Kitab, dan As-Sab\'ul Matsani.',
      source: 'HR. Tirmidzi', narrator: 'Abu Hurairah r.a.', grade: 'Hadis Hasan'
    }
  ],
  '1:5': [
    {
      arabic: 'لَا يَزَالُ الدُّعَاءُ مُسْتَجَابًا مَا لَمْ يَسْتَعْجِلِ الْعَبْدُ',
      indo: 'Doa senantiasa dikabulkan selama hamba tidak terburu-buru (dalam menunggu terkabulnya).',
      source: 'HR. Bukhari & Muslim', narrator: 'Abu Hurairah r.a.', grade: 'Sahih'
    }
  ],
  '2:255': [
    {
      arabic: 'مَنْ قَرَأَ آيَةَ الْكُرْسِيِّ دُبُرَ كُلِّ صَلَاةٍ مَكْتُوبَةٍ لَمْ يَمْنَعْهُ مِنْ دُخُولِ الْجَنَّةِ إِلَّا أَنْ يَمُوتَ',
      indo: 'Barangsiapa membaca Ayat Kursi setelah setiap shalat wajib, tidak ada yang menghalanginya masuk surga kecuali kematian.',
      source: 'HR. An-Nasai', narrator: 'Abu Umamah r.a.', grade: 'Sahih'
    },
    {
      arabic: 'أَعْظَمُ آيَةٍ فِي كِتَابِ اللَّهِ آيَةُ الْكُرْسِيِّ',
      indo: 'Ayat yang paling agung dalam Kitab Allah adalah Ayat Kursi.',
      source: 'HR. Muslim', narrator: 'Ubay bin Ka\'ab r.a.', grade: 'Sahih'
    }
  ],
  '2:286': [
    {
      arabic: 'إِنَّ اللَّهَ لَا يُكَلِّفُ نَفْسًا إِلَّا وُسْعَهَا',
      indo: 'Sesungguhnya Allah tidak membebani seseorang melainkan sesuai dengan kesanggupannya.',
      source: 'HR. Muslim', narrator: 'Ibnu Abbas r.a.', grade: 'Sahih'
    }
  ],
  '3:185': [
    {
      arabic: 'كُلُّ نَفْسٍ ذَائِقَةُ الْمَوْتِ',
      indo: 'Tiap-tiap yang berjiwa akan merasakan mati.',
      source: 'HR. Tirmidzi', narrator: 'Abu Hurairah r.a.', grade: 'Hadis Hasan'
    }
  ],
};

/* ══ ASBABUN NUZUL DATA ══════════════════════════════════════ */
const ASBABUN_NUZUL = {
  '2:6':   'Ayat ini diturunkan berkaitan dengan tokoh-tokoh Yahudi di Madinah yang keras hatinya dan menolak kebenaran Islam meskipun sudah jelas bagi mereka.',
  '2:255': 'Ayat Kursi diturunkan ketika Rasulullah ﷺ sedang bersama para sahabatnya. Ibnu Mas\'ud r.a. berkata bahwa tidak ada langit maupun bumi yang lebih agung dari ayat ini.',
  '2:286': 'Ayat ini diturunkan sebagai penenang bagi kaum Muslimin yang khawatir akan bisikan hati mereka, menunjukkan kasih sayang Allah yang tidak membebani di luar kemampuan hamba-Nya.',
  '3:190': 'Ayat ini turun pada malam hari di rumah Ummu Salamah. Rasulullah ﷺ menangis ketika membacanya, dan bersabda: "Celakalah orang yang membacanya tetapi tidak merenungkannya."',
  '5:3':   'Ayat ini turun pada hari Arafah, pada Haji Wada\'. Merupakan ayat terakhir yang diturunkan terkait hukum-hukum syariat.',
  '9:128': 'Ayat ini diturunkan menunjukkan betapa besar kasih sayang Rasulullah ﷺ kepada umatnya, serta bukti kerahmatan Allah melalui pengutusan Nabi.',
};

/* ══ TADABUR THEMES ══════════════════════════════════════════ */
const TADABUR_THEMES_DB = {
  '1': [
    { icon: '🌟', text: 'Tawakkal kepada Allah' },
    { icon: '🕌', text: 'Ibadah dan Doa' },
    { icon: '🛤️', text: 'Petunjuk Jalan Lurus' },
    { icon: '💎', text: 'Ketulusan Niat' },
  ],
  '2': [
    { icon: '📖', text: 'Petunjuk Bagi Orang Bertakwa' },
    { icon: '🌍', text: 'Tanggung Jawab Manusia' },
    { icon: '⚖️', text: 'Keadilan dan Hukum Allah' },
    { icon: '🤲', text: 'Taubat dan Ampunan' },
  ],
  'default': [
    { icon: '🌟', text: 'Tauhid (Keesaan Allah)' },
    { icon: '🤲', text: 'Ibadah dan Ketaatan' },
    { icon: '💪', text: 'Sabar dan Syukur' },
    { icon: '🧠', text: 'Ilmu dan Hikmah' },
  ],
};

const REFLECTION_QUESTIONS = [
  'Apa pesan utama yang Allah sampaikan melalui ayat ini kepada kita?',
  'Bagaimana ayat ini dapat mengubah cara pandang saya dalam kehidupan sehari-hari?',
  'Nilai-nilai apa yang terkandung dalam ayat ini yang perlu saya terapkan?',
  'Apakah ada kebiasaan buruk yang perlu saya tinggalkan berdasarkan pelajaran dari ayat ini?',
  'Bagaimana saya dapat mengamalkan kandungan ayat ini dalam keluarga dan lingkungan saya?',
];

const AMAL_YAUMIYAH = [
  'Baca ayat ini minimal sekali setiap hari dengan penuh penghayatan',
  'Jadikan kandungan ayat ini sebagai bahan renungan dan doa',
  'Diskusikan makna ayat ini dengan keluarga atau teman',
  'Catat satu pelajaran penting dari ayat ini di buku harian',
  'Ajarkan kepada orang lain agar menjadi amal jariyah',
];

/* ══════════════════════════════════════════════════════════
   INIT
   ══════════════════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  surahId = parseInt(params.get('surah')) || 1;
  ayatNum = parseInt(params.get('ayat'))  || 1;
  if (surahId < 1)   surahId = 1;
  if (surahId > 114) surahId = 114;

  const backBtn = document.getElementById('btn-back-surah');
  if (backBtn) {
    backBtn.href = `surah.html?id=${surahId}`;
    backBtn.addEventListener('click', () => {
      sessionStorage.setItem('5dquran_mode', 'baca');
    });
  }

  loadSurahAndAyat();
  initTabs();
  initTafsirSelector();
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

/* ══════════════════════════════════════════════════════════
   LOAD MAIN DATA
   ══════════════════════════════════════════════════════════ */
async function loadSurahAndAyat() {
  try {
    const res  = await fetch(`${AC_BASE}/surah/${surahId}/editions/quran-uthmani,id.indonesian`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();

    surahInfo  = json.data[0];
    totalAyat  = surahInfo.ayahs.length;

    if (ayatNum < 1)         ayatNum = 1;
    if (ayatNum > totalAyat) ayatNum = totalAyat;

    const arabicAyah = surahInfo.ayahs[ayatNum - 1];
    const indoAyah   = json.data[1].ayahs[ayatNum - 1];

    let rawArabic = arabicAyah.text;
    if (surahId !== 1 && surahId !== 9 && ayatNum === 1) {
      rawArabic = stripBismillahPrefix(rawArabic);
    }
    arabicText    = rawArabic;
    indoText      = indoAyah ? indoAyah.text : '';
    globalAyahNum = arabicAyah.number;

    renderSurahBar();
    renderAyatHero();
    buildPicker();

    // Load first tab
    loadTabContent('kosakata');
  } catch (err) {
    console.error('Load error:', err);
    document.getElementById('ayat-hero-loading').innerHTML =
      '<div class="error-icon">⚠️</div><p>Gagal memuat data. Periksa koneksi.</p>';
  }
}

/* ══════════════════════════════════════════════════════════
   RENDER INFO BAR
   ══════════════════════════════════════════════════════════ */
function renderSurahBar() {
  document.title = `${surahInfo.englishName} : ${ayatNum} — Pelajari — 5D Quran`;
  const revType = surahInfo.revelationType === 'Meccan' ? 'Makkiyah' : 'Madaniyah';

  document.getElementById('pli-surah').innerHTML = `
    <span class="pli-number">${surahInfo.number}</span>
    <span class="pli-arabic">${surahInfo.name}</span>
    <span class="pli-name">${surahInfo.englishName}</span>
    <span class="pli-type">${revType}</span>
  `;

  const nav = document.getElementById('pli-nav');
  nav.style.display = 'flex';
  document.getElementById('pli-ayat-cur').textContent   = ayatNum;
  document.getElementById('pli-ayat-total').textContent = totalAyat;

  const prevBtn = document.getElementById('pli-prev');
  const nextBtn = document.getElementById('pli-next');
  prevBtn.disabled = ayatNum <= 1;
  nextBtn.disabled = ayatNum >= totalAyat;
  prevBtn.onclick = () => navigateAyat(ayatNum - 1);
  nextBtn.onclick = () => navigateAyat(ayatNum + 1);
}

function buildPicker() {
  const picker = document.getElementById('pli-picker');
  picker.innerHTML = '';
  for (let i = 1; i <= totalAyat; i++) {
    const opt = document.createElement('option');
    opt.value = i;
    opt.textContent = `Ayat ${i}`;
    opt.style.backgroundColor = '#16162a';
    opt.style.color = '#f1f5f9';
    if (i === ayatNum) opt.selected = true;
    picker.appendChild(opt);
  }
  picker.addEventListener('change', () => navigateAyat(parseInt(picker.value)));
}

/* ══════════════════════════════════════════════════════════
   RENDER AYAT HERO
   ══════════════════════════════════════════════════════════ */
function renderAyatHero() {
  const sName = surahInfo.englishName;

  document.getElementById('ahc-arabic').textContent   = arabicText;
  document.getElementById('ahc-badge').textContent    = `${sName} : ${ayatNum}`;
  document.getElementById('ahc-translation').textContent = indoText;
  document.getElementById('ahc-ref').textContent =
    `Q.S. ${surahInfo.name} (${surahInfo.number}) : ${ayatNum}`;

  document.getElementById('ahc-tulis').href =
    `tulis.html?surah=${surahId}&ayat=${ayatNum}`;

  document.getElementById('ahc-copy').onclick = () => {
    const txt = `${arabicText}\n\n${indoText}\n\n— Q.S. ${surahInfo.englishName}: ${ayatNum}`;
    navigator.clipboard.writeText(txt).then(() => showToast('✓ Ayat disalin!')).catch(() => {});
  };

  document.getElementById('ayat-hero-loading').style.display  = 'none';
  document.getElementById('ayat-hero-content').style.display  = 'block';
}

/* ══════════════════════════════════════════════════════════
   NAVIGATION
   ══════════════════════════════════════════════════════════ */
function navigateAyat(num) {
  if (num < 1 || num > totalAyat) return;
  const url = `pelajari.html?surah=${surahId}&ayat=${num}`;
  window.location.href = url;
}

/* ══════════════════════════════════════════════════════════
   TABS
   ══════════════════════════════════════════════════════════ */
function initTabs() {
  document.querySelectorAll('.stab').forEach(btn => {
    btn.addEventListener('click', () => {
      const tab = btn.dataset.tab;
      switchTab(tab);
    });
  });
}

function switchTab(tab) {
  activeTab = tab;
  document.querySelectorAll('.stab').forEach(b => b.classList.remove('active'));
  document.getElementById(`stab-${tab}`).classList.add('active');
  document.querySelectorAll('.study-panel').forEach(p => p.style.display = 'none');
  document.getElementById(`panel-${tab}`).style.display = 'block';
  document.getElementById(`panel-${tab}`).classList.add('active');
  loadTabContent(tab);
}

function loadTabContent(tab) {
  switch (tab) {
    case 'kosakata': loadKosaKata(); break;
    case 'tafsir':   loadTafsir(activeTafsirKey); break;
    case 'hadis':    renderHadis(); break;
    case 'tadabur':  renderTadabur(); break;
  }
}

/* ══════════════════════════════════════════════════════════
   TAB 1 — KOSA KATA (Word by Word)
   ══════════════════════════════════════════════════════════ */
async function loadKosaKata() {
  if (wordsData) { renderKosaKata(wordsData); return; }

  showKosaState('loading');
  try {
    // Quran.com API: language=id gives Indonesian word-by-word translations!
    const key = `${surahId}:${ayatNum}`;
    const url = `${QCOM_BASE}/verses/by_key/${key}?language=id&words=true&word_fields=text_uthmani,translation,transliteration,word_type,char_type_name`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();

    wordsData = json.verse.words.filter(w => w.char_type_name === 'word' || w.word_type === 'word');
    if (!wordsData.length) {
      wordsData = json.verse.words.filter(w => w.char_type_name !== 'end');
    }
    renderKosaKata(wordsData);
  } catch (err) {
    console.warn('Quran.com words API failed, using fallback:', err);
    // Fallback: split Arabic text into words manually
    const words = arabicText.split(/\s+/).map((w, i) => ({
      text_uthmani: w, transliteration: null, translation: { text: '' },
      position: i + 1, char_type_name: 'word'
    }));
    wordsData = words;
    renderKosaKata(wordsData);
  }
}

let currentWordAudio = null;
function playWordAudio(url, cardEl) {
  if (currentWordAudio) {
    currentWordAudio.pause();
    document.querySelectorAll('.word-card.playing').forEach(c => c.classList.remove('playing'));
  }
  const audio = new Audio(url);
  currentWordAudio = audio;
  cardEl.classList.add('playing');
  audio.play().catch(e => console.warn('Word audio error:', e));
  audio.onended = () => cardEl.classList.remove('playing');
  audio.onerror = () => cardEl.classList.remove('playing');
}

function renderKosaKata(words) {
  showKosaState('content');
  const container = document.getElementById('kosa-content');

  const wordTypeLabel = (w) => {
    const t = (w.char_type_name || '').toLowerCase();
    if (t.includes('verb'))     return { cls: 'verb',     label: "Fi'il (Kata Kerja)" };
    if (t.includes('noun'))     return { cls: 'noun',     label: 'Isim (Kata Benda)' };
    if (t.includes('particle') || t.includes('harf')) return { cls: 'particle', label: 'Harf (Partikel)' };
    return { cls: 'other', label: 'Kosakata' };
  };

  container.innerHTML = `
    <div class="kosa-intro">
      💡 <strong>Kosakata Kata per Kata (Bahasa Indonesia)</strong> — Pelajari makna setiap kata dalam ayat ini secara mendalam.
      Klik kartu atau ikon speaker untuk mendengarkan lafal suara masing-masing kata.
    </div>
    <div class="word-grid" id="word-grid"></div>
    <div class="study-card" style="margin-top:1.25rem">
      <div class="study-card-header">
        <span class="study-card-icon">🔤</span>
        <span class="study-card-title">Teks Lengkap & Terjemahan Ayat</span>
      </div>
      <div class="study-card-body">
        <div style="font-family:var(--font-quran);font-size:2rem;line-height:2.5;direction:rtl;text-align:right;color:var(--gold-400)">
          ${arabicText}
        </div>
        <div style="font-size:0.95rem;color:var(--text-secondary);margin-top:1rem;line-height:1.8;border-left:3px solid var(--border-gold);padding-left:1rem">
          ${indoText}
        </div>
      </div>
    </div>
  `;

  const grid = container.querySelector('#word-grid');
  words.forEach((w, i) => {
    const tl = w.transliteration ? w.transliteration.text : '';
    const tr = w.translation     ? w.translation.text     : '';
    const { cls, label } = wordTypeLabel(w);
    const audioUrl = w.audio_url ? `https://audio.qurancdn.com/${w.audio_url}` : null;

    const card = document.createElement('div');
    card.className = `word-card type-${cls}${audioUrl ? ' has-audio' : ''}`;
    card.title = audioUrl ? 'Klik untuk dengarkan audio kata ini' : '';
    card.innerHTML = `
      <div class="wc-header">
        <span class="wc-num">${i + 1}</span>
        ${audioUrl ? `
          <button class="wc-audio-btn" title="Dengarkan kata ini" type="button">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>
          </button>` : ''}
      </div>
      <div class="wc-arabic">${w.text_uthmani || w.text}</div>
      ${tl ? `<div class="wc-latin">${tl}</div>` : ''}
      <div class="wc-meaning">${tr || '—'}</div>
      <span class="wc-type ${cls}">${label}</span>
    `;

    if (audioUrl) {
      card.addEventListener('click', (e) => {
        playWordAudio(audioUrl, card);
      });
    }

    grid.appendChild(card);
  });
}

function showKosaState(state) {
  document.getElementById('kosa-loading').style.display = state === 'loading' ? 'flex' : 'none';
  document.getElementById('kosa-error').style.display   = state === 'error'   ? 'flex' : 'none';
  document.getElementById('kosa-content').style.display = state === 'content' ? 'block': 'none';
}

/* ══════════════════════════════════════════════════════════
   TAB 2 — TAFSIR
   ══════════════════════════════════════════════════════════ */
function initTafsirSelector() {
  document.querySelectorAll('.tsb-btn[data-tsid]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.tsb-btn[data-tsid]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      activeTafsirKey = btn.dataset.tsid;
      if (activeTab === 'tafsir') loadTafsir(activeTafsirKey);
    });
  });

  const dlBtn = document.getElementById('btn-download-tafsir');
  if (dlBtn) {
    dlBtn.addEventListener('click', async () => {
      const type = (activeTafsirKey === 'jalalayn') ? 'jalalayn' : 'ibnukatsir';
      const label = (type === 'jalalayn') ? 'Tafsir Al-Jalalain' : 'Tafsir Ibnu Katsir';
      dlBtn.innerHTML = '<span>⏳ Mengunduh...</span>';
      try {
        const res = await fetch(`data/tafsir/${type}/${surahId}.json`);
        if (!res.ok) throw new Error('File tidak ditemukan');
        const data = await res.json();
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `Tafsir_${type.toUpperCase()}_Surah_${surahId}.json`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        dlBtn.innerHTML = '<span>✅ Selesai!</span>';
        setTimeout(() => { dlBtn.innerHTML = '<span>⬇️ Unduh Tafsir</span>'; }, 2000);
      } catch (err) {
        dlBtn.innerHTML = '<span>⚠️ Gagal</span>';
        setTimeout(() => { dlBtn.innerHTML = '<span>⬇️ Unduh Tafsir</span>'; }, 2000);
      }
    });
  }
}

async function loadTafsir(key) {
  if (tafsirCache[key]) { renderTafsirContent(key, tafsirCache[key]); return; }

  const cfg = TAFSIRS[key];
  if (!cfg) return;

  document.getElementById('tafsir-loading').style.display = 'flex';
  document.getElementById('tafsir-content').innerHTML = '';

  try {
    let html = '';

    if (key === 'kemenag') {
      html = await fetchKemenagTafsir();
    } else if (key === 'jalalayn') {
      html = await fetchJalalainTafsir();
    } else if (key === 'ibnukatsir') {
      html = await fetchIbnuKatsirTafsir(cfg);
    }

    tafsirCache[key] = html;
    renderTafsirContent(key, html);
  } catch (err) {
    console.warn(`Tafsir ${key} fetch failed:`, err);
    tafsirCache[key] = `<p style="color:var(--text-muted)">⚠️ Gagal memuat tafsir ini. Silakan periksa koneksi internet Anda.</p>`;
    renderTafsirContent(key, tafsirCache[key]);
  } finally {
    document.getElementById('tafsir-loading').style.display = 'none';
  }
}

async function fetchKemenagTafsir() {
  // eQuran.id API for full-surah Kemenag tafsir, filter to ayatNum
  const res  = await fetch(`${EQ_BASE}/tafsir/${surahId}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();

  const tafsirArr = json.data.tafsir;
  const entry     = tafsirArr.find(t => t.ayat === ayatNum);
  if (!entry) return `<p style="color:var(--text-muted)">Tafsir ayat ini tidak tersedia.</p>`;

  return `
    <div class="tafsir-lang-badge">🇮🇩 Tafsir Lengkap Kemenag RI</div>
    <div class="tafsir-body">${formatTafsirText(entry.teks)}</div>
  `;
}

async function fetchJalalainTafsir() {
  // 1. Try local Indonesian per-surah JSON (instant & offline)
  try {
    const res = await fetch(`data/tafsir/jalalayn/${surahId}.json`);
    if (res.ok) {
      const data = await res.json();
      const txt = data[String(ayatNum)];
      if (txt) {
        return `
          <div class="tafsir-lang-badge">📜 Tafsir Al-Jalalain (Bahasa Indonesia) — Imam al-Mahalli & as-Suyuti</div>
          <div class="tafsir-body">${formatTafsirText(txt)}</div>
        `;
      }
    }
  } catch (e) {
    console.warn('Local jalalayn fetch failed, trying fallback...', e);
  }

  // 2. Fallback to GitHub raw Indonesian dataset
  try {
    const res = await fetch('https://raw.githubusercontent.com/renomureza/quran-api-id/main/src/data/tmp/tafsir-jalalayn.json');
    if (res.ok) {
      const data = await res.json();
      const key = `${surahId}.${ayatNum}`;
      if (data[key]) {
        return `
          <div class="tafsir-lang-badge">📜 Tafsir Al-Jalalain (Bahasa Indonesia) — Imam al-Mahalli & as-Suyuti</div>
          <div class="tafsir-body">${formatTafsirText(data[key])}</div>
        `;
      }
    }
  } catch (e) {
    console.warn('GitHub raw jalalayn fetch failed:', e);
  }

  // 3. Fallback to AlQuran.cloud authentic Arabic text
  try {
    const res = await fetch(`${AC_BASE}/ayah/${surahId}:${ayatNum}/ar.jalalayn`);
    if (res.ok) {
      const json = await res.json();
      if (json.data && json.data.text) {
        return `
          <div class="tafsir-lang-badge">📜 Tafsir Al-Jalalain (Teks Bahasa Arab)</div>
          <div class="tafsir-body" style="font-family:var(--font-quran);font-size:1.35rem;line-height:2.2;direction:rtl;text-align:right">
            ${json.data.text}
          </div>
        `;
      }
    }
  } catch (e) {}

  return `<p style="color:var(--text-muted)">Tafsir Al-Jalalain tidak tersedia untuk ayat ini.</p>`;
}

async function fetchIbnuKatsirTafsir(cfg) {
  const key = `${surahId}:${ayatNum}`;

  // 1. Try local Indonesian per-surah JSON dataset (instant & offline)
  try {
    const res = await fetch(`data/tafsir/ibnukatsir/${surahId}.json`);
    if (res.ok) {
      const data = await res.json();
      const txt = data[String(ayatNum)];
      if (txt) {
        return `
          <div class="tafsir-lang-badge">🇮🇩 Tafsir Ibnu Katsir (Bahasa Indonesia) — Kitab Tafsir Al-Qur'an Al-'Azhim</div>
          <div class="tafsir-body">${txt}</div>
        `;
      }
    }
  } catch (e) {
    console.warn('Local ibnukatsir fetch failed:', e);
  }

  // 2. Check localStorage cache for previously translated Indonesian text
  const cachedIndo = localStorage.getItem(`ik_id_${key}`);
  if (cachedIndo) {
    return `
      <div class="tafsir-lang-badge" style="display:flex;align-items:center;justify-content:space-between">
        <span>🇮🇩 Tafsir Ibnu Katsir (Bahasa Indonesia)</span>
        <button type="button" class="btn-sm" style="font-size:0.75rem;padding:0.2rem 0.6rem;background:rgba(255,255,255,0.08);border:1px solid var(--border);border-radius:4px;cursor:pointer;color:var(--text-secondary)" onclick="toggleIbnuKatsirLang(this)">Lihat Teks Inggris</button>
      </div>
      <div class="tafsir-body tafsir-body-indo">${cachedIndo}</div>
      <div class="tafsir-body tafsir-body-en" style="display:none" id="ik-en-${key}"></div>
    `;
  }

  // 3. Fetch from Quran.com API v4 (resource 169 - English Translation of Ibn Kathir)
  const res  = await fetch(`${QCOM_BASE}/tafsirs/169/by_ayah/${key}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();

  const rawText = (json.tafsir && json.tafsir.text) || (json.data && (json.data.text || json.data.body)) || '';
  if (!rawText) return `<p style="color:var(--text-muted)">Tafsir Ibnu Katsir tidak tersedia untuk ayat ini.</p>`;

  // 4. Translate to Bahasa Indonesia
  try {
    const translatedHtml = await translateTafsirToIndonesian(rawText);
    if (translatedHtml) {
      try { localStorage.setItem(`ik_id_${key}`, translatedHtml); } catch (e) {}
      return `
        <div class="tafsir-lang-badge" style="display:flex;align-items:center;justify-content:space-between">
          <span>🇮🇩 Tafsir Ibnu Katsir (Bahasa Indonesia)</span>
          <button type="button" class="btn-sm" style="font-size:0.75rem;padding:0.2rem 0.6rem;background:rgba(255,255,255,0.08);border:1px solid var(--border);border-radius:4px;cursor:pointer;color:var(--text-secondary)" onclick="toggleIbnuKatsirLang(this)">Lihat Teks Inggris</button>
        </div>
        <div class="tafsir-body tafsir-body-indo">${translatedHtml}</div>
        <div class="tafsir-body tafsir-body-en" style="display:none">${rawText}</div>
      `;
    }
  } catch (err) {
    console.warn('Auto-translation failed:', err);
  }

  // Clean up HTML tags if translation failed
  const cleaned = rawText
    .replace(/<h\d[^>]*>/gi, '<h3>')
    .replace(/<\/h\d>/gi, '</h3>');

  return `
    <div class="tafsir-lang-badge">${cfg.icon} Tafsir Ibnu Katsir (Kitab Tafsir Al-Qur'an Al-Azhim) — ${cfg.source}</div>
    <div class="tafsir-body">${cleaned}</div>
  `;
}

/* ── Translation helper for Tafsir Ibnu Katsir ── */
async function translateTafsirToIndonesian(rawHtml) {
  let cleaned = rawHtml.replace(/<script[^>]*>.*?<\/script>/gi, '');
  const parser = new DOMParser();
  const doc = parser.parseFromString(cleaned, 'text/html');
  const elements = Array.from(doc.body.querySelectorAll('p, h1, h2, h3, h4, blockquote, li'));

  let paragraphs = [];
  if (elements.length > 0) {
    elements.forEach(el => {
      if (el.querySelector('p, h1, h2, h3, h4, blockquote, li')) return;
      const t = el.innerText.trim();
      if (t) paragraphs.push(t);
    });
  } else {
    paragraphs = doc.body.innerText.split(/\n\n+/).map(s => s.trim()).filter(Boolean);
  }

  if (!paragraphs.length) return null;

  const translatedBlocks = [];
  let chunk = [];
  let chunkLen = 0;

  for (const p of paragraphs) {
    const arabicMatch = p.match(/[\u0600-\u06FF]/g);
    if (arabicMatch && (arabicMatch.length / p.length) > 0.4) {
      if (chunk.length > 0) {
        const tr = await translateTextChunk(chunk.join('\n\n'));
        translatedBlocks.push(tr);
        chunk = [];
        chunkLen = 0;
      }
      translatedBlocks.push(`<p style="font-family:var(--font-quran);font-size:1.25rem;direction:rtl;text-align:right;color:var(--gold-400)">${p}</p>`);
      continue;
    }

    if (chunkLen + p.length > 1100) {
      const tr = await translateTextChunk(chunk.join('\n\n'));
      translatedBlocks.push(tr);
      chunk = [p];
      chunkLen = p.length;
    } else {
      chunk.push(p);
      chunkLen += p.length + 2;
    }
  }

  if (chunk.length > 0) {
    const tr = await translateTextChunk(chunk.join('\n\n'));
    translatedBlocks.push(tr);
  }

  let outHtml = '';
  for (const block of translatedBlocks) {
    if (block.startsWith('<p style=')) {
      outHtml += block;
    } else {
      const parts = block.split(/\n\n+/);
      for (const pt of parts) {
        const trimmed = pt.trim();
        if (!trimmed) continue;
        if (trimmed.length < 60 && !trimmed.endsWith('.')) {
          outHtml += `<h3>${trimmed}</h3>`;
        } else {
          outHtml += `<p>${trimmed}</p>`;
        }
      }
    }
  }

  return outHtml;
}

async function translateTextChunk(text) {
  if (!text.trim()) return '';
  try {
    const q = encodeURIComponent(text);
    const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=id&dt=t&q=${q}`;
    const res = await fetch(url);
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data) && Array.isArray(data[0])) {
        return data[0].map(item => item[0] || '').join('');
      }
    }
  } catch (e) {
    console.warn('gtx translation failed, trying fallback...', e);
  }

  try {
    const q = encodeURIComponent(text.substring(0, 480));
    const url = `https://api.mymemory.translated.net/get?q=${q}&langpair=en|id`;
    const res = await fetch(url);
    if (res.ok) {
      const data = await res.json();
      if (data.responseData && data.responseData.translatedText) {
        return data.responseData.translatedText;
      }
    }
  } catch (e) {}

  return text;
}

window.toggleIbnuKatsirLang = function(btn) {
  const card = btn.closest('.study-card') || document;
  const indoBody = card.querySelector('.tafsir-body-indo');
  const enBody = card.querySelector('.tafsir-body-en');
  if (!indoBody || !enBody) return;
  if (indoBody.style.display === 'none') {
    indoBody.style.display = 'block';
    enBody.style.display = 'none';
    btn.textContent = 'Lihat Teks Inggris';
  } else {
    indoBody.style.display = 'none';
    enBody.style.display = 'block';
    btn.textContent = 'Lihat Bahasa Indonesia';
  }
};

function formatTafsirText(text) {
  if (!text) return '';
  return text
    .split(/\n\n+/)
    .map(p => `<p>${p.trim().replace(/\n/g, '<br>')}</p>`)
    .join('');
}

function renderTafsirContent(key, html) {
  const cfg = TAFSIRS[key];
  document.getElementById('tafsir-loading').style.display = 'none';
  document.getElementById('tafsir-content').innerHTML = `
    <div class="study-card">
      <div class="study-card-header">
        <span class="study-card-icon">${cfg.icon}</span>
        <span class="study-card-title">${cfg.name}</span>
        <span class="study-card-subtitle">${cfg.source}</span>
      </div>
      <div class="study-card-body">${html}</div>
    </div>
    <div style="font-size:0.75rem;color:var(--text-muted);margin-top:0.5rem;text-align:center">
      Sumber: ${cfg.source} • Bahasa: ${cfg.lang}
    </div>
  `;
}

/* ══════════════════════════════════════════════════════════
   TAB 3 — HADIS (Authentic Database & Downloaded Hadith)
   ══════════════════════════════════════════════════════════ */
let hadisDatabase = null;

async function getHadisDatabase() {
  if (hadisDatabase) return hadisDatabase;
  try {
    const res = await fetch('data/hadis/hadis-ayat.json');
    if (res.ok) {
      hadisDatabase = await res.json();
      return hadisDatabase;
    }
  } catch (e) {
    console.warn('Failed to load local hadis-ayat.json:', e);
  }
  return null;
}

async function renderHadis() {
  const container = document.getElementById('hadis-content');
  if (!container) return;

  const key     = `${surahId}:${ayatNum}`;
  const asbabun = ASBABUN_NUZUL[key] || null;

  // Show temporary loading indicator if database hasn't loaded yet
  if (!hadisDatabase) {
    container.innerHTML = `
      <div class="study-loading" style="display:flex">
        <div class="study-spinner"></div>
        <span>Memuat data hadis autentik...</span>
      </div>
    `;
  }

  const db = await getHadisDatabase();

  // Find relevant hadiths — per-ayat specific only, no global fallback
  let specificHadiths = [];
  if (db && db.hadis_by_ayat && db.hadis_by_ayat[key]) {
    specificHadiths = db.hadis_by_ayat[key];
  } else if (HADIS_DATA[key]) {
    specificHadiths = HADIS_DATA[key];
  }

  // Surah-level authentic hadiths
  let surahHadith = null;
  if (db && db.hadis_by_surah && db.hadis_by_surah[String(surahId)]) {
    surahHadith = db.hadis_by_surah[String(surahId)];
  }

  // General Quran virtue hadiths
  const generalHadiths = (db && db.general_hadis) || [];

  let html = '';

  // 1. Asbabun Nuzul (if any)
  if (asbabun) {
    html += `
      <div class="asbabun-box">
        <div class="asbabun-title">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          Asbabun Nuzul (Sebab Turunnya Ayat)
        </div>
        <div class="asbabun-text">${asbabun}</div>
      </div>
    `;
  }

  // 2. Specific Hadiths for this Ayat
  if (specificHadiths.length > 0) {
    html += `
      <div class="study-card">
        <div class="study-card-header">
          <span class="study-card-icon">📜</span>
          <span class="study-card-title">Hadis Terkait Ayat Ini</span>
          <span class="study-card-subtitle">${specificHadiths.length} hadis shahih/hasan</span>
        </div>
        <div style="padding:0">
          ${specificHadiths.map((h, i) => `
            <div class="hadis-card" style="border:none;border-bottom:${i < specificHadiths.length-1 ? '1px solid var(--border)' : 'none'};margin:0;border-radius:0">
              <div class="hadis-arabic">${h.arabic}</div>
              <div class="hadis-indo">${h.indo}</div>
              <div class="hadis-meta">
                <span class="hadis-source">${h.source}</span>
                <span class="hadis-narrator">dari ${h.narrator}</span>
                ${h.grade ? `<span style="font-size:0.7rem;color:var(--gold-400);margin-left:auto;font-weight:600">${h.grade}</span>` : ''}
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    `;
  }

  // 3. Surah-level Hadith (if available)
  if (surahHadith) {
    html += `
      <div class="study-card" style="${specificHadiths.length > 0 ? 'margin-top:1rem;' : ''}">
        <div class="study-card-header">
          <span class="study-card-icon">⭐</span>
          <span class="study-card-title">${surahHadith.title || 'Keutamaan Surah Ini'}</span>
          <span class="study-card-subtitle">Riwayat Hadis</span>
        </div>
        <div style="padding:0">
          <div class="hadis-card" style="border:none;margin:0;border-radius:0">
            <div class="hadis-arabic">${surahHadith.arabic}</div>
            <div class="hadis-indo">${surahHadith.indo}</div>
            <div class="hadis-meta">
              <span class="hadis-source">${surahHadith.source}</span>
              <span class="hadis-narrator">dari ${surahHadith.narrator}</span>
              ${surahHadith.grade ? `<span style="font-size:0.7rem;color:var(--gold-400);margin-left:auto;font-weight:600">${surahHadith.grade}</span>` : ''}
            </div>
          </div>
        </div>
      </div>
    `;
  }

  // 4. If no specific ayat hadith — show dignified "in curation" notice (NOT global fallback)
  if (specificHadiths.length === 0 && !asbabun) {
    const surahName = surahInfo ? (surahInfo.name + ' (' + surahInfo.englishName + ')') : 'surah ini';
    html += `
      <div class="study-card" style="${surahHadith ? 'margin-top:1rem;' : ''}">
        <div class="study-card-header">
          <span class="study-card-icon">🔬</span>
          <span class="study-card-title">Hadis Sedang Dikurasi</span>
        </div>
        <div class="study-card-body" style="line-height:1.9">
          <p style="font-size:0.9rem;color:var(--text-secondary)">
            Hadis yang secara khusus berkaitan dengan <strong>ayat ${ayatNum}</strong> dari ${surahName} sedang dalam proses kurasi dan verifikasi sanad oleh tim editorial.
          </p>
          <p style="font-size:0.85rem;color:var(--text-muted);margin-top:0.75rem">
            Gunakan tautan di bawah untuk mencari hadis berdasarkan tema ayat ini secara mandiri.
          </p>
        </div>
      </div>
    `;
  }

  // 5. Reference links for in-depth research
  const surahEnName = surahInfo ? surahInfo.englishName : 'Al-Quran';
  const searchQ   = encodeURIComponent(`surah ${surahEnName} ayat ${ayatNum}`);
  html += `
    <div class="hadis-search-section">
      <div class="hadis-search-title">🔍 Telusuri Hadis Secara Mendalam</div>
      <div class="hadis-search-text">
        Cari takhrij sanad, syarah, dan derajat hadis terkait ayat ini:
      </div>
      <div class="hadis-search-links">
        <a class="hadis-search-link" href="https://dorar.net/hadith/search?q=${searchQ}" target="_blank" rel="noopener">
          🌐 Dorar.net
        </a>
        <a class="hadis-search-link" href="https://sunnah.com/?q=${searchQ}" target="_blank" rel="noopener">
          📖 Sunnah.com
        </a>
        <a class="hadis-search-link" href="https://shamela.ws" target="_blank" rel="noopener">
          📚 Syamilah
        </a>
      </div>
    </div>
  `;

  container.innerHTML = html;
}

/* ══════════════════════════════════════════════════════════
   TAB 4 — TADABUR
   ══════════════════════════════════════════════════════════ */
function renderTadabur() {
  const container = document.getElementById('tadabur-content');
  const themes    = TADABUR_THEMES_DB[String(surahId)] || TADABUR_THEMES_DB['default'];
  const surahName = surahInfo ? surahInfo.englishName : 'Al-Quran';

  // Generate reflection questions contextualised to the ayat
  const questions = [
    `Apa yang Allah ingin sampaikan kepadamu melalui Q.S. ${surahName} ayat ${ayatNum} ini?`,
    `Bagaimana kandungan ayat ini dapat mengubah sikap dan perilakumu sehari-hari?`,
    `Nilai moral dan spiritual apa yang paling menonjol dalam ayat ini?`,
    `Apakah ada bagian dari hidupmu yang perlu diperbaiki berdasarkan ayat ini?`,
    `Bagaimana kamu dapat berbagi kandungan ayat ini kepada orang di sekitarmu?`,
  ];

  // Random 2 amal yaumiyah
  const amal = [...AMAL_YAUMIYAH].sort(() => Math.random() - 0.5).slice(0, 3);

  const html = `
    <div class="tadabur-header">
      <div class="tadabur-header-icon">🤲</div>
      <h2>Tadabur Q.S. ${surahName} : ${ayatNum}</h2>
      <p>
        "Apakah mereka tidak mentadaburi (merenungkan) Al-Quran? Sekiranya Al-Quran itu bukan dari Allah,
        niscaya mereka menemukan pertentangan yang banyak di dalamnya." — Q.S. An-Nisa' : 82
      </p>
    </div>

    <!-- Ayat Display -->
    <div class="study-card" style="margin-bottom:1.5rem">
      <div class="study-card-header">
        <span class="study-card-icon">✨</span>
        <span class="study-card-title">Ayat yang Ditadaburi</span>
      </div>
      <div class="study-card-body">
        <div style="font-family:var(--font-quran);font-size:2rem;line-height:2.4;direction:rtl;text-align:right;color:var(--gold-400);margin-bottom:1rem">
          ${arabicText}
        </div>
        <div style="font-size:0.95rem;line-height:1.8;color:var(--text-secondary);font-style:italic;border-left:3px solid var(--border-gold);padding-left:1rem">
          "${indoText}"
        </div>
      </div>
    </div>

    <!-- Tema Utama -->
    <div class="study-card" style="margin-bottom:1.25rem">
      <div class="study-card-header">
        <span class="study-card-icon">🌟</span>
        <span class="study-card-title">Tema & Nilai Utama</span>
      </div>
      <div class="study-card-body">
        <div class="themes-grid" style="margin-bottom:0">
          ${themes.map(t => `
            <div class="theme-chip">
              <span class="theme-chip-icon">${t.icon}</span>
              ${t.text}
            </div>
          `).join('')}
        </div>
      </div>
    </div>

    <!-- Pertanyaan Renungan -->
    <div class="reflection-section">
      <div class="reflection-title">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        Pertanyaan Renungan
      </div>
      <div class="reflection-list">
        ${questions.map((q, i) => `
          <div class="reflection-item">
            <div class="ri-num">${i + 1}</div>
            <div class="ri-text">${q}</div>
          </div>
        `).join('')}
      </div>
    </div>

    <!-- Amal Yaumiyah -->
    <div class="amal-section">
      <div class="amal-title">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
        Amal Harian dari Ayat Ini
      </div>
      <ul class="amal-list">
        ${amal.map(a => `<li>${a}</li>`).join('')}
      </ul>
    </div>

    <!-- Doa Penutup -->
    <div class="dua-card">
      <div class="dua-arabic">اللَّهُمَّ أَرِنَا الْحَقَّ حَقًّا وَارْزُقْنَا اتِّبَاعَهُ</div>
      <div class="dua-latin">Allāhumma arinā al-ḥaqqa ḥaqqan warzuqnā ittibā'ah</div>
      <div class="dua-meaning">
        "Ya Allah, tunjukkanlah kepada kami kebenaran sebagai kebenaran dan anugerahkanlah kami kemampuan untuk mengikutinya."
      </div>
    </div>
  `;

  container.innerHTML = html;
}

/* ══════════════════════════════════════════════════════════
   UTILS
   ══════════════════════════════════════════════════════════ */
function showToast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg; el.classList.add('show');
  setTimeout(() => el.classList.remove('show'), 2200);
}

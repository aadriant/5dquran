/* ══════════════════════════════════════════════════════════
   AUTH + PROFIL — Google Sign-In, gate halaman, simpan
   setting ke server (per profil), bukan localStorage.
   ══════════════════════════════════════════════════════════ */
const Auth = {
  me: null,          // {profil, setting, favorit, terakhir, bookmark}
  googleClientId: '',
  SIAP: false,

  async init() {
    try {
      const r = await fetch('api.php?a=me', { credentials: 'same-origin' });
      this.me = await r.json();
    } catch (e) {
      this.me = { ok: true, login: false };
    }
    try {
      const c = await fetch('api.php?a=config', { credentials: 'same-origin' });
      this.googleClientId = (await c.json()).google_client_id || '';
    } catch (e) { /* biarkan kosong */ }
    this.SIAP = true;
    return this.me;
  },

  sudahLogin() { return !!(this.me && this.me.login); },

  setting() {
    return (this.me && this.me.setting) ||
      { tampilan: 'arab_terjemah', qari: 'alafasy', mushaf: 'madani' };
  },

  profil() { return (this.me && this.me.profil) || null; },

  /* Simpan satu setting ke server lalu segarkan cache lokal */
  async simpanSetting(key, val) {
    this.me.setting = Object.assign(this.setting(), { [key]: val });
    try {
      const r = await fetch('api.php?a=setting', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ key, val }),
      });
      const d = await r.json();
      if (d.setting) this.me.setting = d.setting;
    } catch (e) { /* tetap pakai nilai lokal */ }
  },

  async simpanMark(jenis, data) {
    if (!this.sudahLogin()) return;
    try {
      const r = await fetch('api.php?a=mark', {
        method: 'POST', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(Object.assign({ jenis }, data)),
      });
      const d = await r.json();
      if (d.data) this.me[jenis] = d.data;
    } catch (e) { /* abaikan, bukan kritis */ }
  },

  async loginGoogle(token) {
    const r = await fetch('api.php?a=login', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ credential: token }),
    });
    const d = await r.json();
    if (!d.ok) throw new Error(d.error || 'Login gagal');
    await this.refresh();
    return d;
  },

  async logout() {
    await fetch('api.php?a=logout', { method: 'POST', credentials: 'same-origin' });
    this.me = { ok: true, login: false };
    location.href = 'index.html';
  },

  async refresh() {
    const r = await fetch('api.php?a=me', { credentials: 'same-origin' });
    this.me = await r.json();
    return this.me;
  },
};

/* ── TOMBOL LOGIN GOOGLE SIAP PAKAI ────────────────────────
   target: elemen wadah. onSukses: dipanggil setelah login. */
function googleButton(target, onSukses) {
  const cid = Auth.googleClientId;
  if (!cid) { target.innerHTML = '<p style="color:#e88">Google Sign-In tidak tersedia.</p>'; return; }

  const pasang = (n = 0) => {
    if (typeof google === 'undefined' || !google.accounts || !google.accounts.id) {
      return n < 30 ? setTimeout(() => pasang(n + 1), 200) : null;
    }
    google.accounts.id.initialize({
      client_id: cid,
      callback: async (res) => {
        try {
          await Auth.loginGoogle(res.credential);
          if (onSukses) onSukses();
          else location.reload();
        } catch (e) { alert('Login gagal: ' + e.message); }
      },
    });
    google.accounts.id.renderButton(target, { theme: 'outline', size: 'large', width: 320 });
  };
  pasang();
}

/* ── GERBANG HALAMAN (surat, setoran, profil) ───────────────
   Kalau belum login, tampilkan layar login. Setelah berhasil,
   lanjut ke halaman yang tadi dituju. */
function gerbang(tujuan) {
  return Auth.init().then(me => {
    if (me.login) return true;

    sessionStorage.setItem('q5_tujuan', tujuan || location.pathname.split('/').pop());

    document.body.innerHTML = `
      <div class="gate">
        <img src="assets/icon.jpg" alt="5D Quran" class="gate-logo">
        <h2>Masuk dulu ya</h2>
        <p>Supaya pilihan kamu tersimpan di profil dan bisa dibuka dari perangkat lain.</p>
        <div id="gate-btn"></div>
        <p class="gate-ket"><a href="index.html">← Kembali ke beranda</a></p>
      </div>`;
    const st = document.createElement('style');
    st.textContent = `
      .gate{max-width:22rem;margin:12vh auto;padding:0 1.4rem;text-align:center;color:#eeeef5;
            font-family:system-ui,-apple-system,'Segoe UI',sans-serif}
      .gate-logo{width:96px;height:96px;border-radius:50%;object-fit:cover;
            border:1px solid rgba(212,168,83,.3);margin-bottom:1rem}
      .gate h2{font-size:1.3rem;margin:0 0 .4rem}
      .gate p{color:#9999bb;font-size:.86rem;margin:0 0 1.3rem}
      .gate-ket{margin-top:1.4rem;font-size:.8rem}
      .gate-ket a{color:#e8bb50;text-decoration:none}
      #gate-btn{display:flex;justify-content:center}
    `;
    document.head.appendChild(st);

    googleButton(document.getElementById('gate-btn'), () => {
      location.href = sessionStorage.getItem('q5_tujuan') || 'index.html';
    });
    return false;
  });
}
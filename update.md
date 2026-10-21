# 5D Quran — Catatan Perubahan (Web → acuan versi Kotlin)

Dokumen ini mencatat **seluruh perubahan dari versi awal** aplikasi web 5D Quran
(`https://5dquran.amiri.id`), supaya **versi Android/Kotlin** dapat mengikuti
perilaku versi web ini.

Terakhir diperbarui: **2026-10-04** (revisi halaman utama, setting contoh, keyboard Arab)
Akar proyek web: `/root/htdocs/5dquran` (nginx statis + PHP 8.1-fpm)

---

## 1. Ringkasan arsitektur versi web

| Bagian | Teknologi | Keterangan |
|---|---|---|
| Halaman | HTML statis (`index.html`, `surah.html`, `hafalan.html`, `tulis.html`, `pelajari.html`, `profil.html`) | disajikan nginx langsung |
| Gaya | `css/style.css`, `css/surah.css`, `css/tulis.css`, `css/pelajari.css` | tanpa framework |
| Logika | `js/app.js`, `js/surah.js`, `js/tulis.js`, `js/pelajari.js`, `js/auth.js` | JavaScript murni, tanpa bundler |
| API | `api.php?a=<aksi>` | satu endpoint, JSON |
| Bootstrap | `/etc/5dquran/api_bootstrap.php` + `/etc/5dquran/config.php` | kredensial di luar web root |
| Basis data | Firebird 3.0 `quran5d.fdb` | tabel `USERS`, `USER_SETTINGS`, `USER_MARKS` |
| Auth | Google Identity Services (GIS) | sesi PHP `q5sid` |
| Data Quran | `quran_complete.json` + `api.alquran.cloud` | audio dari `everyayah.com` |

> **Catatan Kotlin:** di Android tidak perlu memakai PHP. Bagian yang **wajib
> ditiru** adalah *aturan penilaian*, *aturan penghitungan progres*, dan
> *struktur data* di bawah. API `api.php` bisa digantikan penyimpanan lokal
> (Room/SQLite) atau tetap memanggil server dengan skema JSON yang sama.

---

## 2. Autentikasi & profil (Google)

Perubahan dari versi awal:

- Login Google **wajib** sebelum membuka `surah.html`, `hafalan.html`, `tulis.html`,
  `pelajari.html`, dan halaman `setting`.
- Gerbang (`gerbang()`) menampilkan logo bulat, tombol Google, dan tautan kembali.
- Sesi server `q5sid` (Secure, HttpOnly, SameSite=Lax). **Cookie tidak mengunci
  domain** → otomatis berlaku juga untuk `5dquran.id`.
- Profil (`profil.html`) menampilkan foto, nama, email, aktivitas, serta
  pengaturan **per profil** yang tersimpan di server (bukan localStorage).

Aturan penyimpanan pengaturan:
`tampilan`, `qari`, `mushaf` → tabel `USER_SETTINGS` (satu baris per user).

---

## 3. Definisi 3 kriteria progres (PENTING untuk Kotlin)

Setiap profil memiliki **3 kriteria** kemajuan, dihitung **per ayat**.
Ayat dihitung selesai bila memenuhi syarat berikut:

| # | Kriteria | Syarat ayat dihitung 1 | Sumber aksi di web |
|---|---|---|---|
| 1 | **Baca** | Ayat **sudah dibuka/dilihat** di halaman surat | `js/surah.js` — `IntersectionObserver` (`catatBaca`) |
| 2 | **Hafalan** | Hasil pencocokan bacaan: **tidak ada yang SALAH** dan **tidak ada yang TERLEWAT** (`wrong === 0 && missing === 0`) | `hafalan.html` — setelah tombol **Nilai Bacaan** ditekan |
| 3 | **Tulisan** | Hasil pencocokan tulisan: **tidak ada yang TERLEWAT** dan **tidak ada yang SALAH** (`wrong === 0 && missing === 0`) | `tulis.html` — tombol **Periksa** |

Total ayat Al-Quran = **6236** (dipakai sebagai penyebut persentase).

Aturan penting:
- Penghitungan **idempoten**: satu ayat hanya dihitung sekali per kriteria,
  walau dibuka/dinilai berkali-kali.
- Kriteria hafalan & tulisan **hanya** dihitung bila sempurna. Jika ada satu
  kata salah atau satu kata terlewat → ayat itu **tidak** dihitung.

---

## 4. Perubahan halaman Hafalan (`hafalan.html`)

### 4.1 Tombol "Nilai Bacaan" & "Ulangi" tidak muncul di HP
**Masalah:** tombol hanya ditampilkan bila `finalText` (hasil akhir pengenalan
suara) tidak kosong. Di Android, Web Speech API sering **tidak pernah**
mengeluarkan hasil final → tombol tak pernah tampil.

**Perbaikan:**
- Fungsi tunggal `tampilkanTombolNilai(tampil)` untuk menampilkan/menyembunyikan
  kedua tombol.
- Tombol ditampilkan bila ada **teks interim** **atau** **rekaman suara**.
- Dipanggil dari `recognition.onresult`, `recognition.onerror`,
  `recognition.onend`, dan `stopRec()`.

### 4.2 Konflik mikrofon Android (MediaRecorder vs SpeechRecognition)
**Akar masalah:** di Android, `MediaRecorder` **merebut mikrofon** sehingga
Web Speech API tidak menerima audio → tidak ada teks sama sekali. Di desktop
keduanya boleh jalan bersamaan.

**Perbaikan:** deteksi perangkat (`isMobile`), dan **MediaRecorder hanya
diaktifkan di desktop**. Di Android cukup andalkan SpeechRecognition.

> **Untuk Kotlin:** mikrofon Android hanya bisa dipakai satu konsumen pada satu
> waktu. Pilih salah satu: (a) rekam → kirim ke layanan speech-to-text, atau
> (b) pakai `SpeechRecognizer` bawaan. **Jangan** jalankan perekam dan
> pengenal suara bersamaan.

### 4.3 Pemutar ulang hasil rekaman
- Pemutar dipindahkan **ke luar kartu hasil** (`#playback-box`) agar langsung
  tampil setelah rekaman selesai (tidak menunggu penilaian).
- Berisi tombol **Putar**, garis waktu (progress bar) yang bisa diklik untuk
  melompat, dan durasi.
- Muncul begitu ada rekaman, walau transkrip kosong.

### 4.4 Pesan saat teks tidak dikenali
Jika pengguna menekan "Nilai Bacaan" tetapi transkrip kosong (kasus Android):
- **Bukan** `alert()` lagi.
- Menampilkan catatan kuning: *"Perangkat tidak mengenali teks bacaan. Gunakan
  rekaman suara di atas untuk menilai secara manual."* dan pemutar rekaman.

### 4.5 Penilaian yang lebih akurat
- Normalisasi Arab (`norm`) menghapus harakat/tanda, menyeragamkan
  `alef wasla/madda/hamzah` → `ا`, `ى` → `ي`, `ة` → `ه`.
- Pencocokan memakai **Levenshtein berbobot** + backtrace untuk klasifikasi
  tiap kata: `correct`, `near`, `wrong`, `missing`.
- Statistik baru **"Mirip"** (`near`, warna biru) di panel hasil dan di
  daftar "kata yang perlu diperhalus".
- Rumus akurasi: `(correct + near*0.75 − wrong*0.25) / total * 100`.
- Ambang "mirip": rasio kemiripan ≥ 0.6.

### 4.6 Penyimpanan progres hafalan
Setelah nilai keluar, jika `wrong === 0 && missing === 0`:
`Auth.simpanMark('hafalan', { surah, ayat, nama })`.

---

## 5. Perubahan halaman Tulis (`tulis.html` + `js/tulis.js`)

Fitur **baru**: penilaian tulisan (sebelumnya hanya buku tulis tanpa penilaian).

- Tombol **✓ Periksa** di toolbar area menulis (`.nb-check`).
- Panel hasil (`#check-panel`) menampilkan: skor %, statistik
  `Benar / Mirip / Salah / Terlewat`, dan diff ayat berwarna per kata.
- Fungsi penilaian `alignAr(ref, spk)` + `normAr()` + `simMirip()` — **logika
  sama** dengan halaman hafalan (agar konsisten).
- Panel hasil disembunyikan saat pindah ayat.
- Jika `wrong === 0 && missing === 0` (sempurna):
  `Auth.simpanMark('tulisan', { surah, ayat, nama })`.
- Tombol toolbar dibuat `flex-wrap` agar tidak terpotong di layar HP.

---

## 6. Halaman Baca (`surah.html` + `js/surah.js`)

- `initLastReadTracker()` kini juga mencatat **setiap ayat yang terlihat**
  sebagai kriteria **baca** (`catatBaca`), tidak hanya posisi terakhir.
- Ayat 1 langsung tercatat tanpa harus digulir.
- Tetap menyimpan posisi `terakhir` (untuk tombol "Lanjutkan").
- Qari tidak lagi dipilih di halaman surat — mengikuti Setting profil.

---

## 7. Grafik statistik di Profil (`profil.html`)

Kartu baru **"📊 Kemajuan Kriteria"** dengan **3 batang**:

| Batang | Warna | Sumber data |
|---|---|---|
| 📖 Baca | biru (`#38bdf8 → #0ea5e9`) | `stats.baca` |
| ✍️ Tulisan | hijau (`#4ade80 → #22c55e`) | `stats.tulisan` |
| 🎤 Hafalan | emas (`#e8bb50 → #d4a853`) | `stats.hafalan` |

- Label: `n / 6.236 (x%)`. Persentase: bila `< 1%` ditampilkan 2 desimal.
- Batang beri kelas `.ada` (lebar minimum 14px) agar **tetap terlihat** walau
  progres masih kecil.
- Data diambil dari `api.php?a=stats`.

Struktur grafik (untuk direplikasi di Kotlin — mis. `LinearProgressIndicator`
atau bar chart):

```
row: label(kiri) ................ nilai(kanan)
     [========== track ==========]
     [=== fill ===]                  ← warna per kriteria
```

---

## 7B. Halaman Utama (`index.html` + `js/app.js`) — revisi

### 7B.1 Filter Semua / Makkiyah / Madaniyah DIHAPUS
- Tiga tombol filter di bawah kolom pencarian dihapus dari HTML.
- Fungsi `initFilters()`, variabel `currentFilter`, dan aturan CSS
  `.filter-tabs` / `.filter-tab` **dihapus seluruhnya** (bukan disembunyikan).
- Pencarian kini hanya menyaring berdasarkan kata kunci.

### 7B.2 Nama surat & arti dalam bahasa Indonesia
- Sumber data baru: **`data/surat-id.json`** (114 surat) berisi
  `{ arab, latin, arti, ayat, tempat }` — arti dalam bahasa Indonesia
  (mis. Al-Fatihah → "Pembukaan", Al-Baqarah → "Sapi Betina").
- Dibuat sekali dengan `api.quran.com/api/v4/chapters?language=id`, disimpan
  lokal supaya cepat & tidak tergantung jaringan.
- Fungsi `infoSurat(nomor)` dan `muatDataSurat()` di `js/app.js`; dipanggil
  sebelum daftar surat dirender.
- Bila data lokal gagal dimuat, otomatis kembali ke data `api.alquran.cloud`
  (nama Inggris) — web tetap jalan.

### 7B.3 Nama Arab tanpa kata "سُورَةُ / سورة"
- `api.alquran.cloud` mengirim nama seperti `سُورَةُ الفاتحة`.
- Ditambahkan fungsi `buangKataSurat()` yang membuang awalan tersebut, sehingga
  kartu hanya menampilkan `الفاتحة`.

### 7B.4 Tiga kartu "Lanjutkan" (baca / tulis / hafalan)
Menggantikan satu kartu "Lanjutkan Membaca". Masing-masing menampilkan
**posisi terakhir** dari kriteria terkait:

| Kartu | Sumber data (`api.php?a=me`) | Tautan |
|---|---|---|
| 📖 Lanjut Baca | `terakhir` | `surah.html?id=<surah>#ayat-<ayat>` |
| ✍️ Lanjut Tulis | `terakhir_tulis` | `tulis.html?surah=<surah>&ayat=<ayat>` |
| 🎤 Lanjut Hafalan | `terakhir_hafalan` | `hafalan.html?surah=<surah>&ayat=<ayat>` |

- Jenis mark baru: **`terakhir_tulis`** dan **`terakhir_hafalan`**
  (satu posisi per user, sama seperti `terakhir`).
- `js/tulis.js` menyimpan `terakhir_tulis` setiap kali berpindah ayat.
- `hafalan.html` menyimpan `terakhir_hafalan` setiap kali ayat dimuat.
- `hafalan.html` kini **membaca parameter `?ayat=`** dan langsung memulai sesi
  dari ayat tersebut (sebelumnya hanya `?surah=`).
- Kartu tanpa riwayat ditampilkan redup dengan tulisan "Belum ada riwayat".

> **Catatan penting untuk Kotlin:** kolom `JENIS` di `USER_MARKS` awalnya
> `VARCHAR(12)` sehingga `terakhir_hafalan` (16 huruf) gagal disimpan. Kolom
> sudah diperlebar ke **`VARCHAR(24)`** (PK di-drop lalu dibuat ulang).
> Di Kotlin gunakan `TEXT` / `VARCHAR(32)` agar tidak terulang.


---

## 8. Perubahan API (`api.php`)

Aksi yang tersedia: `config`, `me`, `login`, `logout`, `setting`, `mark`, `stats`.

### 8.1 `mark` — menerima jenis baru
Jenis yang sah sekarang:
`favorit`, `terakhir`, `bookmark`, **`baca`**, **`hafalan`**, **`tulisan`**.

Body (JSON):
```json
{ "jenis": "hafalan", "surah": 1, "ayat": 3, "nama": "Al-Fatihah" }
```
- `surah` 1–114, `ayat` 1–286. Hashap: kirim `"hapus": true` untuk menghapus.
- `baca`, `hafalan`, `tulisan` **kumulatif** (satu baris per ayat).
- `terakhir` selalu diganti (hanya satu posisi).

### 8.2 `stats` — statistik kemajuan (BARU)
Memerlukan login. Respons:
```json
{
  "ok": true,
  "stats": { "baca": 3, "hafalan": 2, "tulisan": 1 },
  "rinci": {
    "baca":    { "1": 3 },
    "hafalan": { "1": 2 },
    "tulisan": { "1": 1 }
  }
}
```
- `stats` = jumlah **ayat unik** selesai per kriteria
  (`COUNT(DISTINCT SURAH || '#' || AYAT)`).
- `rinci` = jumlah ayat per surat, per kriteria (untuk grafik per surah).

### 8.3 Perbaikan lain pada API
- **Firebird** tidak mendukung `lastInsertId()` → diganti
  `INSERT ... RETURNING ID` (ini menyebabkan error "Unexpected end of JSON input").
- Validasi tambahan `ayat > 286` ditolak.

---

## 9. Struktur basis data (Firebird 3.0)

### `USERS`
| Kolom | Tipe |
|---|---|
| ID | INTEGER (PK) |
| GOOGLE_ID | VARCHAR |
| EMAIL | VARCHAR |
| NAMA | VARCHAR |
| FOTO | VARCHAR |
| TERAKHIR | TIMESTAMP |

### `USER_SETTINGS`
| Kolom | Tipe |
|---|---|
| USER_ID | INTEGER |
| TAMPILAN | VARCHAR (`arab_terjemah` / `arab_saja`) |
| QARI | VARCHAR (`alafasy`/`husary`/`minshawi`/`basit`/`ajamy`) |
| MUSHAF | VARCHAR (`madani`/`naskh`/`indopak`) |
| UPDATED | TIMESTAMP |

### `USER_MARKS` — dipakai untuk semua progres
| Kolom | Tipe | Keterangan |
|---|---|---|
| USER_ID | INTEGER | pemilik |
| JENIS | VARCHAR | `favorit`/`terakhir`/`bookmark`/**`baca`**/**`hafalan`**/**`tulisan`** |
| SURAH | INTEGER | 1–114 |
| AYAT | INTEGER | 1–286 |
| NAMA | VARCHAR | nama surat |
| DIBUAT | TIMESTAMP | waktu |

> **Saran untuk Kotlin:** buat tabel `progress(user_id, jenis, surah, ayat, waktu)`
> dengan kunci unik `(user_id, jenis, surah, ayat)` → idempotensi otomatis,
> tidak perlu cek manual.

---

## 10. Perubahan infrastruktur (bukan bagian Kotlin, untuk catatan)

- Vhost `5dquran.amiri.id` (HTTPS Let's Encrypt, redirect HTTP→HTTPS).
- Vhost persiapan `5dquran.id` (menunggu DNS + certbot:
  `sudo certbot --nginx -d 5dquran.id -d www.5dquran.id`).
- Alamat lama `amiri.id/5dquran/` di-*redirect* 301 ke domain baru
  (di `/etc/nginx/sites-enabled/amiri.id`).
- Cache statis: JS/CSS 1 jam, gambar/JSON 30 hari.
- Google Cloud Console: origin `https://5dquran.amiri.id` sudah didaftarkan
  (kalau muncul `origin_mismatch`, tambahkan origin di
  *Authorized JavaScript origins*).
- **Tanpa domain ter-hardcode** di kode → aman dipindah ke `5dquran.id`.

---

## 11. Daftar berkas yang berubah

| Berkas | Perubahan inti |
|---|---|
| `api.php` | jenis `baca`/`hafalan`/`tulisan`, aksi `stats`, `RETURNING ID` |
| `js/auth.js` | `simpanMark` (sudah ada, dipakai jenis baru) |
| `js/surah.js` | `catatBaca` — hitung kriteria **baca** per ayat |
| `hafalan.html` | tombol nilai, deteksi mobile, pemutar rekaman, normalisasi & alignment, simpan `hafalan` |
| `tulis.html` | tombol **Periksa**, panel hasil `#check-panel` |
| `js/tulis.js` | `normAr`/`alignAr`/`simMirip`/`periksaTulisan`, simpan `tulisan` |
| `profil.html` | kartu & grafik **Kemajuan Kriteria** (+ CSS/JS) |
| `css/tulis.css` | gaya panel hasil periksa, `.nb-check`, `flex-wrap` |

---

## 12. Checklist implementasi versi Kotlin

1. **Auth**: Google Sign-In → simpan `id`, `nama`, `email`, `foto`.
2. **Penyimpanan progres**: satu baris per `(user, kriteria, surah, ayat)` unik.
3. **Kriteria baca**: catat ayat saat tampil/dilihat di layar.
4. **Kriteria hafalan**: rekam suara → pencocokan kata → hitung **hanya jika**
   0 salah & 0 terlewat.
5. **Kriteria tulisan**: input tulisan → pencocokan kata → hitung **hanya jika**
   0 salah & 0 terlewat.
6. **Normalisasi Arab + Levenshtein berbobot**: rumus & ambang sama seperti
   Bagian 4.5 dan 5.
7. **Mikrofon**: jangan jalankan perekam & pengenal suara bersamaan.
8. **Grafik profil**: 3 batang (Baca/Tulisan/Hafalan), penyebut 6236 ayat.
9. **Total ayat** konstanta: `6236`.

---

## 13. Rumus & konstanta acuan (ringkas untuk Kotlin)

```
TOTAL_AYAT          = 6236
AMBANG_MIRIP        = 0.6
BOBOT_NEAR          = 0.75
PENALTI_WRONG       = 0.25
akurasi(%)          = clamp((correct + near*0.75 - wrong*0.25) / total * 100, 0, 100)

Ayat selesai HAFALAN / TULISAN  <=>  wrong == 0 && missing == 0 && total > 0
Ayat selesai BACA               <=>  ayat pernah ditampilkan
```

Normalisasi huruf Arab (semua varian → bentuk dasar):

```
ا أ إ آ ٱ   ->  ا
ى  ي        ->  ي
ة           ->  ه
ؤ           ->  و
ئ           ->  ي
(buang harakat, tanda, angka Arab, & non [\u0621-\u064A])
```

---

## 14. Pelajaran penting (WAJIB dibaca sebelum port ke Kotlin)

### 14.1 `Auth.init()` harus dipanggil di setiap halaman ber‑Auth
**Gejala:** tulisan & hafalan tampak berhasil ("✓ Tulisan Sempurna") tetapi
kartu **"Lanjut Tulis" / "Lanjut Hafalan"** dan angka progres tidak bertambah.

**Sebab:** `Auth.init()` tidak dipanggil di `tulis.html`/`hafalan.html`, sehingga
`Auth.me` kosong dan `Auth.simpanMark()` berhenti di baris pertama
(`if (!this.sudahLogin()) return;`) — **tanpa error apa pun**.

**Aturan untuk Kotlin:** sebelum menyimpan progres apa pun, pastikan sesi login
sudah dimuat. Jangan mengandalkan "tidak ada error" sebagai bukti data tersimpan —
periksa hasil penyimpanan.

### 14.2 Halaman ber‑Auth sebaiknya memakai gerbang yang sama
`surah.html` & `hafalan.html` memakai `gerbang('...')` yang otomatis memanggil
`Auth.init()` dan menampilkan layar "Masuk dulu ya". `tulis.html` kini memakai
pola yang sama agar konsisten (sebelumnya tanpa gerbang sama sekali).

### 14.3 Panjang kolom `JENIS` di `USER_MARKS`
Semula `VARCHAR(12)`, sedangkan `terakhir_hafalan` = 16 karakter →
error *string right truncation* secara senyap di sisi API.
Sudah diperlebar ke **`VARCHAR(24)`** (PK di‑drop lalu dibuat ulang).
Di Kotlin pakai `TEXT`/`VARCHAR(32)`.

### 14.4 Jangan simpan nama Arab mentah ke kolom `NAMA`
Label dari daftar surat berisi nomor + `سُورَةُ` + harakat
(mis. `"2. سُورَةُ البَقَرَةِ"`). Sudah dibersihkan (`labelBersih`) sebelum
disimpan. Di Kotlin simpan nama terjemahan Indonesia saja.

### 14.5 Firebird bisa mati mendadak
Saat DB mati, `api.php?a=me` balas kosong → front‑end melaporkan
*"Unexpected end of JSON input"*. Sudah ditambahkan `Restart=always`
(5 detik) pada unit `firebird3.0`. Di sisi aplikasi Kotlin, tangani
kegagalan koneksi dengan pesan yang jelas, bukan error JSON.


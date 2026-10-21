<?php
// API tunggal 5D Quran. Rute via ?a=<aksi>
require_once '/etc/5dquran/api_bootstrap.php';
q5_boot();

// ── Helper Multi-Path Penyimpanan Feedback ──
function q5_feedback_file() {
    static $resolved = null;
    if ($resolved !== null) return $resolved;

    $candidates = [
        __DIR__ . '/data/feedback_messages.json',
        __DIR__ . '/feedback_messages.json',
        '/var/tmp/5dquran_feedback_messages.json',
        sys_get_temp_dir() . '/5dquran_feedback_messages.json',
        '/tmp/5dquran_feedback_messages.json'
    ];

    foreach ($candidates as $c) {
        if (file_exists($c) && filesize($c) > 2) {
            $resolved = $c;
            return $resolved;
        }
    }

    foreach ($candidates as $c) {
        $d = dirname($c);
        if (!is_dir($d)) @mkdir($d, 0777, true);
        if (is_dir($d) && is_writable($d)) {
            $test = @file_put_contents($c, '[]');
            if ($test !== false) {
                $resolved = $c;
                return $resolved;
            }
        }
    }

    $resolved = sys_get_temp_dir() . '/5dquran_feedback_messages.json';
    @file_put_contents($resolved, '[]');
    return $resolved;
}

function q5_read_all_feedback() {
    $candidates = [
        __DIR__ . '/data/feedback_messages.json',
        __DIR__ . '/feedback_messages.json',
        '/var/tmp/5dquran_feedback_messages.json',
        sys_get_temp_dir() . '/5dquran_feedback_messages.json',
        '/tmp/5dquran_feedback_messages.json'
    ];
    $merged = [];
    $seenIds = [];
    foreach ($candidates as $c) {
        if (file_exists($c) && filesize($c) > 2) {
            $raw = @file_get_contents($c);
            $data = $raw ? json_decode($raw, true) : null;
            if (is_array($data)) {
                foreach ($data as $item) {
                    $id = $item['id'] ?? (string)mt_rand();
                    if (!isset($seenIds[$id])) {
                        $seenIds[$id] = true;
                        $merged[] = $item;
                    }
                }
            }
        }
    }
    return $merged;
}

function q5_save_all_feedback($allChats) {
    $targetFile = q5_feedback_file();
    $json = json_encode(array_values($allChats), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE);
    $ok = @file_put_contents($targetFile, $json);
    if ($ok === false) {
        @file_put_contents('/var/tmp/5dquran_feedback_messages.json', $json);
        @file_put_contents(sys_get_temp_dir() . '/5dquran_feedback_messages.json', $json);
        @file_put_contents('/tmp/5dquran_feedback_messages.json', $json);
    }
    return true;
}

$acao = isset($_GET['a']) ? $_GET['a'] : '';
$raw  = file_get_contents('php://input');
$in   = $raw ? json_decode($raw, true) : [];
if (!is_array($in)) $in = [];

switch ($acao) {

// ── Konfigurasi publik untuk halaman (client id Google) ──
case 'config':
    q5_json(['ok' => true, 'google_client_id' => $GLOBALS['q5cfg']['google']['client_id']]);

// ── Status login + profil + setting (satu panggilan) ──
case 'me':
    $u = q5_user();
    if (!$u) q5_json(['ok' => true, 'login' => false]);
    q5_json([
        'ok'       => true,
        'login'    => true,
        'profil'   => [
            'id'    => (int)$u['ID'],
            'nama'  => $u['NAMA'],
            'email' => $u['EMAIL'],
            'foto'  => $u['FOTO'],
        ],
        'setting'  => q5_settings($u['ID']),
        'favorit'  => q5_marks($u['ID'], 'favorit'),
        'terakhir' => q5_marks($u['ID'], 'terakhir'),
        'terakhir_tulis'   => q5_marks($u['ID'], 'terakhir_tulis'),
        'terakhir_hafalan' => q5_marks($u['ID'], 'terakhir_hafalan'),
        'bookmark' => q5_marks($u['ID'], 'bookmark'),
    ]);

// ── Login/register dengan ID token Google ──
case 'login':
    $cred = isset($in['credential']) ? $in['credential'] : '';
    if ($cred === '') q5_json(['ok' => false, 'error' => 'token kosong'], 400);

    $g = q5_verify_google_token($cred);
    if (!$g) q5_json(['ok' => false, 'error' => 'token Google tidak sah'], 401);

    $db  = q5_db();
    $st  = $db->prepare('SELECT ID FROM USERS WHERE GOOGLE_ID = ?');
    $st->execute([$g['sub']]);
    $row = $st->fetch();

    if ($row) {
        $uid = (int)$row['ID'];
        $db->prepare('UPDATE USERS SET NAMA = ?, EMAIL = ?, FOTO = ?, TERAKHIR = CURRENT_TIMESTAMP WHERE ID = ?')
           ->execute([$g['name'] ?? '', $g['email'], $g['picture'] ?? '', $uid]);
        $baru = false;
    } else {
        // Firebird tidak mendukung lastInsertId(); pakai RETURNING (native)
        $ins = $db->prepare('INSERT INTO USERS (GOOGLE_ID, EMAIL, NAMA, FOTO, TERAKHIR) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING ID');
        $ins->execute([$g['sub'], $g['email'], $g['name'] ?? '', $g['picture'] ?? '']);
        $uid  = (int)($ins->fetchColumn() ?: 0);
        $baru = true;
    }

    $_SESSION['uid'] = $uid;
    session_regenerate_id(true);
    q5_json(['ok' => true, 'baru' => $baru, 'nama' => $g['name'] ?? '']);

// ── Sinkronisasi Profil & Pengaturan dari Aplikasi Android / Web ──
case 'sync_app':
    $email = trim((string)($in['email'] ?? ''));
    if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        q5_json(['ok' => false, 'error' => 'email tidak sah'], 400);
    }
    $nama = trim((string)($in['nama'] ?? ''));
    $foto = trim((string)($in['foto'] ?? ''));
    $gid  = trim((string)($in['google_id'] ?? $email));

    $db = q5_db();
    $st = $db->prepare('SELECT ID, NAMA, EMAIL, FOTO FROM USERS WHERE EMAIL = ? OR GOOGLE_ID = ?');
    $st->execute([$email, $gid]);
    $row = $st->fetch();

    if ($row) {
        $uid = (int)$row['ID'];
        if ($nama !== '' || $foto !== '') {
            $db->prepare('UPDATE USERS SET NAMA = COALESCE(NULLIF(?, \'\'), NAMA), FOTO = COALESCE(NULLIF(?, \'\'), FOTO), TERAKHIR = CURRENT_TIMESTAMP WHERE ID = ?')
               ->execute([$nama, $foto, $uid]);
        }
    } else {
        $ins = $db->prepare('INSERT INTO USERS (GOOGLE_ID, EMAIL, NAMA, FOTO, TERAKHIR) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING ID');
        $ins->execute([$gid, $email, $nama ?: $email, $foto]);
        $uid = (int)($ins->fetchColumn() ?: 0);
    }

    if (session_status() === PHP_SESSION_ACTIVE) {
        $_SESSION['uid'] = $uid;
    }

    // Jika app mengirimkan update setting dari HP, simpan ke database
    if (isset($in['update_setting']) && is_array($in['update_setting'])) {
        $us = $in['update_setting'];
        $ada = $db->prepare('SELECT USER_ID FROM USER_SETTINGS WHERE USER_ID = ?');
        $ada->execute([$uid]);
        if (!$ada->fetch()) {
            $db->prepare('INSERT INTO USER_SETTINGS (USER_ID) VALUES (?)')->execute([$uid]);
        }
        if (!empty($us['qari'])) {
            $db->prepare('UPDATE USER_SETTINGS SET QARI = ?, UPDATED = CURRENT_TIMESTAMP WHERE USER_ID = ?')->execute([$us['qari'], $uid]);
        }
        if (!empty($us['contoh'])) {
            $db->prepare('UPDATE USER_SETTINGS SET CONTOH = ?, UPDATED = CURRENT_TIMESTAMP WHERE USER_ID = ?')->execute([$us['contoh'], $uid]);
        }
        if (!empty($us['tampilan'])) {
            $db->prepare('UPDATE USER_SETTINGS SET TAMPILAN = ?, UPDATED = CURRENT_TIMESTAMP WHERE USER_ID = ?')->execute([$us['tampilan'], $uid]);
        }
        if (!empty($us['mushaf'])) {
            $db->prepare('UPDATE USER_SETTINGS SET MUSHAF = ?, UPDATED = CURRENT_TIMESTAMP WHERE USER_ID = ?')->execute([$us['mushaf'], $uid]);
        }
    }

    // Update marks posisi terakhir bila dikirimkan dari HP
    if (isset($in['update_marks']) && is_array($in['update_marks'])) {
        foreach (['terakhir', 'terakhir_tulis', 'terakhir_hafalan'] as $mKey) {
            if (!empty($in['update_marks'][$mKey]) && is_array($in['update_marks'][$mKey])) {
                $m = $in['update_marks'][$mKey];
                $s = (int)($m['surah'] ?? 0);
                $a = (int)($m['ayat'] ?? 0);
                $n = (string)($m['nama'] ?? '');
                if ($s >= 1 && $s <= 114 && $a >= 1) {
                    $db->prepare('DELETE FROM USER_MARKS WHERE USER_ID = ? AND JENIS = ?')->execute([$uid, $mKey]);
                    $db->prepare('INSERT INTO USER_MARKS (USER_ID, JENIS, SURAH, AYAT, NAMA) VALUES (?, ?, ?, ?, ?)')
                       ->execute([$uid, $mKey, $s, $a, $n]);
                }
            }
        }
    }

    // Ambil setting lengkap & marks dari server
    $stg = q5_settings($uid);
    $terakhir = q5_marks($uid, 'terakhir');
    $terakhir_tulis = q5_marks($uid, 'terakhir_tulis');
    $terakhir_hafalan = q5_marks($uid, 'terakhir_hafalan');

    // Ambil stats kemajuan
    $cnt = $db->prepare("SELECT JENIS, COUNT(DISTINCT SURAH || '#' || AYAT) AS C FROM USER_MARKS WHERE USER_ID = ? AND JENIS IN ('baca','hafalan','tulisan') GROUP BY JENIS");
    $cnt->execute([$uid]);
    $stats = ['baca' => 0, 'hafalan' => 0, 'tulisan' => 0];
    foreach ($cnt->fetchAll() as $r) {
        $k = strtolower(trim($r['JENIS']));
        if (isset($stats[$k])) $stats[$k] = (int)$r['C'];
    }

    q5_json([
        'ok' => true,
        'user' => [
            'id' => $uid,
            'email' => $email,
            'nama' => $row ? $row['NAMA'] : $nama,
            'foto' => $row ? $row['FOTO'] : $foto,
        ],
        'setting' => $stg,
        'terakhir' => !empty($terakhir) ? $terakhir[0] : null,
        'terakhir_tulis' => !empty($terakhir_tulis) ? $terakhir_tulis[0] : null,
        'terakhir_hafalan' => !empty($terakhir_hafalan) ? $terakhir_hafalan[0] : null,
        'stats' => $stats
    ]);

// ── Keluar ──
case 'logout':
    $_SESSION = [];
    session_destroy();
    q5_json(['ok' => true]);

// ── Simpan satu pengaturan ──
case 'setting':
    $u = q5_require();
    $key = isset($in['key']) ? $in['key'] : '';
    $val = isset($in['val']) ? $in['val'] : '';
    $izinkan = [
        'tampilan' => ['arab_terjemah', 'arab_saja'],
        'qari'     => null, // dicek di bawah
        'mushaf'   => ['madani', 'naskh', 'indopak'],
    'contoh'   => ['ya', 'tidak'],
    ];
    $qariAman = ['alafasy', 'husary', 'minshawi', 'basit', 'ajamy', 'sudais', 'ghamdi'];
    if (!array_key_exists($key, $izinkan)) q5_json(['ok' => false, 'error' => 'kunci tidak dikenal'], 400);
    if ($key === 'qari' && !in_array($val, $qariAman, true)) q5_json(['ok' => false, 'error' => 'qari tidak dikenal'], 400);
    if (is_array($izinkan[$key]) && !in_array($val, $izinkan[$key], true)) q5_json(['ok' => false, 'error' => 'nilai tidak sah'], 400);

    $kolom = ['tampilan' => 'TAMPILAN', 'qari' => 'QARI', 'mushaf' => 'MUSHAF', 'contoh' => 'CONTOH'][$key];
    $db = q5_db();
    // Pastikan baris setting ada dulu, baru update (hindari urutan terbalik)
    $ada = $db->prepare('SELECT USER_ID FROM USER_SETTINGS WHERE USER_ID = ?');
    $ada->execute([$u['ID']]);
    if (!$ada->fetch()) {
        $db->prepare('INSERT INTO USER_SETTINGS (USER_ID) VALUES (?)')->execute([$u['ID']]);
    }
    $db->prepare('UPDATE USER_SETTINGS SET ' . $kolom . ' = ?, UPDATED = CURRENT_TIMESTAMP WHERE USER_ID = ?')
       ->execute([$val, $u['ID']]);
    q5_json(['ok' => true, 'setting' => q5_settings($u['ID'])]);

// ── Favorit / terakhir dibaca / bookmark (dipakai lintas perangkat) ──
case 'mark':
    $u = q5_require();
    $jenis = isset($in['jenis']) ? $in['jenis'] : '';
    if (!in_array($jenis, ['favorit', 'terakhir', 'bookmark', 'baca', 'hafalan', 'tulisan', 'terakhir_tulis', 'terakhir_hafalan'], true)) q5_json(['ok' => false, 'error' => 'jenis tidak sah'], 400);

    $surah = (int)($in['surah'] ?? 0);
    $ayat  = (int)($in['ayat'] ?? 0);
    $nama  = (string)($in['nama'] ?? '');
    if ($surah < 1 || $surah > 114 || $ayat < 1) q5_json(['ok' => false, 'error' => 'surat/ayat tidak sah'], 400);
    if ($ayat > 286) q5_json(['ok' => false, 'error' => 'nomor ayat tidak sah'], 400);

    $db = q5_db();
    if (!empty($in['hapus'])) {
        $db->prepare('DELETE FROM USER_MARKS WHERE USER_ID = ? AND JENIS = ? AND SURAH = ? AND AYAT = ?')
           ->execute([$u['ID'], $jenis, $surah, $ayat]);
    } else {
        // 'terakhir*' hanya satu posisi per user: hapus dulu yang lama
        if ($jenis === 'terakhir' || $jenis === 'terakhir_tulis' || $jenis === 'terakhir_hafalan') {
            $db->prepare('DELETE FROM USER_MARKS WHERE USER_ID = ? AND JENIS = ?')->execute([$u['ID'], $jenis]);
        }
        $upd = $db->prepare('UPDATE USER_MARKS SET NAMA = ?, DIBUAT = CURRENT_TIMESTAMP WHERE USER_ID = ? AND JENIS = ? AND SURAH = ? AND AYAT = ?');
        $upd->execute([$nama, $u['ID'], $jenis, $surah, $ayat]);
        if ($upd->rowCount() === 0) {
            $db->prepare('INSERT INTO USER_MARKS (USER_ID, JENIS, SURAH, AYAT, NAMA) VALUES (?, ?, ?, ?, ?)')
               ->execute([$u['ID'], $jenis, $surah, $ayat, $nama]);
        }
    }
    q5_json(['ok' => true, 'data' => q5_marks($u['ID'], $jenis)]);

// ── Statistik kemajuan per kriteria (baca / hafalan / tulisan) ──
case 'stats':
    $u = q5_require();
    $db = q5_db();
    $cnt = $db->prepare("SELECT JENIS, COUNT(DISTINCT SURAH || '#' || AYAT) AS C FROM USER_MARKS WHERE USER_ID = ? AND JENIS IN ('baca','hafalan','tulisan') GROUP BY JENIS");
    $cnt->execute([$u['ID']]);
    $out = ['baca' => 0, 'hafalan' => 0, 'tulisan' => 0];
    foreach ($cnt->fetchAll() as $r) {
        $k = strtolower(trim($r['JENIS']));
        if (isset($out[$k])) $out[$k] = (int)$r['C'];
    }
    // Rincian per surat: berapa ayat unik yang sudah selesai tiap kriteria
    $rinci = ['baca' => [], 'hafalan' => [], 'tulisan' => []];
    $dt = $db->prepare("SELECT JENIS, SURAH, COUNT(DISTINCT AYAT) AS N FROM USER_MARKS WHERE USER_ID = ? AND JENIS IN ('baca','hafalan','tulisan') GROUP BY JENIS, SURAH");
    $dt->execute([$u['ID']]);
    foreach ($dt->fetchAll() as $r) {
        $k = strtolower(trim($r['JENIS']));
        if (isset($rinci[$k])) $rinci[$k][(int)$r['SURAH']] = (int)$r['N'];
    }
    q5_json(['ok' => true, 'stats' => $out, 'rinci' => $rinci]);

// ── Tulisan: simpan (POST) atau ambil (GET) teks hasil ketikan ──
case 'tulisan':
    $u = q5_require();
    $db = q5_db();

    // GET → ambil tulisan (satu ayat bila surah+ayat, atau daftar)
    if ($_SERVER['REQUEST_METHOD'] === 'GET') {
        $surah = (int)($_GET['surah'] ?? 0);
        $ayat  = (int)($_GET['ayat']  ?? 0);
        if ($surah > 0 && $ayat > 0) {
            $st = $db->prepare('SELECT SURAH, AYAT, NAMA, TEKS, DIPERIKSA, UPDATED FROM USER_TULISAN WHERE USER_ID = ? AND SURAH = ? AND AYAT = ?');
            $st->execute([$u['ID'], $surah, $ayat]);
            $r = $st->fetch();
            q5_json(['ok' => true, 'data' => $r ? q5_tulisan_row($r) : null]);
        }
        if ($surah > 0) {
            $st = $db->prepare('SELECT SURAH, AYAT, NAMA, TEKS, DIPERIKSA, UPDATED FROM USER_TULISAN WHERE USER_ID = ? AND SURAH = ? ORDER BY AYAT');
            $st->execute([$u['ID'], $surah]);
        } else {
            $st = $db->prepare('SELECT SURAH, AYAT, NAMA, TEKS, DIPERIKSA, UPDATED FROM USER_TULISAN WHERE USER_ID = ? ORDER BY SURAH, AYAT');
            $st->execute([$u['ID']]);
        }
        $rows = [];
        foreach ($st->fetchAll() as $r) $rows[] = q5_tulisan_row($r);
        q5_json(['ok' => true, 'data' => $rows]);
    }

    // POST → simpan
    $surah = (int)($in['surah'] ?? 0);
    $ayat  = (int)($in['ayat']  ?? 0);
    $teks  = (string)($in['teks'] ?? '');
    $nama  = (string)($in['nama'] ?? '');
    $diperiksa = !empty($in['diperiksa']) ? 1 : 0;
    if ($surah < 1 || $surah > 114 || $ayat < 1 || $ayat > 286) q5_json(['ok' => false, 'error' => 'surat/ayat tidak sah'], 400);
    if ($teks === '') q5_json(['ok' => false, 'error' => 'teks kosong'], 400);
    if (mb_strlen($teks) > 4000) q5_json(['ok' => false, 'error' => 'teks terlalu panjang'], 400);

    $upd = $db->prepare('UPDATE USER_TULISAN SET TEKS = ?, NAMA = ?, DIPERIKSA = ?, UPDATED = CURRENT_TIMESTAMP WHERE USER_ID = ? AND SURAH = ? AND AYAT = ?');
    $upd->execute([$teks, $nama, $diperiksa, $u['ID'], $surah, $ayat]);
    if ($upd->rowCount() === 0) {
        $db->prepare('INSERT INTO USER_TULISAN (USER_ID, SURAH, AYAT, NAMA, TEKS, DIPERIKSA) VALUES (?, ?, ?, ?, ?, ?)')
           ->execute([$u['ID'], $surah, $ayat, $nama, $teks, $diperiksa]);
    }
    q5_json(['ok' => true, 'tulisan' => [$surah, $ayat]]);


// ── Chat Pelaporan Kesalahan & Ide Usulan (Kirim ke pengembang) ──
case 'chat_send':
    $userId = trim((string)($in['user_id'] ?? ''));
    if ($userId === '') $userId = 'anon_' . substr(md5($_SERVER['REMOTE_ADDR'] ?? '127.0.0.1'), 0, 8);

    $userName = trim((string)($in['user_name'] ?? 'Pengguna 5D Quran'));
    $tipe = trim((string)($in['tipe'] ?? 'usulan')); // 'kesalahan', 'usulan', 'tanya'
    $pesan = trim((string)($in['pesan'] ?? ''));
    $deviceInfo = trim((string)($in['device_info'] ?? 'Android Native'));
    $appVer = trim((string)($in['app_version'] ?? '1.0.1'));

    if ($pesan === '') {
        q5_json(['ok' => false, 'error' => 'Pesan tidak boleh kosong'], 400);
    }

    $allChats = q5_read_all_feedback();
    $msgId = 'msg_' . time() . '_' . substr(md5(uniqid(mt_rand(), true)), 0, 6);
    $newMsg = [
        'id'          => $msgId,
        'user_id'     => $userId,
        'user_name'   => $userName,
        'tipe'        => $tipe,
        'pesan'       => $pesan,
        'sender'      => 'user',
        'device_info' => $deviceInfo,
        'app_version' => $appVer,
        'created_at'  => date('Y-m-d H:i:s'),
        'timestamp'   => time()
    ];

    $allChats[] = $newMsg;
    q5_save_all_feedback($allChats);

    // Kirim email notifikasi ke pengembang (ariadrianto@gmail.com)
    $destEmail = 'ariadrianto@gmail.com';
    $tipeLabel = ($tipe === 'kesalahan') ? '🚨 Laporan Kesalahan / Bug' : (($tipe === 'usulan') ? '💡 Ide Usulan Fitur' : '💬 Tanya / Masukan');
    $subject = "[5D Quran v{$appVer}] {$tipeLabel} dari {$userName}";
    $adminUrl = "https://5dquran.amiri.id/api.php?a=chat_admin&uid=" . urlencode($userId);

    $body = "Assalamu'alaikum Warahmatullahi Wabarakatuh,\n\n"
          . "Ada pesan / laporan baru dari aplikasi 5D Quran (v{$appVer}):\n\n"
          . "========================================================\n"
          . "Kategori       : " . strtoupper($tipe) . " ({$tipeLabel})\n"
          . "Pengirim       : {$userName}\n"
          . "User ID        : {$userId}\n"
          . "Versi Aplikasi : {$appVer}\n"
          . "Perangkat      : {$deviceInfo}\n"
          . "Waktu          : " . date('Y-m-d H:i:s') . "\n"
          . "========================================================\n\n"
          . "ISI PESAN:\n"
          . "--------------------------------------------------------\n"
          . $pesan . "\n"
          . "--------------------------------------------------------\n\n"
          . "UNTUK MEMBALAS PESAN INI LANGSUNG KE HP PENGGUNA:\n"
          . "Silakan klik tautan di bawah ini untuk membuka halaman balas cepat:\n"
          . $adminUrl . "\n\n"
          . "Balasan Anda akan langsung muncul di dalam aplikasi 5D Quran pengguna.\n\n"
          . "Jazakallahu khairan,\n5D Quran System\n";

    $headers = "From: 5D Quran <noreply@amiri.id>\r\n"
             . "Reply-To: noreply@amiri.id\r\n"
             . "X-Mailer: PHP/" . phpversion();

    @mail($destEmail, $subject, $body, $headers);

    q5_json([
        'ok'         => true,
        'message_id' => $msgId,
        'info'       => 'Pesan Anda telah berhasil diteruskan ke tim pengembang.'
    ]);

// ── Ambil riwayat chat & jawaban untuk user_id tertentu ──
case 'chat_get':
    $userId = trim((string)($_GET['user_id'] ?? $in['user_id'] ?? ''));
    if ($userId === '') {
        q5_json(['ok' => false, 'error' => 'user_id diperlukan'], 400);
    }

    $all = q5_read_all_feedback();
    $userChats = [];
    foreach ($all as $m) {
        if (isset($m['user_id']) && $m['user_id'] === $userId) {
            $userChats[] = [
                'id'         => $m['id'],
                'sender'     => $m['sender'], // 'user' atau 'admin'
                'user_name'  => $m['user_name'] ?? '',
                'tipe'       => $m['tipe'] ?? 'chat',
                'pesan'      => $m['pesan'],
                'created_at' => $m['created_at'] ?? '',
                'timestamp'  => $m['timestamp'] ?? 0
            ];
        }
    }
    q5_json([
        'ok'       => true,
        'user_id'  => $userId,
        'messages' => $userChats
    ]);

// ── Balasan admin ke user ──
case 'chat_reply':
    $userId    = trim((string)($in['user_id'] ?? $_POST['user_id'] ?? ''));
    $balasan   = trim((string)($in['pesan'] ?? $_POST['pesan'] ?? ''));
    $adminName = trim((string)($in['admin_name'] ?? $_POST['admin_name'] ?? 'Tim Pengembang 5D Quran'));

    if ($userId === '' || $balasan === '') {
        q5_json(['ok' => false, 'error' => 'user_id dan pesan balasan harus diisi'], 400);
    }

    $allChats = q5_read_all_feedback();
    $msgId = 'rep_' . time() . '_' . substr(md5(uniqid(mt_rand(), true)), 0, 6);
    $replyMsg = [
        'id'          => $msgId,
        'user_id'     => $userId,
        'user_name'   => $adminName,
        'tipe'        => 'balasan',
        'pesan'       => $balasan,
        'sender'      => 'admin',
        'created_at'  => date('Y-m-d H:i:s'),
        'timestamp'   => time()
    ];

    $allChats[] = $replyMsg;
    q5_save_all_feedback($allChats);

    if (isset($_POST['web_submit'])) {
        header('Location: api.php?a=chat_admin&uid=' . urlencode($userId) . '&success=1');
        exit;
    }

    q5_json(['ok' => true, 'reply_id' => $msgId, 'status' => 'Balasan tersimpan']);

// ── Debug status penyimpanan feedback ──
case 'chat_debug':
    $candidates = [
        __DIR__ . '/data/feedback_messages.json',
        __DIR__ . '/feedback_messages.json',
        '/var/tmp/5dquran_feedback_messages.json',
        sys_get_temp_dir() . '/5dquran_feedback_messages.json',
        '/tmp/5dquran_feedback_messages.json'
    ];
    $status = [];
    foreach ($candidates as $c) {
        $d = dirname($c);
        $status[$c] = [
            'dir_exists'   => is_dir($d),
            'dir_writable' => is_writable($d),
            'file_exists'  => file_exists($c),
            'filesize'     => file_exists($c) ? filesize($c) : 0,
        ];
    }
    q5_json([
        'ok'            => true,
        'resolved_file' => q5_feedback_file(),
        'all_count'     => count(q5_read_all_feedback()),
        'paths'         => $status
    ]);

// ── Web Admin Interface Balas Chat ──
case 'chat_admin':
    $uid = trim((string)($_GET['uid'] ?? ''));
    $all = q5_read_all_feedback();

    // Filter pesan untuk uid jika ada, atau kelompokkan per user_id
    $threads = [];
    foreach ($all as $m) {
        $u = $m['user_id'] ?? 'unknown';
        if (!isset($threads[$u])) $threads[$u] = [];
        $threads[$u][] = $m;
    }

    $selectedThread = ($uid !== '' && isset($threads[$uid])) ? $threads[$uid] : ($uid !== '' ? [] : null);
    $success = isset($_GET['success']);

    header('Content-Type: text/html; charset=utf-8');
    ?>
    <!DOCTYPE html>
    <html lang="id">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Admin Chat & Pelaporan — 5D Quran</title>
        <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #080812; color: #F8FAFC; margin: 0; padding: 20px; }
            .container { max-width: 800px; margin: 0 auto; background: #121324; border: 1px solid #262847; border-radius: 16px; padding: 24px; }
            h1 { color: #F0C060; font-size: 1.5rem; margin-top: 0; }
            .alert-success { background: rgba(16,185,129,0.2); border: 1px solid #10B981; color: #10B981; padding: 12px; border-radius: 8px; margin-bottom: 16px; }
            .bubble { padding: 12px 16px; border-radius: 12px; margin-bottom: 12px; max-width: 85%; }
            .bubble-user { background: #1a1c33; border: 1px solid #38BDF8; margin-right: auto; }
            .bubble-admin { background: rgba(240,192,96,0.15); border: 1px solid #F0C060; margin-left: auto; text-align: right; }
            .meta { font-size: 0.75rem; color: #94A3B8; margin-bottom: 4px; }
            .text { font-size: 0.95rem; line-height: 1.4; white-space: pre-wrap; }
            textarea { width: 100%; box-sizing: border-box; background: #181a30; border: 1px solid #262847; color: #F8FAFC; border-radius: 8px; padding: 12px; font-size: 1rem; min-height: 100px; }
            button { background: #F0C060; color: #080812; font-weight: bold; border: none; padding: 12px 24px; border-radius: 8px; cursor: pointer; font-size: 1rem; margin-top: 10px; }
            button:hover { background: #FDE68A; }
            .thread-list { list-style: none; padding: 0; }
            .thread-item { padding: 12px; border-bottom: 1px solid #262847; }
            .thread-item a { color: #38BDF8; text-decoration: none; font-weight: bold; }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>💬 Balas Chat &amp; Pelaporan Pengguna — 5D Quran</h1>
            <?php if ($success): ?>
                <div class="alert-success">✅ Balasan Anda berhasil disimpan dan akan langsung tampil di HP pengguna!</div>
            <?php endif; ?>

            <?php if ($uid !== ''): ?>
                <p><a href="api.php?a=chat_admin" style="color:#94A3B8;">&larr; Kembali ke daftar semua pengguna</a></p>
                <h3>Percakapan dengan User ID: <span style="color:#38BDF8;"><?= htmlspecialchars($uid) ?></span></h3>
                <div style="background:#080812; padding:16px; border-radius:12px; margin-bottom:20px; max-height:450px; overflow-y:auto;">
                    <?php if (empty($selectedThread)): ?>
                        <p style="color:#94A3B8;">Belum ada pesan untuk pengguna ini.</p>
                    <?php else: ?>
                        <?php foreach ($selectedThread as $msg): ?>
                            <?php $isAdmin = ($msg['sender'] ?? '') === 'admin'; ?>
                            <div class="bubble <?= $isAdmin ? 'bubble-admin' : 'bubble-user' ?>">
                                <div class="meta">
                                    <strong><?= htmlspecialchars($msg['user_name'] ?? ($isAdmin ? 'Tim Pengembang' : 'Pengguna')) ?></strong>
                                    <?php if (!empty($msg['tipe']) && !$isAdmin): ?>
                                        <span style="color:#F0C060;">[<?= htmlspecialchars(strtoupper($msg['tipe'])) ?>]</span>
                                    <?php endif; ?>
                                    · <?= htmlspecialchars($msg['created_at'] ?? '') ?>
                                </div>
                                <div class="text"><?= htmlspecialchars($msg['pesan'] ?? '') ?></div>
                            </div>
                        <?php endforeach; ?>
                    <?php endif; ?>
                </div>

                <h3>Tulis Balasan untuk Pengguna Ini:</h3>
                <form method="POST" action="api.php?a=chat_reply">
                    <input type="hidden" name="web_submit" value="1">
                    <input type="hidden" name="user_id" value="<?= htmlspecialchars($uid) ?>">
                    <textarea name="pesan" placeholder="Tulis jawaban atau respon Anda di sini... Jawaban ini akan langsung muncul di HP pengguna." required></textarea>
                    <br>
                    <button type="submit">🚀 Kirim Balasan ke Pengguna</button>
                </form>

            <?php else: ?>
                <h3>Daftar Percakapan Pengguna (<?= count($threads) ?> pengguna):</h3>
                <?php if (empty($threads)): ?>
                    <p style="color:#94A3B8;">Belum ada laporan atau masukan dari pengguna.</p>
                <?php else: ?>
                    <ul class="thread-list">
                        <?php foreach ($threads as $tUid => $tMsgs): ?>
                            <?php 
                            $tMsgsArr = array_values($tMsgs);
                            $last = !empty($tMsgsArr) ? $tMsgsArr[count($tMsgsArr) - 1] : []; 
                            ?>
                            <li class="thread-item">
                                <a href="api.php?a=chat_admin&uid=<?= urlencode((string)$tUid) ?>">
                                    User: <?= htmlspecialchars($last['user_name'] ?? (string)$tUid) ?> (<?= htmlspecialchars((string)$tUid) ?>)
                                </a>
                                <div class="meta" style="margin-top:4px;">
                                    Pesan terakhir: <?= htmlspecialchars(mb_substr($last['pesan'] ?? '', 0, 70)) ?>... · <?= htmlspecialchars($last['created_at'] ?? '') ?>
                                    (<?= count($tMsgs) ?> pesan)
                                </div>
                            </li>
                        <?php endforeach; ?>
                    </ul>
                <?php endif; ?>
            <?php endif; ?>
        </div>
    </body>
    </html>
    <?php
    exit;

}

q5_json(['ok' => false, 'error' => 'aksi tidak dikenal'], 404);



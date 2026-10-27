import http.server
import socketserver
import os
import socket

PORT = 8080
DIRECTORY = os.path.dirname(os.path.abspath(__file__))

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

class ApkHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def do_GET(self):
        if self.path == '/' or self.path == '/index.html':
            # Find latest apk and aab
            apks = [f for f in os.listdir(DIRECTORY) if f.endswith('.apk')]
            apks.sort(key=lambda x: os.path.getmtime(os.path.join(DIRECTORY, x)), reverse=True)
            latest_apk = apks[0] if apks else ''
            size_mb = f"{os.path.getsize(os.path.join(DIRECTORY, latest_apk)) / (1024*1024):.1f} MB" if latest_apk else '0 MB'

            aabs = [f for f in os.listdir(DIRECTORY) if f.endswith('.aab')]
            aabs.sort(key=lambda x: os.path.getmtime(os.path.join(DIRECTORY, x)), reverse=True)
            latest_aab = aabs[0] if aabs else ''
            aab_size_mb = f"{os.path.getsize(os.path.join(DIRECTORY, latest_aab)) / (1024*1024):.1f} MB" if latest_aab else '0 MB'

            html = f"""<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>5D Quran — Install APK & Play Store AAB</title>
    <style>
        body {{
            background: #080812;
            color: #F8FAFC;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            margin: 0;
            padding: 20px;
            box-sizing: border-box;
        }}
        .card {{
            background: #121324;
            border: 1px solid #262847;
            border-radius: 20px;
            padding: 30px 24px;
            max-width: 420px;
            width: 100%;
            text-align: center;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
        }}
        .badge {{
            display: inline-block;
            background: rgba(240, 192, 96, 0.15);
            color: #F0C060;
            padding: 4px 12px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: bold;
            margin-bottom: 16px;
        }}
        h1 {{
            font-size: 22px;
            margin: 0 0 8px;
            color: #F8FAFC;
        }}
        p {{
            color: #94A3B8;
            font-size: 14px;
            margin: 0 0 20px;
            line-height: 1.5;
        }}
        .btn {{
            display: block;
            background: linear-gradient(135deg, #F0C060, #C8860A);
            color: #080812;
            text-decoration: none;
            padding: 14px;
            border-radius: 12px;
            font-weight: bold;
            font-size: 15px;
            margin-bottom: 12px;
        }}
        .btn-aab {{
            background: linear-gradient(135deg, #00FFD5, #00A389);
            color: #080812;
        }}
        .meta {{
            margin-top: 8px;
            margin-bottom: 18px;
            font-size: 11px;
            color: #64748B;
        }}
    </style>
</head>
<body>
    <div class="card">
        <div class="badge">RILIS &amp; PENGUJIAN</div>
        <h1>📱 5D Quran Native</h1>
        <p>Pilih file yang ingin diunduh:</p>
        <a href="/{latest_apk}" class="btn" download>📥 Download APK (Pasang di HP)</a>
        <div class="meta">{latest_apk} ({size_mb})</div>
        <a href="/{latest_aab}" class="btn btn-aab" download>🚀 Download AAB (Upload ke Play Store)</a>
        <div class="meta">{latest_aab} ({aab_size_mb})</div>
    </div>
</body>
</html>"""
            content = html.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(content)))
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(content)
        else:
            super().do_GET()

class ReusableThreadingServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

def run():
    socketserver.TCPServer.allow_reuse_address = True
    start_port = PORT
    httpd = None
    active_port = start_port

    for port in range(start_port, start_port + 20):
        try:
            httpd = ReusableThreadingServer(("", port), ApkHandler)
            active_port = port
            break
        except OSError as e:
            # WinError 10048 / EADDRINUSE
            if getattr(e, 'winerror', None) == 10048 or getattr(e, 'errno', None) in (48, 98, 10048):
                continue
            raise

    if not httpd:
        print(f"[ERROR] Port {start_port} sampai {start_port + 19} sedang digunakan.")
        return

    local_ip = get_local_ip()
    print("\n=======================================================")
    print("  SERVER APK AKTIF!")
    print("  Buka browser di HP (Chrome / browser bawaan):")
    print(f"  --> http://{local_ip}:{active_port}")
    if active_port != start_port:
        print(f"  (Catatan: Port {start_port} sedang sibuk, otomatis memakai port {active_port})")
    print("  Tekan Ctrl+C di terminal ini untuk berhenti.")
    print("=======================================================\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[INFO] Server dihentikan.")
    finally:
        httpd.server_close()

if __name__ == '__main__':
    run()

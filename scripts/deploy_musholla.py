import paramiko
import os
import sys
import time

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

def safe_print(text):
    print(text)
    sys.stdout.flush()

def deploy():
    hostname = '192.168.8.8'
    username = 'alijaya'
    password = '060111'
    remote_base = '/home/alijaya/musholla'
    local_base = os.path.abspath('d:/absensi-sekoah')

    safe_print(f"Connecting to {hostname} as {username}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(hostname, port=22, username=username, password=password, timeout=15)
    safe_print("SSH connected.")

    # 1. Pastikan folder views/queue dan public/js ada di remote
    ensure_dir_cmd = f"mkdir -p {remote_base}/src/views/queue {remote_base}/public/js"
    ssh.exec_command(ensure_dir_cmd)
    safe_print(f"Ensured remote directories: queue, public/js")

    # 2. Daftar file yang akan diunggah (HANYA KODE & TEMPLATE, DATABASE SQLITE TIDAK DISENTUH!)
    files_to_sync = [
        "src/database/db.js",
        "src/services/prayerService.js",
        "src/services/queueService.js",
        "src/services/ttsService.js",
        "src/routes/kiosk.js",
        "src/routes/queue.js",
        "src/routes/cms.js",
        "src/routes/idcard.js",
        "src/server.js",
        "src/views/kiosk.ejs",
        "src/views/queue/display.ejs",
        "src/views/queue/desk.ejs",
        "src/views/dashboard/sidebar.ejs",
        "src/views/dashboard/cms.ejs",
        "src/views/idcard-print.ejs",
        "public/js/html2canvas.min.js",
        "public/js/jszip.min.js",
    ]

    sftp = ssh.open_sftp()
    for rel_path in files_to_sync:
        local_file = os.path.join(local_base, rel_path.replace('/', os.sep))
        remote_file = f"{remote_base}/{rel_path}"
        if not os.path.exists(local_file):
            safe_print(f"ERROR: Local file not found: {local_file}")
            return
        safe_print(f"Uploading {rel_path} -> {remote_file}...")
        sftp.put(local_file, remote_file)
        safe_print(f"Uploaded {rel_path} successfully.")
    sftp.close()

    # 3. Remote Node Syntax Check
    safe_print("\nChecking Node syntax on remote server...")
    check_cmd = (
        f"export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && "
        f"node -c {remote_base}/src/database/db.js && "
        f"node -c {remote_base}/src/services/prayerService.js && "
        f"node -c {remote_base}/src/services/queueService.js && "
        f"node -c {remote_base}/src/services/ttsService.js && "
        f"node -c {remote_base}/src/routes/kiosk.js && "
        f"node -c {remote_base}/src/routes/queue.js && "
        f"node -c {remote_base}/src/routes/cms.js && "
        f"node -c {remote_base}/src/routes/idcard.js && "
        f"node -c {remote_base}/src/server.js"
    )
    stdin, stdout, stderr = ssh.exec_command(check_cmd, timeout=10)
    err = stderr.read().decode('utf-8', errors='replace').strip()
    if err:
        safe_print(f"Syntax Error on Remote:\n{err}")
        ssh.close()
        return
    safe_print("Remote syntax check PASSED cleanly!")

    # 4. Restart PM2 'musholla' ONLY
    safe_print("\nRestarting PM2 process 'musholla' (Port 3010)...")
    restart_cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && pm2 restart musholla && pm2 status"
    stdin, stdout, stderr = ssh.exec_command(restart_cmd, timeout=15)
    out = stdout.read().decode('utf-8', errors='replace').strip()
    safe_print(f"PM2 Restart Output:\n{out}")

    # 5. Check Logs
    time.sleep(3)
    safe_print("\nChecking PM2 logs for 'musholla'...")
    log_cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && pm2 logs musholla --lines 25 --nostream"
    stdin, stdout, stderr = ssh.exec_command(log_cmd, timeout=10)
    out = stdout.read().decode('utf-8', errors='replace').strip()
    safe_print(f"Recent Logs:\n{out}")

    # 6. Curl verification on port 3010
    safe_print("\nVerifying HTTP endpoints on port 3010...")
    test_cmd = (
        "curl -s -o /dev/null -w 'HTTP Code Display: %{http_code}\\n' http://127.0.0.1:3010/display && "
        "curl -s http://127.0.0.1:3010/api/prayer-times"
    )
    stdin, stdout, stderr = ssh.exec_command(test_cmd, timeout=10)
    test_out = stdout.read().decode('utf-8', errors='replace').strip()
    safe_print(f"Test Result:\n{test_out}")

    ssh.close()
    safe_print("\n🎉 MUSHOLLA (TPQ AL-MUHAJIRIN) DEPLOYMENT COMPLETED SUCCESSFULLY! 🎉")

if __name__ == '__main__':
    deploy()

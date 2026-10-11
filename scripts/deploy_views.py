import paramiko
import os
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

def deploy():
    hostname = '192.168.8.8'
    username = 'alijaya'
    password = '060111'
    remote_dir = '/home/alijaya/sd1'
    
    files_to_upload = [
        ('ecosystem.config.js', f"{remote_dir}/ecosystem.config.js"),
        ('package.json', f"{remote_dir}/package.json"),
        ('src/database/db.js', f"{remote_dir}/src/database/db.js"),
        ('src/database/seed.js', f"{remote_dir}/src/database/seed.js"),
        ('src/utils/timeHelper.js', f"{remote_dir}/src/utils/timeHelper.js"),
        ('src/routes/cms.js', f"{remote_dir}/src/routes/cms.js"),
        ('src/routes/idcard.js', f"{remote_dir}/src/routes/idcard.js"),
        ('src/routes/landing.js', f"{remote_dir}/src/routes/landing.js"),
        ('src/routes/whatsapp.js', f"{remote_dir}/src/routes/whatsapp.js"),
        ('src/routes/attendances.js', f"{remote_dir}/src/routes/attendances.js"),
        ('src/routes/dashboard.js', f"{remote_dir}/src/routes/dashboard.js"),
        ('src/routes/kiosk.js', f"{remote_dir}/src/routes/kiosk.js"),
        ('src/services/bot.js', f"{remote_dir}/src/services/bot.js"),
        ('src/services/leaveService.js', f"{remote_dir}/src/services/leaveService.js"),
        ('src/services/queue.js', f"{remote_dir}/src/services/queue.js"),
        ('src/services/scheduler.js', f"{remote_dir}/src/services/scheduler.js"),
        ('src/services/summary.js', f"{remote_dir}/src/services/summary.js"),
        ('src/services/whatsapp.js', f"{remote_dir}/src/services/whatsapp.js"),
        ('src/services/waLidStore.js', f"{remote_dir}/src/services/waLidStore.js"),
        ('data/wa-lid-map.json', f"{remote_dir}/data/wa-lid-map.json"),
        ('src/views/landing.ejs', f"{remote_dir}/src/views/landing.ejs"),
        ('src/views/dashboard/whatsapp.ejs', f"{remote_dir}/src/views/dashboard/whatsapp.ejs"),
        ('src/views/dashboard/cms.ejs', f"{remote_dir}/src/views/dashboard/cms.ejs"),
        ('src/views/idcard-print.ejs', f"{remote_dir}/src/views/idcard-print.ejs"),
        ('src/views/dashboard/sidebar.ejs', f"{remote_dir}/src/views/dashboard/sidebar.ejs"),
        ('src/views/dashboard/index.ejs', f"{remote_dir}/src/views/dashboard/index.ejs"),
    ]

    print(f"Connecting to {hostname} as {username}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(hostname, port=22, username=username, password=password, timeout=10)
    print("SSH connected.")

    # Pastikan folder src/utils ada di server
    ssh.exec_command(f"mkdir -p {remote_dir}/src/utils")
    import time; time.sleep(1)

    sftp = ssh.open_sftp()
    for local_path, remote_path in files_to_upload:
        full_local = os.path.abspath(local_path)
        print(f"Uploading {full_local} -> {remote_path}...")
        sftp.put(full_local, remote_path)
    sftp.close()
    print("Upload completed.")

    cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && cd /home/alijaya/sd1 && npm install && pm2 restart sd1 && pm2 status"
    print(f"Executing: {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    if out:
        print("STDOUT:\n", out)
    if err:
        print("STDERR:\n", err)

    ssh.close()
    print("Deploy successfully finished!")

if __name__ == '__main__':
    deploy()

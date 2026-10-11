import paramiko
import os
import sys

# Ensure UTF-8 output on Windows console
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

def deploy():
    hostname = '192.168.8.8'
    username = 'alijaya'
    password = '060111'
    remote_dir = '/home/alijaya/sd1'
    
    local_bot_js = os.path.abspath('src/services/bot.js')
    remote_bot_js = f"{remote_dir}/src/services/bot.js"

    print(f"Connecting to {hostname} as {username}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(hostname, port=22, username=username, password=password, timeout=10)
    print("SSH connected.")

    sftp = ssh.open_sftp()
    print(f"Uploading {local_bot_js} -> {remote_bot_js}...")
    sftp.put(local_bot_js, remote_bot_js)
    sftp.close()
    print("Upload completed.")

    cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && pm2 restart sd1 && pm2 status"
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

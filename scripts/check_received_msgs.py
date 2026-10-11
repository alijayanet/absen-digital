import paramiko
import sys
import time

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=15, banner_timeout=30)

cmd = "grep -E 'Pesan diterima|Pesan diabaikan' ~/.pm2/logs/sd1-out.log | tail -n 30"
stdin, stdout, stderr = ssh.exec_command(f"export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && {cmd}")
out = stdout.read().decode('utf-8', errors='replace')
print("=== RECENT RECEIVED MESSAGES IN SD1 ===")
print(out)
ssh.close()

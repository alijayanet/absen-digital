import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

stdin, stdout, stderr = ssh.exec_command("ps aux | grep -i 'sd1' | grep -v grep")
out = stdout.read().decode('utf-8', errors='replace')
print("=== PS SD1 ===")
print(out)

stdin, stdout, stderr = ssh.exec_command("fuser /home/alijaya/sd1/data/baileys_auth/creds.json 2>/dev/null || true")
out = stdout.read().decode('utf-8', errors='replace')
print("=== FUSER CREDS.JSON ===")
print(out)

ssh.close()

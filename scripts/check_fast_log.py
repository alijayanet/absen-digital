import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

cmd = "tail -n 500 ~/.pm2/logs/sd1-out.log | grep -E 'Pesan|rekap|menu|conflict|Terkoneksi|Status' | tail -n 40"
stdin, stdout, stderr = ssh.exec_command(cmd)
out = stdout.read().decode('utf-8', errors='replace')
print("=== TAIL THEN GREP ===")
print(out)
ssh.close()

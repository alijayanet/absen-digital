import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

cmd = "grep -n -i -C 5 'Phone: 6281947215703): \"menu\"' ~/.pm2/logs/sd1-out.log || grep -n -i -C 5 'Phone: 6281947215703): \"rekap menu\"' ~/.pm2/logs/sd1-out.log || echo 'None'"
stdin, stdout, stderr = ssh.exec_command(cmd)
print("=== CONTEXT OF MENU MESSAGE ===")
print(stdout.read().decode('utf-8', errors='replace'))
ssh.close()

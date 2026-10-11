import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

stdin, stdout, stderr = ssh.exec_command("tail -n 200 /home/alijaya/.pm2/logs/sd1-out.log")
stdin.close()
lines = stdout.readlines()
print(f"Total lines read: {len(lines)}")
for line in lines:
    if any(k in line.lower() for k in ['pesan', '81947215703', 'rekap', 'menu', 'conflict', '440', 'terkoneksi']):
        print(line.strip())

ssh.close()

import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)
sftp = ssh.open_sftp()

with sftp.open('/home/alijaya/cctv-monitoring/whatsapp_bot.js', 'r') as f:
    content = f.read().decode('utf-8', errors='replace')
    lines = content.split('\n')
    for j in range(275, min(len(lines), 340)):
        print(f"{j+1}: {lines[j]}")

sftp.close()
ssh.close()

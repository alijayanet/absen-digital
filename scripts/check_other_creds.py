import paramiko
import sys
import json

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)
sftp = ssh.open_sftp()

for path in [
    '/home/alijaya/pulsa/auth_info_baileys/creds.json',
    '/home/alijaya/billing_backup_safe/auth_info_baileys/creds.json',
]:
    try:
        with sftp.open(path, 'r') as f:
            data = json.loads(f.read().decode('utf-8'))
            print(path, "->", data.get('me', {}).get('id'), "regId:", data.get('registrationId'))
    except Exception as e:
        print(path, "-> error:", e)

sftp.close()
ssh.close()

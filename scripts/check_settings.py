import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && cd /home/alijaya/sd1 && node -e \"const db = require('./src/database/db'); console.log(db.prepare('SELECT * FROM settings').all());\""
stdin, stdout, stderr = ssh.exec_command(cmd)
print("=== SETTINGS ===")
print(stdout.read().decode('utf-8', errors='replace'))
ssh.close()

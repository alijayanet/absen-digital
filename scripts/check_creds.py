import paramiko
import sys
import json

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

def run(cmd):
    full_cmd = f"export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && {cmd}"
    stdin, stdout, stderr = ssh.exec_command(full_cmd)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    return out + err

print("=== CHECK ME IN SD1 CREDS ===")
print(run("node -e \"const c = require('/home/alijaya/sd1/data/baileys_auth/creds.json'); console.log('SD1:', JSON.stringify(c.me));\""))

print("=== CHECK ME IN BILLING-RTRW CREDS ===")
print(run("node -e \"const c = require('/home/alijaya/billing-rtrw/auth_info_baileys/creds.json'); console.log('BILLING:', JSON.stringify(c.me));\""))

print("=== CHECK ME IN CCTV-MONITORING CREDS ===")
print(run("node -e \"const c = require('/home/alijaya/cctv-monitoring/auth_info_baileys/creds.json'); console.log('CCTV:', JSON.stringify(c.me));\" 2>/dev/null || true"))

ssh.close()

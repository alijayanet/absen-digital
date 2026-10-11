import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

def run(cmd):
    full_cmd = f"export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && {cmd}"
    stdin, stdout, stderr = ssh.exec_command(full_cmd)
    return stdout.read().decode('utf-8', errors='replace')

print("=== FIND CREDS.JSON (FAST) ===")
print(run("find /home/alijaya -maxdepth 4 -name 'creds.json' -not -path '*/node_modules/*' 2>/dev/null"))

print("=== SEARCH FOR 081947215703 IN SD1 LOGS ===")
print(run("grep -i '081947215703' ~/.pm2/logs/sd1* 2>/dev/null || echo 'Not found in pm2 logs'"))
print(run("grep -i '81947215703' ~/.pm2/logs/sd1* 2>/dev/null || echo 'Not found 81947215703'"))

print("=== SEARCH FOR 'rekap' IN SD1 LOGS ===")
print(run("grep -i 'rekap' ~/.pm2/logs/sd1-out.log | tail -n 20 2>/dev/null || true"))

print("=== CHECK WHAT BILLING-RTRW DOES ===")
print(run("head -n 30 /home/alijaya/billing-rtrw/app-customer.js 2>/dev/null || true"))

ssh.close()

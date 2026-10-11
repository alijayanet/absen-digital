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

print("=== CHECK OTHER APPS USING BAILEYS OR WHATSAPP ===")
print(run("grep -rn 'baileys' /home/alijaya/*/package.json 2>/dev/null"))

print("=== CHECK CREDS.JSON ACROSS /home/alijaya ===")
print(run("find /home/alijaya -name 'creds.json' 2>/dev/null"))

print("=== CHECK BILLING LOGS FOR WHATSAPP ===")
print(run("tail -n 20 ~/.pm2/logs/billing-out.log 2>/dev/null || true"))

print("=== CHECK PM2 APPS ENVIRONMENT / CWD ===")
print(run("pm2 jlist | jq '.[] | {name: .name, pm_id: .pm_id, pid: .pid, cwd: .pm2_env.pm_cwd, status: .pm2_env.status}' 2>/dev/null || pm2 list"))

ssh.close()

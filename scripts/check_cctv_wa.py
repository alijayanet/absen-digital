import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(f"export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && {cmd}")
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    return out + err

print("=== CHECK CCTV-MONITORING WHATSAPP LOGS / CODE ===")
print(run("cat /home/alijaya/cctv-monitoring/whatsapp_bot.js | grep -n 'makeWASocket' -A 10 -B 2"))

print("=== CHECK CCTV LOG FILE IF ANY ===")
print(run("ls -la /home/alijaya/cctv-monitoring/logs/ 2>/dev/null || ls -la /home/alijaya/cctv-monitoring/*.log 2>/dev/null || echo 'no log file'"))

ssh.close()

import paramiko
import sys
import time

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

time.sleep(2)
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

def run(cmd):
    stdin, stdout, stderr = ssh.exec_command(f"export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && {cmd}")
    out = stdout.read().decode('utf-8', errors='replace')
    return out

print("=== CHECK PARENT OF 1087 ===")
print(run("ps -fp 1087"))

print("=== CHECK WHAT cctv-monitoring IS ===")
print(run("head -n 25 /home/alijaya/cctv-monitoring/index.js"))

print("=== CHECK CRONTAB ===")
print(run("crontab -l || true"))

print("=== CHECK SYSTEMD USER/SYSTEM UNITS FOR CCTV ===")
print(run("ls -la /etc/systemd/system/ | grep -i cctv || true"))

ssh.close()

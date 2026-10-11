import paramiko
import sys
import time

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

script = """
export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin
echo "--- PS 1087 ---"
ps -fp 1087
echo "--- SYSTEMD STATUS 1087 ---"
cat /proc/1087/cgroup 2>/dev/null
echo "--- CCTV HEAD ---"
head -n 25 /home/alijaya/cctv-monitoring/index.js 2>/dev/null
echo "--- DONE ---"
"""

stdin, stdout, stderr = ssh.exec_command(f"bash -c '{script}'")
stdin.close()
print(stdout.read().decode('utf-8', errors='replace'))
ssh.close()

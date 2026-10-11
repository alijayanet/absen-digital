import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

stdin, stdout, stderr = ssh.exec_command("echo '060111' | sudo -S systemctl status cctv-web.service --no-pager")
out = stdout.read().decode('utf-8', errors='replace')
err = stderr.read().decode('utf-8', errors='replace')
print("=== CCTV SERVICE ===")
print(out)
if err:
    print("ERR:", err)

ssh.close()

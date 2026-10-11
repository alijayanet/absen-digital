import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

stdin, stdout, stderr = ssh.exec_command("tail -n 80 ~/.pm2/logs/sd1-out.log")
print("=== TAIL 80 ===")
print(stdout.read().decode('utf-8', errors='replace'))

stdin, stdout, stderr = ssh.exec_command("tail -n 30 ~/.pm2/logs/sd1-error.log")
print("=== ERROR LOG ===")
print(stdout.read().decode('utf-8', errors='replace'))

ssh.close()

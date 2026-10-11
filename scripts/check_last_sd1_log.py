import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

cmd = "tail -n 25 ~/.pm2/logs/sd1-error.log"
stdin, stdout, stderr = ssh.exec_command(cmd)
print("=== LAST 25 LINES OF SD1-ERROR.LOG ===")
print(stdout.read().decode('utf-8', errors='replace'))
ssh.close()

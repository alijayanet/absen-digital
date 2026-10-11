import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

cmd = "curl -s http://localhost:3009/whatsapp/status || curl -s http://localhost:3009/api/whatsapp/status"
stdin, stdout, stderr = ssh.exec_command(cmd)
print("=== STATUS ENDPOINT ===")
print(stdout.read().decode('utf-8', errors='replace'))
ssh.close()

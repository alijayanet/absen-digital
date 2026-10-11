import paramiko
import sys
import json

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin && pm2 jlist"
stdin, stdout, stderr = ssh.exec_command(cmd)
data = stdout.read().decode('utf-8', errors='replace')
procs = json.loads(data)
for p in procs:
    if p['name'] == 'sd1':
        print("Name:", p['name'])
        print("PID:", p['pid'])
        print("Script:", p['pm2_env']['pm_exec_path'])
        print("CWD:", p['pm2_env']['pm_cwd'])
        print("Status:", p['pm2_env']['status'])
        print("Restarts:", p['pm2_env']['restart_time'])

ssh.close()

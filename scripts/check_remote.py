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
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    return out, err

out, err = run("pm2 list")
print("=== PM2 LIST ===")
print(out)

out, err = run("tail -n 40 ~/.pm2/logs/sd1-out.log")
print("=== SD1 OUT LOG ===")
print(out)

out, err = run("tail -n 40 ~/.pm2/logs/sd1-error.log")
print("=== SD1 ERR LOG ===")
print(out)

ssh.close()

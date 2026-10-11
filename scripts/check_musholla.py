import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

def check():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect('192.168.8.8', username='alijaya', password='060111', timeout=10)
    
    cmd = """
curl -s -o /dev/null -w 'HTTP Code /display: %{http_code}\\n' http://127.0.0.1:3010/display
curl -s -i http://127.0.0.1:3010/kiosk | head -n 5
curl -s -o /dev/null -w 'HTTP Code /kiosk?mode=kiosk_only: %{http_code}\\n' "http://127.0.0.1:3010/kiosk?mode=kiosk_only"
curl -s http://127.0.0.1:3010/display | grep -o 'id="kioskPane"'
curl -s http://127.0.0.1:3010/display | grep -o 'id="queuePane"'
curl -s http://127.0.0.1:3010/display | grep -o 'id="tablesGrid"'
"""
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    print("STDOUT:\n", out)
    if err:
        print("STDERR:\n", err)
    ssh.close()

if __name__ == '__main__':
    check()

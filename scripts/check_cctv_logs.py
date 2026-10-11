import paramiko
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)

script = """
echo "=== CCTV SERVICE LOGS ==="
journalctl -u cctv-web.service -n 25 --no-pager 2>/dev/null || true
echo "=== CCTV WHATSAPP BOT JS ==="
cat /home/alijaya/cctv-monitoring/whatsapp_bot.js | grep -E "baileys|makeWASocket|auth" | head -n 20 2>/dev/null || true
"""

stdin, stdout, stderr = ssh.exec_command(f"bash -c '{script}'")
stdin.close()
print(stdout.read().decode('utf-8', errors='replace'))
ssh.close()

import paramiko
import sys
import json

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.8.8', port=22, username='alijaya', password='060111', timeout=10)
sftp = ssh.open_sftp()

def get_creds_info(path):
    try:
        with sftp.open(path, 'r') as f:
            data = json.loads(f.read().decode('utf-8'))
            return {
                'id': data.get('me', {}).get('id'),
                'registrationId': data.get('registrationId'),
            }
    except Exception as e:
        return {'error': str(e)}

print("CCTV:", get_creds_info('/home/alijaya/cctv-monitoring/auth_info_baileys/creds.json'))
print("SD1:", get_creds_info('/home/alijaya/sd1/data/baileys_auth/creds.json'))

sftp.close()
ssh.close()

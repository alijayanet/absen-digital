import paramiko
import os
import sys

def safe_print(text):
    sys.stdout.buffer.write((str(text) + '\n').encode('utf-8', errors='replace'))

def deploy():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        safe_print("Connecting to 192.168.8.8...")
        client.connect('192.168.8.8', username='alijaya', password='060111', timeout=15)
        safe_print("Connected to SSH successfully.")
        
        sftp = client.open_sftp()
        
        files_to_sync = [
            ("data/wa-lid-map.json", "/home/alijaya/sd1/data/wa-lid-map.json"),
            ("src/services/waLidStore.js", "/home/alijaya/sd1/src/services/waLidStore.js"),
            ("src/services/whatsapp.js", "/home/alijaya/sd1/src/services/whatsapp.js"),
            ("src/services/bot.js", "/home/alijaya/sd1/src/services/bot.js"),
        ]
        
        local_base = "D:/absensi-sekoah"
        
        for rel_local, remote_path in files_to_sync:
            local_path = os.path.join(local_base, rel_local)
            safe_print(f"Uploading {rel_local} -> {remote_path}...")
            sftp.put(local_path, remote_path)
            safe_print(f"Uploaded {rel_local} successfully.")
            
        sftp.close()
        
        # Test node syntax on remote
        safe_print("Checking syntax on remote server...")
        check_cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin; " \
                    "node -c /home/alijaya/sd1/src/services/waLidStore.js && " \
                    "node -c /home/alijaya/sd1/src/services/whatsapp.js && " \
                    "node -c /home/alijaya/sd1/src/services/bot.js"
        stdin, stdout, stderr = client.exec_command(check_cmd, timeout=10)
        err = stderr.read().decode('utf-8', errors='replace').strip()
        if err:
            safe_print(f"Syntax Error: {err}")
            return
        safe_print("Remote syntax check passed!")
        
        # Restart PM2 process
        safe_print("Restarting PM2 process 'sd1'...")
        restart_cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin; pm2 restart sd1"
        stdin, stdout, stderr = client.exec_command(restart_cmd, timeout=15)
        out = stdout.read().decode('utf-8', errors='replace').strip()
        safe_print(f"PM2 Restart Output:\n{out}")
        
        # Check logs
        import time
        time.sleep(2)
        safe_print("Checking recent PM2 logs for sd1...")
        log_cmd = "export PATH=$PATH:/home/alijaya/.nvm/versions/node/v20.20.2/bin; pm2 logs sd1 --lines 20 --nostream"
        stdin, stdout, stderr = client.exec_command(log_cmd, timeout=10)
        out = stdout.read().decode('utf-8', errors='replace').strip()
        safe_print(f"Recent Logs:\n{out}")
        
        safe_print("\n🎉 DEPLOYMENT TO PRODUCTION SUCCESSFUL! 🎉")
        
    except Exception as e:
        safe_print(f"Deployment failed: {e}")
    finally:
        client.close()

if __name__ == '__main__':
    deploy()

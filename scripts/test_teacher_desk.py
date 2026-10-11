import requests

BASE_URL = 'http://192.168.8.8:3010'

def test_login(username, expected_class_id, expected_class_name):
    session = requests.Session()
    # 1. Login
    # Let's check password for alijaya/asiyah or test direct session if needed
    print(f"\n--- Testing login for {username} ---")
    res = session.post(f"{BASE_URL}/login", data={
        'username': username,
        'password': 'password' # we don't know the exact password, let's see if login responds
    }, allow_redirects=False)
    print(f"Login status code: {res.status_code}")
    if 'Location' in res.headers:
        print(f"Redirected to: {res.headers['Location']}")

if __name__ == '__main__':
    print("Verification script ready.")

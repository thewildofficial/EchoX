#!/usr/bin/env python3
"""
X API OAuth 2.0 Token Generator (PKCE Flow)

This script generates a user access token for testing the X API integration.
It implements the OAuth 2.0 PKCE flow which is the recommended authentication
method for mobile and desktop applications.

SECURITY NOTE:
- Your CLIENT_ID and CLIENT_SECRET should be stored in `local.properties` (git-ignored)
- Never commit tokens or credentials to version control
- Generated tokens expire after 2 hours

Usage:
    python3 scripts/get_x_token.py

The script will:
1. Open your browser for X authorization
2. Start a local server to capture the callback
3. Exchange the authorization code for an access token
4. Save the token to scripts/tokens/access_token.txt (git-ignored)
5. Display the token for manual addition to local.properties
"""

import requests
import secrets
import hashlib
import base64
import webbrowser
import os
import sys
from urllib.parse import urlparse, parse_qs
from pathlib import Path

# Configuration - Load from environment or local.properties
def load_config():
    """Load X API credentials from local.properties"""
    config = {}
    local_props = Path(__file__).parent.parent / "local.properties"
    
    if not local_props.exists():
        print("ERROR: local.properties not found!")
        print("Please create local.properties with:")
        print("  X_CLIENT_ID=your_client_id")
        print("  X_CLIENT_SECRET=your_client_secret")
        sys.exit(1)
    
    with open(local_props) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                config[key.strip()] = value.strip()
    
    if 'X_CLIENT_ID' not in config:
        print("ERROR: X_CLIENT_ID not found in local.properties")
        sys.exit(1)
    
    return config

config = load_config()
CLIENT_ID = config['X_CLIENT_ID']
CLIENT_SECRET = config.get('X_CLIENT_SECRET', '')  # Optional for PKCE

# Use the app's configured redirect URI
REDIRECT_URI = "echox://auth"
SCOPES = [
    "tweet.read",
    "tweet.write",
    "users.read",
    "offline.access",
    "media.write"  # CRITICAL for video uploads
]

print("=" * 60)
print("X API OAuth 2.0 Token Generator")
print("=" * 60)
print(f"Client ID: {CLIENT_ID[:10]}...")
print(f"Scopes: {', '.join(SCOPES)}")
print("=" * 60)

# Step 1: Generate PKCE parameters
print("\n[1/4] Generating PKCE parameters...")
code_verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode('utf-8').rstrip('=')
code_challenge = base64.urlsafe_b64encode(
    hashlib.sha256(code_verifier.encode('utf-8')).digest()
).decode('utf-8').rstrip('=')
print(f"✓ Code verifier generated (length: {len(code_verifier)})")

# Step 2: Build authorization URL and open browser
print("\n[2/4] Opening browser for X authorization...")
auth_url = (
    f"https://twitter.com/i/oauth2/authorize?"
    f"response_type=code&"
    f"client_id={CLIENT_ID}&"
    f"redirect_uri={REDIRECT_URI}&"
    f"scope={'+'.join(SCOPES)}&"
    f"state=state&"
    f"code_challenge={code_challenge}&"
    f"code_challenge_method=S256"
)

print(f"Authorization URL: {auth_url[:80]}...")
print("\n⚠️  IMPORTANT: Authorize the app in your browser...")
print(f"\nAfter authorization, you'll be redirected to: {REDIRECT_URI}?state=...&code=...")
print("The browser will show an error (that's expected for echox:// URLs on desktop)")
webbrowser.open(auth_url)

# Step 3: Manual callback input
print("\n[3/4] Waiting for callback...")
print("\n📋 Copy the ENTIRE URL from your browser's address bar after authorization")
print("   (It will look like: echox://auth?state=state&code=...)")
print("\nPaste it here:")

callback_url = input().strip()

# Parse the callback URL
try:
    parsed = urlparse(callback_url)
    query = parse_qs(parsed.query)
    
    if 'error' in query:
        print(f"\n❌ Authorization failed: {query['error'][0]}")
        sys.exit(1)
    
    if 'code' not in query:
        print(f"\n❌ No authorization code found in URL: {callback_url}")
        sys.exit(1)
    
    auth_code = query['code'][0]
    print(f"✓ Authorization code received: {auth_code[:10]}...")
    
except Exception as e:
    print(f"\n❌ Failed to parse callback URL: {e}")
    print(f"URL provided: {callback_url}")
    sys.exit(1)

# Step 4: Exchange code for token
print("\n[4/4] Exchanging authorization code for access token...")

token_data = {
    "code": auth_code,
    "grant_type": "authorization_code",
    "client_id": CLIENT_ID,
    "redirect_uri": REDIRECT_URI,
    "code_verifier": code_verifier
}

# Add client_secret if available (not required for PKCE but recommended)
if CLIENT_SECRET:
    token_data["client_secret"] = CLIENT_SECRET

try:
    token_response = requests.post(
        "https://api.twitter.com/2/oauth2/token",
        data=token_data,
        timeout=10
    )
    token_response.raise_for_status()
    tokens = token_response.json()
    
    if 'access_token' not in tokens:
        print(f"\n❌ Token exchange failed: {tokens}")
        sys.exit(1)
    
    print("✓ Access token received!")
    
    # Save token to file (git-ignored)
    tokens_dir = Path(__file__).parent / "tokens"
    tokens_dir.mkdir(exist_ok=True)
    
    token_file = tokens_dir / "access_token.txt"
    with open(token_file, 'w') as f:
        f.write(tokens['access_token'])
    
    print(f"✓ Token saved to: {token_file}")
    
    # Display token info
    print("\n" + "=" * 60)
    print("SUCCESS! Token Generated")
    print("=" * 60)
    print(f"\nAccess Token: {tokens['access_token'][:20]}...[REDACTED]")
    
    if 'refresh_token' in tokens:
        print(f"Refresh Token: Available")
    
    if 'expires_in' in tokens:
        print(f"Expires In: {tokens['expires_in']} seconds (~{tokens['expires_in']//3600} hours)")
    
    print("\n" + "=" * 60)
    print("NEXT STEPS:")
    print("=" * 60)
    print("\n1. Add this line to your local.properties:")
    print(f"\n   X_TEST_USER_TOKEN={tokens['access_token']}")
    print("\n⚠️  SECURITY WARNING: Never commit this token to version control or share it publicly.")
    print("   Store it only in local.properties, which should be in .gitignore.")
    print("\n2. Run the isolation test:")
    print("\n   ./gradlew test --tests XApiIsolationTest")
    print("\n" + "=" * 60)
    
except requests.exceptions.RequestException as e:
    print(f"\n❌ Token exchange failed: {e}")
    if hasattr(e.response, 'text'):
        print(f"Response: {e.response.text}")
    sys.exit(1)

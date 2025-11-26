# X API Testing Guide

This document explains how to test the X API integration in EchoX, useful for debugging issues or verifying new features.

## Test Files Location

- **Isolation Test**: [`XApiIsolationTest.kt`](file:///Users/aban/drive/Projects/EchoX/app/src/test/java/com/echox/app/data/api/XApiIsolationTest.kt)
- **Service Implementation**: [`XApiService.kt`](file:///Users/aban/drive/Projects/EchoX/app/src/main/java/com/echox/app/data/api/XApiService.kt)
- **Integration Documentation**: [`X_API_Integration.md`](file:///Users/aban/drive/Projects/EchoX/docs/X_API_Integration.md)

## Running the Isolation Test

### Prerequisites

1. **Generate a Test Token**:
   ```bash
   # Set your X API credentials in local.properties
   X_CLIENT_ID=your_client_id_here
   X_CLIENT_SECRET=your_client_secret_here
   
   # Generate a user access token using OAuth 2.0 PKCE
   # See "Generating Test Tokens" section below
   ```

2. **Add Token to `local.properties`**:
   ```properties
   X_TEST_USER_TOKEN=your_generated_token_here
   ```

### Run the Test

```bash
./gradlew test --tests "com.echox.app.data.api.XApiIsolationTest"
```

Or via Android Studio: Right-click on `XApiIsolationTest.kt` → Run

## What the Test Validates

The `XApiIsolationTest` performs a complete end-to-end test:

1. ✅ **Media Upload (V2 Flow)**:
   - INIT: Creates media upload session with `total_bytes`
   - APPEND: Uploads video in 1MB chunks
   - FINALIZE: Completes upload and waits for processing

2. ✅ **Multi-Video Threading**:
   - Uploads 2 separate video chunks
   - Creates root tweet with first video
   - Replies to root tweet with second video (creates thread)

3. ✅ **Response Validation**:
   - Verifies all API calls return success status codes
   - Confirms `media_id` is returned from uploads
   - Validates `tweet_id` is returned from tweet creation

## Generating Test Tokens

### Method 1: OAuth 2.0 PKCE Flow (Python)

Create `scripts/get_x_token.py`:

```python
#!/usr/bin/env python3
import requests
import secrets
import hashlib
import base64
import webbrowser
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

CLIENT_ID = "YOUR_CLIENT_ID"
REDIRECT_URI = "http://127.0.0.1:8080/callback"
SCOPES = ["tweet.read", "tweet.write", "users.read", "offline.access", "media.write"]

# Generate PKCE parameters
code_verifier = base64.urlsafe_b64encode(secrets.token_bytes(64)).decode('utf-8').rstrip('=')
code_challenge = base64.urlsafe_b64encode(
    hashlib.sha256(code_verifier.encode('utf-8')).digest()
).decode('utf-8').rstrip('=')

# Step 1: Build authorization URL
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

print(f"Opening browser for authorization...")
webbrowser.open(auth_url)

# Step 2: Capture callback
class CallbackHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        query = parse_qs(urlparse(self.path).query)
        if 'code' in query:
            self.server.auth_code = query['code'][0]
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write(b"<h1>Authorization successful! You can close this window.</h1>")
        else:
            self.send_response(400)
            self.end_headers()

server = HTTPServer(('127.0.0.1', 8080), CallbackHandler)
server.handle_request()
auth_code = server.auth_code

# Step 3: Exchange code for token
token_response = requests.post(
    "https://api.twitter.com/2/oauth2/token",
    data={
        "code": auth_code,
        "grant_type": "authorization_code",
        "client_id": CLIENT_ID,
        "redirect_uri": REDIRECT_URI,
        "code_verifier": code_verifier
    }
)

tokens = token_response.json()
print(f"\nAccess Token: {tokens['access_token']}")
print(f"\nAdd to local.properties:")
print(f"X_TEST_USER_TOKEN={tokens['access_token']}")
```

Run:
```bash
python3 scripts/get_x_token.py
```

### Method 2: Using X Developer Portal

For quick testing, you can also generate tokens via:
1. Go to [X Developer Portal](https://developer.twitter.com/en/portal/dashboard)
2. Navigate to your app → Keys and Tokens
3. Generate "OAuth 2.0 User Access Token"
4. **IMPORTANT**: Ensure you select these scopes:
   - `tweet.read`
   - `tweet.write`
   - `users.read`
   - `media.write` ⚠️ **CRITICAL for uploads**

## Common Test Issues

### Issue: 403 Forbidden

**Cause**: Missing `media.write` scope in your token.

**Solution**: Regenerate your token with all required scopes (see above).

---

### Issue: 413 Payload Too Large

**Cause**: Chunk size > 1MB.

**Solution**: Verify `XApiService.kt` uses 1MB chunks:
```kotlin
val chunkSize = 1 * 1024 * 1024  // Must be <= 1MB for V2
```

---

### Issue: 429 Too Many Requests

**Cause**: X API rate limiting (common during testing).

**Solution**:
- Wait 15 minutes before retrying
- Use a different test account
- For app testing: Token is enough, user profile fetch can fail (see "Profile Rate Limiting" below)

---

### Issue: "Segments do not add up"

**Cause**: Total uploaded bytes don't match `total_bytes` in INIT.

**Solution**: Ensure exact file size is sent in INIT:
```kotlin
val fileSize = videoFile.length()
// Send fileSize as total_bytes in INIT
```

---

### Issue: Profile Rate Limiting (429 on getUserProfile)

**Expected Behavior**: During development, X may rate-limit profile fetches.

**Not a Blocker**: The app can still upload videos with just the token. The user profile is only needed for UI display (name, avatar). The upload logic in `SharePipeline.kt` now checks for `hasToken` instead of `user != null`.

## Test Video Files

The isolation test uses video chunks from your app's cache:
- `chunk_0.mp4`
- `chunk_1.mp4`

To generate test chunks:
1. Record a 3+ minute audio in the app
2. Let it segment into multiple videos
3. Copy chunk files from device:
   ```bash
   adb pull /data/data/com.echox.app/cache/chunk_0.mp4 app/src/test/resources/
   ```

## Debugging Tips

### Enable Verbose Logging

Add to `XApiService.kt`:
```kotlin
private val client = HttpClient(CIO) {
    install(Logging) {
        logger = Logger.ANDROID
        level = LogLevel.ALL
    }
}
```

### Capture Full Response Bodies

```kotlin
val errorBody = response.bodyAsText()
println("XApi Error: $errorBody")
```

### Check Token Validity

```bash
curl -X GET "https://api.twitter.com/2/users/me" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

If this returns 401, your token is invalid/expired.

## Test Data Cleanup

After running tests, you may want to delete test tweets:

1. Visit [twitter.com](https://twitter.com)
2. Navigate to your profile
3. Delete tweets manually (X API v2 doesn't support bulk deletion yet)

Or use the X Developer Portal → Projects → Your App → Delete tweets via API.

## Reference Implementation

For a raw example of the upload flow (without Android dependencies), see the Python script approach:

```python
# test_upload_v2.py - Minimal V2 upload test
import requests

ACCESS_TOKEN = "your_token"
VIDEO_FILE = "test_video.mp4"

# 1. INIT
with open(VIDEO_FILE, 'rb') as f:
    file_size = len(f.read())

init_response = requests.post(
    "https://api.twitter.com/2/media/upload/initialize",
    headers={"Authorization": f"Bearer {ACCESS_TOKEN}"},
    json={"media_category": "tweet_video", "total_bytes": file_size}
)
media_id = init_response.json()["media_id_string"]

# 2. APPEND (1MB chunks)
with open(VIDEO_FILE, 'rb') as f:
    segment_index = 0
    while chunk := f.read(1024 * 1024):  # 1MB
        requests.post(
            f"https://api.twitter.com/2/media/upload/{media_id}/append",
            headers={"Authorization": f"Bearer {ACCESS_TOKEN}"},
            files={"media": chunk},
            data={"segment_index": segment_index}
        )
        segment_index += 1

# 3. FINALIZE
requests.post(
    f"https://api.twitter.com/2/media/upload/{media_id}/finalize",
    headers={"Authorization": f"Bearer {ACCESS_TOKEN}"},
    json={"media_id": media_id}
)

# 4. TWEET
tweet_response = requests.post(
    "https://api.twitter.com/2/tweets",
    headers={"Authorization": f"Bearer {ACCESS_TOKEN}"},
    json={"text": "Test video", "media": {"media_ids": [media_id]}}
)
print(f"Tweet ID: {tweet_response.json()['data']['id']}")
```

## Further Reading

- [X API V2 Media Upload Reference](https://developer.twitter.com/en/docs/twitter-api/v2/media-upload)
- [X API V2 Tweets Reference](https://developer.twitter.com/en/docs/twitter-api/tweets/manage-tweets/api-reference/post-tweets)
- [OAuth 2.0 PKCE Flow](https://developer.twitter.com/en/docs/authentication/oauth-2-0/user-access-token)

# X API V2 Integration Guide

This document details the integration of the X (formerly Twitter) API V2 for media uploads and threaded tweet posting in EchoX.

## 1. Overview

We use the **X API V2** for all operations to ensure long-term stability and avoid deprecated V1.1 endpoints (which return `403 Forbidden`).

*   **Authentication:** OAuth 2.0 User Access Token (PKCE)
*   **Media Upload:** V2 Upload Endpoints (Chunked)
*   **Posting:** V2 Tweet Endpoint

## 2. Authentication

**Type:** OAuth 2.0 User Context
**Scopes Required:**
*   `tweet.read`
*   `tweet.write`
*   `users.read`
*   `offline.access` (for refresh tokens)
*   `media.write` (CRITICAL for video uploads)

**Token Handling:**
*   Tokens are obtained via the `get_x_token.py` script (for development/testing).
*   In production, the app should implement a full OAuth 2.0 PKCE flow (using `AppAuth-Android` or similar).
*   User tokens expire after **2 hours**.

## 3. Media Upload (V2)

We use a chunked upload flow to support large video files (up to 512MB).

**Critical Constraint:**
> **Chunk Size:** Must be **<= 1 MB**.
> The V2 API is strict. Sending 5MB chunks (standard in V1.1) often results in `413 Payload Too Large`.

### Step 1: INITIALIZE
**Endpoint:** `POST https://api.twitter.com/2/media/upload/initialize`
**Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`
**Body:**
```json
{
  "media_category": "tweet_video",
  "total_bytes": 12345678
}
```
*   `total_bytes` is **required** in V2.

### Step 2: APPEND (Loop)
**Endpoint:** `POST https://api.twitter.com/2/media/upload/{media_id}/append`
**Headers:** `Authorization: Bearer <token>` (Multipart handling adds content-type)
**Body (Multipart Form Data):**
*   `segment_index`: 0, 1, 2...
*   `media`: <Binary Data (Max 1MB)>

> **⚠️ IMPORTANT:** Do **NOT** include `media_id` in the multipart body. It is passed in the URL path only. Including it in the body causes `400 Bad Request`.

**Implementation Note:**
We use `FileInputStream` with a 1MB buffer to stream data efficiently and avoid `OutOfMemoryError` on large files.

### Step 3: FINALIZE
**Endpoint:** `POST https://api.twitter.com/2/media/upload/{media_id}/finalize`
**Headers:** `Authorization: Bearer <token>`, `Content-Type: application/json`
**Body:**
```json
{
  "media_id": "12345..."
}
```

### Step 4: Processing Check
The `FINALIZE` response may contain `processing_info`.
*   If `state` is `pending` or `in_progress`, wait for `check_after_secs` and poll the status.
*   **Note:** For V2, robust status polling can be complex. For short videos, a simple delay (e.g., 2-5s) often suffices, but production apps should implement polling.

## 4. Tweet Creation & Threading

**Endpoint:** `POST https://api.twitter.com/2/tweets`

### Root Tweet
```json
{
  "text": "My Video Thread (1/3)",
  "media": {
    "media_ids": ["123..."]
  }
}
```

### Reply Tweet (Threading)
To create a thread, reply to the previous tweet ID.
```json
{
  "text": "Part 2 (2/3)",
  "media": {
    "media_ids": ["456..."]
  },
  "reply": {
    "in_reply_to_tweet_id": "<ROOT_TWEET_ID>"
  }
}
```

## 5. Code References

*   **`XApiService.kt`**: Core implementation of the API calls.
    *   `uploadMedia()`: Handles the INIT-APPEND-FINALIZE flow with streaming.
    *   `postThread()`: Orchestrates uploading multiple videos and chaining tweets.
*   **`SharePipeline.kt`**: Prepares video files (segmentation) and calls `XApiService`.

## 6. Troubleshooting

| Error | Cause | Fix |
| :--- | :--- | :--- |
| `403 Forbidden` | Using V1.1 endpoints or missing `media.write` scope. | Use V2 endpoints and check token scopes. |
| `413 Payload Too Large` | Chunk size > 1MB. | Reduce chunk size to 1MB. |
| `400 Bad Request` | `media_id` in APPEND body or missing `total_bytes` in INIT. | Remove `media_id` from form data; add `total_bytes`. |
| `OutOfMemoryError` | Loading entire video into RAM. | Use `FileInputStream` and buffer streaming. |

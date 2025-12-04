# Debugging Guide - Viewing Logs from Your Device

## Quick Commands

### View All EchoX Logs (Real-time)
```bash
adb logcat -s EchoX_Upload:E EchoX_Video:E EchoX_ERROR:E XApi:E XApi_Ktor:E AndroidRuntime:E
```

### View All EchoX Logs (with timestamps)
```bash
adb logcat -v time -s EchoX_Upload:E EchoX_Video:E EchoX_ERROR:E XApi:E XApi_Ktor:E AndroidRuntime:E
```

### Clear Logs and Start Fresh
```bash
adb logcat -c && adb logcat -s EchoX_Upload:E EchoX_Video:E EchoX_ERROR:E XApi:E XApi_Ktor:E AndroidRuntime:E
```

### View Recent Logs (Last 100 lines)
```bash
adb logcat -d | grep -E "EchoX|XApi" | tail -100
```

### Save Logs to File
```bash
adb logcat -s EchoX_Upload:E EchoX_Video:E EchoX_ERROR:E XApi:E XApi_Ktor:E AndroidRuntime:E > echox_logs.txt
```

## Log Tags Used in EchoX

| Tag | Purpose | Level |
|-----|---------|-------|
| `EchoX_Upload` | File upload progress and errors | Debug/Error |
| `EchoX_Video` | Video generation progress | Debug |
| `EchoX_ERROR` | General app errors | Error |
| `XApi` | X API operations (uploads, tweets) | Debug/Error |
| `XApi_Ktor` | HTTP client logs (detailed) | Debug |
| `AndroidRuntime` | System crashes and exceptions | Error |

## Log Levels

- **E** = Error (red) - Something went wrong
- **W** = Warning (orange) - Potential issues
- **D** = Debug (blue) - Informational messages
- **I** = Info (green) - General information

## Common Debugging Scenarios

### 1. Thread Upload Failing
```bash
adb logcat -s XApi:E EchoX_Upload:E EchoX_ERROR:E
```
Look for:
- "Media upload failed" - Check file size and upload errors
- "Media processing timed out" - Video processing taking too long
- "Tweet creation failed" - API errors or media not ready

### 2. Video Generation Issues
```bash
adb logcat -s EchoX_Video:E EchoX_ERROR:E
```
Look for:
- "Video generation failed" - Check Media3 Transformer errors
- File sizes - Should be reasonable (< 50MB for 140s videos)

### 3. X API Errors
```bash
adb logcat -s XApi:E XApi_Ktor:D
```
Look for:
- HTTP status codes (403, 413, 400, etc.)
- Error responses from X API
- Authentication issues

### 4. Full App Debugging
```bash
adb logcat -s EchoX_Upload:E EchoX_Video:E EchoX_ERROR:E XApi:E AndroidRuntime:E
```

## Tips

1. **Clear logs before testing**: `adb logcat -c` to start fresh
2. **Use timestamps**: Add `-v time` to see when things happen
3. **Filter by tag**: Use `-s TagName:Level` to focus on specific areas
4. **Save important logs**: Redirect to a file for later analysis
5. **Watch for stack traces**: Errors with `Log.e()` include full stack traces

## Example Output

```
12-25 10:30:15.123  D/XApi: Upload INIT response: {"data":{"id":"12345"}}
12-25 10:30:15.456  D/XApi: Media upload initialized. Media ID: 12345
12-25 10:30:16.789  D/XApi: Appended segment 0 successfully
12-25 10:30:17.012  D/XApi: Media Finalize response: {"processing_info":{...}}
12-25 10:30:19.345  D/XApi: Media upload completed successfully. Media ID: 12345
12-25 10:30:20.678  E/XApi: Tweet creation failed: Media not ready
```

## Troubleshooting

### No logs appearing?
1. Check device is connected: `adb devices`
2. Make sure you're filtering correctly (tags are case-sensitive)
3. Try viewing all logs: `adb logcat | grep EchoX`

### Too many logs?
- Use more specific tags: `-s XApi:E` (only errors)
- Filter by app: `adb logcat | grep com.echox.app`

### Need to see HTTP requests/responses?
- Enable `XApi_Ktor:D` tag for detailed HTTP logging
- Note: This can be very verbose!






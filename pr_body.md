## Description

This PR implements a comprehensive Settings screen that addresses issue #4, providing users with account management, app preferences, and logout functionality.

## Changes

### New Features
- **Settings Screen** (`SettingsScreen.kt`)
  - Account information display (name, username, profile avatar)
  - Auto-post toggle preference (defaults to off)
  - Video quality selector (720p/1080p, defaults to 720p)
  - Logout functionality

- **App Preferences Helper** (`AppPreferences.kt`)
  - Persistent storage using SharedPreferences
  - Manages auto-post and video quality settings
  - Preferences persist across app restarts

### Updates
- **Navigation** (`Navigation.kt`)
  - Added Settings route
  - Integrated with existing Library route

- **RecordScreen** (`RecordScreen.kt`)
  - Replaced logout button with Settings icon button
  - Added Library button alongside Settings button
  - Improved header layout with proper spacing

## Technical Details

### Preferences Storage
- Uses Android SharedPreferences (non-encrypted) for app preferences
- Separate from encrypted token storage in XRepository
- Default values:
  - Auto-post: `false`
  - Video quality: `720p`

### Logout Flow
- Clears OAuth tokens via `XRepository.logout()`
- Removes access token, refresh token, and expiration time
- Clears cached user profile
- Navigation automatically redirects to login screen via `LaunchedEffect` watching `isAuthenticated`

### UI/UX
- Material Design 3 components
- Consistent with app's dark theme (XDark background)
- Accessible from main recording screen via Settings icon
- Back navigation support

## Testing

- [x] Settings screen accessible from RecordScreen
- [x] Account info displays correctly
- [x] Preferences persist after app restart
- [x] Logout clears tokens and redirects to login
- [x] Merged with latest main branch (includes Library feature)

## Acceptance Criteria Met

✅ Settings accessible from main screen  
✅ Logout clears tokens and returns to login  
✅ Preferences persist across app restarts  

## Screenshots

Settings screen includes:
- Account section with user avatar, name, and username
- Preferences section with auto-post toggle and video quality selector
- Logout button at bottom

## Related Issues

Closes #4

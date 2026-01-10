# JCI Kiosk App - Changelog

## Current Features (v4.12)

### Kiosk Mode
- Full-screen immersive mode with hidden system bars
- Device Owner lock task mode (prevents exiting)
- Auto-start on boot
- Preferred Ethernet connection (falls back to WiFi)

### Navigation
- Bottom nav bar with Home, Back, Forward, Admin buttons
- Edge swipe (top or bottom) to reveal controls
- Auto-hide controls after 5 seconds
- WebView navigation with back/forward support

### Zoom Controls
- Zoom slider (50% - 300%)
- Zoom +/- buttons
- Reset to 100% button

### Auto-Refresh
- Configurable idle timeout (1-60 minutes)
- Countdown timer display
- Resets on user interaction
- Can be disabled in settings

### Auto-Login
- Detects login pages (sign/login/auth in URL)
- Auto-fills username and password
- Configurable credentials in settings
- Can be disabled

### Error Handling
- Full-screen error overlay on connection failure
- Retry button
- Shows error message

### Admin Panel (password protected)
- General Settings (home URL, timeout, always-on display, auto-login)
- WiFi Settings
- Change admin password
- Check for updates (GitHub releases)
- Restart application
- Exit kiosk mode

### Updates
- OTA updates from GitHub releases
- Version comparison
- SHA256 checksum verification
- Silent install via PackageInstaller

---

## Version History

### v4.12 (2026-01-09)
- Removed unused code (getPageInfo, reload wrapper, orphan callbacks)
- Fixed unused imports and variables
- Simplified hideSystemUI logic

### v4.10 (2026-01-09)
- Removed unused Compose theme files (ui/theme/)
- Removed unused AdminActivity (replaced by FullAdminActivity)
- Removed legacy options menu code (using bottom nav instead)
- Removed unused showRollbackDialog method
- Cleaned up EdgeSwipeDetector unused fields

### v4.9 and earlier
- Initial kiosk implementation
- WebView with auto-login
- Device admin receiver
- Boot receiver for auto-start
- Update manager with GitHub integration

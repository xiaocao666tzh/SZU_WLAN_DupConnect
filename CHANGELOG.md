# Changelog

All notable changes to this project are documented in this file.

## [1.0.1] - 2026-09-07

### Fixed
- Keep connect-loop stop latch set after credential delete so deleted accounts are never reused mid-retry
- Perform real Wi-Fi disconnect/disable on API 26–28 legacy path during failure→retry

## [1.0.0] - 2026-09-07

### Added
- First release of 深大一键联网
- Local-only campus card credential storage (save once, delete manually)
- Auto-connect to `SZU_CTC&CMCC` and portal login at `172.30.225.42:801`
- Baidu reachability check before success dialog
- Retry with 3s disconnect/reconnect and attempt counter
- Bottom live log panel and emphasized success attempt count

# Changelog

## [1.0.0] - 2026-09-10

The Ledger release: a full redesign, document scanning, OCR, and a lock on
the front door.

### Added
- Document scanning with edge detection and perspective correction
- Scan editor: rotate, crop, brightness, contrast, filters, and black-box redaction
- Text blocks, so notes sit alongside scans in the same document
- On-device OCR: read the text off a scan, copy it, keep it as a note, or use it
  to name the document
- Full-text search across scanned text, alongside name, person and tag
- Tags, which cut across people
- PDF export for a whole document or a single page, choosing which blocks go in
- Save a scan to the gallery, or a PDF to Documents
- Multi-select on the grid for bulk share, delete and move-to-person
- Biometric / device-credential app lock
- Editing tools in the full-screen viewer
- Report an issue, in the settings drawer, alongside the version

### Changed
- Redesigned every screen on the Ledger design system: no radii, no elevation,
  2px rules in place of shadows, and the Archivo typeface
- Navigation moved to a bottom bar; the drawer is settings only
- PDFs are now written with the platform PDF writer instead of a third-party
  library that did not support Android
- Onboarding shows a real 3D vault rather than a flat approximation

### Fixed
- Redaction now clears the cached OCR text, so a blacked-out number is no
  longer still findable by search
- The app lock covers every screen, not just the home grid — it could be
  stepped around through recents
- Export no longer freezes the app while it zips the vault
- Drag-to-reorder works again; the handler was being re-attached on every refresh
- Profile photos persist across restarts
- OCR runs off the main thread
- Saving to the gallery and Documents reports failure instead of silently doing
  nothing, and no longer leaves half-written files behind
- Quick-start suggestions on the empty state are wired up and no longer blank

### Removed
- The developer page
- `MANAGE_EXTERNAL_STORAGE` and `READ_MEDIA_IMAGES`, neither of which the app
  ever requested

## [1.0] - 2025-11-03
### Added
- Initial release of DocKeep
- Document creation and management
- Image handling from camera and gallery
- Local storage organization
- Light/dark theme toggle
- Search functionality
- Document sharing and export capabilities
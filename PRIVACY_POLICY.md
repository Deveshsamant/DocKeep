# Privacy Policy for DocKeep

Last updated: September 10, 2026

## Introduction

DocKeep is an open-source Android application for storing and organizing
personal documents on your own device. This policy explains exactly what the
app does with your data.

## Data collection

**DocKeep collects nothing.** There is no account, no analytics, no crash
reporting and no advertising. Nothing you put into the app is transmitted off
your device, and the developer has no way to see any of it.

## Where your data lives

Documents, scans, notes, tags, the text read by OCR, and your profile details
are stored in the app's own private storage on your device. They are removed
when you uninstall the app.

Files you explicitly export leave that private storage because you asked them
to: saving a scan puts it in your gallery, saving a PDF puts it in your
Documents folder, and sharing hands the file to whichever app you pick. What
happens to a file after you share it is governed by the app you shared it with.

## Permissions

- **Camera** — capturing and scanning pages. Images go straight into the app's
  storage; nothing is uploaded.
- **Biometric** — the optional app lock. Fingerprint and face data never leave
  the Android system and are never visible to DocKeep; the system only tells
  the app whether authentication succeeded.
- **Internet** — used for one thing: downloading Google's document-scanner
  module from Google Play Services the first time you scan. None of your
  documents, images or text are ever sent over the network.
- **Vibrate** — the haptic tick when you drag a document into a new position.
- **Read/write external storage** — on Android 10 and older only, to save
  scans to your gallery. Newer versions of Android do not need it and the app
  does not request it there.

The app does not request broad storage access. When you add an existing image,
Android's own photo picker gives DocKeep access to the single file you chose
and nothing else.

## Text recognition (OCR)

Reading text off a scan runs entirely on your device, using a model bundled
inside the app. Your documents are not sent anywhere to be processed.

## Document scanning

The scanning feature uses Google Play Services' document scanner. The module
itself is downloaded from Google on first use; the scanning then happens on
your device. Google's handling of the Play Services download is covered by
[Google's Privacy Policy](https://policies.google.com/privacy).

## Children's privacy

DocKeep does not collect data from anyone, including children under 13.

## Data security

Because everything stays on your device, your data is as safe as your device
is. Turn on a screen lock, and consider enabling DocKeep's own app lock under
Settings.

## Your rights

You hold your data directly. You can export the whole vault as a ZIP at any
time from Settings, delete any document from within the app, and remove
everything by uninstalling it. There is no server-side copy to request or
erase.

## Changes to this policy

Any changes will be posted to this page, with the date at the top updated.

## Contact

Questions, or a problem with the app:

- Issues: https://github.com/Deveshsamant/DocKeep/issues
- Email: deveshsamant007@gmail.com

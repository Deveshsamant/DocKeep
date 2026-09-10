# DocKeep 1.0.0 — Play Console paste sheet

Everything here is ready to paste. `com.dockeep.app`, versionCode 1,
versionName 1.0.0, targetSdk 36, minSdk 21.

---

## Upload

**`DocKeep-1.0.0.aab`** — this is the file Play takes. Signed with the upload
key at `E:/keystores/dockeep-upload.jks`.

`DocKeep-1.0.0-signed.apk` is the same build as an APK. Play will not accept it
for a new app; it is there so you can sideload exactly what you are shipping
and check it against the AAB.

---

## Store listing

**App name** (30 max)

```
DocKeep
```

**Short description** (80 max — this is 69)

```
Scan, store and search your documents. Everything stays on your phone.
```

**Full description** (4000 max)

```
DocKeep keeps your important documents on your own phone. There is no account, no sync and no server — nothing you put in ever leaves the device.

Scan a page and DocKeep straightens it, crops it and cleans it up. Edit it afterwards: rotate, crop, adjust brightness and contrast, or black out a number before you share it. Add written notes alongside the scans in the same document.

Find anything fast. DocKeep reads the text off your scans on-device, so searching works on what is written inside a document, not just its name. Tag documents across family members, or file them under the person they belong to.

Export a PDF of a whole document or a single page, choosing exactly which parts to include. Save scans to your gallery, PDFs to your Documents folder, or share straight to another app.

Lock the whole vault behind your fingerprint, face or device PIN. Back everything up as a single ZIP whenever you like.

DocKeep is open source: https://github.com/Deveshsamant/DocKeep
```

**Category** — Productivity
**Tags** — documents, scanner, privacy

---

## Graphics

| Asset | File | Status |
|---|---|---|
| App icon 512×512 | `play-icon-512.png` | ready |
| Feature graphic 1024×500 | `feature-graphic-1024x500.png` | ready |
| Phone screenshots | — | **you need to take these** |

Play needs a minimum of 2 phone screenshots; 4–6 is the practical sweet spot.
They must be real captures of the app — Play's listing policy requires
screenshots to represent the actual app, and mockups are a rejection risk.

Take them on your phone with power + volume-down, on these screens:

1. Home grid with a decent number of documents
2. A document open, showing scans and a text block
3. The scan editor with the redaction tool visible
4. Search showing a result matched on text inside a scan
5. The settings drawer with the biometric lock switch

---

## App content answers

| Question | Answer |
|---|---|
| Privacy policy | `https://github.com/Deveshsamant/DocKeep/blob/master/PRIVACY_POLICY.md` |
| App access | All functionality available without special access |
| Ads | No ads |
| Content rating | Utility; answer No throughout → Everyone / PEGI 3 |
| Target audience | 13+ and up (an under-13 band pulls you into Families policy) |
| Data safety | **No data collected, no data shared** |
| Government apps | No |
| Financial features | No |
| Health | No |

The Data safety answers must agree with the privacy policy. They do — the app
collects nothing, and `PRIVACY_POLICY.md` was rewritten for 1.0.0 to describe
exactly the permissions the manifest now requests.

---

## Permissions Play will show

CAMERA, USE_BIOMETRIC, VIBRATE, INTERNET, and READ/WRITE_EXTERNAL_STORAGE
capped at API 32/29 for older devices. No broad storage permission is
requested, so no declaration form is required.

---

## Release

Go to **Internal testing** first, not straight to Production. Upload the AAB,
add release notes, install from the internal-testing link on your own phone,
confirm it works, then promote.

If your developer account is a personal one created after November 2023, Google
requires a closed test with at least 12 testers opted in continuously for 14
days before production access. Your Dashboard will say so.

---

## Before the next release

`versionCode` must increase for every upload. 1.0.0 is versionCode 1, so the
next one is 2 — Play rejects a duplicate outright.

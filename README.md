# BPR Downloader

A Downloader-style app for Fire TV: type a URL, get the file, install it. The
difference is **search history** — everything you download is saved, filtered as
you type, and one click re-downloads it.

No ads.

## Install on the Fire TV

Sideload the APK from:

```
https://github.com/BPRVT/BPRDownloader/releases/latest/download/BPRDownloader.apk
```

GitHub Actions rebuilds it on every push, so that link always points at the
current build.

## What it does

- **Home** — URL box that doubles as a history search. Type to filter, click a
  row to download it again. Anything that isn't a URL becomes a Google search.
  Long-press a row to favourite or delete it.
- **Browser** — built-in WebView. Press **Cursor** for a D-pad mouse pointer;
  Back turns it off. File links download instead of opening.
- **Send** — stop typing URLs with a remote. Open the address it shows in your
  phone's browser, enter the PIN from the TV, and paste links straight in. The
  server only runs while this screen is open.
- **Files** — installs APKs, opens anything else, deletes what you're done with.
- **Settings** — home page, history size, cursor speed, delete-after-install.

## Signing

Builds are signed with the debug key unless you add these repo secrets:
`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Without them
you may need to uninstall before installing a new build.

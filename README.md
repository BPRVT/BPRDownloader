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

Builds use a throwaway debug key until you set up a real one, which means each
release refuses to install over the last. To fix that once:

1. Actions → **Generate signing key** → **Run workflow**.
2. Download the `signing-key` artifact from that run.
3. Settings → Secrets and variables → Actions, and add the four secrets named
   in the artifact's `README.txt`.

Every build after that is signed with the same key, so updates install over the
top and your history survives. Keep a backup of `KEYSTORE_B64.txt` — losing it
means no more in-place updates, ever.

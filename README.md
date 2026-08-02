# BPR Downloader

Send links from your phone to your Fire TV. It downloads them and offers to
install APKs. That's it — no browser, no ads, no typing URLs with a remote.

## Install on the Fire TV

```
https://github.com/BPRVT/BPRDownloader/releases/latest/download/BPRDownloader.apk
```

## Using it

1. Open the app on the Fire TV. It shows an address and a 4-digit PIN.
2. Open that address in your phone's browser, on the same Wi-Fi.
3. Enter the PIN once, paste a link, hit **Send to TV**.

The download starts immediately. APKs prompt to install when they finish, and
everything you've downloaded stays listed on the right of the TV screen so you
can install or delete it later.

Past downloads show up on the phone page under **Recent** — tap one to grab it
again without pasting.

The server runs only while the app is open, and every action needs the PIN.

## Signing

Builds use a throwaway debug key until you set one up, which means each release
refuses to install over the last:

1. Actions → **Generate signing key** → **Run workflow**.
2. Download the `signing-key` artifact.
3. Add the four secrets it names under Settings → Secrets and variables → Actions.

Keep a backup of `KEYSTORE_B64.txt`.

# BTC Wallet

A simple, self-custodial Bitcoin wallet for Android.

> **Early software.** Version 0.1.0 is not released yet and the code has not been audited.
> Try it on Signet or Testnet first, and only keep small amounts on Mainnet.

## Features

- New wallet with a 12-word seed, written down and checked before use
- PIN lock with 6 or 8 digits, auto lock and a wait after wrong tries
- Receive with a QR code, copy and share
- Send with a fee choice, review and slide to send
- Activity with each stage from Sending to Confirmed, and a details card per payment
- Mainnet, Signet and Testnet4, each with its own wallet
- Debug log export behind the PIN, without seed, keys, addresses or amounts

## Build

Needs Docker. Run `make help` for all targets.

```
make image   # once, builds the Android build image
make build   # builds the debug APK
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Install

Copy `app-debug.apk` to your phone and open it. Android asks once to allow installs from that app.
Release builds are on the [Releases](https://github.com/lordhck/wallet/releases) page.

Requires Android 8.0 or newer.

## Privacy

Your seed, PIN and keys never leave the phone. The app talks to these servers:

- **Blockchain:** blockstream.info, with mempool.space as backup
- **Price:** Coinbase, with Kraken as backup. Fetched on every network, shown in USD on Mainnet only

You can set your own server in Settings.

## License

[MIT](LICENSE)

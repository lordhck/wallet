# BTC Wallet: technical whitepaper

Version 0.1.0 - Lord J. Hackwell - hey@lordhck.com

> This describes the code as it is in version 0.1.0. The app has not been audited.
> Use Signet or Testnet first, and only keep small amounts on Mainnet.

## 1. Goal

BTC Wallet is a self-custodial Bitcoin wallet for Android. It does three things: show the balance, send and receive. Everything else lives in Settings.

The design rules are:

- The keys never leave the phone.
- There are no accounts, no analytics and no ads.
- The app depends on as few things as possible, and on well-known code for the hard parts.
- When it isn't sure, it says so instead of guessing.

## 2. Building blocks

| Part | Used for |
|---|---|
| [BDK](https://bitcoindevkit.org) (`bdk-android` 2.3.1) | Seeds, keys, descriptors, the wallet database, coin selection, signing and syncing |
| Kotlin and Jetpack Compose | The app and its screens |
| Android Keystore | A key that is bound to the device and can't be exported |
| ZXing | QR codes |
| CameraX | Scanning QR codes |

The app needs Android 8.0 (API 26) or newer. It asks for internet, network state and the camera, and nothing else. The camera is only used to scan, and only when you tap scan.

## 3. Keys

### Seed

A new wallet gets a 12-word BIP39 seed from BDK. Before the wallet opens, you write the words down and then pick three of them, each shown among two words that aren't in the seed. A restore takes 12 or 24 words, which BDK checks against the BIP39 checksum. There is no BIP39 passphrase.

### Derivation

Every wallet is Native SegWit (P2WPKH) under BIP84:

- Receive addresses: `m/84'/0'/0'/0/*`
- Change addresses: `m/84'/0'/0'/1/*`

Signet and Testnet4 use coin type `1'`. Any wallet that follows BIP84, such as Sparrow or BlueWallet, can restore the same seed and see the same funds.

### Networks

Mainnet, Signet and Testnet4 each have their own wallet database (`wallet-Main.sqlite`, `wallet-Signet.sqlite`, `wallet-Testnet.sqlite`). The same seed derives all three. Switching network closes one and opens the other, and the two never mix.

## 4. Storage

The seed is the only secret. The app keeps it in a vault encrypted twice:

1. **With your PIN.** PBKDF2-HMAC-SHA256 with a random 16-byte salt and 50,000 rounds makes a 256-bit key. AES-256-GCM encrypts the seed with it.
2. **With the device.** That result is encrypted again with AES-256-GCM, using a 256-bit key that lives in the Android Keystore. On phones with secure hardware, the key never leaves it.

The round count is saved with the vault, so a later version can raise it without locking anyone out.

The wallet database holds addresses and transactions but no private keys. It is in the app's private folder. Android backup is turned off (`allowBackup="false"`), so neither file goes to cloud backup.

While the wallet is unlocked, the seed words are held in memory. Locking the app drops them.

### PIN

- **Length:** 6 or 8 digits.
- **Wrong tries:** the first 5 are free. After that, each wrong PIN locks the vault: 30 seconds, then 1 minute, doubling to about an hour.
- **Auto lock:** after 60 seconds in the background.
- **Change PIN:** opens the vault with the old PIN and seals it again with the new one and a new salt.

### Delete wallet

You tap, then hold for 1.5 seconds. This deletes the vault, the Keystore key and the wallet database for every network. Without the 12 words, the funds are gone.

## 5. Threat model

**Protects against:**

- **Someone holding your locked phone.** They need the PIN, and the wait after 5 wrong tries slows guessing down.
- **A copy of the app's files.** Without the Keystore key, which stays inside the phone, the vault can't be opened.
- **Screenshots and the app switcher.** Screenshot protection is on by default and hides the app's screen from both.
- **Cloud backups.** Android backup is off, so nothing goes to Google's cloud.

**Does not protect against:**

- **A rooted or compromised phone.** Malware that can run as the app can use the Keystore key. The PIN wait is enforced by the app, so it could get around that too. With the device key in hand, a 6-digit PIN at 50,000 rounds can be guessed in hours on fast hardware. An 8-digit PIN takes about 100 times longer.
- **Someone who sees your 12 words.** The seed is the wallet.
- **What the server learns.** See section 6.

Biometric unlock isn't in 0.1.0. It shows as "Coming soon".

## 6. Network and privacy

### Blockchain data

The app doesn't run a Bitcoin node. It reads the chain from an Esplora server over HTTPS:

| Network | Default | Backup |
|---|---|---|
| Mainnet | blockstream.info | mempool.space |
| Signet | blockstream.info/signet | mempool.space/signet |
| Testnet4 | blockstream.info/testnet4 | mempool.space/testnet4 |

You can set your own Esplora server, such as electrs or mempool on your own node, in Settings → Advanced → Server. A custom server has no backup.

**What the server sees:**

- your IP address
- every address the wallet checks
- every transaction you send

From that it can link your addresses together. To keep this private, use your own server. There's no built-in Tor in 0.1.0.

### Price

The USD price comes from Coinbase, with Kraken as backup. The app fetches it about every 15 minutes while it's open. The request carries no wallet data. USD amounts are only shown on Mainnet.

Nothing else is contacted.

## 7. Syncing

BDK does the syncing.

### First scan

A restored seed gets one full scan of both address chains. The scan stops after 20 unused addresses in a row, which is BDK's gap limit. A seed made in the app has no history, so it skips the scan.

### Regular syncs

After the first scan, each sync only checks addresses the wallet has already handed out:

- **Every 28 seconds** while something is in progress: the first sync, a payment waiting for confirmations, or Receive opened in the last 10 minutes.
- **Every 5 minutes** otherwise.

### Being polite to servers

Public servers block clients that send too many requests at once. The app:

- sends one request at a time, with a 1-second gap between addresses
- gives every request a 20-second timeout
- rests a server that answers *429 Too Many Requests* for 10 minutes, and uses the backup meanwhile
- rests a server that stops answering for 5 minutes, doubling each time up to an hour
- waits longer between syncs after each failure in a row
- doesn't count a failure that happened while Android had cut the app's network in the background

When every server is resting, the app says so instead of retrying.

### Status chip

The network chip on Home, Send and Receive shows:

- **Green:** synced, and the balance is current.
- **Orange, pulsing:** syncing.
- **Orange, steady:** no server can be reached right now.

## 8. Sending

1. **Address:** paste, type or scan a `bitcoin:` link. The address must belong to the open network.
2. **Amount:** in BTC, sats or USD, or Max to send the whole balance.
3. **Fee:** see "Fees" below.
4. **Review:** BDK builds the transaction, picks the coins and adds change to a fresh change address. The review sheet shows the amount, the fee and where it goes. Nothing has left the phone yet.
5. **Slide to send:** BDK signs on the phone, and the signed transaction goes to the server.

### Fees

The server's estimates for 6, 3 and 1 blocks set Slow, Medium and Fast. A slider covers 1 sat/vB up to twice the Fast rate. Estimates are fetched when Send opens, at most once a minute. On test networks, Slow and Medium are 1 sat/vB, since test coins have no value.

### After sending

The payment shows in Activity right away:

- **Sending…** while it's on its way to the server.
- **0 of 6 confirmations** once the server has it.
- **Confirmed** after 6 blocks.

A server can take a moment to list a new transaction. During that time, the app keeps showing it for up to 10 minutes, so it doesn't blink out of Activity.

Replace-by-fee (raising the fee of a stuck payment) isn't in 0.1.0.

## 9. Receiving

Receive shows the next unused receive address as text and as a `bitcoin:` QR code. Copy and Share hand over the plain address. New address reveals the next one, so you can use a fresh address for each payment. Opening Receive makes syncs fast for 10 minutes, so an incoming payment shows up quickly.

## 10. Debug log

The app keeps a log of what it does, such as syncs, server answers, errors and screen changes. The last 3,000 lines are kept on the phone.

**Export:** Settings → Advanced, behind the PIN. You choose where to save the file.

**Never written to the log:**

- the seed or the PIN
- keys
- addresses
- amounts
- a custom server's address

As a second safety net, anything that looks like an address or a long hex string is replaced with `<hidden>` before it is written.

## 11. Limits in 0.1.0

- Single-signature Native SegWit only. No Taproot, multisig or hardware wallets.
- No BIP39 passphrase.
- No replace-by-fee or child-pays-for-parent.
- No Tor and no Electrum servers, only Esplora.
- Switching network during a first scan waits for the scan to finish.
- Not audited.

## 12. Source

The full source is at [github.com/lordhck/wallet](https://github.com/lordhck/wallet) under the MIT license. The wallet logic is in `Bitcoin.kt` and `WalletModel.kt`, and the vault is in `Vault.kt`.

*Not your keys, not your coins.*

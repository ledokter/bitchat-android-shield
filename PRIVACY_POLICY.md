# bitchat Privacy Policy

*Last updated: January 2025*

## Our Commitment

bitchat is designed with privacy as its foundation. We believe private communication is a fundamental human right. This policy explains how bitchat protects your privacy.

## Summary

**WE DO NOT COLLECT ANY INFORMATION.**

- **No personal data collection** - We don't collect names, emails, or phone numbers
- **No location data collection** - Location is accessed only for local processing (BLE/Geohash) and is never collected or sent to us
- **Hybrid Functionality** - bitchat offers two modes of communication:
  - **Bluetooth Mesh Chat**: This mode is completely offline, using peer-to-peer Bluetooth connections. It does not use any servers or internet connection.
  - **Geohash Chat**: This mode uses an internet connection to communicate with others in a specific geographic area. It relies on Nostr relays for message transport.
- **No tracking by chatbit** - chatbit itself has no analytics, telemetry, or user tracking. The free version shows video ads from Google AdMob only when you ask to open certain settings (see "Free version: ads and payments")
- **Open source** - You can verify these claims by reading our code

## What Information bitchat Stores

### On Your Device Only

1. **Identity Key** 
   - A cryptographic key generated on first launch
   - Stored locally in your device's secure storage
   - Allows you to maintain "favorite" relationships across app restarts
   - Never leaves your device

2. **Nickname**
   - The display name you choose (or auto-generated)
   - Stored only on your device
   - Shared with peers you communicate with

3. **Message History** (if enabled)
   - When room owners enable retention, messages are saved locally
   - Stored encrypted on your device
   - You can delete this at any time

4. **Favorite Peers**
   - Public keys of peers you mark as favorites
   - Stored only on your device
   - Allows you to recognize these peers in future sessions

### Temporary Session Data

During each session, bitchat temporarily maintains:
- Active peer connections (forgotten when app closes)
- Routing information for message delivery
- Cached messages for offline peers (12 hours max)

## What Information is Shared

### With Other bitchat Users

When you use bitchat, nearby peers can see:
- Your chosen nickname
- Your ephemeral public key (changes each session)
- Messages you send to public rooms or directly to them
- Your approximate Bluetooth signal strength (for connection quality)

### With Room Members

When you join a password-protected room:
- Your messages are visible to others with the password
- Your nickname appears in the member list
- Room owners can see you've joined

## What We DON'T Do

bitchat **never**:
- Collects personal information
- Collects location history
- Transmits any data to us (the developers)
- Stores data on servers
- Shares data with third parties (except the ad network, only when you choose to watch a video in the free version)
- Uses analytics or telemetry
- Creates user profiles
- Requires registration

## Free version: ads and payments

- **Video ads (Google AdMob).** In the free version, opening the anti-spam settings or the advanced settings first shows a short video ad. Nothing ad-related runs before you tap "watch the video". In Europe a consent form (Google User Messaging Platform) asks whether ads may be personalized. Google AdMob may then collect your device's advertising ID, IP address and ad interactions under [Google's privacy policy](https://policies.google.com/privacy). The video loads over a direct connection, not through Tor. Your messages are never sent to the ad network.
- **chatbit Pro (Google Play Billing).** Pro is bought, renewed and cancelled through Google Play. Google handles the payment; chatbit only asks Google Play whether this device's Play account owns Pro. chatbit keeps no account and receives no payment details.
- **Pro removes the ads**: no video, and no ad network contact at all.

## Encryption

All private messages use end-to-end encryption:
- **X25519** for key exchange
- **AES-256-GCM** for message encryption
- **Ed25519** for digital signatures
- **Argon2id** for password-protected rooms

## Your Rights

You have complete control:
- **Delete Everything**: Triple-tap the logo to instantly wipe all data
- **Leave Anytime**: Close the app and your presence disappears
- **No Account**: Nothing to delete from servers because there are none
- **Portability**: Your data never leaves your device unless you export it

## Location Data & Permissions

To provide the core functionality of bitchat, we access your device's location data. This access is necessary for the following specific purposes:

### 1. Bluetooth Low Energy (BLE) Scanning
- **Why we need it:** The Android operating system requires Location permission to scan for nearby Bluetooth LE devices (especially on Android 11 and lower). This is a system-level requirement because Bluetooth scans can theoretically be used to derive location.
- **How we use it:** We use this permission strictly to discover other bitchat peers nearby for the "Bluetooth Mesh Chat" mode.
- **Privacy protection:** We do not record or store your location during this process. The data is processed instantaneously by the Android system to facilitate the connection.

### 2. Geohash Chat Functionality
- **Why we need it:** The "Geohash Chat" mode allows you to communicate with others in your approximate geographic area.
- **How we use it:** If you enable this mode, we access your location to calculate a "geohash" (a short alphanumeric string representing a geographic region). This geohash is used to find and subscribe to relevant channels on decentralized Nostr relays.
- **Privacy protection:** 
  - Your precise GPS coordinates are **never** sent to any server or peer.
  - Only the coarse geohash (representing an area, not a pinpoint) is shared with the Nostr network.
  - You can use the "Bluetooth Mesh Chat" mode without this feature if you prefer.

**We do not collect, store, or share your location history.** Location data is processed locally on your device to enable these specific features.

## Children's Privacy

bitchat does not knowingly collect information from children. The app has no age verification because it collects no personal information from anyone.

## Data Retention

- **Messages**: Deleted from memory when app closes (unless room retention is enabled)
- **Identity Key**: Persists until you delete the app
- **Favorites**: Persist until you remove them or delete the app
- **Everything Else**: Exists only during active sessions

## Security Measures

- All communication is encrypted
- No data transmitted to servers (there are none)
- Open source code for public audit
- Regular security updates
- Cryptographic signatures prevent tampering

## Changes to This Policy

If we update this policy:
- The "Last updated" date will change
- The updated policy will be included in the app
- No retroactive changes can affect data (since we don't collect any)

## Contact

bitchat is an open source project. For privacy questions:
- Review our code: https://github.com/yourusername/bitchat
- Open an issue on GitHub
- Join the discussion in public rooms

## Philosophy

Privacy isn't just a feature—it's the entire point. bitchat proves that modern communication doesn't require surrendering your privacy. No accounts, no servers, no surveillance. Just people talking freely.

---

*This policy is released into the public domain under The Unlicense, just like bitchat itself.*
# chatbit — Politique de confidentialité / Privacy policy

*Dernière mise à jour / Last updated: 2026-10-25*

## Français

chatbit est une messagerie sans compte, conçue pour ne collecter aucune donnée.

**Ce que nous ne collectons pas.** Pas de compte, pas de numéro de téléphone, pas d'adresse e-mail, pas de carnet d'adresses, pas de statistiques d'utilisation, pas de publicité, pas de traceur. Les développeurs de chatbit n'exploitent aucun serveur et ne reçoivent aucune donnée.

**Comment les messages circulent.**
- *Hors ligne* : par Bluetooth et Wi-Fi directement entre téléphones proches (réseau « mesh »).
- *Salons géographiques et messages privés à distance* : par des relais publics du protocole Nostr, gérés par des tiers. Toutes ces connexions passent par le réseau Tor par défaut, ce qui masque votre adresse IP ; si Tor n'est pas disponible, chatbit attend au lieu de se connecter directement.
- *Messages privés et salons privés* : chiffrés de bout en bout ; les relais ne voient qu'un contenu illisible.
- Les messages publics des salons géographiques sont, par nature, lisibles par les personnes présentes dans ces salons.

**Localisation.** Votre position sert uniquement à calculer, sur le téléphone, le nom de zone approximatif (« geohash ») des salons proches. La position précise ne quitte jamais l'appareil ; seule la zone du salon où vous écrivez est visible des autres, et l'annonce de présence est désactivée par défaut.

**Données sur l'appareil.** Pseudo, réglages, listes de blocage, messages et médias sont stockés uniquement sur votre téléphone, chiffrés par une clé du coffre sécurisé d'Android. Par défaut, les messages sont effacés au bout de 24 heures, et toutes les données sont effacées si l'appli n'est pas ouverte pendant 30 jours. Toucher trois fois le logo efface tout immédiatement. Les métadonnées des photos (position GPS, date, appareil) sont retirées avant l'envoi.

**Permissions.** Bluetooth et appareils à proximité (réseau mesh), localisation (salons géographiques et exigence d'Android pour le Bluetooth/Wi-Fi), notifications, caméra (uniquement pour scanner un QR code), micro (uniquement pour les messages vocaux). Le verrouillage de l'appli utilise uniquement un code PIN propre à chatbit (ni empreinte ni visage).

**Mises à jour.** chatbit peut vérifier via Tor, sur GitHub, si une version plus récente existe, uniquement pour pouvoir la partager hors ligne avec d'autres personnes. Aucune information vous concernant n'est envoyée.

**Enfants.** chatbit n'est pas destiné aux enfants de moins de 13 ans.

**Contact.** https://github.com/ledokter/chatbit/issues

## English

chatbit is an account-free messenger designed to collect no data.

**What we do not collect.** No account, phone number, email, contacts, usage analytics, ads or trackers. The chatbit developers run no server and receive no data.

**How messages travel.**
- *Offline*: over Bluetooth and Wi-Fi directly between nearby phones (mesh network).
- *Location channels and remote private messages*: through public relays of the Nostr protocol, run by third parties. All these connections go through the Tor network by default, which hides your IP address; if Tor is unavailable, chatbit waits instead of connecting directly.
- *Private messages and private rooms*: end-to-end encrypted; relays only see unreadable content.
- Public messages in location channels are, by design, readable by people in those channels.

**Location.** Your position is only used on the phone to compute the approximate area name ("geohash") of nearby channels. Your precise position never leaves the device; only the area of the channel you write in is visible to others, and presence announcements are off by default.

**Data on the device.** Nickname, settings, block lists, messages and media are stored only on your phone, encrypted with a key held by Android's secure keystore. By default, messages are deleted after 24 hours and all data is wiped if the app is not opened for 30 days. Triple-tapping the logo wipes everything immediately. Photo metadata (GPS position, date, device) is removed before sending.

**Permissions.** Bluetooth and nearby devices (mesh), location (location channels and Android's requirement for Bluetooth/Wi-Fi), notifications, camera (only to scan QR codes), microphone (only for voice messages). The app lock only uses chatbit's own PIN (no fingerprint or face).

**Updates.** chatbit may check GitHub through Tor for a newer version, only so it can share it offline with other people. No information about you is sent.

**Children.** chatbit is not intended for children under 13.

**Contact.** https://github.com/ledokter/chatbit/issues

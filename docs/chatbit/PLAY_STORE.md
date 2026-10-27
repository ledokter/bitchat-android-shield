# Publier chatbit sur Google Play — pas-à-pas

À faire une fois. Ensuite, chaque mise à jour = `./tools/chatbit-release.sh X.Y.Z` puis envoi du fichier `.aab` dans la console.

## 1. Compte développeur
1. https://play.google.com/console → créer un compte **personnel** (25 $, vérification d'identité).
2. Les comptes personnels récents doivent faire un **test fermé avec au moins 12 testeurs pendant 14 jours** avant la mise en production. Préparer la liste d'adresses Gmail des testeurs.

## 2. Créer l'appli
- Nom : **chatbit** · Langue par défaut : français · Appli · Gratuite.
- Nom du paquet (définitif) : **app.chatbit** (déjà configuré).

## 3. Signature : utiliser NOTRE clé (indispensable pour des mises à jour transparentes)
Tableau de bord → Configurer → Intégrité de l'appli → Signature d'applications :
1. Choisir **« Utiliser une autre clé de signature d'application »** → « Exporter et importer une clé à partir d'un keystore Java ».
2. Télécharger l'outil **PEPK** et la clé de chiffrement proposés par la console, puis lancer (adapter le nom des fichiers téléchargés) :
   ```
   java -jar pepk.jar --keystore=$HOME/.chatbit-keys/chatbit-release.jks --alias=chatbit \
     --output=chatbit-signing-key.zip --include-cert --rsa-aes-encryption \
     --encryption-key-path=encryption_public_key.pem
   ```
   Le mot de passe est dans `~/.chatbit-keys/password`.
3. Importer `chatbit-signing-key.zip`.
4. **Clé d'envoi** : importer le certificat de `~/.chatbit-keys/chatbit-upload.jks` (alias `upload`) :
   ```
   keytool -export -rfc -keystore $HOME/.chatbit-keys/chatbit-upload.jks -alias upload -file upload_certificate.pem
   ```
Résultat : Play, GitHub, le point d'accès Wi-Fi et Bluetooth distribuent tous la même signature → les utilisateurs passent d'un canal à l'autre sans jamais réinstaller.

## 4. Fiche du Store
- Description courte (80 car.) : *Messagerie sans Internet ni compte : Bluetooth, Tor, salons privés chiffrés.*
- Icône 512×512, capture d'écran (au moins 2), visuel 1024×500.
- Catégorie : Communication.

## 5. Contenu de l'appli (questionnaires)
- **Politique de confidentialité** : `https://github.com/ledokter/chatbit/blob/main/docs/chatbit/PRIVACY.md`
- **Sécurité des données** : *aucune donnée collectée ni partagée* ; données chiffrées en transit : oui ; possibilité de supprimer les données : oui (effacement dans l'appli).
- **Publicités** : non. **Public cible** : 13 ans et plus.
- **Contenu généré par les utilisateurs** : oui — moyens de modération : blocage, bouton « Signaler », filtre anti-flood automatique.
- **Localisation en arrière-plan** : justification — « Garder le réseau mesh Bluetooth actif pour relayer les messages hors ligne quand l'appli est fermée ; la position n'est ni collectée ni transmise. » + courte vidéo de démonstration (demandée par Google).
- **Service de premier plan** (type *connectedDevice*) : maintien du réseau mesh Bluetooth.

## 6. Test fermé puis production
1. Tests → Test fermé → créer une version → envoyer `dist/chatbit-X.Y.Z.aab` → ajouter les 12 testeurs.
2. Après 14 jours : Production → promouvoir la version.
3. Mises à jour suivantes : `./tools/chatbit-release.sh X.Y.Z` (publie aussi sur GitHub), puis envoyer le nouveau `.aab`. Les utilisateurs Play sont mis à jour automatiquement.

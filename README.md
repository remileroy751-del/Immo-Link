# ImmoLink Android V5 — correctif stabilité + interface

Plateforme Android immobilière pour le Togo, Bénin, Mali, Burkina Faso et Côte d'Ivoire.

## Fonctionnalités V5
1. Favoris.
2. Recherche avancée : louer/vendre/bailler, type, ville, budget.
3. Notifications Firebase Cloud Messaging.
4. Recherches enregistrées et alertes sur les nouvelles annonces correspondantes.
5. Recherche limitée au pays du compte côté application + règles Firestore.
6. Maximum 4 photos par annonce. La première est l'image principale de la carte ; les autres apparaissent dans la fiche détaillée.
7. Profil public du propriétaire/démarcheur.
8. Pages d'agences immobilières.
9. Signalement d'annonce.
10. Certification d'agence avec contrôle administratif.
11. Notifications lors de nouveaux messages.

### Règles commerciales
- Compte simple : 3 annonces actives.
- Agence : 15 annonces actives.
- Extension agence : paiement unique de 5 000 FCFA => +10 annonces, total 25.
- Certification agence : pièce d'identité du propriétaire, recto + verso, puis 2 000 FCFA/mois. Le badge n'est actif qu'après validation administrative et paiement confirmé.
- Chaque annonce expire automatiquement après 7 jours.

## Paiements
L'intégration serveur utilise CinetPay Checkout V2. Les clés marchandes ne sont jamais placées dans l'APK.

Avant déploiement :

```bash
firebase functions:secrets:set CINETPAY_API_KEY
firebase functions:secrets:set CINETPAY_SITE_ID
```

Créer `functions/.env` à partir de `functions/.env.example` et renseigner `PUBLIC_BASE_URL` avec l'URL publique de vos Cloud Functions, par exemple :

```text
PUBLIC_BASE_URL=https://us-central1-immolink-a532.cloudfunctions.net
```

Puis :

```bash
npm --prefix functions install
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

CinetPay doit être activé/configuré pour le compte marchand ImmoLink. Les notifications serveur vérifient ensuite le statut réel de la transaction avant de débloquer les avantages payants.

## Firebase
Le fichier `app/google-services.json` est déjà inclus et correspond au package `com.immolink.appname`.

## Compilation APK
Le workflow `.github/workflows/build-apk.yml` utilise Gradle 9.6.1 et produit un APK debug dans les artifacts GitHub Actions.

Pour une version release signée, ajouter ultérieurement un keystore Android et les secrets GitHub correspondants.


### Correctif CI GitHub Actions
Le workflow utilise Gradle **9.6.1** avec `gradle/actions/setup-gradle@v4`. Le rapport de CI indiquait que la valeur `9.6` n'était pas acceptée par l'action ; une version exacte est maintenant fournie. Gradle 9.6.1 est une version officielle de Gradle.

## Correctifs V5 importants
- Le profil utilisateur est chargé avec gestion d'erreur : une erreur Firebase ne ferme plus l'application.
- Toutes les principales lectures asynchrones de l'interface sont protégées par des gestionnaires d'erreur afin d'éviter les fermetures lors d'un retour réseau ou d'une permission Firebase.
- La recherche principale utilise une requête Firestore simple (pays + statut + mode) puis applique les filtres optionnels localement, ce qui évite les combinaisons d'index fragiles au premier démarrage.
- L'inscription crée le profil privé et le profil public avant de laisser l'état connecté se stabiliser.
- Les règles Firestore autorisent désormais la création/mise à jour du profil public par son propriétaire, tout en conservant l'isolation stricte par pays.
- Les écrans agence/profil utilisent des listes correctement contraintes pour éviter les erreurs de mesure Compose.
- L'interface reprend directement les couleurs du logo : bleu profond, orange et fonds clairs, avec cartes, boutons, en-têtes et navigation modernisés.
- L'icône Android utilise uniquement le dessin du logo, sans le texte « ImmoLink / VOTRE LIEN IMMOBILIER ». Le logo complet reste utilisé dans l'écran d'accueil.

## Important après le remplacement du projet
Le ZIP est organisé directement à la racine du dépôt GitHub : le dossier `.github` et `app` doivent être au même niveau que `build.gradle.kts`.
Pour le correctif Firebase côté sécurité/règles, redéployer les règles après remplacement du projet :

```bash
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

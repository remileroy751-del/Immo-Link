# ImmoLink V5 — correctif de stabilité

## Ce qui a été corrigé

- Crash après inscription : le chargement du profil et les accès Firebase sont maintenant gérés par des blocs `try/catch` côté interface.
- Crash au redémarrage : si Firebase renvoie une erreur ou si le profil est incomplet, l'application affiche un écran de récupération avec `Réessayer` / `Se déconnecter` au lieu de fermer.
- Inscription : création du document `users` puis du document `publicProfiles` avant de laisser l'utilisateur poursuivre normalement.
- Règles Firestore : le propriétaire peut créer/mettre à jour son profil public, sans pouvoir modifier son pays.
- Recherche : requête Firestore simplifiée pour éviter les combinaisons d'index optionnelles qui peuvent échouer sur une nouvelle configuration Firebase.
- Écrans agence/profil : listes bornées correctement pour éviter les erreurs de mesure Compose.
- Icône Android : nouveau launcher icon basé uniquement sur le pictogramme du logo fourni, sans le mot `ImmoLink` ni le slogan.
- Interface : palette cohérente avec le logo (bleu profond + orange), cartes arrondies, hiérarchie typographique, boutons et navigation modernisés.

## Après mise en ligne

Le projet Android peut être compilé par GitHub Actions avec `.github/workflows/build-apk.yml`.

Pour appliquer aussi les règles Firebase incluses dans cette version :

```bash
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

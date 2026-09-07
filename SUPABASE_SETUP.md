# Installation Supabase ImmoLink — pas à pas

## 1. SQL Editor
Dans le dashboard Supabase du projet ImmoLink, ouvre **SQL Editor** → **New query**.

Copie-colle entièrement le fichier `supabase_schema.sql`, puis clique **Run**.

Le script crée :
- profiles
- agencies
- listings
- favorites
- saved_searches
- chats
- chat_messages
- reports
- agency_certifications
- payments
- notifications
- fonctions SQL de quota, publication, expiration, favoris, suppression de compte
- Row Level Security (RLS)
- buckets Storage
- policies Storage
- publication Realtime pour messages/notifications

## 2. Auth
Dans **Authentication → Providers → Email** :
- Email provider : ON
- Confirm email : OFF

Le compte ImmoLink utilise directement l'adresse e-mail saisie par l'utilisateur comme identifiant Supabase Auth. Le numéro WhatsApp reste enregistré dans le profil uniquement pour le contact direct sur WhatsApp.

## 3. Compilation
Aucune commande Firebase n'est nécessaire.

Décompresser le ZIP, déposer son contenu à la racine du dépôt GitHub, puis lancer :
**Actions → Build ImmoLink APK → Run workflow**.

## 4. Sécurité
Ne jamais ajouter :
- `service_role`
- clé secrète KKiaPay
- secret Edge Function

dans le code Android.


## Inscription ImmoLink

L'inscription utilise désormais l'e-mail Supabase comme identifiant principal. Le numéro WhatsApp reste obligatoire pour le contact direct, mais n'est plus utilisé comme identifiant Auth.

Champs demandés : pays, ville, prénom, WhatsApp, e-mail, mot de passe, confirmation du mot de passe.

Le mot de passe ImmoLink doit contenir au minimum 8 caractères et uniquement des lettres/chiffres. L'application permet d'afficher ou masquer le mot de passe.

Dans Supabase : Authentication → Providers → Email → désactiver la confirmation d'e-mail si vous voulez que l'utilisateur soit connecté immédiatement après l'inscription.

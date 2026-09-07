# ImmoLink — version Supabase

Cette version abandonne Supabase pour Supabase.

## Projet Supabase
- URL: https://hraiykeenouojxrcwrlx.supabase.co
- Clé publishable intégrée dans `SupabaseRepository.kt` (clé publishable, jamais de service_role dans l'application).

## Première configuration — une seule fois dans Supabase
1. Ouvrir **SQL Editor**.
2. Créer une nouvelle requête.
3. Copier-coller **tout le contenu de `supabase_schema.sql`**.
4. Cliquer sur **Run**.
5. Aller dans **Authentication → Providers → Email** et désactiver **Confirm email** pour permettre à ImmoLink de créer immédiatement la session après l'inscription avec son identifiant interne.

## Important
L'application utilise un e-mail technique dérivé du pays + numéro WhatsApp (`u...@immolink.app`) afin de conserver l'inscription demandée par ImmoLink (pays, ville, prénom, WhatsApp, mot de passe de 6 caractères) sans imposer une adresse e-mail à l'utilisateur.

Ne jamais mettre une clé `service_role` dans Android. Seule la clé publishable peut être embarquée dans l'application.

## GitHub
Le workflow `.github/workflows/build-apk.yml` compile directement l'APK debug. Aucun Supabase CLI n'est nécessaire pour compiler.

## CinetPay
Les paiements CinetPay restent côté serveur. Le projet contient la structure SQL `payments` et `agency_certifications`. Les secrets CinetPay doivent être placés dans une fonction serveur Supabase/Edge Function, jamais dans l'application Android.


### Authentification
- Identifiant principal : adresse e-mail Supabase.
- WhatsApp reste stocké dans `profiles.phone` uniquement pour le contact direct.
- Mot de passe : minimum 8 caractères, lettres/chiffres uniquement côté application.
- Confirmation du mot de passe obligatoire à l'inscription.
- Affichage/masquage du mot de passe disponible dans les champs de mot de passe.
- Désactiver la confirmation e-mail dans Supabase Auth si la connexion doit être immédiate après inscription.

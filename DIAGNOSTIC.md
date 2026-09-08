# Diagnostic du crash au lancement — ce qui a été corrigé

## 1. Thème incompatible avec AppCompatActivity (corrigé)
`Theme.ImmoLink` héritait de `android:style/Theme.Material.Light.NoActionBar`
(thème système) alors que `MainActivity` étend `AppCompatActivity`, qui exige
un thème descendant de `Theme.AppCompat`. C'est l'une des causes les plus
fréquentes de crash immédiat au lancement sous Android.
→ Corrigé : le thème hérite maintenant de `Theme.AppCompat.Light.NoActionBar`.

## 2. Initialisation du SDK Kkiapay au mauvais endroit (corrigé)
La documentation officielle de Kkiapay est explicite : le SDK doit être
initialisé dans `Application.onCreate()`, pas dans une Activity. Le code
initialisait `Kkiapay.init(...)` dans `MainActivity.onCreate()`, ce qui ne
respecte pas le cycle de vie attendu par le SDK et peut provoquer un crash
précoce (potentiellement natif, donc invisible même à un gestionnaire
d'exceptions Java).
→ Corrigé : l'initialisation a été déplacée dans une nouvelle classe
`CrashHandlerApp` (qui étend `Application`), déclarée dans le manifeste via
`android:name=".CrashHandlerApp"`.

## 3. Capteur de rapport d'erreur renforcé
Le gestionnaire d'exceptions global est maintenant installé dans
`attachBaseContext()` de l'Application — le tout premier point d'entrée
disponible dans le cycle de vie de l'app, avant même l'initialisation de
Kkiapay ou de MainActivity. Toute exception Java non gérée, où qu'elle
survienne, ouvre désormais un écran affichant le rapport complet avec un
bouton "Copier".

## Si le rapport ne s'affiche toujours pas

Cela indiquerait un **crash natif** (code C/C++ dans une bibliothèque tierce,
par ex. le SDK Kkiapay ou une dépendance graphique) — ce type de crash ne peut
PAS être intercepté par du code Java/Kotlin, quel qu'il soit. Le seul moyen
d'obtenir le vrai message d'erreur est de lire le journal système (logcat)
au moment du crash. Deux options, sans PC :

1. **Installer une app de lecture de logs** directement sur le téléphone
   (ex. "LogFox" ou "Logcat Reader" sur le Play Store / F-Droid), lancer
   l'app ImmoLink, laisser le crash se produire, puis chercher dans le log
   la ligne contenant `FATAL EXCEPTION`, `SIGSEGV`, `signal 11`, ou le nom
   du package `com.immolink.appname` juste avant l'arrêt du processus.
2. **Avec un PC** : brancher le téléphone en USB (mode débogage USB activé),
   puis lancer :
   ```
   adb logcat *:E
   ```
   ouvrir l'app, et copier les lignes qui apparaissent au moment du crash.

Envoie-moi ce texte et je pourrai identifier la cause exacte.

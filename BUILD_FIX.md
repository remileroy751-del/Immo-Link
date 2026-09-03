# ImmoLink V4 - correctif de compilation

Le projet utilise AGP 9.4.0. AGP 9 active le support Kotlin intégré : le plugin `org.jetbrains.kotlin.android` ne doit donc pas être appliqué.

Le plugin Compose reste actif et est aligné sur Kotlin 2.2.10, version fournie par le support Kotlin intégré d'AGP 9.

Le workflow GitHub utilise Gradle 9.6.1.

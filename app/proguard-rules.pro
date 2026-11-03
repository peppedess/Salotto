# Regole R8 di Salotto.
# Le librerie (Compose, Coil, OkHttp, WorkManager, coroutine) portano già le loro regole.

# Componenti dichiarati nel manifest o creati dal sistema.
-keep class it.peppedess.salotto.service.** { *; }
-keep class it.peppedess.salotto.kiosk.KioskAdmin { *; }

# Dipendenze opzionali di OkHttp non presenti su Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Righe utili negli errori.
-keepattributes SourceFile,LineNumberTable

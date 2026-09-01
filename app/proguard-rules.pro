# R8 v plném režimu. Knihovny (OkHttp, Okio, Media3, Compose) si vlastní pravidla
# přinášejí samy, takže tady je jen to, co se týká přímo této aplikace.

# Vstupní body deklarované v manifestu si R8 drží sám (Activity, Service,
# Application, FileProvider), proto pro ně žádná pravidla nejsou potřeba.

# org.json je součást systému Androidu, ne knihovna v APK.
-dontwarn org.json.**

# OkHttp volitelně sahá po věcech, které v appce nejsou (Conscrypt, BouncyCastle,
# GraalVM). Bez tohoto by R8 hlásil varování na chybějící třídy.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**

# Zachovat čitelné názvy zdrojových souborů a řádků v případném pádu.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

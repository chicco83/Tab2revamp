#!/usr/bin/env bash
# =============================================================================
# Tab2Monitor - build dell'APK senza Gradle
# Versione: v0.10.0 - 2026-10-22 02:30 (branch installer)
# v0.10.0: minSdk 21 (Android 5.0+), targetSdk 25 separato; controllo API con
#          API_CHECK=1 (compila contro platforms/android-21: un metodo piu' recente
#          non protetto da Build.VERSION fa fallire javac); firma di RELEASE se sono
#          impostate T2M_KEYSTORE / T2M_KS_PASS / T2M_KEY_ALIAS (password mai nel repo),
#          altrimenti chiave di debug come prima. Con minSdk < 24 apksigner aggiunge da
#          solo la firma v1 (JAR) richiesta da Android 5-6.
# =============================================================================
# Perche' senza Gradle: l'app e' fatta di 3 classi Java e un'icona; Gradle +
# Android Gradle Plugin scaricherebbero centinaia di MB. Bastano gli strumenti
# dell'SDK gia' installato:
#   aapt2 (risorse + manifest) -> javac (Java 8, android.jar) -> d8 (dex)
#   -> jar (aggiunge classes.dex) -> zipalign -> apksigner (chiave di debug)
#
# Requisiti (verificati sul PC il 2026-10-21):
#   - Android SDK in %LOCALAPPDATA%\Android\Sdk (build-tools 34-36, platforms 34+)
#   - JDK: quello incluso in Android Studio (C:\Program Files\Android\Android Studio\jbr)
#     oppure JAVA_HOME. Nessun download necessario.
#   - chiave: ~/.android/debug.keystore (creata se manca, password "android")
#
# Uso (Git Bash, dalla radice del repo o da qui):
#   bash android/tab2monitor_app/build_tab2monitor_v0.10.0_20261022-0230.sh
#   API_CHECK=1 bash ...                      (solo controllo API 21, niente APK)
#   T2M_KEYSTORE=~/.tab2monitor/release.jks T2M_KEY_ALIAS=tab2monitor T2M_KS_PASS=... bash ...
# Risultato: android/tab2monitor_app/dist/Tab2Monitor_v<ver>.apk (escluso da git)
# =============================================================================
set -euo pipefail

VERSION_NAME="0.10.0"
VERSION_CODE=13
MIN_SDK=21
TARGET_SDK=25
# 2026-10-22 v0.9.1: MIN_SDK=25 (usato anche come target)

HERE="$(cd "$(dirname "$0")" && pwd)"
BUILD="$HERE/build"
DIST="$HERE/dist"

# --- strumenti ---------------------------------------------------------------
SDK="${ANDROID_SDK_ROOT:-${LOCALAPPDATA:-}/Android/Sdk}"
SDK="$(cygpath -u "$SDK" 2>/dev/null || echo "$SDK")"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
# android.jar: la piattaforma piu' recente con API numerica (serve solo per compilare;
# minSdk 25 garantisce che l'APK giri su Android 7.1)
PLAT="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)"
ANDROID_JAR="$PLAT/android.jar"

if [ -n "${JAVA_HOME:-}" ]; then JH="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")"
else JH="/c/Program Files/Android/Android Studio/jbr"; fi
[ -x "$JH/bin/javac" ] || [ -x "$JH/bin/javac.exe" ] || { echo "JDK non trovato (JAVA_HOME o Android Studio jbr)"; exit 1; }
export PATH="$JH/bin:$PATH"
# d8 e apksigner vengono lanciati con "java -cp/-jar" e non con d8.bat/apksigner.bat:
# i .bat spezzano gli argomenti che contengono spazi ("VIBE CODING").

EXE=""; [ -f "$BT/aapt2.exe" ] && EXE=".exe"
echo "build-tools: $BT"
echo "android.jar: $ANDROID_JAR"
echo "JDK:         $JH"

# --- pulizia -----------------------------------------------------------------
rm -rf "$BUILD"; mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$DIST"

# --- 1. risorse + manifest ---------------------------------------------------
"$BT/aapt2$EXE" compile --dir "$HERE/res" -o "$BUILD/res.zip"
"$BT/aapt2$EXE" link -I "$ANDROID_JAR" \
    --manifest "$HERE/AndroidManifest.xml" \
    --min-sdk-version $MIN_SDK --target-sdk-version $TARGET_SDK \
    --version-code $VERSION_CODE --version-name "$VERSION_NAME" \
    --java "$BUILD/gen" -o "$BUILD/unsigned.apk" "$BUILD/res.zip"

# --- 2. Java -> class (bytecode Java 8, API di android.jar) -------------------
# percorsi in formato Windows e tra virgolette: il repo sta in "G:\Il mio Drive\..."
# (spazi) e javac.exe non capisce i percorsi /g/... di Git Bash dentro un @file
find "$HERE/src" "$BUILD/gen" -name '*.java' | while read -r f; do
    printf '"%s"\n' "$(cygpath -m "$f" 2>/dev/null || echo "$f")"; done > "$BUILD/sources.txt"
javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"

# --- 2b. v0.10.0: controllo API minSdk ---------------------------------------
# javac contro android.jar della API 21: ogni chiamata a un metodo/classe piu' recente
# fa fallire la compilazione (i rami protetti da Build.VERSION.SDK_INT vanno chiamati
# con riflessione o restano fuori dal controllo: qui ce ne sono solo per API 23,
# Context.checkSelfPermission / Activity.requestPermissions, elencati in ALLOW_NEWER).
if [ "${API_CHECK:-0}" = "1" ]; then
    J21="$SDK/platforms/android-$MIN_SDK/android.jar"
    [ -f "$J21" ] || { echo "manca $J21 (sdkmanager \"platforms;android-$MIN_SDK\")"; exit 1; }
    mkdir -p "$BUILD/classes21"
    if javac -nowarn -Xlint:-options -encoding UTF-8 -source 8 -target 8 \
        -bootclasspath "$J21" -d "$BUILD/classes21" @"$BUILD/sources.txt" 2> "$BUILD/api21.txt"; then
        echo "API $MIN_SDK: nessuna chiamata a API piu' recenti"
    else
        echo "API $MIN_SDK: simboli mancanti (se non sono in ALLOW_NEWER vanno protetti):"
        grep -E "error:|symbol:" "$BUILD/api21.txt" | sed 's/^/  /'
    fi
    exit 0
fi

# --- 3. class -> dex ---------------------------------------------------------
# le .class vengono passate a d8 dentro un jar (niente liste di file con spazi)
( cd "$BUILD/classes" && jar cf ../classes.jar . )
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api $MIN_SDK \
    --lib "$ANDROID_JAR" --output "$BUILD/dex" "$BUILD/classes.jar"

# --- 4. dex nell'APK, allineamento, firma ------------------------------------
( cd "$BUILD/dex" && jar uf "$BUILD/unsigned.apk" classes.dex )
"$BT/zipalign$EXE" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

OUT="$DIST/Tab2Monitor_v$VERSION_NAME.apk"
if [ -n "${T2M_KEYSTORE:-}" ]; then
    # v0.10.0: chiave di release (file e password fuori dal repository)
    : "${T2M_KS_PASS:?serve T2M_KS_PASS}"; : "${T2M_KEY_ALIAS:?serve T2M_KEY_ALIAS}"
    KS="$(cygpath -m "$T2M_KEYSTORE" 2>/dev/null || echo "$T2M_KEYSTORE")"
    echo "firma: release ($KS, alias $T2M_KEY_ALIAS)"
    java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass env:T2M_KS_PASS --key-pass env:T2M_KS_PASS \
        --ks-key-alias "$T2M_KEY_ALIAS" --min-sdk-version $MIN_SDK --out "$OUT" "$BUILD/aligned.apk"
else
    # 2026-10-22 v0.9.1: unica strada (chiave di debug)
    KS="$HOME/.android/debug.keystore"
    if [ ! -f "$KS" ]; then
        mkdir -p "$HOME/.android"
        keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
            -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
            -dname "CN=Android Debug,O=Android,C=US"
    fi
    echo "firma: debug ($KS)"
    java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
        --ks-key-alias androiddebugkey --min-sdk-version $MIN_SDK --out "$OUT" "$BUILD/aligned.apk"
fi
java -jar "$BT/lib/apksigner.jar" verify "$OUT"

ls -l "$OUT"
echo "OK: $OUT"

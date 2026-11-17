#!/bin/bash
# 뉴니버스 런처 APK 빌드 (Gradle 없이 SDK 도구로 직접)
set -e
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$PATH
SDK=${ANDROID_HOME:-$HOME/android-sdk}; BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
WEB=../sand-transition
rm -rf build && mkdir -p build/gen build/classes build/dex build/apk
# 1) 웹 자산 복사
rm -rf assets/www && mkdir -p assets/www
cp $WEB/index.html assets/www/
cp -r $WEB/wallpapers assets/www/
cp -r $WEB/models assets/www/
# 2) 리소스 컴파일/링크
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/base.apk -I $JAR --manifest AndroidManifest.xml -A assets --java build/gen build/res.zip --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34 --version-code 21 --version-name 0.21-test
# 3) 자바 → dex
javac -source 17 -target 17 -encoding UTF-8 -classpath $JAR -d build/classes $(find src build/gen -name '*.java') 2>&1 | grep -v "^Note:" || true
$BT/d8 --release --min-api 26 --lib $JAR --output build/dex $(find build/classes -name '*.class')
# 4) dex 넣고 정렬·서명
cp build/base.apk build/unsigned.apk
python3 -c "import zipfile;z=zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED);z.write('build/dex/classes.dex','classes.dex');z.close()"
$BT/zipalign -f -p 4 build/unsigned.apk build/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out build/newniverse-launcher.apk build/aligned.apk
$BT/apksigner verify build/newniverse-launcher.apk && ls -la build/newniverse-launcher.apk

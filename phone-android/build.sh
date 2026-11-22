#!/bin/bash
# 뉴니버스 전화 APK 빌드 (Gradle 없이 SDK 도구로 직접)  →  build/newniverse-phone.apk
# 서명: 런처와 같은 고정 릴리스 키 (~/.nv-release/release.jks, 비밀번호는 같은 폴더 storepass 파일). 저장소에 넣지 말 것.
set -e
cd "$(dirname "$0")"
VC=2; VN=2-test
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$PATH
SDK=${ANDROID_HOME:-$HOME/android-sdk}; BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
KEYDIR=${NV_KEYDIR:-$HOME/.nv-release}
B=build/work
rm -rf $B && mkdir -p $B/gen $B/classes $B/dex
$BT/aapt2 compile --dir res -o $B/res.zip
$BT/aapt2 link -o $B/base.apk -I $JAR --manifest AndroidManifest.xml -A assets --java $B/gen $B/res.zip --auto-add-overlay \
  --min-sdk-version 26 --target-sdk-version 34 --version-code $VC --version-name $VN --replace-version
javac -Xlint:-deprecation -source 17 -target 17 -encoding UTF-8 -classpath $JAR -d $B/classes $(find src -name '*.java') $(find $B/gen -name '*.java') 2>&1 | grep -v "^Note:" || true
ls $B/classes/kr/newniverse/phone/MainActivity.class >/dev/null
$BT/d8 --release --min-api 26 --lib $JAR --output $B/dex $(find $B/classes -name '*.class')
cp $B/base.apk $B/unsigned.apk
python3 -c "import zipfile;z=zipfile.ZipFile('$B/unsigned.apk','a',zipfile.ZIP_DEFLATED);z.write('$B/dex/classes.dex','classes.dex');z.close()"
$BT/zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
OUT=build/newniverse-phone.apk
$BT/apksigner sign --ks $KEYDIR/release.jks --ks-key-alias newniverse --ks-pass file:$KEYDIR/storepass --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $OUT $B/aligned.apk
$BT/apksigner verify $OUT && ls -la $OUT

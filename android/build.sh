#!/bin/bash
# 뉴니버스 런처 APK 빌드 (Gradle 없이 SDK 도구로 직접)
#   ./build.sh basic  → 기본판 (접근성 서비스 없음, 권한 최소)  build/newniverse-launcher-basic.apk
#   ./build.sh plus   → 추가판 (두 번 톡톡 진짜 화면 끄기 포함)  build/newniverse-launcher-plus.apk
# 서명: 고정 릴리스 키 (~/.nv-release/release.jks, 비밀번호는 같은 폴더 storepass 파일). 저장소에 넣지 말 것.
set -e
cd "$(dirname "$0")"
FLAVOR=${1:-basic}
VC=44; VN=0.43b-$FLAVOR
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$PATH
SDK=${ANDROID_HOME:-$HOME/android-sdk}; BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
KEYDIR=${NV_KEYDIR:-$HOME/.nv-release}
WEB=../sand-transition
B=build/$FLAVOR
rm -rf $B && mkdir -p $B/gen/kr/newniverse/launcher $B/classes $B/dex $B/res
# 1) 웹 자산 복사
rm -rf assets/www && mkdir -p assets/www
cp $WEB/index.html assets/www/
cp -r $WEB/wallpapers assets/www/
cp -r $WEB/models assets/www/
# 2) 판별 설정
cp -r res/* $B/res/
if [ "$FLAVOR" = basic ]; then MAN=AndroidManifest.basic.xml; rm -rf $B/res/xml; SRC=$(find src -name '*.java' ! -name LockService.java); BASIC=true
else MAN=AndroidManifest.xml; SRC=$(find src -name '*.java'); BASIC=false; fi
echo "package kr.newniverse.launcher; final class Flavor{ static final boolean BASIC=$BASIC; }" > $B/gen/kr/newniverse/launcher/Flavor.java
# 3) 리소스 컴파일/링크
$BT/aapt2 compile --dir $B/res -o $B/res.zip
$BT/aapt2 link -o $B/base.apk -I $JAR --manifest $MAN -A assets --java $B/gen $B/res.zip --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34 --version-code $VC --version-name $VN --replace-version
# 4) 자바 → dex
javac -source 17 -target 17 -encoding UTF-8 -classpath $JAR -d $B/classes $SRC $(find $B/gen -name '*.java') 2>&1 | grep -v "^Note:" || true
$BT/d8 --release --min-api 26 --lib $JAR --output $B/dex $(find $B/classes -name '*.class')
# 5) dex 넣고 정렬·서명
cp $B/base.apk $B/unsigned.apk
python3 -c "import zipfile;z=zipfile.ZipFile('$B/unsigned.apk','a',zipfile.ZIP_DEFLATED);z.write('$B/dex/classes.dex','classes.dex');z.close()"
$BT/zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
OUT=build/newniverse-launcher-$FLAVOR.apk
$BT/apksigner sign --ks $KEYDIR/release.jks --ks-key-alias newniverse --ks-pass file:$KEYDIR/storepass --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $OUT $B/aligned.apk
$BT/apksigner verify $OUT && ls -la $OUT

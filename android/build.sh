#!/bin/bash
# 뉴니버스 런처 APK 빌드 (Gradle 없이 SDK 도구로 직접)
#   ./build.sh basic       → 기본판 시험판 (전원 창 서비스 포함, 화면 끄기 접근성 없음)  build/newniverse-launcher-basic.apk
#   ./build.sh plus        → 추가판 시험판 (두 번 톡톡 화면 끄기 + 전원 창)            build/newniverse-launcher-plus.apk
#   ./build.sh basic play  → Play 스토어용 기본판 (전원 창 서비스 제외)
#   ./build.sh plus play   → Play 스토어용 추가판 (화면 끄기만, 전원 창 서비스 제외)
# 전원 창(v44b)은 시험 기능이다. 스토어에 올리려면 접근성 선언서를 내거나 위 play 인자로 이 서비스를 빼야 한다.
# 서명: 고정 릴리스 키 (~/.nv-release/release.jks, 비밀번호는 같은 폴더 storepass 파일). 저장소에 넣지 말 것.
set -e
cd "$(dirname "$0")"
FLAVOR=${1:-basic}
STORE=${2:-}
VC=48; VN=0.46b-$FLAVOR
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
if [ "$FLAVOR" = basic ]; then MAN0=AndroidManifest.basic.xml; BASIC=true
else MAN0=AndroidManifest.xml; BASIC=false; fi
# play 인자: 전원 창 접근성 서비스를 빼고 Flavor.POWER=false (스토어 출시용)
POWER=true
if [ "$STORE" = play ]; then POWER=false; fi
FIND=(find src -name '*.java')
if [ "$BASIC" = true ]; then FIND+=(! -name LockService.java); rm -f $B/res/xml/lock_service.xml; fi
if [ "$POWER" = false ]; then FIND+=(! -name PowerService.java); rm -f $B/res/xml/power_service.xml; fi
if [ ! "$(ls -A $B/res/xml 2>/dev/null)" ]; then rm -rf $B/res/xml; fi
SRC=$("${FIND[@]}")
cp "$MAN0" $B/AndroidManifest.xml
MAN=$B/AndroidManifest.xml
if [ "$POWER" = false ]; then
  python3 strip-power.py "$MAN"
fi
echo "package kr.newniverse.launcher; final class Flavor{ static final boolean BASIC=$BASIC; static final boolean POWER=$POWER; }" > $B/gen/kr/newniverse/launcher/Flavor.java
# 3) 리소스 컴파일/링크
$BT/aapt2 compile --dir $B/res -o $B/res.zip
$BT/aapt2 link -o $B/base.apk -I $JAR --manifest $MAN -A assets --java $B/gen $B/res.zip --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34 --version-code $VC --version-name $VN --replace-version
# 4) 자바 → dex
javac -source 17 -target 17 -encoding UTF-8 -classpath $JAR -d $B/classes $SRC $(find $B/gen -name '*.java') >$B/javac.log 2>&1 || true
if grep -E "error:" $B/javac.log >/dev/null; then grep -E "error:|^src/" $B/javac.log | head -40; exit 1; fi
$BT/d8 --release --min-api 26 --lib $JAR --output $B/dex $(find $B/classes -name '*.class')
# 5) dex 넣고 정렬·서명
cp $B/base.apk $B/unsigned.apk
python3 -c "import zipfile;z=zipfile.ZipFile('$B/unsigned.apk','a',zipfile.ZIP_DEFLATED);z.write('$B/dex/classes.dex','classes.dex');z.close()"
$BT/zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
if [ "$POWER" = false ]; then OUT=build/newniverse-launcher-$FLAVOR-play.apk; else OUT=build/newniverse-launcher-$FLAVOR.apk; fi
$BT/apksigner sign --ks $KEYDIR/release.jks --ks-key-alias newniverse --ks-pass file:$KEYDIR/storepass --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $OUT $B/aligned.apk
$BT/apksigner verify $OUT && ls -la $OUT

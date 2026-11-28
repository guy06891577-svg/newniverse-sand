#!/bin/bash
# 뉴니버스 전화 APK 빌드 (Gradle 없이 SDK 도구로 직접)  →  build/newniverse-phone.apk
# 서명: 런처와 같은 고정 릴리스 키 (~/.nv-release/release.jks, 비밀번호는 같은 폴더 storepass 파일). 저장소에 넣지 말 것.
# v6: 명함 글자 읽기용 ML Kit(한국어 번들 모델) 부품을 같이 넣음 → deps/fetch-deps.sh 로 ~/.nv-deps/mlkit 에 미리 받아 둠
#     NV_ABIS: 넣을 CPU 종류 (기본 arm64-v8a armeabi-v7a = 실제 폰. 에뮬레이터 시험은 x86_64 추가)
set -e
cd "$(dirname "$0")"
VC=6; VN=6-test
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$PATH
SDK=${ANDROID_HOME:-$HOME/android-sdk}; BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
KEYDIR=${NV_KEYDIR:-$HOME/.nv-release}
DEPS=${NV_DEPS:-$HOME/.nv-deps/mlkit}
ABIS=${NV_ABIS:-"arm64-v8a armeabi-v7a"}
OUT=${NV_OUT:-build/newniverse-phone.apk}
B=build/work
rm -rf $B && mkdir -p $B/gen $B/classes $B/dex $B/aar $B/libres
ls $DEPS/text-recognition-korean-16.0.1.aar >/dev/null || { echo "ML Kit 부품 없음: deps/fetch-deps.sh 먼저"; exit 1; }

# ---- 라이브러리(AAR) 풀기: 코드 · 리소스 · assets(모델) · jni(.so) ----
LIBJARS=""; PKGS=""; LIBRES=""; ASSETS="-A assets"
for f in $DEPS/*.aar; do
  n=$(basename $f .aar); d=$B/aar/$n; mkdir -p $d; (cd $d && unzip -oq $f)
  [ -f $d/classes.jar ] && LIBJARS="$LIBJARS:$d/classes.jar"
  [ -d $d/libs ] && for j in $d/libs/*.jar; do LIBJARS="$LIBJARS:$j"; done
  p=$(python3 -c "import re,sys;print(re.search(r'package=\"([^\"]+)\"',open('$d/AndroidManifest.xml').read()).group(1))")
  if [ -d $d/res ] && [ -n "$(ls -A $d/res)" ]; then $BT/aapt2 compile --dir $d/res -o $B/libres/$n.zip; LIBRES="$LIBRES $B/libres/$n.zip"; PKGS="$PKGS:$p"; fi
  [ -d $d/assets ] && ASSETS="$ASSETS -A $d/assets"
done
for f in $DEPS/*.jar; do LIBJARS="$LIBJARS:$f"; done
LIBJARS=${LIBJARS#:}; PKGS=${PKGS#:}

$BT/aapt2 compile --dir res -o $B/res.zip
# 모델 파일(assets/mlkit-google-ocr-models)은 압축하지 않음 (ML Kit 가 바로 열어 씀)
$BT/aapt2 link -o $B/base.apk -I $JAR --manifest AndroidManifest.xml $ASSETS --java $B/gen $LIBRES $B/res.zip --auto-add-overlay \
  --extra-packages "$PKGS" --no-compress-regex "(\.tflite|\.binarypb|\.bincfg|\.fb|\.pb|_model)$" \
  --min-sdk-version 26 --target-sdk-version 34 --version-code $VC --version-name $VN --replace-version
javac -Xlint:-deprecation -source 17 -target 17 -encoding UTF-8 -classpath $JAR:$LIBJARS -d $B/classes $(find src src-ocr -name '*.java') $(find $B/gen -name '*.java') 2>&1 | grep -v "^Note:" || true
ls $B/classes/kr/newniverse/phone/MainActivity.class $B/classes/kr/newniverse/phone/CardScanActivity.class >/dev/null
$BT/d8 --release --min-api 26 --lib $JAR --output $B/dex $(find $B/classes -name '*.class') ${LIBJARS//:/ } 2>&1 | grep -v "^Warning" || true
ls $B/dex/classes.dex >/dev/null
cp $B/base.apk $B/unsigned.apk
python3 - "$B" "$ABIS" <<'PY'
import zipfile,os,sys,glob
B,abis=sys.argv[1],sys.argv[2].split()
z=zipfile.ZipFile(B+'/unsigned.apk','a',zipfile.ZIP_DEFLATED)
for f in sorted(os.listdir(B+'/dex')):
  if f.endswith('.dex'): z.write(B+'/dex/'+f,f)
for so in glob.glob(B+'/aar/*/jni/*/*.so'):
  abi=so.split('/')[-2]
  if abi in abis: z.write(so,'lib/%s/%s'%(abi,os.path.basename(so)))
z.close()
PY
$BT/zipalign -f -p 4 $B/unsigned.apk $B/aligned.apk
mkdir -p "$(dirname "$OUT")"
$BT/apksigner sign --ks $KEYDIR/release.jks --ks-key-alias newniverse --ks-pass file:$KEYDIR/storepass --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out $OUT $B/aligned.apk
$BT/apksigner verify $OUT && ls -la $OUT

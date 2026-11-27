#!/bin/bash
# 뉴니버스 전화 시험: 순수 자바(번호 규칙·요약 묶기) + 매니페스트 + Robolectric(방해 금지 켜고 되돌리기·자동 답장 규칙·전화 이어붙이기·끝난 뒤 요약).
# 필요: JDK17+, Maven(~/tools/apache-maven-3.9.9), 안드로이드 SDK(aapt2). 인터넷(Maven 저장소)은 처음 한 번만.
set -e
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.9/bin:$PATH
SDK=${ANDROID_HOME:-$HOME/android-sdk}; BT=$SDK/build-tools/34.0.0
rm -rf gen && mkdir -p gen && $BT/aapt2 compile --dir ../../res -o /tmp/phone-robo-res.zip
$BT/aapt2 link -o /tmp/phone-robo-base.apk -I $SDK/platforms/android-34/android.jar --manifest ../../AndroidManifest.xml -A ../../assets --java gen /tmp/phone-robo-res.zip --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34
mkdir -p lib
for a in "androidx/test/monitor/1.7.1/monitor-1.7.1" "androidx/test/core/1.6.1/core-1.6.1" "androidx/tracing/tracing/1.1.0/tracing-1.1.0" "androidx/test/espresso/espresso-idling-resource/3.6.1/espresso-idling-resource-3.6.1"; do
  n=$(basename $a); [ -f lib/$n.jar ] || { cp ../../../memo-android/tests/robo/lib/$n.jar lib/$n.jar 2>/dev/null || { curl -sfL -o lib/$n.aar https://maven.google.com/$a.aar && unzip -p lib/$n.aar classes.jar > lib/$n.jar && rm lib/$n.aar; }; }
done
mvn -B -o -q test "$@" || mvn -B -q test "$@"
echo "phone tests: OK"

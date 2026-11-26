#!/bin/bash
# v7: Robolectric(안드로이드 흉내) 시험 — 가짜 문자함·가짜 달력으로 ‘다시 읽기’와 받기 경로를 끝까지 돌림.
# 필요: JDK17, Maven(~/tools/apache-maven-3.9.9), 안드로이드 SDK(aapt2). 인터넷(Maven 저장소)은 처음 한 번만.
set -e
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.9/bin:$PATH
SDK=${ANDROID_HOME:-$HOME/android-sdk}; BT=$SDK/build-tools/34.0.0
# 1) R.java (리소스 번호) 만들기
rm -rf gen && mkdir -p gen && $BT/aapt2 compile --dir ../../res -o /tmp/robo-res.zip
$BT/aapt2 link -o /tmp/robo-base.apk -I $SDK/platforms/android-34/android.jar --manifest ../../AndroidManifest.xml -A ../../assets --java gen /tmp/robo-res.zip --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34
# 2) 순수 자바 시험이 쓰는 글감 가져오기
mkdir -p fixtures/kr/newniverse/sms && cp ../CalV5Test.java ../MmsTest.java ../CalV8Test.java fixtures/kr/newniverse/sms/
# 3) androidx 테스트 jar (aar 안의 classes.jar)
mkdir -p lib
for a in "androidx/test/monitor/1.7.1/monitor-1.7.1" "androidx/test/core/1.6.1/core-1.6.1" "androidx/tracing/tracing/1.1.0/tracing-1.1.0" "androidx/test/espresso/espresso-idling-resource/3.6.1/espresso-idling-resource-3.6.1"; do
  n=$(basename $a); [ -f lib/$n.jar ] || { curl -sfL -o lib/$n.aar https://maven.google.com/$a.aar && unzip -p lib/$n.aar classes.jar > lib/$n.jar && rm lib/$n.aar; }
done
mvn -B -q test "$@"
echo "robolectric: OK"

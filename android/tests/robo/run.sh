#!/bin/bash
# 런처 TTS(NvTts) 성공·실패·무음·대기 초과 재시도 시험. Gradle 없이 Maven + Robolectric.
set -e
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.9/bin:$PATH
mkdir -p src/main/java/kr/newniverse/launcher lib
cp -f ../../src/kr/newniverse/launcher/NvTts.java src/main/java/kr/newniverse/launcher/NvTts.java
for a in monitor-1.7.1.jar core-1.6.1.jar tracing-1.1.0.jar espresso-idling-resource-3.6.1.jar; do
  [ -f lib/$a ] || cp -f ../../../phone-android/tests/robo/lib/$a lib/$a
done
mvn -B -o -q test "$@" || mvn -B -q test "$@"
echo "launcher TTS tests: OK"

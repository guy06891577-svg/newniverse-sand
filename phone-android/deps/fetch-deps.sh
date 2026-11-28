#!/bin/bash
# v6 명함 글자 읽기(ML Kit 한국어, 기기 안 번들 모델) 부품 내려받기 → ~/.nv-deps/mlkit/*.aar|*.jar
# 버전은 mlkit-pom.xml 에 고정 (text-recognition-korean 16.0.1). appcompat·fragment 는 안 씀.
set -e
cd "$(dirname "$0")"
OUT=${NV_DEPS:-$HOME/.nv-deps/mlkit}; mkdir -p "$OUT"
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}
MVN=${MVN:-$HOME/tools/apache-maven-3.9.9/bin/mvn}
"$MVN" -q -f mlkit-pom.xml dependency:copy-dependencies -DoutputDirectory="$OUT" -DexcludeTransitive=false
ls "$OUT"

#!/bin/bash
set -e
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-$HOME/jdk}; export PATH=$JAVA_HOME/bin:$PATH
OUT=tests/out
rm -rf "$OUT" && mkdir -p "$OUT"
# pure-java sources only (no android.*)
SRC=$(ls src/kr/newniverse/sms/{InboxLogic,FeedFilter,SmsSafety,ApptParser,CalLogic,MmsPdu,MmsLogic,MsgMerge,CalDecide}.java)
javac -encoding UTF-8 -source 17 -target 17 -d "$OUT" $SRC tests/InboxLogicTest.java tests/FeedFilterTest.java tests/CalTest.java tests/MmsTest.java tests/CalV5Test.java tests/CalV6Test.java
python3 - <<'PY'
import subprocess,sys
ok=True
for c in ["kr.newniverse.sms.InboxLogicTest","kr.newniverse.sms.FeedFilterTest","kr.newniverse.sms.CalTest","kr.newniverse.sms.MmsTest","kr.newniverse.sms.CalV5Test","kr.newniverse.sms.CalV6Test"]:
    print("==",c)
    r=subprocess.run(["java","-cp","tests/out",c])
    if r.returncode!=0: ok=False
sys.exit(0 if ok else 1)
PY

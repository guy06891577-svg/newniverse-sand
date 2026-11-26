#!/usr/bin/env python3
"""v7: 화면(JS)이 부르는 NV.이름() 이 모두 Bridge.java 의 @JavascriptInterface 로 있는지, 화면이 기다리는 window.NV_… 를 자바가 부르는지 확인"""
import re,sys,os
here=os.path.dirname(os.path.abspath(__file__))
js=open(os.path.join(here,'..','assets/www/index.html'),encoding='utf-8').read()
br=open(os.path.join(here,'..','src/kr/newniverse/sms/Bridge.java'),encoding='utf-8').read()
used=set(re.findall(r'\bNV\.([A-Za-z_]\w*)',js))
exposed=set(re.findall(r'@JavascriptInterface\s+(?:public\s+)?[\w<>\[\]]+\s+(\w+)\s*\(',br))
missing=sorted(n for n in used if n not in exposed)
fails=0
if missing: print("FAIL JS calls bridge methods that do not exist:",missing); fails+=1
# 자바가 화면에 돌려주는 콜백 이름이 화면에 정의돼 있는지
cb=set(re.findall(r'window\.(NV_\w+)&&',br+open(os.path.join(here,'..','src/kr/newniverse/sms/MainActivity.java'),encoding='utf-8').read()))
defined=set(re.findall(r'window\.(NV_\w+)\s*=',js))
nodef=sorted(n for n in cb if n not in defined)
if nodef: print("FAIL Java calls JS callbacks that are not defined:",nodef); fails+=1
for need in ['calRereadStart','diag','calPending','calPendAdd','calPendNo','openNotifSettings']:
    if need not in exposed: print("FAIL missing",need); fails+=1
if 'NV_reread' not in defined: print("FAIL NV_reread not defined"); fails+=1
# 다시 읽기 단추의 data-b 와 act() 가지가 맞는지
if 'data-b="calreread"' not in js or "k==='calreread'" not in js: print("FAIL calreread button/handler mismatch"); fails+=1
# v8: 앱 안 카드 단추(data-b)와 act() 가지가 맞는지
for k in ['pendadd:','pendno:','notifset']:
    if ('data-b="'+k) not in js and k!='notifset': print("FAIL card button missing",k); fails+=1
    if ("'"+k+"'") not in js and ("startsWith('"+k+"')") not in js: print("FAIL act() branch missing",k); fails+=1
if 'data-b="notifset"' not in js: print("FAIL notifset button missing"); fails+=1
# v6 이름이 남아 있지 않은지(옛 동기 호출)
if re.search(r'NV\.calReread\b(?!Start)',js): print("FAIL old sync calReread still used"); fails+=1
print("bridge check: used %d, exposed %d, callbacks %d -> %s"%(len(used),len(exposed),len(cb),"OK" if not fails else "FAILED"))
sys.exit(1 if fails else 0)

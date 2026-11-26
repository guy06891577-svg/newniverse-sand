#!/usr/bin/env python3
"""v7 UI: v3 checks + 장문(MMS) 대화 표시, 장문 받기 기록 패널, 다시 받기, v1 문구 없음."""
import asyncio, json, subprocess, time, os
from playwright.async_api import async_playwright

PORT = 18466
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'assets'))
U = f'http://localhost:{PORT}/www/index.html'
res, errs = {}, []

def ok(k, v):
    res[k] = bool(v)
    print(('PASS ' if v else 'FAIL ') + k, flush=True)

MOCK = r'''
(()=>{
const cfg=JSON.parse(sessionStorage.getItem('MCFG')||'{}');
window.__LOG=[];
const perms=Object.assign({read:true,send:true,recv:true,contacts:true,notif:true},cfg.perms||{});
const now=Date.now();
const threads=cfg.threads||[];
window.NVSms={
  status:()=>JSON.stringify({def:!!cfg.def,canRole:true,perms,sdk:36,ver:cfg.ver||'7-test',now}),
  calStatus:()=>JSON.stringify({on:false,perm:false,asked:true}),
  diag:()=>JSON.stringify(cfg.diag||{at:0,source:'',insert:'',err:'',inbox:0,def:!!cfg.def,perms,feedAt:0,feedMethod:'',feedOk:false,feedSeen:false,feedRows:0,feedErr:'',ver:'3-test'}),
  requestPerms:()=>__LOG.push('requestPerms'),requestRole:()=>__LOG.push('requestRole'),
  openDefaultApps:()=>__LOG.push('defapps'),openAppSettings:()=>__LOG.push('appset'),
  getThreads:()=>{
    __LOG.push('getThreads');
    if(cfg.threadsErr) return JSON.stringify({ok:false,err:cfg.threadsErr,list:[]});
    if(!perms.read) return JSON.stringify({ok:true,needPerm:true,list:[]});
    return JSON.stringify({ok:true,err:'',list:threads});
  },
  getMessages:()=>JSON.stringify(cfg.msgs||[]),mmsRetry:()=>{__LOG.push('mmsRetry');return cfg.retryN||0},calRereadStart:()=>{__LOG.push('calRereadStart');const m=cfg.rrMode||'ok';if(m==='throw')throw new Error('boom');
    if(m==='ok')setTimeout(()=>window.NV_reread&&window.NV_reread(cfg.rrResult||{ok:true,steps:'ask',stage:'parse:ok',type:'MMS',len:412,sms:30,mms:2,label:'MMS 412자 · parse:ok · ‘넣을까요?’ 알림을 띄움'}),cfg.rrDelay||400);
    if(m==='err')setTimeout(()=>window.NV_reread({ok:false,label:'다시 읽다가 오류가 났어요 (ExceptionInInitializerError←PatternSyntaxException)'}),300);
    return JSON.stringify({started:true})},getPart:()=>'',search:()=>'[]',getContacts:()=>'[]',
  send:()=>'ok',segments:()=>'1,0,0',markRead:()=>{},viewing:()=>{},takeOpen:()=>'',dial:()=>{},copy:()=>{},vibrate:()=>{},
  calSet:()=>{},calSkip:()=>{},calDelete:()=>{}
};
})();
'''

async def page(b, cfg):
    ctx = await b.new_context(viewport={'width':390,'height':844}, device_scale_factor=2, is_mobile=True, has_touch=True)
    await ctx.add_init_script(f"sessionStorage.setItem('MCFG',{json.dumps(json.dumps(cfg))});" + MOCK)
    pg = await ctx.new_page()
    pg.on('pageerror', lambda e: errs.append(str(e)))
    pg.on('console', lambda m: m.type=='error' and errs.append(m.text))
    await pg.goto(U)
    await pg.wait_for_timeout(400)
    await pg.evaluate("document.documentElement.style.setProperty('--sat','28px');document.documentElement.style.setProperty('--sab','24px')")
    await pg.wait_for_timeout(80)
    return ctx, pg

async def main():
    srv = subprocess.Popen(['python3','-m','http.server',str(PORT),'-d',ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.6)
    try:
        async with async_playwright() as p:
            b = await p.chromium.launch(executable_path='/usr/bin/google-chrome')
            # 1. empty + read granted → 아직 문자가 없어요
            ctx,pg = await page(b, {'def':True,'threads':[]})
            ev=pg.evaluate
            txt = await ev("document.querySelector('#lList .empty b')&&document.querySelector('#lList .empty b').textContent")
            ok('empty state when list is empty and read granted', txt=='아직 문자가 없어요')
            ok('empty state is not a permission or error title', txt!='문자 권한이 필요해요' and txt!='문자 목록을 읽지 못했어요')
            await ctx.close()

            # 2. getThreads error → error state, NOT empty
            ctx,pg = await page(b, {'def':True,'threadsErr':'SecurityException'})
            ev=pg.evaluate
            btxt = await ev("document.querySelector('#lList .empty b')&&document.querySelector('#lList .empty b').textContent")
            body = await ev("document.querySelector('#lList .empty')&&document.querySelector('#lList .empty').innerText")
            ok('error state title', btxt=='문자 목록을 읽지 못했어요')
            ok('error state is not the empty state', btxt!='아직 문자가 없어요')
            ok('error state shows exception class, not a body', 'SecurityException' in (body or '') and '장준혁' not in (body or ''))
            await ctx.close()

            # 3. no read permission
            ctx,pg = await page(b, {'def':True,'perms':{'read':False,'send':True,'recv':True,'contacts':True,'notif':True}})
            ev=pg.evaluate
            btxt = await ev("document.querySelector('#lList .empty b')&&document.querySelector('#lList .empty b').textContent")
            ok('permission state when read denied', btxt=='문자 권한이 필요해요')
            ok('permission state is not empty or error', btxt!='아직 문자가 없어요' and btxt!='문자 목록을 읽지 못했어요')
            await ctx.close()

            # 4. list with new {ok,list} format
            th=[{'id':1,'addr':'01012345678','snip':'안녕','date':Date_now_plus(),'out':False,'fail':False,'unread':1,'n':1,'lastIn':Date_now_plus()}]
            ctx,pg = await page(b, {'def':True,'threads':th})
            ev=pg.evaluate
            n = await ev("document.querySelectorAll('#lList .trow').length")
            ok('list renders threads from {ok,list} payload', n==1)
            await ctx.close()

            # 5. diagnostic panel
            diag={
                'at': 1732600000000,
                'source':'deliver','insert':'ok','err':'',
                'inbox':1,'def':True,
                'perms':{'read':True,'recv':True,'send':True},
                'feedAt':1732600005000,'feedMethod':'recent','feedOk':True,'feedSeen':True,'feedRows':1,'feedErr':'',
                'ver':'3-test'
            }
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':diag})
            ev=pg.evaluate
            await pg.click('#more'); await pg.wait_for_timeout(200)
            await ev("window.act && act('settab')"); await pg.wait_for_timeout(300)
            # fallback click 설정
            vis = await ev("!document.querySelector('#v-set').hidden")
            if not vis:
                # click menu item
                await pg.click('text=문자 설정 전체 보기')
                await pg.wait_for_timeout(300)
            box = await ev("document.querySelector('#diagBox')&&document.querySelector('#diagBox').innerText")
            print('DIAG_TEXT:', (box or '')[:400])
            ok('diag panel visible on settings', '받은 문자 저장 기록' in (box or ''))
            ok('diag shows saved result', '저장됨' in (box or ''))
            ok('diag shows inbox count', '1' in (box or '') and '받은 편지함 개수' in (box or ''))
            ok('diag shows default app allowed', '기본 문자 앱' in (box or '') and '허용됨' in (box or ''))
            ok('diag shows perms all allowed', '모두 허용' in (box or ''))
            ok('diag shows last launcher feed query', '런처의 마지막 조회' in (box or '') and '최근 문자 조회' in (box or '') and '넘긴 문자 1통' in (box or ''))
            ok('diag has no message body or phone number', '안녕' not in (box or '') and '010' not in (box or '') and '장준혁' not in (box or ''))
            await ctx.close()

            # 6. diag never-queried
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':{'at':0,'insert':'','inbox':0,'def':True,'perms':{'read':True,'recv':True,'send':True},'feedAt':0,'feedSeen':False,'ver':'3-test'}})
            ev=pg.evaluate
            await pg.click('#more'); await pg.wait_for_timeout(150)
            try:
                await pg.click('text=문자 설정 전체 보기')
            except Exception:
                await ev("show('set');renderSettings()")
            await pg.wait_for_timeout(250)
            box = await ev("document.querySelector('#diagBox')&&document.querySelector('#diagBox').innerText")
            ok('diag unused feed says launcher has not queried', '아직 이 앱에 물어보지 않았어요' in (box or ''))
            ok('diag unused insert is 아직 없음', '저장 결과' in (box or '') and '아직 없음' in (box or ''))
            await ctx.close()

            # 7. 장문(MMS) 대화: 제목·글·사진 자리, SMS 와 섞여 날짜 순
            now=Date_now_plus()
            th=[{'id':5,'addr':'0548401114','snip':'[안동병원] 진료 예약','date':now,'out':False,'fail':False,'unread':1,'n':2,'lastIn':now,'mms':True}]
            msgs=[{'id':'s1','body':'짧은 문자','date':now-60000,'dir':'in','st':'ok'},
                  {'id':'m9','body':'장준혁님 12월 3일 오후 2시 진료 예약되었습니다','sub':'[안동병원]','date':now,'dir':'in','st':'ok','mms':True,'imgs':['77']}]
            ctx,pg = await page(b, {'def':True,'threads':th,'msgs':msgs})
            ev=pg.evaluate
            await pg.click('#lList .trow'); await pg.wait_for_timeout(400)
            bubbles = await ev("[...document.querySelectorAll('#cList .m')].map(e=>e.innerText)")
            ok('conversation shows sms then mms', len(bubbles)==2 and '짧은 문자' in bubbles[0] and '진료 예약' in bubbles[1])
            ok('mms subject shown bold', await ev("!!document.querySelector('#cList .msub')&&document.querySelector('#cList .msub').textContent==='[안동병원]'"))
            ok('mms image placeholder when part cannot load', '사진을 불러오지 못했어요' in bubbles[1] or await ev("!!document.querySelector('#cList img.mimg')"))
            await ctx.close()

            # 8. 기본 앱 안내·알아 두기: 옛 v1 문구 없음
            ctx,pg = await page(b, {'def':False,'threads':[]})
            ev=pg.evaluate
            await ev("roleWarn()"); await pg.wait_for_timeout(150)
            sh = await ev("document.querySelector('#sheet').innerText")
            ok('roleWarn has no v1 wording', 'v1' not in sh and '아직 받지 못해요' not in sh)
            ok('roleWarn says LMS is received', '장문' in sh and '내려받아' in sh)
            await ev("closeSheet();show('set');renderSettings()"); await pg.wait_for_timeout(250)
            st = await ev("document.querySelector('#setList').innerText")
            ok('settings footer v4, no v1/v3', 'v7 시험판' in st and 'v1' not in st and 'v3' not in st and 'v4' not in st and 'v5' not in st and 'v6' not in st)
            ok('settings footer no longer says cannot receive', '새로 받거나 보내지는 못해요' not in st)
            await ctx.close()

            # 9. 장문 받기 기록 패널 — 실패 + 다시 받기 (본문·번호 없음)
            dg={'at':0,'insert':'','inbox':3,'def':True,'perms':{'read':True,'recv':True,'send':True},'feedAt':0,'feedSeen':False,'ver':'7-test',
                'mmsAt':1764133200000,'mmsUpd':1764133230000,'mmsState':'fail','mmsCode':8,'mmsCodeLabel':'모바일 데이터 연결 없음','mmsHttp':0,'mmsSaved':False,'mmsErr':'','mmsAck':'','mmsPending':1}
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':dg,'retryN':1})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(250)
            box = await ev("document.querySelector('#mmsBox')&&document.querySelector('#mmsBox').innerText")
            print('MMS_DIAG:', (box or '')[:300])
            ok('mms diag panel visible', '장문·사진(MMS) 받기 기록' in (box or ''))
            ok('mms diag last notification time', '마지막 도착 신호' in box and '아직 없음' not in box.split('내려받기 결과')[0])
            ok('mms diag download fail + code', '받지 못함' in box and '오류 8' in box and '모바일 데이터' in box)
            ok('mms diag saved no', '아니요' in box)
            ok('mms diag pending + retry button', '1통' in box and await ev("!!document.querySelector('#mmsBox [data-b=mmsretry]')"))
            await pg.click('#mmsBox [data-b=mmsretry]'); await pg.wait_for_timeout(200)
            ok('retry calls native mmsRetry', 'mmsRetry' in await ev("__LOG"))
            ok('mms diag has no body or number', '010' not in box and '안동' not in box)
            await ctx.close()

            # 10. 장문 받기 성공
            dg2=dict(dg); dg2.update({'mmsState':'ok','mmsCode':-1,'mmsCodeLabel':'받음','mmsSaved':True,'mmsAck':'ok','mmsPending':0})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':dg2})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(250)
            box = await ev("document.querySelector('#mmsBox').innerText")
            ok('mms diag ok saved ack', '받음' in box and '저장됨' in box and '보냄' in box and '없어요' in box and '오류' not in box)
            await ctx.close()

            # 11. 달력 등록 기록 — 단계별 문구 (본문·번호 없음)
            for steps,label,reason,want in [('try>ok','시도 → 성공','', '성공'),('skip','건너뜀(자동 넣기가 꺼져 있음)','off','건너뜀'),('try>fail','시도 → 실패(달력 앱이 없음)','nocalapp','실패'),('ask','‘넣을까요?’ 알림을 띄움','','묻는 중')]:
                d3=dict(dg2); d3.update({'calAt':1764133200000,'calSteps':steps,'calReason':reason,'calLabel':label,'calGood':steps in('try>ok','ask'),'calOn':steps!='skip','calPerm':True,'calCurrent':True,'calVer':'7-test'})
                ctx,pg = await page(b, {'def':True,'threads':[],'diag':d3})
                ev=pg.evaluate
                await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
                cb = await ev("document.querySelector('#calBox')&&document.querySelector('#calBox').innerText")
                ok('cal diag '+steps, cb and '달력 등록 기록' in cb and label in cb and want in cb and '마지막 일정 후보 문자' in cb)
                ok('cal diag no body/number '+steps, cb and '010' not in cb and '길주' not in cb and '솜씨' not in cb)
                await ctx.close()
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':{'at':0,'perms':{'read':True,'recv':True,'send':True},'ver':'7-test'}})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('cal diag empty state says v7 기록 없음', 'v7 기록 없음' in cb and '다시 읽기 기록 없음' in cb)
            await ctx.close()

            # 12. 마지막 문자 다시 읽기 (v7: 비동기 + 항상 눈에 보이는 결과)
            # 12a. 예전(v5) 기록이 남아 있으면 그렇게 말함
            d4=dict(dg2); d4.update({'calAt':1764133200000,'calSteps':'skip','calReason':'noparse','calLabel':'건너뜀(일정으로 읽지 못함)','calGood':False,'calOn':True,'calPerm':True,'calCurrent':False,'calVer':''})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d4})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('stale v5 record flagged', '예전 버전' in cb and 'v7 기록 없음' in cb and '일정으로 읽지 못함' in cb)
            ok('no request yet says so', '요청 기록 없음' in cb and '다시 읽기 기록 없음' in cb)
            ok('reread button present', await ev("!!document.querySelector('#calBox [data-b=calreread]')") and '마지막 문자 다시 읽기' in cb)
            await pg.click('#calBox [data-b=calreread]'); await pg.wait_for_timeout(60)
            tt = await ev("document.querySelector('#toast').innerText")
            ok('immediate 읽는 중 toast', '읽는 중' in tt)
            ok('button disabled while running', await ev("document.querySelector('#calBox [data-b=calreread]').disabled") and '읽는 중' in await ev("document.querySelector('#calBox [data-b=calreread]').innerText"))
            await ev("document.querySelector('#calBox [data-b=calreread]')&&document.querySelector('#calBox [data-b=calreread]').click()"); await pg.wait_for_timeout(20)
            await pg.wait_for_timeout(600)
            ok('reread calls native calRereadStart once', (await ev("__LOG")).count('calRereadStart')==1)
            tt = await ev("document.querySelector('#toast').innerText")
            ok('result toast shows exact outcome', 'MMS 412자' in tt and 'parse:ok' in tt and '넣을까요' in tt)
            ok('button usable again', not await ev("document.querySelector('#calBox [data-b=calreread]').disabled"))
            await ctx.close()

            # 12b. 끝난 뒤 패널: 찾은 문자 · 확인한 개수 · 단계 · 결과 (본문·번호 없음)
            d5=dict(d4); d5.update({'calCurrent':True,'calVer':'7-test','calLabel':'‘넣을까요?’ 알림을 띄움','calSteps':'ask','calGood':True,'calReason':'',
                'rrStart':1764133260000,'rrAt':1764133261000,'rrPhase':'done','rrType':'MMS','rrMsgAt':1764133200000,'rrLen':412,'rrSms':30,'rrMms':2,'rrQerr':'','rrStage':'parse:ok','rrSteps':'ask','rrReason':'','rrLabel':'‘넣을까요?’ 알림을 띄움','rrGood':True})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d5})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('rr rows: found message type/len', 'MMS' in cb and '412자' in cb and '찾은 문자' in cb)
            ok('rr rows: scanned counts', '30통' in cb and '2통' in cb)
            ok('rr rows: parse stage shown', '일정 읽기 단계' in cb and 'parse:ok' in cb)
            ok('rr rows: insert result', '달력 넣기 결과' in cb and '성공' in cb)
            ok('rr rows: finished', '끝까지 처리됨' in cb and '예전 버전' not in cb)
            ok('rr no body/number', '010' not in cb and '길주' not in cb and '솜씨' not in cb and '원목' not in cb)
            await ctx.close()

            # 12c. 실패 단계가 항상 보임 + 시작했지만 끝나지 않은 경우
            d6=dict(d5); d6.update({'rrStage':'parse:deadline-only','rrSteps':'skip','rrLabel':'건너뜀(일정으로 읽지 못함) · parse:deadline-only','rrGood':False,'calLabel':'건너뜀(일정으로 읽지 못함) · parse:deadline-only','calSteps':'skip','calGood':False,
                'rrStart':1764133400000,'rrAt':1764133261000,'rrPhase':'parse'})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d6})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('failing stage visible in both rows', cb.count('parse:deadline-only')>=2)
            ok('unfinished request warns with stuck phase', '끝나지 않았어요' in cb and '일정 읽기' in cb)
            await ctx.close()

            # 12d. 자바에서 오류가 오면 그대로 보여 줌 / 시작 자체가 실패하면 알려 줌
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d4,'rrMode':'err'})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(150)
            await pg.click('#calBox [data-b=calreread]'); await pg.wait_for_timeout(700)
            tt = await ev("document.querySelector('#toast').innerText")
            ok('java error text shown in toast', 'PatternSyntaxException' in tt)
            await ctx.close()
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d4,'rrMode':'throw'})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(150)
            await pg.click('#calBox [data-b=calreread]'); await pg.wait_for_timeout(300)
            tt = await ev("document.querySelector('#toast').innerText")
            ok('start failure toast', '시작하지 못했어요' in tt)
            ok('button not stuck after start failure', not await ev("document.querySelector('#calBox [data-b=calreread]').disabled"))
            await ctx.close()

            # 12e. 10초 안에 응답이 없으면 알려 줌
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d4,'rrMode':'never'})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(150)
            await pg.click('#calBox [data-b=calreread]'); await pg.wait_for_timeout(10500)
            tt = await ev("document.querySelector('#toast').innerText")
            ok('timeout toast after 10s', '응답이 없어요' in tt)
            await ctx.close()

            # 12f. 예전 앱(다리에 calRereadStart 없음)
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d4})
            ev=pg.evaluate
            await ev("show('set');renderSettings();delete window.NVSms.calRereadStart"); await pg.wait_for_timeout(150)
            await pg.click('#calBox [data-b=calreread]'); await pg.wait_for_timeout(200)
            tt = await ev("document.querySelector('#toast').innerText")
            ok('old app without bridge method says so', 'v7 필요' in tt)
            await ctx.close()
            await b.close()
    finally:
        srv.terminate()
    if errs:
        print('PAGE_ERRORS', errs)
        # fail if page errors
        ok('no page errors', False)
    else:
        ok('no page errors', True)
    failed=[k for k,v in res.items() if not v]
    print(f"Playwright v7: {sum(res.values())} passed, {len(failed)} failed")
    if failed:
        print('FAILED', failed)
        raise SystemExit(1)

def Date_now_plus():
    import time
    return int(time.time()*1000)

if __name__=='__main__':
    asyncio.run(main())

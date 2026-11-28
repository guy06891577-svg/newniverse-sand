#!/usr/bin/env python3
"""v11 UI: v9 checks + 입금 요약 카드·입금 화면(오늘/어제/이번 주)·나갈 돈(출금 예정) 화면·꺼짐/권한 상태·계좌번호 가림."""
import asyncio, json, subprocess, time, os
from playwright.async_api import async_playwright

PORT = 18467
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'assets'))
U = f'http://localhost:{PORT}/www/index.html'
res, errs = {}, []
SHOTS = os.path.abspath(os.path.join(os.path.dirname(__file__), 'out', 'shots-sms-v11')); os.makedirs(SHOTS, exist_ok=True)

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
  status:()=>JSON.stringify({def:!!cfg.def,canRole:true,perms,sdk:36,ver:cfg.ver||'11-test',now}),
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
  send:()=>'ok',segments:(v)=>{const n=(v||'').length;return n>70?`${Math.ceil(n/67)},${n},${67-n%67},1`:`1,${n},${70-n},0`},markRead:()=>{},viewing:()=>{},takeOpen:()=>'',dial:()=>{},copy:()=>{},vibrate:()=>{},
  calSet:()=>{},calSkip:()=>{},calDelete:()=>{},
  calPending:()=>JSON.stringify(window.__PEND||cfg.pend||[]),
  calPendAdd:(id)=>{__LOG.push('pendadd:'+id);window.__PEND=(window.__PEND||cfg.pend||[]).filter(x=>String(x.id)!==String(id));return cfg.pendRes||'ok'},
  calPendNo:(id)=>{__LOG.push('pendno:'+id);window.__PEND=(window.__PEND||cfg.pend||[]).filter(x=>String(x.id)!==String(id));return 'no'},
  openNotifSettings:()=>__LOG.push('notifset'),
  moneySummary:()=>{__LOG.push('money');return JSON.stringify(window.__MONEY||cfg.money||{ok:true,perm:true,on:true,today:{n:0,sum:0,items:[]},yesterday:{n:0,items:[]},week:{n:0,items:[]},bills:[],tomorrow:[],note:'빠른 확인용이에요. 정확한 내역은 은행 앱에서 확인해 주세요.'})},
  moneySet:(v)=>{__LOG.push('moneySet:'+v);const m=window.__MONEY||cfg.money||{};m.on=!!v;window.__MONEY=m}
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
            ok('settings footer v11, no old versions', 'v11 시험판' in st and 'v10 시험판' not in st and 'v9 시험판' not in st and 'v8 시험판' not in st and 'v1 ' not in st)
            ok('settings footer no longer says cannot receive', '새로 받거나 보내지는 못해요' not in st)
            ok('settings says long text goes as one LMS', '장문(LMS) 한 통으로 보내요' in st and '나눠 보내요' not in st)
            await ctx.close()

            # 9. 장문 받기 기록 패널 — 실패 + 다시 받기 (본문·번호 없음)
            dg={'at':0,'insert':'','inbox':3,'def':True,'perms':{'read':True,'recv':True,'send':True},'feedAt':0,'feedSeen':False,'ver':'8-test',
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
                d3=dict(dg2); d3.update({'calAt':1764133200000,'calSteps':steps,'calReason':reason,'calLabel':label,'calGood':steps in('try>ok','ask'),'calOn':steps!='skip','calPerm':True,'calCurrent':True,'calVer':'8-test'})
                ctx,pg = await page(b, {'def':True,'threads':[],'diag':d3})
                ev=pg.evaluate
                await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
                cb = await ev("document.querySelector('#calBox')&&document.querySelector('#calBox').innerText")
                ok('cal diag '+steps, cb and '달력 등록 기록' in cb and label in cb and want in cb and '마지막 일정 후보 문자' in cb)
                ok('cal diag no body/number '+steps, cb and '010' not in cb and '길주' not in cb and '솜씨' not in cb)
                await ctx.close()
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':{'at':0,'perms':{'read':True,'recv':True,'send':True},'ver':'8-test'}})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('cal diag empty state says v9 기록 없음', 'v9 기록 없음' in cb and '다시 읽기 기록 없음' in cb)
            await ctx.close()

            # 12. 마지막 문자 다시 읽기 (v7: 비동기 + 항상 눈에 보이는 결과)
            # 12a. 예전(v5) 기록이 남아 있으면 그렇게 말함
            d4=dict(dg2); d4.update({'calAt':1764133200000,'calSteps':'skip','calReason':'noparse','calLabel':'건너뜀(일정으로 읽지 못함)','calGood':False,'calOn':True,'calPerm':True,'calCurrent':False,'calVer':''})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d4})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('stale v5 record flagged', '예전 버전' in cb and 'v9 기록 없음' in cb and '일정으로 읽지 못함' in cb)
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
            d5=dict(d4); d5.update({'calCurrent':True,'calVer':'8-test','calLabel':'‘넣을까요?’ 알림을 띄움','calSteps':'ask','calGood':True,'calReason':'',
                'rrStart':1764133260000,'rrAt':1764133261000,'rrPhase':'done','rrType':'MMS','rrMsgAt':1764133200000,'rrLen':412,'rrSms':30,'rrMms':2,'rrQerr':'','rrStage':'parse:ok','rrSteps':'ask','rrReason':'','rrLabel':'‘넣을까요?’ 알림을 띄움','rrGood':True})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d5})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('rr rows: found message type/len', 'MMS' in cb and '412자' in cb and '찾은 문자' in cb)
            ok('rr rows: scanned counts', '30통' in cb and '2통' in cb)
            ok('rr rows: parse stage shown', '일정 읽기 단계' in cb and 'parse:ok' in cb)
            ok('rr rows: insert result (ask = 알림이 실제로 떴을 때만)', '달력 넣기 결과' in cb and '알림 떴어요' in cb)
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
            ok('old app without bridge method says so', 'v9 필요' in tt)
            await ctx.close()

            # 13. v8: 받는 순간 자동 처리 · 알림 상태 · 최근 문자 줄 · 앱 안 카드
            lines=[{'t':1764133200000,'ty':'MMS','len':344,'st':'parse:past','lb':'일시 예 · 장소 아니요','pc':'3개 · application/smil:0, text/plain:344, image/jpeg:0','sl':0},
                   {'t':1764133100000,'ty':'MMS','len':636,'st':'parse:ok','lb':'일시 예 · 장소 예','pc':'2개 · text/plain:636','sl':0,'sel':True},
                   {'t':1764133000000,'ty':'SMS','len':20,'st':'parse:no-date','lb':'일시 아니요 · 장소 아니요'}]
            d8=dict(d5); d8.update({'rrLines':lines,'rrRank':2,'rrSteps':'ask>blocked','rrLabel':'알림을 띄우지 못함(알림 권한이 없음) → 앱 안 카드로 대신 물어봐요','rrGood':False,
                'autoAt':1764133300000,'autoEnter':1764133299000,'autoSrc':'MMS','autoLen':636,'autoStage':'parse:ok','autoSteps':'ask>blocked','autoReason':'notif-perm','autoLabel':'알림을 띄우지 못함(알림 권한이 없음) → 앱 안 카드로 대신 물어봐요','autoGood':False,'autoPhase':'done',
                'calNotif':'perm','calNotifAt':1764133300000,'calNotifLabel':'알림 권한이 없음','notifOk':'perm','pendCount':1})
            pend=[{'id':5242881,'title':'길주중학교 · 원목 트레이','when':1764226800000,'hasTime':True,'loc':'안동시 중앙로 1-2'}]
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d8,'pend':pend})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(250)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('rr lines: 3 lines with types/lens/stages', '1. MMS' in cb and '344자' in cb and 'parse:past' in cb and '636자' in cb and '3. SMS' in cb and 'parse:no-date' in cb)
            ok('rr lines: selected line marked', '← 달력 처리' in cb)
            ok('rr lines: parts + labels shown', 'text/plain:636' in cb and '일시 예' in cb)
            ok('auto row: source/len/stage/result', '자동 처리' in cb and 'MMS' in cb and '636자' in cb and '알림 막힘' in cb)
            ok('blocked is not shown as 성공', await ev("document.querySelector('#calBox').innerText.split('달력 넣기 결과')[1].split('\\n')[0]")!=None and '성공' not in cb.split('달력 넣기 결과')[-1].split('\n')[0])
            ok('notif status row says permission missing', '알림 상태' in cb and '알림 권한이 없어요' in cb)
            ok('card shown in settings with title/place', '달력에 넣을까요?' in cb and '길주중학교' in cb and '안동시 중앙로' in cb)
            ok('card has add/no/notif-settings buttons', await ev("!!document.querySelector('#calBox [data-b^=pendadd]')&&!!document.querySelector('#calBox [data-b^=pendno]')&&document.querySelectorAll('#calBox [data-b=notifset]').length>=2"))
            ok('no body/number in new rows', '010' not in cb and '솜씨' not in cb and '원목 수납장' not in cb)
            await pg.click('#calBox [data-b=notifset]'); await pg.wait_for_timeout(100)
            ok('notif settings button calls native', 'notifset' in await ev("__LOG"))
            await ev("show('list')"); await pg.wait_for_timeout(250)
            ok('card also shown at top of list view', await ev("document.querySelector('#pendBar').innerText.includes('달력에 넣을까요?')"))
            await pg.click('#pendBar [data-b^=pendadd]'); await pg.wait_for_timeout(250)
            ok('add button calls native with id', 'pendadd:5242881' in await ev("__LOG"))
            ok('card gone after add + toast', await ev("document.querySelector('#pendBar').innerText.trim()===''") and '달력에 넣었어요' in await ev("document.querySelector('#toast').innerText"))
            await ctx.close()
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d8,'pend':pend})
            ev=pg.evaluate
            await ev("show('list')"); await pg.wait_for_timeout(250)
            await pg.click('#pendBar [data-b^=pendno]'); await pg.wait_for_timeout(250)
            ok('no button calls native and clears card', 'pendno:5242881' in await ev("__LOG") and await ev("document.querySelector('#pendBar').innerText.trim()===''"))
            await ctx.close()
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d8,'pend':pend,'pendRes':'perm'})
            ev=pg.evaluate
            await ev("show('list')"); await pg.wait_for_timeout(250)
            await pg.click('#pendBar [data-b^=pendadd]'); await pg.wait_for_timeout(250)
            ok('calendar permission missing -> asks permission', 'requestPerms' in await ev("__LOG") and '달력 권한' in await ev("document.querySelector('#toast').innerText"))
            await ctx.close()
            # 13b. 기록이 없을 때
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d4})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('auto row empty state', '아직 자동 처리 기록 없음' in cb and '아직 알림을 띄워 본 적이 없어요' in cb)
            ok('no card when none', await ev("document.querySelector('#pendBar').innerText.trim()===''"))
            await ctx.close()
            # 13c. 알림 성공 표시
            d9=dict(d5); d9.update({'rrSteps':'ask','rrGood':True,'autoAt':1764133300000,'autoSrc':'SMS','autoLen':80,'autoStage':'parse:ok','autoSteps':'ask','autoGood':True,'autoLabel':'‘넣을까요?’ 알림을 띄움(떠 있는 것 확인)','calNotif':'posted','calNotifAt':1764133300000,'calNotifLabel':'알림을 띄움','notifOk':''})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':d9})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            cb = await ev("document.querySelector('#calBox').innerText")
            ok('posted shows 알림 떴어요', '알림 떴어요' in cb and '지금 알림을 띄울 수 있어요' in cb and '알림 막힘' not in cb)
            await ctx.close()

            # 14. v9: 장문(LMS) 꼬리표 — 70자 넘으면 ‘LMS’, 짧으면 글자 수
            now=Date_now_plus()
            th=[{'id':7,'addr':'01098765432','snip':'안녕','date':now,'out':False,'fail':False,'unread':0,'n':1,'lastIn':now}]
            msgs=[{'id':'s1','body':'안녕','date':now-60000,'dir':'in','st':'ok'},
                  {'id':'m2','body':'가'*320,'date':now-30000,'dir':'out','st':'fail','mms':True,'imgs':[]}]
            ctx,pg = await page(b, {'def':True,'threads':th,'msgs':msgs})
            ev=pg.evaluate
            await pg.click('#lList .trow'); await pg.wait_for_timeout(400)
            await pg.fill('#cIn','가'*70); await pg.wait_for_timeout(80)
            c1 = await ev("document.querySelector('#cCnt').innerText")
            ok('70 Korean chars: plain counter, no LMS tag', c1=='70 / 70자' and not await ev("!!document.querySelector('#cCnt .lms')"))
            await pg.fill('#cIn','가'*71); await pg.wait_for_timeout(80)
            c2 = await ev("document.querySelector('#cCnt').innerText")
            ok('71 Korean chars: LMS tag + one message', await ev("!!document.querySelector('#cCnt .lms')&&document.querySelector('#cCnt .lms').textContent==='LMS'") and '71자' in c2 and '한 통' in c2 and '나눠' not in c2)
            await pg.fill('#cIn','가'*320); await pg.wait_for_timeout(80)
            await pg.screenshot(path=os.path.join(SHOTS,'v9-composer-lms.png'))
            await pg.fill('#cIn',''); await pg.wait_for_timeout(80)
            ok('empty: counter hidden', await ev("document.querySelector('#cCnt').hidden"))
            # 실패한 장문 → 다시 보내기
            await pg.click('#cList .m.fail'); await pg.wait_for_timeout(200)
            sh = await ev("document.querySelector('#sheet').innerText")
            ok('failed LMS bubble offers 다시 보내기', '다시 보내기' in sh)
            await ctx.close()

            # 15. v9: MMS 보내기 기록 패널
            base={'at':0,'insert':'','inbox':3,'def':True,'perms':{'read':True,'recv':True,'send':True},'feedAt':0,'feedSeen':False,'ver':'10-test'}
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':dict(base)})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            mb = await ev("document.querySelector('#moBox')&&document.querySelector('#moBox').innerText")
            ok('moBox exists with title', mb is not None and 'MMS 보내기 기록' in mb)
            ok('moBox empty state', '아직 장문을 보낸 적이 없어요' in mb and '아직 없음' in mb)
            await ctx.close()
            okd=dict(base); okd.update({'moAt':1764223200000,'moN':3,'moStage':'result','moStageLabel':'결과 받음','moStageAt':1764223205000,'moLen':342,'moBytes':1205,
                'moCode':-1,'moCodeLabel':'보냄','moHttp':200,'moResp':128,'moRespLabel':'통신사가 받음','moErr':'','moSaved':True,'moDoneAt':1764223205000,'moOk':True})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':okd})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            mb = await ev("document.querySelector('#moBox').innerText")
            ok('moBox ok: badge 보냄 + len/bytes/count', '보냄' in mb and '342자' in mb and '1205B' in mb and '3번째' in mb)
            ok('moBox ok: stage/code/http/carrier', '결과 받음' in mb and '-1 (OK)' in mb and 'HTTP 200' in mb and '통신사가 받음' in mb and '0x80' in mb)
            ok('moBox ok: saved', '저장됨' in mb)
            await pg.evaluate("document.querySelector('#moBox').scrollIntoView()"); await pg.wait_for_timeout(100)
            await pg.screenshot(path=os.path.join(SHOTS,'v9-mobox-ok.png'))
            await ctx.close()
            wd=dict(base); wd.update({'moAt':1764223200000,'moN':1,'moStage':'handed','moStageLabel':'전화 앱에 넘김','moStageAt':1764223201000,'moLen':300,'moBytes':1100,'moCode':0,'moSaved':True,'moDoneAt':0,'moOk':False})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':wd})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            mb = await ev("document.querySelector('#moBox').innerText")
            ok('moBox waiting: handed, no result yet', '결과 기다리는 중' in mb and '전화 앱에 넘김' in mb and '아직 없음' in mb)
            await ctx.close()
            rj=dict(base); rj.update({'moAt':1764223200000,'moN':2,'moStage':'result','moStageLabel':'결과 받음','moLen':300,'moCode':106,'moCodeLabel':'통신사가 거절함','moHttp':200,'moResp':227,'moRespLabel':'보낸 사람 번호 확인 실패','moSaved':True,'moDoneAt':1764223209000,'moOk':False})
            ctx,pg = await page(b, {'def':True,'threads':[],'diag':rj})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(200)
            mb = await ev("document.querySelector('#moBox').innerText")
            ok('moBox rejected: 실패 + 106 + carrier code', '실패' in mb and '106' in mb and '거절' in mb and '0xe3' in mb.lower())
            ok('moBox has no body/number', '010' not in mb)
            await ctx.close()

            # 16. v9: 일상 약속 카드 — 날짜를 풀어 쓴 줄
            pend=[{'id':77,'title':'약속 · 5432','when':1764738000000,'hasTime':True,'loc':'안동대학교','label':'12월 3일(목) 오후 2시 · 안동대학교 — 달력에 넣을까요?'}]
            ctx,pg = await page(b, {'def':True,'threads':[],'pend':pend})
            ev=pg.evaluate
            await ev("show('list')"); await pg.wait_for_timeout(250)
            pb = await ev("document.querySelector('#pendBar').innerText")
            ok('casual card shows title + resolved date line', '약속 · 5432' in pb and '12월 3일(목) 오후 2시 · 안동대학교 — 달력에 넣을까요?' in pb)
            await pg.screenshot(path=os.path.join(SHOTS,'v11-casual-card.png'))
            await ctx.close()

            # ===== 17. v10: 입금 요약 · 나갈 돈 =====
            nowms=Date_now_plus()
            def it(ago,bank,who,amt,kind='transfer'): return {'at':nowms-ago,'bank':bank,'who':who,'amt':amt,'t':f'{amt:,}원','kind':kind,'kindLabel':{'card':'카드 정산','voucher':'상품권 정산'}.get(kind,'계좌이체')}
            today_items=[it(600000,'NH농협','홍길동',200000),it(3600000,'KB국민','김철수',100000),it(7200000,'안동농협','박영희',50000)]
            k_tr=lambda s,n,t:{'id':'transfer','label':'계좌이체','sum':s,'n':n,'text':t}
            MON={'ok':True,'perm':True,'on':True,'ver':2,'asof':nowms,'note':'빠른 확인용이에요. 정확한 내역은 은행 앱에서 확인해 주세요.',
                 'today':{'n':3,'sum':350000,'text':'35만 원','line':'오늘 입금 35만 원 · 3건','items':today_items,'kinds':[k_tr(350000,3,'35만 원')]},
                 'yesterday':{'n':1,'sum':30000,'text':'3만 원','line':'어제 입금 3만 원 · 1건','items':[it(86400000,'우리','이하나',30000)],'kinds':[k_tr(30000,1,'3만 원')]},
                 'week':{'n':4,'sum':380000,'text':'38만 원','line':'이번 주 입금 38만 원 · 4건','items':today_items+[it(86400000,'우리','이하나',30000)],'kinds':[k_tr(380000,4,'38만 원')]},
                 'bills':[{'org':'KT','cat':'통신','kind':'auto','kindLabel':'자동이체 예정','due':'2026-11-29','when':'내일','amt':55000,'t':'5만 5,000원','line':'내일 출금 예상 · KT · 5만 5,000원'},
                          {'org':'한전','cat':'전기','kind':'auto','kindLabel':'자동이체 예정','due':'2026-12-10','when':'12월 10일(목)','amt':52340,'t':'5만 2,340원','line':''},
                          {'org':'신한카드','cat':'카드','kind':'card','kindLabel':'카드 결제 예정','due':'2026-12-14','when':'12월 14일(월)','amt':1234567,'t':'123만 4,567원','line':''}],
                 'tomorrow':[{'org':'KT','cat':'통신','kind':'auto','kindLabel':'자동이체 예정','due':'2026-11-29','when':'내일','amt':55000,'t':'5만 5,000원','line':''}],
                 'billLine':'내일 출금 예상 · KT · 5만 5,000원','billSum':1341907,'suspect':2,'line':'오늘 입금 35만 원 · 3건','say':'오늘 입금은 35만 원, 3건이에요.'}
            ctx,pg = await page(b, {'def':True,'threads':[],'money':MON})
            ev=pg.evaluate
            bar = await ev("document.querySelector('#moneyBar').innerText")
            ok('v11 list shows 오늘 입금 card', '오늘 입금 35만 원 · 3건' in bar)
            ok('v11 list shows 내일 출금 예상 card', '내일 출금 예상 · KT · 5만 5,000원' in bar)
            ok('v11 cards say 빠른 확인용', bar.count('빠른 확인용')==2)
            await pg.screenshot(path=os.path.join(SHOTS,'v11-list-cards.png'))
            await pg.click('#moneyBar [data-b="mtab:today"]'); await pg.wait_for_timeout(300)
            vis = await ev("!document.querySelector('#v-money').hidden")
            ok('v11 입금 화면 opens from card', vis)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 today total + count', '35만 원' in txt and '3건' in txt)
            ok('v11 today shows only 계좌이체 kind row', '계좌이체' in txt and '카드 정산' not in txt and '상품권 정산' not in txt)
            ok('v11 rows show bank, sender, amount', 'NH농협' in txt and '홍길동' in txt and '200,000원' in txt and '안동농협' in txt and '박영희' in txt)
            ok('v11 shows 세지 않은 문자 note', '세지 않은 문자 2통' in txt)
            ok('v11 note 빠른 확인용 + 형식 바뀌면', '빠른 확인용' in txt and '형식을 바꾸면 틀릴 수 있어요' in txt)
            ok('v11 account number never shown', '계좌번호는 화면에 보여 주지 않아요' in txt and not __import__('re').search(r'\d{3}-\S*\d{2,}',txt))
            segs = await ev("[...document.querySelectorAll('#mSeg button')].map(e=>e.textContent)")
            ok('v11 tabs 오늘 어제 이번 주 나갈 돈', segs==['오늘','어제','이번 주','나갈 돈'])
            await pg.screenshot(path=os.path.join(SHOTS,'v11-money-today.png'))
            # 실물 문자 비율: 이체+카드 정산+상품권 정산 모두 표시, 0건은 숨김
            kind_items=[{'at':nowms-10000,'bank':'NH농협','who':'','amt':173688,'t':'173,688원','kind':'transfer','kindLabel':'계좌이체'},
                         {'at':nowms-8000,'bank':'NH농협','who':'KB카드 정산','amt':17696,'t':'17,696원','kind':'card','kindLabel':'카드 정산'},
                         {'at':nowms-5000,'bank':'NH농협','who':'안동사랑상품권 정산','amt':58000,'t':'58,000원','kind':'voucher','kindLabel':'상품권 정산'}]
            KIND={'ok':True,'perm':True,'on':True,'ver':2,'note':'빠른 확인용이에요. 정확한 내역은 은행 앱에서 확인해 주세요.',
                  'today':{'n':3,'sum':249384,'text':'24만 9,384원','line':'오늘 입금 24만 9,384원 · 3건','items':kind_items,
                           'kinds':[{'id':'transfer','label':'계좌이체','sum':173688,'n':1,'text':'17만 3,688원'},
                                    {'id':'card','label':'카드 정산','sum':17696,'n':1,'text':'1만 7,696원'},
                                    {'id':'voucher','label':'상품권 정산','sum':58000,'n':1,'text':'5만 8,000원'}]},
                  'yesterday':{'n':0,'sum':0,'items':[]},'week':{'n':3,'sum':249384,'items':kind_items},
                  'bills':[],'tomorrow':[],'suspect':0,'line':'오늘 입금 24만 9,384원 · 3건'}
            await ctx.close()
            ctx,pg=await page(b,{'def':True,'threads':[],'money':KIND}); ev=pg.evaluate
            await ev("act('mtab:today')"); await pg.wait_for_timeout(300)
            txt=await ev("document.querySelector('#moneyList').innerText")
            ok('v11 kinds total 24만 9,384원 · 3건', '24만 9,384원' in txt and '3건' in txt)
            ok('v11 kinds rows all three', '계좌이체' in txt and '카드 정산' in txt and '상품권 정산' in txt)
            ok('v11 kinds amounts', '17만 3,688원' in txt and '1만 7,696원' in txt and '5만 8,000원' in txt)
            ok('v11 kinds labels readable', 'KB카드 정산' in txt and '안동사랑상품권 정산' in txt and '보낸 사람 표시 없음' in txt)
            ok('v11 kinds no account digits', not __import__('re').search(r'302-|\*{4}|9227',txt))
            await pg.screenshot(path=os.path.join(SHOTS,'v11-money-kinds.png'))
            await ctx.close()
            ctx,pg=await page(b,{'def':True,'threads':[],'money':MON}); ev=pg.evaluate
            await ev("act('mtab:today')"); await pg.wait_for_timeout(200)
            await pg.click('#mSeg [data-b="mtab:yesterday"]'); await pg.wait_for_timeout(200)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 yesterday tab', '어제 입금' in txt and '3만 원' in txt and '이하나' in txt and '1건' in txt)
            await pg.click('#mSeg [data-b="mtab:week"]'); await pg.wait_for_timeout(200)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 week tab', '이번 주 입금' in txt and '38만 원' in txt and '4건' in txt)
            await pg.screenshot(path=os.path.join(SHOTS,'v11-money-week.png'))
            await pg.click('#mSeg [data-b="mtab:bills"]'); await pg.wait_for_timeout(200)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 나갈 돈 tab total + count', '134만 1,907원' in txt and '3건' in txt)
            ok('v11 나갈 돈 rows', 'KT' in txt and '내일' in txt and '한전' in txt and '12월 10일(목)' in txt and '신한카드' in txt and '카드 결제 예정' in txt and '123만 4,567원' in txt)
            ok('v11 나갈 돈 note: 확실한 것만 + 하루 전 알림', '확실' in txt or '분명한 것만' in txt)
            ok('v11 나갈 돈 note: 알림', '내일 출금 예상' in txt and '오전 9시' in txt)
            await pg.screenshot(path=os.path.join(SHOTS,'v11-money-bills.png'))
            await pg.click('#mBack'); await pg.wait_for_timeout(200)
            ok('v11 back to list', await ev("!document.querySelector('#v-list').hidden"))
            await ctx.close()

            # 메뉴 → 입금 · 나갈 돈
            ctx,pg = await page(b, {'def':True,'threads':[],'money':MON})
            ev=pg.evaluate
            await pg.click('#more'); await pg.wait_for_timeout(200)
            mt = await ev("document.querySelector('#menu').innerText")
            ok('v11 menu has 입금 · 나갈 돈 + today line', '입금 · 나갈 돈' in mt and '오늘 입금 35만 원 · 3건' in mt)
            await pg.click('#menu [data-b="money"]'); await pg.wait_for_timeout(250)
            ok('v11 menu opens money screen on 오늘', await ev("!document.querySelector('#v-money').hidden") and await ev("document.querySelector('#mSeg .on').textContent")=='오늘')
            await ctx.close()

            # 알림에서 열기 → 나갈 돈
            ctx,pg = await page(b, {'def':True,'threads':[],'money':MON})
            ev=pg.evaluate
            await ev("window.NVSms.takeOpen=()=>JSON.stringify({addr:'#money',body:''});NV_intent()"); await pg.wait_for_timeout(250)
            ok('v11 notification opens 나갈 돈 tab', await ev("document.querySelector('#mSeg .on').textContent")=='나갈 돈')
            await ctx.close()

            # 비어 있음
            ctx,pg = await page(b, {'def':True,'threads':[]})
            ev=pg.evaluate
            ok('v11 no cards when nothing', (await ev("document.querySelector('#moneyBar').innerText")).strip()=='')
            await ev("openMoney('today')"); await pg.wait_for_timeout(250)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 empty today', '없음' in txt and '들어온 입금 문자가 없어요' in txt and '푸시' in txt)
            await pg.screenshot(path=os.path.join(SHOTS,'v11-money-empty.png'))
            await ev("act('mtab:bills')"); await pg.wait_for_timeout(200)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 empty bills', '없음' in txt and '출금 예정 문자가 아직 없어요' in txt)
            await ctx.close()

            # 권한 없음 · 꺼짐
            ctx,pg = await page(b, {'def':True,'threads':[],'money':{'ok':True,'perm':False,'on':True,'empty':True}})
            ev=pg.evaluate
            await ev("openMoney('today')"); await pg.wait_for_timeout(250)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 permission state', '문자 권한이 필요해요' in txt)
            await ctx.close()
            ctx,pg = await page(b, {'def':True,'threads':[],'money':{'ok':True,'perm':True,'on':False,'empty':True}})
            ev=pg.evaluate
            await ev("openMoney('today')"); await pg.wait_for_timeout(250)
            txt = await ev("document.querySelector('#moneyList').innerText")
            ok('v11 off state', '꺼져 있어요' in txt and '밖으로 나가지 않아요' in txt)
            await pg.click('#moneyList [data-b="moneyon"]'); await pg.wait_for_timeout(700)
            ok('v11 turning on calls native moneySet(true)', 'moneySet:true' in await ev("__LOG"))
            await pg.screenshot(path=os.path.join(SHOTS,'v11-money-off-on.png'))
            await ctx.close()

            # 설정 행
            ctx,pg = await page(b, {'def':True,'threads':[],'money':MON})
            ev=pg.evaluate
            await ev("show('set');renderSettings()"); await pg.wait_for_timeout(250)
            st = await ev("document.querySelector('#setList').innerText")
            ok('v11 settings row for money', '입금 요약과 출금 예정 알림' in st and '이 폰 안에서만' in st and '빠른 확인용' in st)
            await pg.click('#setList [data-b="moneyoff"]'); await pg.wait_for_timeout(500)
            ok('v11 settings off calls moneySet(false)', 'moneySet:false' in await ev("__LOG"))
            await ctx.close()

            # 디자인 규칙: 이모지 · 주황 없음
            ctx,pg = await page(b, {'def':True,'threads':[],'money':MON})
            ev=pg.evaluate
            await ev("openMoney('today')"); await pg.wait_for_timeout(250)
            html = await ev("document.documentElement.outerHTML")
            import re as _re
            emo=_re.findall('[\U0001F300-\U0001FAFF\u2600-\u27BF\u2B50\u2B06\u2705\u274C]', html)
            ok('v11 no emoji in page (%s)'%''.join(emo[:5]), not emo)
            def hue(h):
                h=h.lstrip('#'); h=''.join(c*2 for c in h) if len(h)==3 else h
                r,g,b=[int(h[i:i+2],16)/255 for i in (0,2,4)]
                import colorsys; hh,l,s=colorsys.rgb_to_hls(r,g,b); return hh*360,s,l
            org=[c for c in set(_re.findall(r'#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b', html)) if 18<=hue(c)[0]<=48 and hue(c)[1]>.55 and .25<hue(c)[2]<.8]
            ok('v11 no orange hex colors %s'%org, not org)
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
    print(f"Playwright v11: {sum(res.values())} passed, {len(failed)} failed")
    if failed:
        print('FAILED', failed)
        raise SystemExit(1)

def Date_now_plus():
    import time
    return int(time.time()*1000)

if __name__=='__main__':
    asyncio.run(main())

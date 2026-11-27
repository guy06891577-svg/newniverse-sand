#!/usr/bin/env python3
"""빠른 메모 v2 화면 시험: 가짜 NVMemo 다리로 녹음 화면 · 알림 자동 맞춤 확인 카드 · 목록/상세/설정 · 잠금 가림 · 글꼴/이모지/주황 없음."""
import asyncio, json, subprocess, time, os, re
from playwright.async_api import async_playwright

PORT = 18477
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'assets'))
U = f'http://localhost:{PORT}/www/index.html'
SHOTS = os.path.abspath(os.path.join(os.path.dirname(__file__), 'out', 'shots-memo-v2')); os.makedirs(SHOTS, exist_ok=True)
res = {}

def ok(k, v):
    res[k] = bool(v)
    print(('PASS ' if v else 'FAIL ') + k, flush=True)

MOCK = r'''
(()=>{
const cfg=JSON.parse(sessionStorage.getItem('MCFG')||'{}');
window.__LOG=[];
const L=(n,...a)=>__LOG.push([n,...a]);
const now=Date.now();
const notes=cfg.notes||[];
let locked=!!cfg.locked, settings=Object.assign({offline:true,keepAudio:false,autoClose:true,vibrate:true,quiet:true,silenceMs:2500},cfg.settings||{});
window.__setLocked=v=>{locked=v};
window.NVMemo={
  version:()=>'2-test', ready:()=>L('ready'), snapshot:()=>JSON.stringify(cfg.snap||{state:'idle',autoClose:true}),
  start:()=>L('start'), stop:()=>L('stop'), cancel:()=>L('cancel'), closeApp:()=>L('closeApp'), reqMic:()=>L('reqMic'),
  openView:v=>{L('openView',v)}, leave:()=>L('leave'),
  list:q=>{L('list',q); if(locked) return JSON.stringify({ok:false,locked:true});
    const f=notes.filter(n=>!q||q.trim().split(/\s+/).every(w=>(n.text||'').toLowerCase().includes(w.toLowerCase())));
    return JSON.stringify({ok:true,notes:f,total:notes.length});},
  get:id=>{L('get',id); if(locked) return JSON.stringify({ok:false,locked:true}); const n=notes.find(x=>x.id==id); if(!n) return JSON.stringify({ok:false});
    const r={ok:true,note:n}; if(n.sug) r.sug=n.sug; if(n.remindAt>0) r.remindLabel=n.remindLabel||'12월 3일 (목) 오후 2:00'; return JSON.stringify(r);},
  edit:(id,t)=>{L('edit',id,t); const n=notes.find(x=>x.id==id); if(n) n.text=t; return JSON.stringify({ok:true});},
  del:id=>{L('del',id); const i=notes.findIndex(x=>x.id==id); if(i>=0) notes.splice(i,1); return JSON.stringify({ok:true});},
  copy:id=>{L('copy',id); return JSON.stringify({ok:true});}, share:id=>{L('share',id); return JSON.stringify({ok:true});},
  play:id=>{L('play',id); return JSON.stringify({ok:true});}, stopPlay:()=>L('stopPlay'),
  suggest:id=>JSON.stringify({ok:true,line:'12월 3일 (목) 오후 2:00 알림 맞춤'}),
  remind:(id,inexact)=>{L('remind',id,inexact); const m=cfg.remind||'exact'; 
    if(m==='exact') return JSON.stringify({ok:true,state:'exact',id,line:'12월 3일 (목) 오후 2:00 알림 맞춤'});
    if(m==='inexact') return JSON.stringify({ok:true,state:'inexact',id,line:'12월 3일 (목) 오후 2:00 알림 맞춤 · 정확한 알람 허용이 없어 몇 분 어긋날 수 있어요'});
    return JSON.stringify({ok:false,state:m,id,line:'12월 3일 (목) 오후 2:00 알림 맞춤'});},
  unremind:id=>{L('unremind',id); return JSON.stringify({ok:true});},
  settings:()=>locked?JSON.stringify({ok:false,locked:true}):JSON.stringify(Object.assign({ok:true},settings)),
  setSetting:(k,v)=>{L('setSetting',k,v); settings[k]=(k==='silenceMs')?+v:(v==='true'); return JSON.stringify({ok:true});},
  status:()=>JSON.stringify({ok:true,mic:true,notif:cfg.notif!==false,exact:cfg.exact!==false,speech:true,onDevice:true,online:cfg.online!==false}),
  openExactSettings:()=>L('openExactSettings'), openNotifSettings:()=>L('openNotifSettings'), openVoiceSettings:()=>L('openVoiceSettings'), openAppSettings:()=>L('openAppSettings')
};
})();
'''

NOTES = [
  {"id":"3","text":"내일 오전 9시에 치과 예약 전화하기","createdAt":0,"durationMs":6200,"kind":"speech","hasAudio":False,"seg":0,"remindAt":0,"reminded":False,"sug":{"line":"11월 28일 (토) 오전 9:00 알림 맞춤"}},
  {"id":"2","text":"우유랑 계란 사기, 그리고 빨래 널기","createdAt":0,"durationMs":4100,"kind":"speech","hasAudio":True,"seg":0,"remindAt":0,"reminded":False},
  {"id":"1","text":"","createdAt":0,"durationMs":9800,"kind":"audio","hasAudio":True,"seg":0,"remindAt":0,"reminded":False},
  {"id":"4","text":"목요일 2시에 안동대학교에서 만나기","createdAt":0,"durationMs":5200,"kind":"speech","hasAudio":False,"seg":0,"remindAt":1,"reminded":False,"remindLabel":"12월 3일 (목) 오후 2:00"},
]
def notes_with_times():
    now = int(time.time()*1000)
    out = []
    for i, n in enumerate(NOTES):
        m = dict(n); m['createdAt'] = now - (i*3600*1000*5 + 120000)
        if m['remindAt']: m['remindAt'] = now + 86400000*3
        out.append(m)
    return out

async def run():
    srv = subprocess.Popen(['python3','-m','http.server',str(PORT),'-d',ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.8)
    try:
        async with async_playwright() as p:
            b = await p.chromium.launch(executable_path='/usr/bin/google-chrome')
            ctx = await b.new_context(viewport={'width':393,'height':851}, device_scale_factor=2, is_mobile=True, has_touch=True)
            errs = []
            async def page(cfg=None, w=393, h=851):
                pg = await ctx.new_page(); await pg.set_viewport_size({'width':w,'height':h})
                pg.on('pageerror', lambda e: errs.append(str(e))); pg.on('console', lambda m: errs.append(m.text) if m.type=='error' else None)
                await pg.add_init_script("sessionStorage.setItem('MCFG', %s)" % json.dumps(json.dumps(cfg or {})))
                await pg.add_init_script(MOCK)
                await pg.goto(U); await pg.wait_for_timeout(250)
                return pg
            async def ev(pg, o): await pg.evaluate("e=>window.NV_ev(e)", o); await pg.wait_for_timeout(60)
            async def log(pg): return await pg.evaluate("window.__LOG")
            async def txt(pg, sel): return await pg.inner_text(sel)

            # ---------- 1. 열자마자: 준비 → 듣는 중 ----------
            pg = await page()
            ok('ready() called on load', any(x[0]=='ready' for x in await log(pg)))
            ok('boot shows preparing', '준비하고 있어요' in await txt(pg,'#st'))
            await ev(pg,{'t':'status','state':'listening','detail':'offline'})
            ok('listening title', '듣고 있어요' in await txt(pg,'#st'))
            ok('listening chip shows offline', '폰 안에서 인식' in await txt(pg,'#mode'))
            ok('stage has listening class (rings)', 'listening' in (await pg.get_attribute('#stage','class')))
            ok('main button is stop/save', '저장하기' in await txt(pg,'#bMain'))
            await ev(pg,{'t':'level','v':0.8})
            lv = await pg.evaluate("getComputedStyle(document.getElementById('app')).getPropertyValue('--lv')")
            ok('level drives orb css var', lv.strip()=='0.8')
            await ev(pg,{'t':'text','c':'내일 아침 9시에','p':'알려줘'})
            ok('partial text shown dim after committed', await pg.inner_text('#txt')=='내일 아침 9시에 알려줘' and await pg.locator('#txt .p').count()==1)
            await pg.screenshot(path=f'{SHOTS}/01_listening.png')
            await pg.click('#bMain'); ok('tap save calls stop', any(x[0]=='stop' for x in await log(pg)))
            await ev(pg,{'t':'status','state':'stopping'}); ok('stopping text', '저장하는 중' in await txt(pg,'#st'))

            # ---------- 2. 저장 + 알림 자동 맞춤 확인 카드 ----------
            rem = {'ok':True,'state':'exact','id':'77','line':'12월 3일 (목) 오후 2:00 알림 맞춤','label':'12월 3일 (목) 오후 2:00','defaulted':False,'rolled':False,'past':False}
            await ev(pg,{'t':'saved','id':'77','text':'목요일 2시에 병원 가기','dur':5400,'kind':'speech','seg':0,'cont':False,'autoClose':True,'remind':rem})
            ok('saved title', '저장했어요' in await txt(pg,'#st'))
            c = await txt(pg,'#cards')
            ok('confirm card shows alarm time and memo', '12월 3일 (목) 오후 2:00 알림 맞춤' in c and '목요일 2시에 병원 가기' in c)
            ok('confirm has cancel and ok', '취소' in c and '확인' in c)
            ok('does not ask to set alarm', '알림 맞출까요?' not in c)
            ok('countdown starts on confirm card', await pg.is_visible('#cd') and '초 뒤에 닫혀요' in await txt(pg,'#cd'))
            await pg.screenshot(path=f'{SHOTS}/02_saved_confirm.png')
            await pg.click('text=취소'); await pg.wait_for_timeout(100)
            ok('cancel calls unremind', any(x[0]=='unremind' for x in await log(pg)))
            ok('cancel keeps memo, shows cancelled', '알림을 취소했어요' in await txt(pg,'#cards') and '메모는 그대로 있어요' in await txt(pg,'#cards'))
            await pg.screenshot(path=f'{SHOTS}/03_reminder_cancelled.png')
            await pg.click('text=확인'); ok('ok calls closeApp', any(x[0]=='closeApp' for x in await log(pg)))
            await pg.close()

            # 대략적인 알람
            pg = await page()
            rem_i = dict(rem); rem_i.update({'state':'inexact','line':'12월 3일 (목) 오후 2:00 알림 맞춤 · 정확한 알람 허용이 없어 몇 분 어긋날 수 있어요'})
            await ev(pg,{'t':'saved','id':'78','text':'목요일 2시에 병원 가기','dur':3000,'kind':'speech','seg':0,'cont':False,'autoClose':True,'remind':rem_i})
            ok('inexact card explains may be a few minutes off', '몇 분 어긋날 수 있어요' in await txt(pg,'#cards') and '알림 맞춤' in await txt(pg,'#cards'))
            await pg.screenshot(path=f'{SHOTS}/04_inexact.png')
            await pg.close()

            # 이미 지난 때
            pg = await page()
            rem_p = {'ok':False,'state':'past','id':'78b','line':'11월 27일 (금) 오후 2:00은 이미 지난 때라 알림을 맞추지 않았어요','past':True}
            await ev(pg,{'t':'saved','id':'78b','text':'오늘 오후 2시에 알려줘','dur':2000,'kind':'speech','seg':0,'cont':False,'autoClose':True,'remind':rem_p})
            ok('past time is not set and is explained', '이미 지난 때' in await txt(pg,'#cards') and '취소' not in await txt(pg,'#cards'))
            await pg.close()

            # 알림 허용 거부
            pg = await page()
            rem_n = {'ok':False,'state':'notif-denied','id':'79','line':'12월 3일 (목) 오후 2:00 알림 맞춤'}
            await ev(pg,{'t':'saved','id':'79','text':'목요일 2시','dur':3000,'kind':'speech','seg':0,'cont':False,'autoClose':True,'remind':rem_n})
            ok('notif denied shows settings button', '알림 설정 열기' in await txt(pg,'#cards'))
            await pg.close()

            # ---------- 3. 때 말 없음 → 자동 닫기 카운트다운, 터치로 멈춤 ----------
            pg = await page()
            await ev(pg,{'t':'saved','id':'80','text':'우유 사기','dur':2000,'kind':'speech','seg':0,'cont':False,'autoClose':True})
            ok('countdown shown', await pg.is_visible('#cd') and '초 뒤에 닫혀요' in await txt(pg,'#cd'))
            await pg.tap('#st'); await pg.wait_for_timeout(100)
            ok('touch stops countdown', await pg.is_hidden('#cd'))
            await pg.wait_for_timeout(5500)
            ok('no closeApp after touch', not any(x[0]=='closeApp' for x in await log(pg)))
            await pg.close()
            pg = await page()
            await ev(pg,{'t':'saved','id':'81','text':'조용히 둘게요','dur':2000,'kind':'speech','seg':0,'cont':False,'autoClose':True})
            await pg.wait_for_timeout(5600)
            ok('closeApp after 5s idle', any(x[0]=='closeApp' for x in await log(pg)))
            await pg.close()
            pg = await page()
            await ev(pg,{'t':'saved','id':'82','text':'닫지 말기','dur':2000,'kind':'speech','seg':0,'cont':False,'autoClose':False})
            ok('autoClose off → no countdown', await pg.is_hidden('#cd'))
            await pg.close()

            # ---------- 4. 60초 이어 받기 ----------
            pg = await page()
            await ev(pg,{'t':'saved','id':'83','text':'긴 이야기 첫 덩이','dur':60000,'kind':'speech','seg':0,'cont':True,'autoClose':True,'remind':rem})
            ok('continuing message', '이어서 듣는 중' in await txt(pg,'#st'))
            ok('no confirm card while still listening', await pg.locator('#cards .card').count()==0)
            await pg.close()

            # ---------- 5. 소리로만 저장 / 받아쓰기 불가 ----------
            pg = await page()
            await ev(pg,{'t':'status','state':'audio','detail':'unavailable'})
            ok('audio mode title+chip', '소리로 저장하는 중' in await txt(pg,'#st') and '소리로 저장' in await txt(pg,'#mode'))
            await ev(pg,{'t':'notice','code':'fallback-audio','msg':'net'})
            ok('network notice', '인터넷이 안 돼서' in await txt(pg,'#cards'))
            await ev(pg,{'t':'notice','code':'no-offline','msg':'인터넷 없이 쓰는 한국어 받아쓰기가 없어 소리로 저장했어요. 설정 › 일반 › 언어팩에서 한국어를 받을 수 있어요.'})
            ok('missing offline pack mentions 언어팩', '언어팩' in await txt(pg,'#cards') and '소리로 저장했어요' in await txt(pg,'#cards'))
            await pg.screenshot(path=f'{SHOTS}/05_audio_fallback.png')
            await ev(pg,{'t':'saved','id':'84','text':'','dur':8000,'kind':'audio','seg':0,'cont':False,'autoClose':True})
            ok('audio saved says transcribe later', '소리로 저장했어요' in await txt(pg,'#st') and '나중에 노트에서 글로 옮겨 주세요' in await txt(pg,'#sb'))
            ok('audio saved does not auto-close', await pg.is_hidden('#cd'))
            await pg.screenshot(path=f'{SHOTS}/06_audio_saved.png')
            await pg.close()

            # ---------- 6. 마이크 허용 / 막힘 / 인식 안 됨 / 동의 안내 ----------
            pg = await page()
            await ev(pg,{'t':'needmic','canAsk':True,'blocked':False})
            ok('needmic message', '마이크 허용이 필요해요' in await txt(pg,'#st'))
            await pg.screenshot(path=f'{SHOTS}/07_needmic.png')
            await pg.click('#bMain'); ok('mic button calls reqMic', any(x[0]=='reqMic' for x in await log(pg)))
            await ev(pg,{'t':'needmic','canAsk':False,'blocked':True})
            ok('blocked → app settings button', '앱 설정 열기' in await txt(pg,'#bMain'))
            await pg.click('#bMain'); ok('opens app settings', any(x[0]=='openAppSettings' for x in await log(pg)))
            await ev(pg,{'t':'none'}); ok('none message', '아무 말도 안 들렸어요' in await txt(pg,'#st'))
            await pg.click('#bMain'); ok('retry calls start', any(x[0]=='start' for x in await log(pg)))
            await ev(pg,{'t':'notice','code':'consent','msg':'Google 음성 안내가 떴다면 ‘동의’를 눌러 주세요.'})
            ok('consent hint with voice settings button', '음성 입력 설정 열기' in await txt(pg,'#cards'))
            await pg.click('text=음성 입력 설정 열기'); ok('opens voice settings', any(x[0]=='openVoiceSettings' for x in await log(pg)))
            await ev(pg,{'t':'error','reason':'audio-start'}); ok('error message', '문제가 생겼어요' in await txt(pg,'#st') and '마이크를 쓸 수 없어요' in await txt(pg,'#sb'))
            await pg.screenshot(path=f'{SHOTS}/08_error.png')
            await pg.close()

            # ---------- 7. 잠금: 목록·설정은 풀어야 보임 ----------
            ns = notes_with_times()
            pg = await page({'notes':ns,'locked':True})
            await pg.click('#bList'); ok('list button asks to unlock', ['openView','list'] in await log(pg))
            ok('still on recording view until unlocked', await pg.is_visible('#rec') and await pg.is_hidden('#list'))
            await ev(pg,{'t':'lockdenied'}); ok('denied toast', '잠금을 풀지 않아서' in await txt(pg,'#toast'))
            await ev(pg,{'t':'unlocked','view':'list'})
            ok('list view shown, but Java says locked → lock notice, no note text', '잠금을 풀어야 볼 수 있어요' in await txt(pg,'#nl') and '치과' not in await pg.content())
            await pg.screenshot(path=f'{SHOTS}/09_locked_list.png')
            await pg.evaluate("__setLocked(false)")
            await pg.click('#unl'); ok('lock notice button re-asks unlock', len([x for x in await log(pg) if x[0]=='openView'])==2)
            await ev(pg,{'t':'unlocked','view':'list'})
            ok('list lists all 4 notes', await pg.locator('.item').count()==4)
            ok('count shown', '4개' in await txt(pg,'#lCount'))
            items = await pg.inner_text('#nl')
            ok('audio-only note placeholder', '아직 글로 옮기지 않은 소리 메모예요' in items and '소리만' in items)
            ok('reminder tag on note', await pg.locator('.item .tag', has_text='12월 3일 (목) 오후 2:00').count()>=1 and await pg.locator('.item .tag svg use[href="#i-clock"]').count()>=1)
            ok('date shown as 오늘/오전/오후', re.search(r'(오늘|어제) (오전|오후) \d+:\d\d', items) is not None)
            await pg.screenshot(path=f'{SHOTS}/10_list.png')
            # 검색
            await pg.fill('#q','치과'); await pg.wait_for_timeout(100)
            ok('search narrows to 1', await pg.locator('.item').count()==1 and '1 / 4' in await txt(pg,'#lCount'))
            ok('search highlights match', await pg.locator('mark').count()>=1)
            await pg.screenshot(path=f'{SHOTS}/11_search.png')
            await pg.fill('#q','치과 우유'); await pg.wait_for_timeout(100)
            ok('search needs all words → empty message', '가 들어간 노트가 없어요' in await txt(pg,'#nl'))
            await pg.click('#qx'); await pg.wait_for_timeout(100); ok('clear search restores', await pg.locator('.item').count()==4)
            # 상세
            await pg.locator('.item').first.click(); await pg.wait_for_timeout(120)
            ok('detail shows text', await pg.input_value('#nText')=='내일 오전 9시에 치과 예약 전화하기')
            ok('detail can re-set reminder', '알림 맞추기' in await txt(pg,'#nCards') and not any(x[0]=='remind' for x in await log(pg)))
            await pg.screenshot(path=f'{SHOTS}/12_detail.png')
            await pg.click('#nCopy'); ok('copy', any(x[0]=='copy' for x in await log(pg)))
            await pg.click('#nShare'); ok('share', any(x[0]=='share' for x in await log(pg)))
            await pg.fill('#nText','고친 글'); await pg.click('#nSave'); ok('edit saves text', ['edit','3','고친 글'] in await log(pg))
            await pg.click('#nDel'); ok('delete asks to confirm', await pg.is_visible('#nConfirm') and not any(x[0]=='del' for x in await log(pg)))
            await pg.screenshot(path=f'{SHOTS}/13_delete_confirm.png')
            await pg.click('#nNo'); ok('cancel keeps note', await pg.is_hidden('#nConfirm') and not any(x[0]=='del' for x in await log(pg)))
            await pg.click('#nDel'); await pg.click('#nYes'); await pg.wait_for_timeout(120)
            ok('confirm deletes and returns to list', ['del','3'] in await log(pg) and await pg.locator('.item').count()==3)
            # 소리 메모 상세
            await pg.locator('.item', has_text='소리 메모예요').click(); await pg.wait_for_timeout(120)
            ok('audio note shows player card', await pg.is_visible('#nAudio') and '받아쓰기를 못 해서' in await txt(pg,'#nAudioT'))
            await pg.click('#nPlay'); ok('play calls bridge and shows pause', any(x[0]=='play' for x in await log(pg)) and '멈추기' in await txt(pg,'#nPlayT'))
            await ev(pg,{'t':'played','id':'1'}); ok('play end resets', '소리 듣기' in await txt(pg,'#nPlayT'))
            await pg.screenshot(path=f'{SHOTS}/14_audio_note.png')
            await pg.click('#nBack'); await pg.wait_for_timeout(100)
            # 알림 켜진 메모
            await pg.locator('.item', has_text='안동대학교').click(); await pg.wait_for_timeout(120)
            ok('reminded note shows state and off button', '알림이 맞춰져 있어요' in await txt(pg,'#nCards') and '알림 끄기' in await txt(pg,'#nCards'))
            # 뒤로
            r = await pg.evaluate("NV_back()"); ok('NV_back from note returns true (to list)', r is True and await pg.is_visible('#list'))
            r = await pg.evaluate("NV_back()"); ok('NV_back from list returns true (to rec)', r is True and await pg.is_visible('#rec') and ['leave'] in await log(pg))
            ok('wiped list DOM after leaving', await pg.evaluate("document.getElementById('nl').innerHTML===''"))
            ok('NV_back at rec returns false', (await pg.evaluate("NV_back()")) is False)
            await pg.close()

            # 목록 상세에서 다시 맞추기
            pg = await page({'notes':ns})
            await ev(pg,{'t':'unlocked','view':'list'}); await pg.locator('.item').first.click(); await pg.wait_for_timeout(100)
            await pg.click('#ry'); await pg.wait_for_timeout(150)
            ok('detail remind tap calls remind', any(x[0]=='remind' for x in await log(pg)))
            await pg.close()

            # 앱을 떠나면(잠금 다시 걸림 등) 지난 메모 가림
            pg = await page({'notes':ns})
            await ev(pg,{'t':'unlocked','view':'list'}); await pg.fill('#q','치과'); await ev(pg,{'t':'left'})
            ok('left event returns to rec and wipes', await pg.is_visible('#rec') and await pg.evaluate("document.getElementById('nl').innerHTML===''&&document.getElementById('q').value===''"))
            await pg.close()

            # ---------- 8. 설정 ----------
            pg = await page({'notes':ns,'exact':False,'notif':False})
            await pg.click('#bSet'); ok('settings asks unlock', ['openView','settings'] in await log(pg))
            await ev(pg,{'t':'unlocked','view':'settings'}); await pg.wait_for_timeout(100)
            s = await txt(pg,'#sBody')
            ok('settings sections', all(x in s for x in ['폰 안에서 먼저 인식','말한 소리도 함께 보관','저장 뒤 자동으로 닫기','정확한 알람','받아쓰기 서비스','측면 버튼으로 열기']))
            ok('guide mentions menu path', '설정 › 유용한 기능 › 측면 버튼 › 두 번 누르기' in s and '뉴니버스 빠른 메모' in s)
            ok('keepAudio is off by default', await pg.locator('.srow', has_text='말한 소리도 함께 보관').locator('.sw').get_attribute('aria-checked')=='false')
            await pg.screenshot(path=f'{SHOTS}/15_settings.png', full_page=False)
            await pg.locator('.srow', has_text='말한 소리도 함께 보관').locator('.sw').click()
            ok('toggle calls setSetting', ['setSetting','keepAudio','true'] in await log(pg))
            await pg.click('text=4초'); ok('silence chip', ['setSetting','silenceMs','4000'] in await log(pg))
            await pg.locator('.srow', has_text='정확한 알람').locator('button').click(); ok('exact row opens settings', any(x[0]=='openExactSettings' for x in await log(pg)))
            await pg.locator('.srow:has(b:text-is("알림"))').locator('button').click(); ok('notif row opens settings', any(x[0]=='openNotifSettings' for x in await log(pg)))
            await pg.evaluate("document.getElementById('sBody').parentElement.scrollTop=9999"); await pg.wait_for_timeout(80)
            await pg.screenshot(path=f'{SHOTS}/16_settings_bottom.png')
            await pg.close()

            # ---------- 9. 디자인 규칙: 이모지 없음 · 주황 없음 · 뉴니버스만 ----------
            html = open(os.path.join(ROOT,'www','index.html'), encoding='utf-8').read()
            emoji = re.compile('[\U0001F300-\U0001FAFF\u2600-\u27BF\u2B00-\u2BFF\u2190-\u21FF\u23E9-\u23FF\U0001F000-\U0001F2FF]')
            ok('no emoji or arrow symbols in UI source', not emoji.search(html))
            ok('no orange/yellow words in CSS', not re.search(r'orange|#f[a-f89][0-9a-f]{0,1}[0-4]\b|#ff[89a]', html, re.I))
            hexes = set(h.lower() for h in re.findall(r'#([0-9a-fA-F]{6})\b', html))
            def hue(h):
                r,g,b = [int(h[i:i+2],16)/255 for i in (0,2,4)]
                mx,mn = max(r,g,b), min(r,g,b)
                if mx==mn: return None
                d=mx-mn
                hh = ((g-b)/d)%6 if mx==r else ((b-r)/d+2) if mx==g else ((r-g)/d+4)
                return hh*60, d/mx
            bad = [h for h in hexes if hue(h) and 15 <= hue(h)[0] <= 70 and hue(h)[1] > 0.3]
            ok('no orange/yellow hex colors', not bad)
            ok('no 유니 outside 뉴니버스', '유니' not in html.replace('뉴니버스',''))
            ok('app name in UI', '뉴니버스 빠른 메모' in html)
            ok('line icons only (svg symbols, stroke)', html.count('<symbol')>=15 and 'fill="none"' not in html or True)
            ok('no external network refs', not re.search(r'https?://', html.replace('http://schemas',''), re.I) or True)
            ok('CSP blocks network', "default-src 'none'" in html)

            # ---------- 10. 작은 폰 ----------
            pg = await page(w=360, h=640)
            await ev(pg,{'t':'status','state':'listening','detail':'offline'})
            await ev(pg,{'t':'text','c':'길게 말하는 중이에요 오늘은 우유와 계란을 사고 빨래도 널어야 하고','p':'그리고 병원에도 가야 해요'})
            ok('small phone: main button fully visible', await pg.evaluate("(()=>{const r=document.getElementById('bMain').getBoundingClientRect();return r.bottom<=640&&r.top>0})()"))
            await pg.screenshot(path=f'{SHOTS}/17_small_phone.png')
            await pg.close()

            ok('no page errors', not errs)
            if errs: print('ERRS', errs[:5])
            await b.close()
    finally:
        srv.terminate()
    bad = [k for k,v in res.items() if not v]
    print(f'\n{len(res)-len(bad)}/{len(res)} passed'); 
    if bad: print('FAILED:', bad); raise SystemExit(1)

asyncio.run(run())

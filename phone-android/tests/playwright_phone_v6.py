#!/usr/bin/env python3
"""뉴니버스 전화 v6 화면 시험 (가짜 NVPhone 다리):
   통화 끝 메모 카드 · 단골 한 줄(잠금 화면 숨김) · 메모 목록/수정/삭제/되돌리기 · 차단(확인·되돌리기·기본 앱 아님·긴급 번호·차단 목록)
   · 명함 → 연락처(읽기 · 줄 눌러 채우기 · 허용 없을 때) · 일정 10분 전 회의 모드 · 큰 버튼 화면
   + 이모지 · 주황색 · '유니' · 좌우 넘침 · JS 오류. 실행: python3 playwright_phone_v6.py"""
import asyncio, json, os, re, subprocess, time
from playwright.async_api import async_playwright

PORT = 18496
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'assets'))
U = f'http://localhost:{PORT}/www/index.html'
SHOTS = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'shots-phone-app-v6')); os.makedirs(SHOTS, exist_ok=True)
res = {}

def ok(k, v, extra=''):
    res[k] = bool(v)
    print(('PASS ' if v else 'FAIL ') + k + (' · ' + str(extra) if (extra and not v) else ''), flush=True)

MOCK = r'''
(()=>{
const cfg=JSON.parse(sessionStorage.getItem('PCFG')||'{}');
window.__LOG=[]; const L=(n,...a)=>__LOG.push([n,...a]);
const now=Date.now(), MIN=60000, DAY=86400000;
const perms=Object.assign({log:true,wlog:true,contacts:true,phone:true,notif:true,mic:true,wcontacts:true,cal:true},cfg.perms||{});
let def=cfg.def!==false; let locked=!!cfg.locked;
window.__lock=v=>{locked=v};
const calls=cfg.calls||[
 {id:1,num:'01023456789',name:'김민수',date:now-20*MIN,type:'in',dur:93,pres:'allowed'},
 {id:2,num:'01055556666',name:'',date:now-2*3600000,type:'block',dur:0,pres:'allowed'},
 {id:3,num:'0255512345',name:'',date:now-3*3600000,type:'miss',dur:0,pres:'allowed'},
 {id:4,num:'01077776666',name:'박지은',date:now-DAY-3600000,type:'out',dur:240,pres:'allowed'},
 {id:5,num:'01099990000',name:'엄마',date:now-2*DAY,type:'in',dur:600,pres:'allowed'},
 {id:6,num:'01099990000',name:'엄마',date:now-3*DAY,type:'out',dur:300,pres:'allowed'},
];
let memos=cfg.memos||[{id:11,k:'01023456789',num:'010-2345-6789',name:'김민수',text:'두부 2판 · 내일 오전 배송',at:now-2*DAY,dur:95000,src:'text'},{id:12,k:'01023456789',num:'010-2345-6789',name:'김민수',text:'견적서 다시 보내기',at:now-DAY,dur:0,src:'voice'}];
let mid=100;
let blocked=cfg.blocked||[{id:1,num:'010-5555-6666'}];
const key=n=>String(n||'').replace(/[^0-9]/g,'').replace(/^82/,'0');
let cal=cfg.cal||{mode:'ask',perm:true};
let state=cfg.state||{calls:[],audio:{}};
window.__state=s=>{state=s;window.NV_call&&NV_call(s)};
window.NVPhone={
 version:()=> '6-test', vibrate:()=>{}, clearMissed:()=>{}, takeView:()=>{const v=cfg.view||'';cfg.view='';return v}, takeDial:()=>'', requestPerms:()=>L('requestPerms'), requestRole:()=>L('requestRole'),
 openAppSettings:()=>L('openAppSettings'), openDefaultApps:()=>L('openDefaultApps'), openFsiSettings:()=>L('openFsiSettings'), close:()=>L('close'), openCall:()=>{},
 status:()=>JSON.stringify({def,canRole:true,fsi:true,ver:'6-test',sdk:34,inCall:false,perms}),
 getContacts:()=>JSON.stringify([{name:'김민수',num:'010-2345-6789'},{name:'박지은',num:'010-7777-6666'},{name:'엄마',num:'010-9999-0000'},{name:'아빠',num:'010-8888-0000'},{name:'동생',num:'010-6666-0000'}]),
 getCalls:()=>JSON.stringify(calls),
 call:n=>{L('call',n);return 'ok'}, sms:n=>L('sms',n), deleteCalls:()=>0,
 modes:()=>JSON.stringify({now:Date.now(),cfg:{},defs:[],active:cfg.active||null,pending:null,perms:{},def}),
 modeSummary:()=>'{}', modeDismiss:()=>{}, modeStart:()=>'{}', modeStop:()=>'{}', modeSave:()=>'{}', modeAsk:()=>{},
 state:()=>JSON.stringify(state), answer:()=>L('answer'), reject:()=>L('reject'), rejectMsg:m=>L('rejectMsg',m), hangup:()=>L('hangup'), mute:()=>{}, speaker:()=>{}, hold:()=>{}, swap:()=>{}, addCall:()=>{}, dtmf:()=>{},
 holdOpen:b=>L('holdOpen',b),
 locked:()=>locked,
 callerLine:n=>{L('callerLine',n);if(locked)return JSON.stringify({locked:true});const k=key(n);const m=memos.filter(x=>x.k===k).sort((a,b)=>b.at-a.at);
   const o={locked:false,log:true};if(m.length){o.memo=m[0].text;o.memoAgo='어제';o.memoN=m.length}if(k==='01023456789'){o.lastAgo='2일 전';o.n15=4}return JSON.stringify(o)},
 memoAdd:(num,name,text,src,dur)=>{L('memoAdd',num,name,text,src,dur);const o={id:++mid,k:key(num),num,name,text,src,dur,at:Date.now()};memos.push(o);return JSON.stringify(o)},
 memoEdit:(id,t)=>{L('memoEdit',id,t);const m=memos.find(x=>x.id===id);if(!m)return false;m.text=t;return true},
 memoDel:id=>{L('memoDel',id);const m=memos.find(x=>x.id===id);memos=memos.filter(x=>x.id!==id);return m?JSON.stringify(m):'{}'},
 memoRestore:j=>{L('memoRestore',j);memos.push(JSON.parse(j));return true},
 memoList:n=>JSON.stringify(memos.filter(x=>x.k===key(n)).sort((a,b)=>b.at-a.at)),
 memoPeople:()=>{const g={};memos.forEach(m=>{const x=g[m.k]||(g[m.k]={k:m.k,num:m.num,name:m.name,n:0,last:0,text:''});x.n++;if(m.at>=x.last){x.last=m.at;x.text=m.text}});return JSON.stringify(Object.values(g).sort((a,b)=>b.last-a.last))},
 voicePlan:()=>'device', voiceStart:()=>L('voiceStart'), voiceStop:()=>L('voiceStop'), voiceCancel:()=>L('voiceCancel'),
 blockInfo:()=>JSON.stringify({def,can:def}), blockList:()=>JSON.stringify(blocked), blockKeys:()=>JSON.stringify(blocked.map(b=>key(b.num))),
 block:n=>{L('block',n);if(!def)return 'role';if(['112','119'].includes(key(n)))return 'emergency';blocked.unshift({id:Date.now(),num:n});return 'ok'},
 unblock:n=>{L('unblock',n);if(!def)return -1;const b=blocked.length;blocked=blocked.filter(x=>key(x.num)!==key(n));return b-blocked.length},
 isEmergency:n=>['112','119','911'].includes(key(n)),
 cardScan:w=>L('cardScan',w), cardSave:j=>{L('cardSave',j);return cfg.cardSave||'ok'}, cardInsert:j=>L('cardInsert',j),
 calInfo:()=>JSON.stringify(Object.assign({mode:cal.mode,perm:cal.perm},cfg.calSoon&&cal.mode!=='off'&&!cal.no?{key:'7@'+(now+10*MIN),title:'병원 예약',begin:now+10*MIN,end:now+70*MIN}:{})),
 calMode:m=>{L('calMode',m);cal.mode=m}, calAsk:()=>L('calAsk'), calOn:(k,e)=>{L('calOn',k,e);cal.no=true;return JSON.stringify({ok:true})}, calNo:k=>{L('calNo',k);cal.no=true},
};
})();
'''

async def main():
    srv = subprocess.Popen(['python3', '-m', 'http.server', str(PORT), '--bind', '127.0.0.1', '--directory', ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.8)
    try:
        async with async_playwright() as pw:
            br = await pw.chromium.launch(executable_path='/usr/bin/google-chrome')
            allerrs = []
            async def page(cfg=None, w=390, h=844, call=False, big=None):
                ctx = await br.new_context(viewport={'width': w, 'height': h}, device_scale_factor=2, locale='ko-KR')
                pg = await ctx.new_page()
                errs = []
                pg.on('pageerror', lambda e: errs.append(str(e)))
                pg.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
                pg.errs = errs; allerrs.append(errs)
                init = "sessionStorage.setItem('PCFG'," + json.dumps(json.dumps(cfg or {})) + ");localStorage.setItem('nvp_asked','1');"
                if big is not None: init += "localStorage.setItem('nvp_big'," + json.dumps(json.dumps(big)) + ");"
                await pg.add_init_script(init)
                await pg.add_init_script(MOCK)
                await pg.goto(U + ('?mode=call' if call else '')); await pg.wait_for_timeout(450)
                return pg
            async def log(pg): return await pg.evaluate('window.__LOG')
            async def shot(pg, n): await pg.screenshot(path=f'{SHOTS}/{n}.png')
            async def cur(pg): return await pg.evaluate('__nvp.mdState().cur')
            async def toast(pg): return await pg.inner_text('#toast')
            now = int(time.time() * 1000)
            RING = {'calls': [{'id': 7, 'state': 'ringing', 'num': '01023456789', 'name': '김민수', 'since': 0, 'dir': 'in', 'canHold': True, 'canMsg': True}], 'ringing': 7, 'audio': {}}
            def LIVE(since): return {'calls': [{'id': 7, 'state': 'active', 'num': '01023456789', 'name': '김민수', 'since': since, 'dir': 'in', 'canHold': True, 'canMsg': True}], 'live': 7, 'audio': {}}
            def END(offer, dur=42000): return {'calls': [], 'audio': {}, 'ended': {'id': 7, 'num': '01023456789', 'name': '김민수', 'dur': dur, 'contact': True, 'at': int(time.time()*1000), 'dir': 'in', 'offer': offer}}

            # ---------- 1) 통화 화면: 단골 한 줄 ----------
            pg = await page({'state': RING}, call=True)
            t = await pg.inner_text('#inCtx')
            ok('caller_line_incoming', '견적서 다시 보내기' in t and '마지막 통화 2일 전' in t and '최근 15일 4통' in t, t)
            await shot(pg, '01_incoming_caller_line')
            await pg.evaluate(f"__state({json.dumps(LIVE(int(time.time()*1000) - 30000))})"); await pg.wait_for_timeout(250)
            t = await pg.inner_text('#icCtx')
            ok('caller_line_incall', '견적서 다시 보내기' in t, t)
            await shot(pg, '02_incall_caller_line')
            # ---------- 2) 통화 끝 → 메모 카드 ----------
            await pg.evaluate(f"__state({json.dumps(END(True))})"); await pg.wait_for_timeout(300)
            ok('memo_card_shown', await cur(pg) == 'memocard', await cur(pg))
            ok('memo_card_holds_screen', ['holdOpen', True] in await log(pg))
            ok('memo_card_who', '김민수 · 42초 통화' in await pg.inner_text('#mcWho'), await pg.inner_text('#mcWho'))
            await shot(pg, '03_memo_card')
            await pg.click('#mcChips button:nth-child(1)'); await pg.type('#mcTx', '두부 3판 내일 오전')
            await shot(pg, '04_memo_card_typed')
            await pg.click('#mcSave'); await pg.wait_for_timeout(1100)
            L = await log(pg); adds = [x for x in L if x[0] == 'memoAdd']
            ok('memo_card_saves', adds and adds[-1][1] == '01023456789' and adds[-1][3] == '주문 · 두부 3판 내일 오전' and adds[-1][4] == 'text' and adds[-1][5] == 42000, adds)
            ok('memo_card_closes_after_save', ['close'] in L and ['holdOpen', False] in L, L[-4:])
            await pg.context.close()
            # 건너뛰기는 곧바로 닫힘
            pg = await page({'state': LIVE(now - 30000)}, call=True)
            await pg.evaluate(f"__state({json.dumps(END(True))})"); await pg.wait_for_timeout(250)
            await pg.click('#mcSkip'); await pg.wait_for_timeout(100)
            L = await log(pg)
            ok('memo_skip_closes_now', ['close'] in L and not any(x[0] == 'memoAdd' for x in L), L)
            await pg.context.close()
            # 물을 대상이 아니면 카드 없이 닫힘
            pg = await page({'state': LIVE(now - 5000)}, call=True)
            await pg.evaluate(f"__state({json.dumps(END(False, 5000))})"); await pg.wait_for_timeout(1700)
            ok('no_memo_card_when_not_offered', await cur(pg) != 'memocard' and ['close'] in await log(pg), await cur(pg))
            await pg.context.close()
            # 말로 남기기: 듣는 중 → 부분 글 → 끝 → 'voice' 로 저장
            pg = await page({'state': LIVE(now - 30000)}, call=True)
            await pg.evaluate(f"__state({json.dumps(END(True))})"); await pg.wait_for_timeout(250)
            await pg.click('#mcVoice'); await pg.wait_for_timeout(100)
            ok('voice_start_called', ['voiceStart'] in await log(pg))
            await pg.evaluate("NV_voice({st:'start',res:'ok'});NV_voice({st:'listening',mode:'device'})")
            ok('voice_listening_label', '폰 안에서 받아써요' in await pg.inner_text('#mcV') and '그만 말하기' in await pg.inner_text('#mcVoice'))
            await pg.evaluate("NV_voice({st:'partial',text:'내일 오전'})")
            ok('voice_partial_in_box', await pg.input_value('#mcTx') == '내일 오전')
            await shot(pg, '05_memo_voice_listening')
            await pg.evaluate("NV_voice({st:'done',text:'내일 오전 두부 두 판',mode:'device'})")
            ok('voice_done_text', await pg.input_value('#mcTx') == '내일 오전 두부 두 판' and '받아썼어요' in await pg.inner_text('#mcV'))
            await pg.click('#mcSave'); await pg.wait_for_timeout(200)
            adds = [x for x in await log(pg) if x[0] == 'memoAdd']
            ok('voice_saved_as_voice', adds and adds[-1][4] == 'voice', adds)
            await pg.context.close()
            # 받아쓰기 실패 안내 (한국어 팩 없음 · 잠금 화면)
            pg = await page({'state': LIVE(now - 30000)}, call=True)
            await pg.evaluate(f"__state({json.dumps(END(True))})"); await pg.wait_for_timeout(250)
            await pg.click('#mcVoice'); await pg.evaluate("NV_voice({st:'start',res:'ok'});NV_voice({st:'error',text:'',why:'lang',mode:'device'})")
            ok('voice_lang_message', '한국어 받아쓰기가 아직 준비되지 않았어요' in await pg.inner_text('#mcV'))
            await pg.click('#mcVoice'); await pg.evaluate("NV_voice({st:'start',res:'locked'})")
            ok('voice_locked_message', '잠금 화면에서는 폰 안 받아쓰기만' in await pg.inner_text('#mcV'))
            await shot(pg, '06_memo_voice_locked_msg')
            await pg.context.close()
            # 잠금 화면: 이름만, 메모 숨김
            pg = await page({'state': RING, 'locked': True}, call=True)
            ok('locked_hides_caller_line', (await pg.inner_text('#inCtx')).strip() == '' and '김민수' in await pg.inner_text('#inName'))
            html = await pg.content(); ok('locked_memo_not_in_dom', '견적서' not in html)
            await shot(pg, '07_locked_incoming_name_only')
            await pg.context.close()

            # ---------- 3) 최근 통화: 메모 · 차단 ----------
            pg = await page({})
            await pg.evaluate("__nvp.show('recent')"); await pg.wait_for_timeout(200)
            t = await pg.inner_text('#rList')
            ok('blocked_call_shows_label', '차단됨' in t or '차단 중' in t, t[:300])
            await pg.click('.grp[data-k$="|01055556666"] .front'); await pg.wait_for_timeout(250)
            ok('blocked_row_detail', '차단됨' in await pg.inner_text('.grp[data-k$="|01055556666"]') and '차단 풀기' in await pg.inner_text('.grp[data-k$="|01055556666"]'))
            await shot(pg, '08_recent_blocked_row')
            await pg.click('.grp[data-k$="|01055556666"] .front'); await pg.wait_for_timeout(150)
            g = '.grp[data-k$="|01023456789"]'
            await pg.click(g + ' .front'); await pg.wait_for_timeout(250)
            ok('row_has_memo_count', '메모 2' in await pg.inner_text(g), await pg.inner_text(g))
            await shot(pg, '09_recent_row_actions')
            await pg.click(g + ' [data-act=block]'); await pg.wait_for_timeout(250)
            st = await pg.inner_text('#sheet')
            ok('block_confirm_sheet', '이 번호를 차단할까요?' in st and '차단하기' in st, st)
            ok('block_not_before_confirm', not any(x[0] == 'block' for x in await log(pg)))
            await shot(pg, '10_block_confirm')
            await pg.click('#sheet [data-a=yes]'); await pg.wait_for_timeout(250)
            ok('block_called', ['block', '01023456789'] in await log(pg))
            ok('block_pill_shown', '차단 중' in await pg.inner_text(g))
            ok('block_undo_toast', '차단했어요' in await toast(pg) and '되돌리기' in await toast(pg), await toast(pg))
            await shot(pg, '11_block_undo_toast')
            await pg.click('#toast button'); await pg.wait_for_timeout(200)
            ok('block_undo_unblocks', ['unblock', '01023456789'] in await log(pg) and '차단 중' not in await pg.inner_text(g + ' .front'))
            # 메모 화면: 추가 · 수정 · 삭제 · 되돌리기
            await pg.click(g + ' [data-act=memo]'); await pg.wait_for_timeout(250)
            ok('memo_view_open', await cur(pg) == 'memo' and '견적서 다시 보내기' in await pg.inner_text('#moList'))
            await pg.fill('#moTx', '다음 주 화요일 다시 전화'); await pg.click('#moList [data-mo=add]'); await pg.wait_for_timeout(200)
            ok('memo_view_add', any(x[0] == 'memoAdd' and x[3] == '다음 주 화요일 다시 전화' for x in await log(pg)) and '다음 주 화요일' in await pg.inner_text('#moList'))
            await shot(pg, '12_memo_view')
            await pg.click('#moList .mo:nth-of-type(1) [data-mo=edit]') if await pg.locator('#moList .mo:nth-of-type(1) [data-mo=edit]').count() else await pg.click('#moList [data-mo=edit]')
            await pg.wait_for_timeout(150)
            await pg.fill('#moEd', '다음 주 수요일 다시 전화'); await pg.click('#moList [data-mo=esave]'); await pg.wait_for_timeout(200)
            ok('memo_view_edit', any(x[0] == 'memoEdit' and x[2] == '다음 주 수요일 다시 전화' for x in await log(pg)) and '수요일' in await pg.inner_text('#moList'))
            n0 = await pg.locator('#moList .mo').count()
            await pg.click('#moList [data-mo=del]'); await pg.wait_for_timeout(200)
            ok('memo_view_delete', await pg.locator('#moList .mo').count() == n0 - 1 and '되돌리기' in await toast(pg))
            await shot(pg, '13_memo_deleted_undo')
            await pg.click('#toast button'); await pg.wait_for_timeout(200)
            ok('memo_view_undo', await pg.locator('#moList .mo').count() == n0 and any(x[0] == 'memoRestore' for x in await log(pg)))
            await pg.click('#v-memo [data-back]'); await pg.wait_for_timeout(200)
            ok('memo_back_to_recent', await cur(pg) == 'recent', await cur(pg))
            # 메모 목록 (사람별)
            await pg.evaluate("__nvp.show('settings')"); await pg.wait_for_timeout(150)
            await pg.click('#setList [data-b=memos]'); await pg.wait_for_timeout(200)
            ok('memos_list', await cur(pg) == 'memos' and '김민수' in await pg.inner_text('#mmList'))
            await shot(pg, '14_memos_list')
            await pg.click('#mmList [data-mk]'); await pg.wait_for_timeout(200)
            ok('memos_list_opens_person', await cur(pg) == 'memo')
            await pg.click('#v-memo [data-back]'); await pg.wait_for_timeout(150)
            ok('memo_back_to_memos', await cur(pg) == 'memos')
            # 차단 목록
            await pg.evaluate("__nvp.show('settings')"); await pg.wait_for_timeout(150)
            await pg.click('#setList [data-b=blocks]'); await pg.wait_for_timeout(200)
            t = await pg.inner_text('#bkList')
            ok('blocks_list', '010-5555-6666' in t and '차단 풀기' in t and '폰 전체 차단 목록' in t, t)
            await shot(pg, '15_blocks_list')
            await pg.click('#bkList [data-unb]'); await pg.wait_for_timeout(200)
            ub = [x for x in await log(pg) if x[0] == 'unblock']
            ok('blocks_unblock', ub and ub[-1][1].replace('-', '') == '01055556666' and '차단한 번호가 없어요' in await pg.inner_text('#bkList'), (ub, await pg.inner_text('#bkList')))
            await pg.context.close()
            # 긴급 번호 · 기본 전화 앱 아님
            pg = await page({'calls': [{'id': 1, 'num': '119', 'name': '', 'date': now - 60000, 'type': 'out', 'dur': 30, 'pres': 'allowed'}]})
            await pg.evaluate("__nvp.show('recent')"); await pg.click('.grp .front'); await pg.wait_for_timeout(200)
            await pg.click('.grp [data-act=block]'); await pg.wait_for_timeout(200)
            ok('emergency_not_blockable', '긴급 번호는 차단할 수 없어요' in await toast(pg) and not any(x[0] == 'block' for x in await log(pg)))
            await pg.context.close()
            pg = await page({'def': False})
            await pg.evaluate("__nvp.show('recent')"); await pg.click('.grp[data-k$="|01023456789"] .front'); await pg.wait_for_timeout(200)
            await pg.click('.grp[data-k$="|01023456789"] [data-act=block]'); await pg.wait_for_timeout(200)
            st = await pg.inner_text('#sheet')
            ok('role_needed_sheet', '기본 전화 앱이어야 차단할 수 있어요' in st, st)
            await shot(pg, '16_block_needs_default_app')
            await pg.click('#sheet [data-b=role]'); await pg.wait_for_timeout(100)
            ok('role_sheet_requests_role', ['requestRole'] in await log(pg))
            await pg.evaluate("__nvp.show('blocks')"); await pg.wait_for_timeout(150)
            ok('blocks_view_role_hint', '기본 전화 앱이어야' in await pg.inner_text('#bkList'))
            await pg.context.close()

            # ---------- 4) 명함 → 연락처 ----------
            pg = await page({})
            await pg.evaluate("__nvp.show('search')"); await pg.wait_for_timeout(150)
            await pg.click('#abCard'); await pg.wait_for_timeout(200)
            st = await pg.inner_text('#sheet')
            ok('card_sheet', '카메라로 찍기' in st and '사진에서 고르기' in st and '저장하지 않아요' in st, st)
            await shot(pg, '17_card_sheet')
            await pg.click('#sheet [data-a=gallery]'); await pg.wait_for_timeout(200)
            ok('card_scan_called', ['cardScan', 'gallery'] in await log(pg) and await cur(pg) == 'card' and '명함 읽는 중' in await pg.inner_text('#cdList'))
            await shot(pg, '18_card_reading')
            R = {'ok': True, 'name': '김민수', 'title': '영업부 과장', 'company': '(주)한빛상사', 'mobile': '010-2345-6789', 'tel': '02-555-1234', 'email': '', 'nameGuess': False, 'companyGuess': False,
                 'lines': ['(주)한빛상사', '김민수', '영업부 과장', 'M 010-2345-6789', 'T 02-555-1234', 'E minsu.kim@hanbit.co.kr', '서울특별시 강남구 테헤란로 123']}
            await pg.evaluate(f"NV_card({json.dumps(R)})"); await pg.wait_for_timeout(250)
            vals = await pg.evaluate("Object.fromEntries([...document.querySelectorAll('#cdList input[data-f]')].map(i=>[i.dataset.f,i.value]))")
            ok('card_form_filled', vals.get('name') == '김민수' and vals.get('mobile') == '010-2345-6789' and vals.get('company') == '(주)한빛상사', vals)
            ok('card_dup_warning', '이미 주소록에 있는 번호예요' in await pg.inner_text('#cdList'))
            await pg.click('#cdList input[data-f=email]')
            await pg.click('#cdList [data-ln="5"]'); await pg.wait_for_timeout(150)
            ok('card_line_fills_focused', await pg.input_value('#cdList input[data-f=email]') == 'minsu.kim@hanbit.co.kr', await pg.input_value('#cdList input[data-f=email]'))
            await shot(pg, '19_card_form')
            await pg.click('#cdList [data-cd=save]'); await pg.wait_for_timeout(250)
            sv = [x for x in await log(pg) if x[0] == 'cardSave']
            j = json.loads(sv[-1][1]) if sv else {}
            ok('card_saved_fields', j.get('name') == '김민수' and j.get('email') == 'minsu.kim@hanbit.co.kr' and j.get('tel') == '02-555-1234', j)
            ok('card_after_save_contacts', await cur(pg) == 'search' and '주소록에 저장했어요' in await toast(pg))
            await pg.context.close()
            # 허용 없음 → 주소록 앱으로
            pg = await page({'cardSave': 'perm'})
            await pg.evaluate("__nvp.show('search')"); await pg.click('#abCard'); await pg.wait_for_timeout(150)
            await pg.click('#sheet [data-a=manual]'); await pg.wait_for_timeout(200)
            ok('card_manual_form', await cur(pg) == 'card' and await pg.locator('#cdList input[data-f=name]').count() == 1)
            await pg.fill('#cdList input[data-f=name]', '이순신'); await pg.fill('#cdList input[data-f=mobile]', '010-1111-2222')
            await pg.click('#cdList [data-cd=save]'); await pg.wait_for_timeout(150)
            await pg.evaluate("NV_perm('contacts',false)"); await pg.wait_for_timeout(200)
            ok('card_perm_denied_sheet', '연락처 저장을 허용하지 않았어요' in await pg.inner_text('#sheet'))
            await shot(pg, '20_card_perm_denied')
            await pg.click('#sheet [data-a=ins]'); await pg.wait_for_timeout(100)
            ins = [x for x in await log(pg) if x[0] == 'cardInsert']
            ok('card_insert_fallback', ins and json.loads(ins[-1][1]).get('name') == '이순신', ins)
            await pg.context.close()
            # 읽기 실패
            pg = await page({})
            await pg.evaluate("__nvp.show('search')"); await pg.click('#abCard'); await pg.click('#sheet [data-a=camera]'); await pg.wait_for_timeout(150)
            await pg.evaluate("NV_card({ok:false,err:'timeout'})"); await pg.wait_for_timeout(200)
            ok('card_error_message', '글자를 읽지 못했어요' in await pg.inner_text('#cdList'))
            await pg.evaluate("__nvp.show('search')"); await pg.click('#abCard'); await pg.click('#sheet [data-a=camera]'); await pg.wait_for_timeout(150)
            await pg.evaluate("NV_card({ok:false,err:'cancel'})"); await pg.wait_for_timeout(200)
            ok('card_cancel_goes_back', await cur(pg) != 'card', await cur(pg))
            await pg.context.close()

            # ---------- 5) 일정 10분 전 ----------
            pg = await page({'calSoon': True})
            await pg.evaluate("__nvp.show('recent')"); await pg.wait_for_timeout(200)
            t = await pg.inner_text('#rBanner')
            ok('cal_banner', '곧 일정 · 병원 예약' in t and '회의 모드 켤까요?' in t and '켜기' in t and '괜찮아요' in t, t)
            await shot(pg, '21_cal_banner')
            await pg.click('#calBan [data-cal=on]'); await pg.wait_for_timeout(200)
            ok('cal_on_called', any(x[0] == 'calOn' for x in await log(pg)) and '회의 모드를 켰어요' in await toast(pg))
            ok('cal_banner_gone', '곧 일정' not in await pg.inner_text('#rBanner'))
            await pg.context.close()
            pg = await page({'calSoon': True})
            await pg.evaluate("__nvp.show('recent')"); await pg.click('#calBan [data-cal=no]'); await pg.wait_for_timeout(200)
            ok('cal_no_called', any(x[0] == 'calNo' for x in await log(pg)) and '곧 일정' not in await pg.inner_text('#rBanner'))
            await pg.evaluate("__nvp.show('settings')"); await pg.wait_for_timeout(150)
            await pg.click('#calSeg [data-calm=off]'); await pg.wait_for_timeout(150)
            ok('cal_mode_off', ['calMode', 'off'] in await log(pg) and 'on' in (await pg.get_attribute('#calSeg [data-calm=off]', 'class') or ''))
            await pg.locator('#calSeg').scroll_into_view_if_needed(); await shot(pg, '22_settings_v6')
            await pg.context.close()
            pg = await page({'cal': {'mode': 'ask', 'perm': False}, 'perms': {'cal': False}})
            await pg.evaluate("__nvp.show('settings')"); await pg.wait_for_timeout(150)
            ok('cal_perm_row', '달력 허용이 필요해요' in await pg.inner_text('#setList'))
            await pg.click('#calSeg [data-calm=auto]'); await pg.wait_for_timeout(150)
            ok('cal_auto_asks_perm', ['calMode', 'auto'] in await log(pg) and ['calAsk'] in await log(pg))
            await pg.context.close()
            pg = await page({'calSoon': True, 'active': {'id': 'drive', 'start': now - 60000, 'end': 0}})
            # 앱 안 카드는 calInfo 가 정해 줌(운전 중이면 Java 쪽이 key 를 안 줌) → 여기선 key 가 없을 때 배너가 없는지
            await pg.evaluate("window.NVPhone.calInfo=()=>JSON.stringify({mode:'ask',perm:true});__nvp.show('recent')"); await pg.wait_for_timeout(200)
            await pg.evaluate("NV_resume()"); await pg.wait_for_timeout(200)
            ok('cal_no_banner_without_key', '곧 일정' not in await pg.inner_text('#rBanner'))
            await pg.context.close()

            # ---------- 6) 큰 버튼 화면 ----------
            pg = await page({})
            ok('big_off_default_recent', await cur(pg) == 'recent', await cur(pg))
            await pg.evaluate("__nvp.show('settings')"); await pg.wait_for_timeout(150)
            await pg.click('#setList [data-b=bigset]'); await pg.wait_for_timeout(200)
            t = await pg.inner_text('#bsList')
            ok('bigset_suggests_frequent', '엄마' in t and '자주 통화한 사람' in t, t)
            await pg.click('#bsList [data-bigtg]'); await pg.wait_for_timeout(150)
            for _ in range(3):
                await pg.click('#bsList .bsl [data-ba] .ubtn'); await pg.wait_for_timeout(120)
            await pg.fill('#bsIn', '아빠'); await pg.wait_for_timeout(150)
            await pg.click('#bsRes [data-ba] .ubtn'); await pg.wait_for_timeout(150)
            B = await pg.evaluate("JSON.parse(localStorage.getItem('nvp_big'))")
            ok('bigset_four_people', B['on'] and len(B['people']) == 4, B)
            ok('bigset_max_four', await pg.locator('#bsIn').count() == 0)
            await shot(pg, '23_bigset')
            await pg.click('#bsList [data-bdone]'); await pg.wait_for_timeout(200)
            ok('big_view', await cur(pg) == 'big' and await pg.locator('#bgList [data-bg]').count() == 4)
            await shot(pg, '24_big_screen')
            sz = await pg.evaluate("(()=>{const r=document.querySelector('#bgList [data-bg]').getBoundingClientRect();return [r.width,r.height]})()")
            fs = await pg.evaluate("parseFloat(getComputedStyle(document.querySelector('#bgList .bgn')).fontSize)")
            ok('big_buttons_are_big', sz[1] >= 96 and sz[0] >= 330 and fs >= 26, (sz, fs))
            await pg.click('#bgList [data-bg="0"]'); await pg.wait_for_timeout(150)
            ok('big_tap_calls', any(x[0] == 'call' for x in await log(pg)))
            await pg.click('#v-big [data-bgv=recent]'); await pg.wait_for_timeout(150)
            ok('big_to_recent', await cur(pg) == 'recent')
            back = await pg.evaluate("window.NV_back?NV_back():null")
            await pg.wait_for_timeout(150)
            ok('back_from_recent_returns_big', await cur(pg) == 'big', (back, await cur(pg)))
            await pg.context.close()
            pg = await page({}, big={'on': True, 'people': [{'key': '01099990000', 'num': '01099990000', 'name': '엄마'}, {'key': '01088880000', 'num': '01088880000', 'name': '아빠'}, {'key': '01066660000', 'num': '01066660000', 'name': ''}]})
            ok('big_on_starts_big', await cur(pg) == 'big', await cur(pg))
            t = await pg.inner_text('#bgList')
            ok('big_uses_contact_name', '엄마' in t and '아빠' in t and '동생' in t, t)
            await shot(pg, '25_big_three')
            await pg.context.close()
            pg = await page({}, big={'on': True, 'people': []})
            ok('big_empty_hint', '큰 버튼에 넣을 사람을 골라 주세요' in await pg.inner_text('#bgList'))
            await pg.context.close()

            # ---------- 7) 작은 화면 넘침 ----------
            for (w, h) in [(360, 720), (320, 640)]:
                pg = await page({'calSoon': True, 'state': LIVE(now - 30000)}, w, h, call=True)
                await pg.evaluate(f"__state({json.dumps(END(True))})"); await pg.wait_for_timeout(250)
                ov = await pg.evaluate("[...document.querySelectorAll('#v-memocard *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>e.className||e.tagName).slice(0,5)")
                ok(f'memocard_no_overflow_{w}', not ov, ov)
                if w == 320: await shot(pg, '26_memocard_320')
                await pg.context.close()
                pg = await page({'calSoon': True}, w, h, big={'on': False, 'people': [{'key': '01099990000', 'num': '01099990000', 'name': '엄마아빠누나동생'}]})
                bad = {}
                for v, sel in [('recent', '#v-recent'), ('memo', '#v-memo'), ('blocks', '#v-blocks'), ('settings', '#v-settings'), ('bigset', '#v-bigset'), ('big', '#v-big'), ('memos', '#v-memos')]:
                    if v == 'memo': await pg.evaluate("__nvp.show('recent')")
                    await pg.evaluate(f"__nvp.show('{v}')" if v != 'memo' else "document.querySelector('.grp[data-k$=\"|01023456789\"] .front').click()")
                    if v == 'memo':
                        await pg.wait_for_timeout(150); await pg.click('.grp[data-k$="|01023456789"] [data-act=memo]')
                    await pg.wait_for_timeout(200)
                    o = await pg.evaluate(f"[...document.querySelectorAll('{sel} *')].filter(e=>{{const r=e.getBoundingClientRect();return r.width>0&&r.right>innerWidth+1&&getComputedStyle(e).position!=='fixed'}}).map(e=>e.className||e.tagName).slice(0,4)")
                    if o: bad[v] = o
                ok(f'v6_views_no_overflow_{w}', not bad, bad)
                if w == 320: await shot(pg, '27_memos_320')
                await pg.context.close()

            ok('no_js_errors', not any(allerrs), [e for e in allerrs if e][:3])
            html = open(os.path.join(ROOT, 'www', 'index.html'), encoding='utf-8').read()
            emoji = re.findall('[\U0001F300-\U0001FAFF\u2600-\u26FF\u2700-\u27BF\U0001F000-\U0001F2FF]', html)
            ok('no_emoji_in_source', not emoji, emoji[:5])
            ok('no_yuni_word', not re.search('유니(?!버스)', html))
            ok('says_newniverse', '뉴니버스' in html)
            hues = []
            for m in re.finditer(r'#([0-9a-fA-F]{6})\b', html):
                r, g_, b = [int(m.group(1)[i:i+2], 16) for i in (0, 2, 4)]
                mx, mn = max(r, g_, b), min(r, g_, b)
                if mx - mn > 60 and mx == r and g_ > b + 30 and g_ > 90 and r > 200 and g_ < r: hues.append(m.group(0))
            ok('no_orange_colors', not hues and not re.search(r'\borange\b', html, re.I), hues)
            await br.close()
    finally:
        srv.terminate()
    bad = [k for k, v in res.items() if not v]
    print(f'\n{len(res)-len(bad)}/{len(res)} PASS', 'FAILED: ' + ', '.join(bad) if bad else 'ALL OK')
    raise SystemExit(1 if bad else 0)

asyncio.run(main())

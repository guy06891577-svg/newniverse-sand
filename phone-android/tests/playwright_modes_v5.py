#!/usr/bin/env python3
"""뉴니버스 전화 v5 · 상황 모드 화면 시험: 가짜 NVPhone 다리로 목록/설정/켜기 흐름/허용 안내/요약/배너/뒤로가기 확인
   + 이모지·주황색·'유니' 글자가 없는지, 좌우로 넘치지 않는지. 실행: python3 playwright_modes_v5.py"""
import asyncio, json, os, re, subprocess, time
from playwright.async_api import async_playwright

PORT = 18491
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'assets'))
U = f'http://localhost:{PORT}/www/index.html'
SHOTS = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', 'shots-phone-app-v5')); os.makedirs(SHOTS, exist_ok=True)
res = {}

def ok(k, v, extra=''):
    res[k] = bool(v)
    print(('PASS ' if v else 'FAIL ') + k + (' · ' + str(extra) if (extra and not v) else ''), flush=True)

MOCK = r'''
(()=>{
const cfg=JSON.parse(sessionStorage.getItem('PCFG')||'{}');
window.__LOG=[]; const L=(n,...a)=>__LOG.push([n,...a]);
const now=Date.now(), MIN=60000;
const perms=Object.assign({log:true,wlog:true,contacts:true,phone:true,notif:true,dnd:false,sms:false,readSms:false},cfg.perms||{});
let def=cfg.def!==false;
const DEFS=[{id:'drive',name:'운전 중',timers:false,text:'지금 운전 중이라 전화를 못 받아요. 도착하면 연락드릴게요.'},{id:'meeting',name:'회의 중',timers:true,text:'지금 회의 중이라 전화를 못 받아요. 끝나면 연락드릴게요.'}];
const CF={};DEFS.forEach(d=>CF[d.id]={text:d.text,allow:'none',repeat:false,reply:true,other:false,contactsOnly:false,texts:true,timer:0});
let active=cfg.active||null, pending=cfg.pending||null;
window.__perm=(k,v)=>{perms[k]=v};
const calls=[
 {id:1,num:'01012345678',name:'김민수',date:now-20*MIN,type:'miss',dur:0,pres:'allowed'},
 {id:2,num:'01099998888',name:'',date:now-5*3600000,type:'in',dur:93,pres:'allowed'},
];
const SUM={id:'drive',name:'운전 중',start:now-68*MIN,end:now-1*MIN,why:'manual',durText:'1시간 7분',dnd:true,needLog:!!cfg.needLog,needSms:!!cfg.needSms,textsOn:true,
 calls:cfg.noCalls?[]:[{key:'01012345678',num:'01012345678',name:'김민수',n:3,last:now-9*MIN,x:'',callable:true},{key:'0212345678',num:'0212345678',name:'',n:1,last:now-30*MIN,x:'',callable:true},{key:'hidden',num:'',name:'',n:1,last:now-50*MIN,x:'',callable:false}],
 texts:[{key:'01077776666',num:'01077776666',name:'박지은',n:2,last:now-12*MIN,x:'어디쯤이야? 도착하면 바로 전화줘',callable:true}],
 log:[{k:'01012345678',num:'01012345678',name:'김민수',at:now-40*MIN,st:'ok'},{k:'01055554444',num:'01055554444',name:'',at:now-33*MIN,st:'wait'},{k:'01033332222',num:'01033332222',name:'이서연',at:now-20*MIN,st:'fail'}],
 skips:{'대표번호(사업자)':3,'이미 보낸 번호':2,'광고·인증 문자 번호':1}};
window.NVPhone={
 version:()=> '5-test', vibrate:()=>{}, clearMissed:()=>{}, takeView:()=>{const v=cfg.view||'';cfg.view='';return v}, takeDial:()=>'', requestPerms:()=>L('requestPerms'), requestRole:()=>L('requestRole'),
 openAppSettings:()=>L('openAppSettings'), openDefaultApps:()=>L('openDefaultApps'), openFsiSettings:()=>L('openFsiSettings'), close:()=>{}, openCall:()=>{},
 status:()=>JSON.stringify({def,canRole:true,fsi:true,ver:'5-test',sdk:36,inCall:false,perms:{log:perms.log,wlog:perms.wlog,contacts:perms.contacts,phone:perms.phone,notif:perms.notif}}),
 getContacts:()=>JSON.stringify([{name:'김민수',num:'010-1234-5678'},{name:'박지은',num:'010-7777-6666'}]),
 getCalls:()=>JSON.stringify(calls),
 call:n=>{L('call',n);return 'ok'}, sms:n=>L('sms',n),
 modes:()=>JSON.stringify({now:Date.now(),cfg:CF,defs:DEFS,active:active,pending:pending,perms:perms,def:def}),
 modeStart:(id,t)=>{L('modeStart',id,t);const c=CF[id];active={id,start:Date.now(),end:t>0?Date.now()+t*MIN:0,dnd:perms.dnd,keep:false,sent:0};pending=null;return JSON.stringify({ok:true,dnd:perms.dnd,keep:false,sms:perms.sms,def:def})},
 modeStop:()=>{L('modeStop');if(active){pending={id:active.id,start:active.start,end:Date.now()};active=null}return JSON.stringify(pending||{})},
 modeSave:(id,j)=>{L('modeSave',id,j);const o=JSON.parse(j);CF[id]=Object.assign({},CF[id],o);return JSON.stringify(CF[id])},
 modeSummary:()=>JSON.stringify(pending?Object.assign({},SUM,{id:pending.id,name:pending.id==='meeting'?'회의 중':'운전 중',start:pending.start,end:pending.end}):{}),
 modeDismiss:()=>{L('modeDismiss');pending=null},
 modeAsk:w=>{L('modeAsk',w)}
};
})();
'''

async def main():
    srv = subprocess.Popen(['python3', '-m', 'http.server', str(PORT), '--bind', '127.0.0.1', '--directory', ROOT], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.8)
    try:
        async with async_playwright() as pw:
            br = await pw.chromium.launch(executable_path='/usr/bin/google-chrome')
            async def page(cfg=None, w=390, h=844):
                ctx = await br.new_context(viewport={'width': w, 'height': h}, device_scale_factor=2, locale='ko-KR')
                pg = await ctx.new_page()
                errs = []
                pg.on('pageerror', lambda e: errs.append(str(e)))
                pg.on('console', lambda m: errs.append(m.text) if m.type == 'error' else None)
                pg.errs = errs
                await pg.add_init_script("sessionStorage.setItem('PCFG'," + json.dumps(json.dumps(cfg or {})) + ")" )
                await pg.add_init_script(MOCK)
                await pg.goto(U); await pg.wait_for_timeout(500)
                return pg
            async def log(pg): return await pg.evaluate('window.__LOG')
            async def shot(pg, n): await pg.screenshot(path=f'{SHOTS}/{n}.png')
            async def cur(pg): return await pg.evaluate('__nvp.mdState().cur')
            now = int(time.time() * 1000)

            # 1) 켜져 있을 때 최근통화 위 배너
            pg = await page({'active': {'id': 'drive', 'start': now - 25 * 60000, 'end': 0, 'dnd': True, 'keep': False, 'sent': 4}, 'perms': {'dnd': True, 'sms': True}})
            await pg.evaluate("__nvp.show('recent')"); await pg.wait_for_timeout(200)
            t = await pg.inner_text('#rBanner')
            ok('banner_active_text', '운전 중 켜져 있어요' in t and '25분째' in t and '4건' in t, t)
            await shot(pg, '01_recent_banner_active')
            await pg.click('#rBanner button'); await pg.wait_for_timeout(300)
            ok('banner_off_goes_to_summary', await cur(pg) == 'summary' and any(x[0] == 'modeStop' for x in await log(pg)), await cur(pg))
            t = await pg.inner_text('#smList')
            ok('summary_has_sections', all(s in t for s in ['놓친 전화', '받은 문자', '보낸 자동 답장', '김민수', '박지은']), t[:200])
            await shot(pg, '02_summary')
            # 요약: 스크롤해서 아래도
            await pg.evaluate("document.querySelector('#smList').scrollTop=9999"); await pg.wait_for_timeout(150)
            await shot(pg, '03_summary_bottom')
            ok('summary_skips_shown', '대표번호(사업자) 3' in await pg.inner_text('#smList'))
            t = await pg.inner_text('#smList'); ok('summary_status_labels', all(s in t for s in ['보냈어요', '보내는 중', '못 보냈어요']))
            await pg.evaluate("document.querySelector('#smList').scrollTop=0")
            # 전화/문자 버튼
            await pg.click('.smrow[data-num="01012345678"] .qb.pri'); await pg.wait_for_timeout(150)
            await pg.click('.smrow[data-num="01012345678"] .qb:not(.pri)'); await pg.wait_for_timeout(150)
            L = await log(pg)
            ok('summary_call_button', any(x[0] == 'call' and x[1] == '01012345678' for x in L), L)
            ok('summary_sms_button', any(x[0] == 'sms' and x[1] == '01012345678' for x in L), L)
            ok('hidden_number_has_no_buttons', await pg.locator('.smrow:not([data-num]) .qb, .smrow[data-num=""] .qb').count() == 0)
            await pg.click('.smok'); await pg.wait_for_timeout(300)
            L = await log(pg)
            ok('summary_done_dismisses', any(x[0] == 'modeDismiss' for x in L) and await cur(pg) == 'recent')
            ok('banner_gone_after_dismiss', '끝났어요' not in await pg.inner_text('#rBanner') and '켜져 있어요' not in await pg.inner_text('#rBanner'))
            ok('no_js_errors_1', not pg.errs, pg.errs)
            await pg.context.close()

            # 2) 끝난 뒤 배너(요약 대기)
            pg = await page({'pending': {'id': 'meeting', 'start': now - 90 * 60000, 'end': now - 2 * 60000}})
            await pg.evaluate("__nvp.show('recent')"); await pg.wait_for_timeout(200)
            t = await pg.inner_text('#rBanner')
            ok('banner_pending_text', '회의 중이 끝났어요' in t and '보기' in t, t)
            await shot(pg, '04_recent_banner_pending')
            await pg.click('#rBanner button'); await pg.wait_for_timeout(300)
            ok('pending_banner_opens_summary', await cur(pg) == 'summary' and '회의 중 요약' in await pg.inner_text('#smTitle'))
            await pg.click('#smBack'); await pg.wait_for_timeout(200)
            ok('summary_back_returns', await cur(pg) == 'recent')
            await pg.context.close()

            # 3) 모드 목록 · 허용이 없을 때
            pg = await page({})
            await pg.click('#tabs button[data-v=keypad]'); await pg.click('#kpMore'); await pg.wait_for_timeout(250)
            ok('menu_has_modes_item', '상황 모드' in await pg.inner_text('#kpMenu'))
            await shot(pg, '05_menu_with_modes')
            await pg.click('#kpMenu [data-b=modes]'); await pg.wait_for_timeout(300)
            ok('modes_view_open', await cur(pg) == 'modes')
            t = await pg.inner_text('#mdList')
            ok('modes_lists_both', '운전 중' in t and '회의 중' in t and '켜기' in t)
            ok('modes_missing_notices', '방해 금지 접근을 허용해 주세요' in t and '문자 보내기를 허용해 주세요' in t)
            await shot(pg, '06_modes_list')
            ok('modes_tabbar_hidden', await pg.evaluate("getComputedStyle(document.querySelector('#tabs')).display") == 'none')
            # 회의: 타이머 칩
            await pg.click('.md[data-id=meeting] [data-md=timer][data-m="60"]'); await pg.wait_for_timeout(150)
            L = await log(pg); sv = [x for x in L if x[0] == 'modeSave' and x[1] == 'meeting']
            ok('timer_saved', sv and json.loads(sv[-1][2])['timer'] == 60, sv)
            ok('timer_chip_on', await pg.locator('.md[data-id=meeting] [data-m="60"].on').count() == 1)
            ok('drive_has_no_timer_chips', await pg.locator('.md[data-id=drive] [data-md=timer]').count() == 0)
            # 설정 펼치기
            await pg.click('.md[data-id=drive] [data-md=open]'); await pg.wait_for_timeout(200)
            await pg.evaluate("document.querySelector('.md[data-id=drive] .mdset').scrollIntoView({block:'start'})"); await pg.wait_for_timeout(150)
            await shot(pg, '07_drive_settings_open')
            t = await pg.inner_text('.md[data-id=drive] .mdset')
            ok('settings_options_present', all(s in t for s in ['모두 조용히', '즐겨찾기만', '연락처 사람만', '15분 안에', '자동 답장 보내기', '연락처에 있는 사람에게만', '일반전화', '받은 문자도 함께 보기']), t)
            await pg.click('.md[data-id=drive] [data-md=allow][data-v=starred]'); await pg.wait_for_timeout(150)
            L = await log(pg); sv = [x for x in L if x[0] == 'modeSave' and x[1] == 'drive']
            ok('allow_saved', sv and json.loads(sv[-1][2])['allow'] == 'starred', sv)
            ok('allow_btn_on', await pg.locator('.md[data-id=drive] [data-v=starred].on').count() == 1)
            await pg.click('.md[data-id=drive] .tg[data-k=other]'); await pg.wait_for_timeout(150)
            sv = [x for x in await log(pg) if x[0] == 'modeSave' and x[1] == 'drive']
            ok('other_toggle_saved', json.loads(sv[-1][2])['other'] is True, sv[-1])
            ok('toggle_aria_updated', await pg.get_attribute('.md[data-id=drive] .tg[data-k=other]', 'aria-checked') == 'true')
            await pg.click('.md[data-id=drive] .tg[data-k=repeat]'); await pg.wait_for_timeout(100)
            # 문구 수정
            await pg.fill('.md[data-id=drive] .mdtx', '운전 중입니다. 곧 연락드릴게요.'); await pg.wait_for_timeout(800)
            sv = [x for x in await log(pg) if x[0] == 'modeSave' and x[1] == 'drive']
            ok('text_saved_debounced', json.loads(sv[-1][2])['text'] == '운전 중입니다. 곧 연락드릴게요.', sv[-1])
            ok('text_counter', '17/140' in await pg.inner_text('.md[data-id=drive] .mdtxf') or '/140' in await pg.inner_text('.md[data-id=drive] .mdtxf'))
            await pg.click('.md[data-id=drive] [data-md=deftext]'); await pg.wait_for_timeout(150)
            ok('text_default_restore', '도착하면 연락드릴게요' in await pg.input_value('.md[data-id=drive] .mdtx'))
            await pg.fill('.md[data-id=drive] .mdtx', 'x'*300)
            ok('text_maxlength_attr', await pg.get_attribute('.md[data-id=drive] .mdtx', 'maxlength') == '140')
            # 자동 답장 끄면 문구칸 잠김
            await pg.click('.md[data-id=drive] .tg[data-k=reply]'); await pg.wait_for_timeout(150)
            ok('reply_off_disables_text', await pg.is_disabled('.md[data-id=drive] .mdtx'))
            await pg.click('.md[data-id=drive] .tg[data-k=reply]'); await pg.wait_for_timeout(150)
            # 문자 읽기 허용 버튼
            await pg.evaluate("document.querySelector('.md[data-id=drive] .mdset').lastElementChild.scrollIntoView()")
            await pg.click('.md[data-id=drive] [data-md=ask][data-w=readsms]'); await pg.wait_for_timeout(100)
            ok('ask_readsms_logged', ['modeAsk', 'readsms'] in await log(pg))
            ok('no_js_errors_3', not pg.errs, pg.errs)
            await pg.click('.md[data-id=drive] [data-md=open]')

            # 4) 처음 켜기 → 허용 안내 시트 → 지금 켜기
            await pg.evaluate("document.querySelector('#mdList').scrollTop=0")
            await pg.click('.md[data-id=drive] [data-md=toggle]'); await pg.wait_for_timeout(350)
            ok('setup_sheet_shown', await pg.evaluate("document.querySelector('#sheet').classList.contains('on')") and '켜기 전에' in await pg.inner_text('#sheet'))
            await shot(pg, '08_setup_sheet')
            ok('mode_not_started_before_sheet_ok', not any(x[0] == 'modeStart' for x in await log(pg)))
            await pg.click('#sheet [data-md=ask][data-w=dnd]'); await pg.wait_for_timeout(100)
            ok('ask_dnd_logged', ['modeAsk', 'dnd'] in await log(pg))
            # 설정 앱에서 돌아옴: 허용됨 으로
            await pg.evaluate("__perm('dnd',true);NV_resume()"); await pg.wait_for_timeout(300)
            ok('sheet_still_open_after_resume', await pg.evaluate("document.querySelector('#sheet').classList.contains('on')"))
            ok('sheet_shows_granted', '허용됨' in await pg.inner_text('#sheet'))
            await shot(pg, '09_setup_sheet_granted')
            # 문자 허용 요청 → 바로 돌아옴(막힘) → 제한된 설정 안내
            await pg.click('#sheet [data-md=ask][data-w=sms]'); await pg.wait_for_timeout(50)
            await pg.evaluate("NV_resume('perm')"); await pg.wait_for_timeout(300)
            t = await pg.inner_text('#sheet')
            ok('restricted_sheet_shown', '제한된 설정 허용' in t and '문자 보내기' in t, t)
            await shot(pg, '10_sms_restricted_sheet')
            await pg.click('#sheet [data-b=appset]'); await pg.wait_for_timeout(100)
            ok('restricted_opens_app_info', ['openAppSettings'] in await log(pg))
            await pg.evaluate("__perm('sms',true)")
            await pg.evaluate("document.querySelector('#bd').click()")
            await pg.click('.md[data-id=drive] [data-md=toggle]'); await pg.wait_for_timeout(300)
            L = await log(pg)
            ok('second_tap_starts_without_sheet', any(x[0] == 'modeStart' for x in L) and not await pg.evaluate("document.querySelector('#sheet').classList.contains('on')"))
            ms = [x for x in L if x[0] == 'modeStart']
            ok('mode_started_drive', ms and ms[-1][1] == 'drive' and ms[-1][2] == 0, ms)
            ok('sheet_closed_after_start', not await pg.evaluate("document.querySelector('#sheet').classList.contains('on')"))
            t = await pg.inner_text('#mdList')
            ok('active_card_state', '켜져' in t or '째' in t and '끄기' in t, t[:300])
            await shot(pg, '11_modes_active')
            # 같은 모드를 다시 눌러 끄기 → 요약
            await pg.click('.md[data-id=drive] [data-md=toggle]'); await pg.wait_for_timeout(350)
            ok('toggle_off_goes_summary', await cur(pg) == 'summary' and any(x[0] == 'modeStop' for x in await log(pg)))
            await pg.click('.smok'); await pg.wait_for_timeout(250)
            ok('after_done_back_to_keypad', await cur(pg) == 'keypad', await cur(pg))
            # 두 번째 켜기는 시트 없이 바로 (한 번만 묻기)
            await pg.click('#kpMore'); await pg.click('#kpMenu [data-b=modes]'); await pg.wait_for_timeout(250)
            await pg.click('.md[data-id=meeting] [data-md=toggle]'); await pg.wait_for_timeout(300)
            L = await log(pg); ms = [x for x in L if x[0] == 'modeStart' and x[1] == 'meeting']
            ok('meeting_started_without_second_ask', ms and ms[-1][2] == 60, ms)
            t = await pg.inner_text('.md[data-id=meeting]')
            ok('meeting_shows_auto_end_time', '자동으로 꺼져요' in t, t)
            await shot(pg, '12_meeting_active')
            await pg.evaluate("history.back&&0")
            # 뒤로가기
            await pg.evaluate("NV_back()"); await pg.wait_for_timeout(200)
            ok('back_from_modes', await cur(pg) in ('keypad', 'recent'), await cur(pg))
            ok('no_js_errors_4', not pg.errs, pg.errs)
            await pg.context.close()

            # 5) 앱 밖(알림/런처)에서 열기
            pg = await page({'view': 'summary', 'pending': {'id': 'drive', 'start': now - 40 * 60000, 'end': now - 1000}})
            ok('intent_summary_on_start', await cur(pg) == 'summary', await cur(pg))
            await pg.context.close()
            pg = await page({'view': 'modes:dnd:drive'})
            ok('intent_dnd_opens_modes_and_sheet', await cur(pg) == 'modes' and await pg.evaluate("document.querySelector('#sheet').classList.contains('on')"), await cur(pg))
            await shot(pg, '13_intent_need_dnd')
            await pg.context.close()
            # 앱 아이콘 길게 누르기 바로가기: 켤지 한 번 묻고, 켜기를 눌러야 켜짐
            pg = await page({'view': 'modes:ask:meeting', 'perms': {'dnd': True, 'sms': True}})
            ok('shortcut_ask_sheet', await cur(pg) == 'modes' and '회의 중 켤까요?' in await pg.inner_text('#sheet'), await pg.inner_text('#sheet'))
            ok('shortcut_does_not_start_by_itself', not any(x[0] == 'modeStart' for x in await log(pg)))
            await shot(pg, '16_shortcut_confirm')
            await pg.click('#sheet [data-md=confirm]'); await pg.wait_for_timeout(300)
            ms = [x for x in await log(pg) if x[0] == 'modeStart']
            ok('shortcut_confirm_starts_meeting', ms and ms[-1][1] == 'meeting', ms)
            await pg.context.close()
            pg = await page({'view': 'modes:ask:drive', 'active': {'id': 'drive', 'start': now - 60000, 'end': 0, 'dnd': True, 'keep': False, 'sent': 0}, 'perms': {'dnd': True, 'sms': True}})
            ok('shortcut_when_already_on_no_sheet', not await pg.evaluate("document.querySelector('#sheet').classList.contains('on')"))
            await pg.context.close()
            pg = await page({'view': 'modes'})
            ok('intent_modes_on_start', await cur(pg) == 'modes')
            await pg.context.close()

            # 6) 요약: 허용 필요 안내 / 놓친 전화 없음
            pg = await page({'view': 'summary', 'pending': {'id': 'drive', 'start': now - 40 * 60000, 'end': now - 1000}, 'needLog': True, 'needSms': True})
            t = await pg.inner_text('#smList')
            ok('summary_needs_permissions', '통화 기록 허용이 필요해요' in t and '문자 읽기 허용이 필요해요' in t, t)
            await shot(pg, '14_summary_needs_perms')
            await pg.context.close()
            pg = await page({'view': 'summary', 'pending': {'id': 'drive', 'start': now - 40 * 60000, 'end': now - 1000}, 'noCalls': True})
            ok('summary_no_missed', '놓친 전화가 없어요' in await pg.inner_text('#smList'))
            await pg.context.close()

            # 7) 설정 화면에 진입 줄 + 설정 → 모드 → 뒤로
            pg = await page({})
            await pg.evaluate("__nvp.show('settings')"); await pg.wait_for_timeout(200)
            ok('settings_has_modes_row', '상황 모드' in await pg.inner_text('#setList'))
            await pg.click('#setList [data-b=modes]'); await pg.wait_for_timeout(250)
            await pg.click('#mdBack'); await pg.wait_for_timeout(200)
            ok('modes_back_to_settings', await cur(pg) == 'settings', await cur(pg))
            await pg.click('#setBack'); await pg.wait_for_timeout(200)
            ok('settings_back_not_to_modes', await cur(pg) != 'modes' and await cur(pg) != 'settings', await cur(pg))
            await pg.context.close()

            # 8) 규칙: 이모지 · 주황 · '유니' · 좌우 넘침 (작은 화면 포함)
            for (w, h) in [(390, 844), (360, 720)]:
                pg = await page({'active': {'id': 'meeting', 'start': now - 5 * 60000, 'end': now + 25 * 60000, 'dnd': True, 'keep': False, 'sent': 1}, 'perms': {'dnd': True, 'sms': True}}, w, h)
                await pg.evaluate("__nvp.show('modes')"); await pg.click('.md[data-id=drive] [data-md=open]'); await pg.wait_for_timeout(250)
                ov = await pg.evaluate("[...document.querySelectorAll('#v-modes *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1&&getComputedStyle(e).position!=='fixed').map(e=>e.className||e.tagName).slice(0,5)")
                sw = await pg.evaluate("document.querySelector('#mdList').scrollWidth<=document.querySelector('#mdList').clientWidth+1")
                ok(f'no_horizontal_overflow_{w}', not ov and sw, ov)
                if w == 360: await shot(pg, '15_modes_small_screen')
                await pg.evaluate("__nvp.show('summary')"); await pg.wait_for_timeout(200)
                ov = await pg.evaluate("document.querySelector('#smList').scrollWidth<=document.querySelector('#smList').clientWidth+1")
                ok(f'summary_no_overflow_{w}', ov)
                await pg.context.close()
            html = open(os.path.join(ROOT, 'www', 'index.html'), encoding='utf-8').read()
            emoji = re.findall('[\U0001F300-\U0001FAFF\u2600-\u26FF\u2700-\u27BF\U0001F000-\U0001F2FF]', html)
            ok('no_emoji_in_source', not emoji, emoji[:5])
            ok('no_yuni_word', not re.search('유니(?!버스)', html))
            ok('says_newniverse', '뉴니버스' in html)
            orange = re.findall(r'#(?:f[a-f0-9]{1}[5-9a]|ff[5-9a-f])[0-9a-f]{3}\b|orange', html, re.I)
            hues = []
            for m in re.finditer(r'#([0-9a-fA-F]{6})\b', html):
                r, g, b = [int(m.group(1)[i:i+2], 16) for i in (0, 2, 4)]
                mx, mn = max(r, g, b), min(r, g, b)
                if mx - mn > 60 and mx == r and g > b + 30 and g > 90 and r > 200 and g < r: hues.append(m.group(0))
            ok('no_orange_colors', not hues, hues)
            await br.close()
    finally:
        srv.terminate()
    bad = [k for k, v in res.items() if not v]
    print(f'\n{len(res)-len(bad)}/{len(res)} PASS', 'FAILED: ' + ', '.join(bad) if bad else 'ALL OK')
    raise SystemExit(1 if bad else 0)

asyncio.run(main())

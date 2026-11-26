#!/usr/bin/env python3
"""v3 UI: empty vs error vs permission, diagnostic panel. No message bodies asserted from diag."""
import asyncio, json, subprocess, time, os
from playwright.async_api import async_playwright

PORT = 18463
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
  status:()=>JSON.stringify({def:!!cfg.def,canRole:true,perms,sdk:36,ver:cfg.ver||'3-test',now}),
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
  getMessages:()=>'[]',getPart:()=>'',search:()=>'[]',getContacts:()=>'[]',
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
    print(f"Playwright v3: {sum(res.values())} passed, {len(failed)} failed")
    if failed:
        print('FAILED', failed)
        raise SystemExit(1)

def Date_now_plus():
    import time
    return int(time.time()*1000)

if __name__=='__main__':
    asyncio.run(main())

package kr.newniverse.phone;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

/** 키패드 · 최근 통화 · 검색 · 설정 (하이브리드: 화면은 assets/www/index.html) */
public class MainActivity extends Activity {
  static final int RQ_ROLE=7, RQ_PERM=8, RQ_MIC=9, RQ_CARD=10, RQ_CONTACTS=11, RQ_CAL=12;
  static final String ACTION_RECENT="kr.newniverse.phone.RECENT", ACTION_ADD="kr.newniverse.phone.ADD", ACTION_MODES="kr.newniverse.phone.MODES", ACTION_MEMOS="kr.newniverse.phone.MEMOS";
  WebView w; String pendingDial, pendingView; boolean loaded;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    Nv.channels(this);
    take(getIntent());
    w=new WebView(this); setContentView(w);
    Nv.setupWeb(this,w,new Bridge(this),"file:///android_asset/www/index.html?mode=main");
    loaded=true;
  }
  /** tel: 번호로 열렸으면 키패드에 채움 */
  private void take(Intent i){
    if(i==null) return; String a=i.getAction();
    if(Intent.ACTION_DIAL.equals(a)||Intent.ACTION_VIEW.equals(a)){
      Uri u=i.getData(); String n=(u!=null&&"tel".equals(u.getScheme()))?u.getSchemeSpecificPart():"";
      pendingDial=n==null?"":n; pendingView="keypad";
    } else if(ACTION_RECENT.equals(a)) pendingView="recent";
    else if(ACTION_ADD.equals(a)){ pendingView="keypad"; pendingDial=""; }
    else if(ACTION_MEMOS.equals(a)){ String n=i.getStringExtra("num"); pendingView=n==null||n.isEmpty()?"memos":"memo:"+n; }
    else if(ACTION_MODES.equals(a)){   // 상황 모드 화면 · 끝난 뒤 요약 · (런처 말 명령이 허용이 없어 못 켰을 때) 허용 안내
      String v=i.getStringExtra("view"); pendingView="summary".equals(v)?"summary":"modes";
      String need=i.getStringExtra("need"), mode=i.getStringExtra("mode");
      if(need!=null&&ModeDefs.valid(mode)) pendingView="modes:"+need+":"+mode;
    }
  }
  @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); take(i); js("window.NV_intent&&NV_intent()"); }
  /** 화면을 보고 있는 동안 통화 기록이 바뀌면(차단된 전화 등) 최근 통화를 새로 그림 */
  final android.os.Handler logH=new android.os.Handler(android.os.Looper.getMainLooper());
  final Runnable logR=()->js("window.NV_log&&NV_log()");
  final android.database.ContentObserver logObs=new android.database.ContentObserver(logH){ @Override public void onChange(boolean self){ logH.removeCallbacks(logR); logH.postDelayed(logR,1200); } };
  boolean logReg;
  @Override protected void onPause(){ super.onPause(); if(logReg){ try{ getContentResolver().unregisterContentObserver(logObs); }catch(Exception e){} logReg=false; } logH.removeCallbacks(logR); }
  @Override protected void onResume(){ super.onResume(); if(!logReg){ try{ getContentResolver().registerContentObserver(android.provider.CallLog.Calls.CONTENT_URI,true,logObs); logReg=true; }catch(Exception e){} } try{ ModeManager.tick(this); }catch(Exception e){} try{ CalWatch.reschedule(this); }catch(Exception e){} js("window.NV_resume&&NV_resume()"); }
  @Override protected void onActivityResult(int rq,int rs,Intent d){
    super.onActivityResult(rq,rs,d);
    if(rq==RQ_CARD){ js("window.NV_card&&NV_card("+cardResult(rs,d)+")"); return; }
    js("window.NV_resume&&NV_resume("+(rq==RQ_ROLE?"'role',"+(rs==RESULT_OK):"''")+")");
  }
  /** 명함 글자 줄 → 칸 나누기 (사진은 :ocr 쪽에서 이미 지움) */
  static String cardResult(int rs,Intent d){
    try{
      String lines=d==null?null:d.getStringExtra("lines");
      if(rs!=RESULT_OK||lines==null){ org.json.JSONObject o=new org.json.JSONObject(); o.put("ok",false); o.put("err",d==null?"cancel":String.valueOf(d.getStringExtra("err"))); return o.toString(); }
      org.json.JSONObject o=CardParser.toJson(CardParser.parse(CardParser.linesFromJson(lines))); o.put("ok",true); return o.toString();
    }catch(Exception e){ return "{ok:false,err:'parse'}"; }
  }
  @Override public void onRequestPermissionsResult(int rq,String[] p,int[] g){
    boolean ok=g!=null&&g.length>0; if(g!=null) for(int x:g) if(x!=android.content.pm.PackageManager.PERMISSION_GRANTED) ok=false;
    if(rq==RQ_MIC){ js("window.NV_perm&&NV_perm('mic',"+ok+")"); return; }
    if(rq==RQ_CONTACTS){ js("window.NV_perm&&NV_perm('contacts',"+ok+")"); return; }
    if(rq==RQ_CAL){ if(ok) CalWatch.reschedule(this); js("window.NV_perm&&NV_perm('cal',"+ok+")"); return; }
    js("window.NV_resume&&NV_resume('perm')");
  }
  @SuppressWarnings("deprecation")
  @Override public void onBackPressed(){
    if(w==null){ super.onBackPressed(); return; }
    w.evaluateJavascript("window.NV_back?NV_back():false",r->{ if(!"true".equals(r)) moveTaskToBack(true); });
  }
  void js(String s){ if(w!=null) w.post(()->w.evaluateJavascript(s,null)); }
  static void jsResume(Activity a){ if(a instanceof MainActivity) ((MainActivity)a).js("window.NV_resume&&NV_resume('perm')"); }
  @Override protected void onDestroy(){ if(w!=null){ w.destroy(); w=null; } super.onDestroy(); }
}

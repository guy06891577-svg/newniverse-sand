package kr.newniverse.sms;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

/** 대화 목록 · 대화 · 새 문자 · 설정 (하이브리드: 화면은 assets/www/index.html) */
public class MainActivity extends Activity {
  static final int RQ_ROLE=7, RQ_PERM=8, RQ_CAL=9; boolean calOn;
  static volatile MainActivity live; static volatile boolean resumed; static volatile String viewing="";
  WebView w; String pendingAddr, pendingBody;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    Nv.channels(this); take(getIntent());
    w=new WebView(this); setContentView(w);
    Nv.setupWeb(this,w,new Bridge(this),"file:///android_asset/www/index.html");
    live=this;
  }
  /** smsto:번호, sms_body / 알림에서 열기 */
  private void take(Intent i){
    if(i==null) return; String a=i.getAction();
    if(Intent.ACTION_SENDTO.equals(a)||Intent.ACTION_SEND.equals(a)||Intent.ACTION_VIEW.equals(a)){
      Uri u=i.getData(); String n="";
      if(u!=null){ n=u.getSchemeSpecificPart(); if(n==null) n=""; int q=n.indexOf('?'); if(q>=0) n=n.substring(0,q); }
      String body=i.getStringExtra("sms_body"); if(body==null) body=i.getStringExtra(Intent.EXTRA_TEXT);
      pendingAddr=n.isEmpty()?"*":n; pendingBody=body==null?"":body;
    } else if(i.getBooleanExtra("calperm",false)){ i.removeExtra("calperm"); getWindow().getDecorView().post(()->reqCal(false));
    } else if(Nv.ACTION_OPEN.equals(a)){ pendingAddr=i.getStringExtra("addr"); pendingBody=""; }
  }
  @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); take(i); js("window.NV_intent&&NV_intent()"); }
  @Override protected void onResume(){ super.onResume(); live=this; resumed=true; js("window.NV_resume&&NV_resume()"); }
  @Override protected void onPause(){ resumed=false; super.onPause(); }
  @Override protected void onActivityResult(int rq,int rs,Intent d){ super.onActivityResult(rq,rs,d); js("window.NV_resume&&NV_resume("+(rq==RQ_ROLE?"'role',"+(rs==RESULT_OK):"''")+")"); }
  /** 달력 권한(읽기·쓰기)을 한 번만 묻는다. turnOn 이면 허용되면 자동 넣기를 켬 */
  void reqCal(boolean turnOn){ calOn=turnOn; if(AutoCal.perm(this)){ if(turnOn) AutoCal.setOn(this,true); js("window.NV_cal&&NV_cal()"); return; }
    requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},RQ_CAL); }
  @Override public void onRequestPermissionsResult(int rq,String[] p,int[] g){
    if(rq==RQ_CAL){ boolean ok=g.length>0; for(int x:g) if(x!=android.content.pm.PackageManager.PERMISSION_GRANTED) ok=false; if(calOn) AutoCal.setOn(this,ok); calOn=false; js("window.NV_cal&&NV_cal()"); return; }
    js("window.NV_resume&&NV_resume('perm')"); }
  @SuppressWarnings("deprecation")
  @Override public void onBackPressed(){
    if(w==null){ super.onBackPressed(); return; }
    w.evaluateJavascript("window.NV_back?NV_back():false",r->{ if(!"true".equals(r)) moveTaskToBack(true); });
  }
  void js(String s){ if(w!=null) w.post(()->{ if(w!=null) w.evaluateJavascript(s,null); }); }
  /** 새 문자가 오거나 보낸 결과가 나오면 화면을 새로 고침 */
  static void refresh(){ MainActivity m=live; if(m!=null) m.js("window.NV_refresh&&NV_refresh()"); }
  @Override protected void onDestroy(){ if(live==this) live=null; if(w!=null){ w.destroy(); w=null; } super.onDestroy(); }
}

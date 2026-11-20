package kr.newniverse.phone;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebView;

/** 키패드 · 최근 통화 · 검색 · 설정 (하이브리드: 화면은 assets/www/index.html) */
public class MainActivity extends Activity {
  static final int RQ_ROLE=7, RQ_PERM=8;
  static final String ACTION_RECENT="kr.newniverse.phone.RECENT", ACTION_ADD="kr.newniverse.phone.ADD";
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
  }
  @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); take(i); js("window.NV_intent&&NV_intent()"); }
  @Override protected void onResume(){ super.onResume(); js("window.NV_resume&&NV_resume()"); }
  @Override protected void onActivityResult(int rq,int rs,Intent d){ super.onActivityResult(rq,rs,d); js("window.NV_resume&&NV_resume("+(rq==RQ_ROLE?"'role'":"''")+")"); }
  @Override public void onRequestPermissionsResult(int rq,String[] p,int[] g){ js("window.NV_resume&&NV_resume('perm')"); }
  @SuppressWarnings("deprecation")
  @Override public void onBackPressed(){
    if(w==null){ super.onBackPressed(); return; }
    w.evaluateJavascript("window.NV_back?NV_back():false",r->{ if(!"true".equals(r)) moveTaskToBack(true); });
  }
  void js(String s){ if(w!=null) w.post(()->w.evaluateJavascript(s,null)); }
  static void jsResume(Activity a){ if(a instanceof MainActivity) ((MainActivity)a).js("window.NV_resume&&NV_resume('perm')"); }
  @Override protected void onDestroy(){ if(w!=null){ w.destroy(); w=null; } super.onDestroy(); }
}

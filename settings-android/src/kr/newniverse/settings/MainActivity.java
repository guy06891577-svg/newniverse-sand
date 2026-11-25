package kr.newniverse.settings;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

/** 뉴니버스 설정 (하이브리드: 화면은 assets/www/index.html) */
public class MainActivity extends Activity {
  WebView w;
  Bridge br;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    w=new WebView(this); setContentView(w);
    br=new Bridge(this);
    Nv.setupWeb(this,w,br,"file:///android_asset/www/index.html");
  }
  @Override protected void onResume(){ super.onResume(); js("window.NV_resume&&NV_resume()"); }
  @SuppressWarnings("deprecation")
  @Override public void onBackPressed(){
    if(w==null){ super.onBackPressed(); return; }
    w.evaluateJavascript("window.NV_back?NV_back():false",r->{ if(!"true".equals(r)) moveTaskToBack(true); });
  }
  void js(String s){ if(w!=null) w.post(()->{ if(w!=null) w.evaluateJavascript(s,null); }); }
  @Override protected void onDestroy(){ if(br!=null) br.close(); if(w!=null){ w.destroy(); w=null; } super.onDestroy(); }
}

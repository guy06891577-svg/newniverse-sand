package kr.newniverse.calc;

import android.app.Activity;
import android.webkit.*;

/** 공용 도구: WebView 설정, 상태 표시줄·내비게이션 바 높이 */
final class Nv {
  static final String VER="3-test";

  static void setupWeb(Activity a,WebView w,Object bridge,String url){
    WebSettings s=w.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setTextZoom(100);
    s.setAllowFileAccess(false); s.setAllowContentAccess(false);
    w.setBackgroundColor(0xFF05040F);
    w.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    w.addJavascriptInterface(bridge,"NVCalc");
    w.setWebChromeClient(new WebChromeClient());
    final int[] ins={0,0};
    w.setOnApplyWindowInsetsListener((v,wi)->{ float d=v.getResources().getDisplayMetrics().density;
      ins[0]=Math.round(wi.getSystemWindowInsetTop()/d); ins[1]=Math.round(wi.getSystemWindowInsetBottom()/d); pushInsets((WebView)v,ins); return v.onApplyWindowInsets(wi); });
    w.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){ return !"file".equals(r.getUrl().getScheme()); }
      @Override public void onPageFinished(WebView v,String u){ pushInsets(v,ins); }
    });
    w.loadUrl(url);
  }
  static void pushInsets(WebView w,int[] ins){ if(ins[0]<=0&&ins[1]<=0) return; int t=Math.max(0,ins[0]), b=Math.max(0,ins[1]);
    t=Math.min(t,120); b=Math.min(b,120);
    w.evaluateJavascript("(function(){var r=document.documentElement.style;r.setProperty('--sat','max(env(safe-area-inset-top,0px), "+t+"px)');r.setProperty('--sab','max(env(safe-area-inset-bottom,0px), "+b+"px)')})()",null); }
}

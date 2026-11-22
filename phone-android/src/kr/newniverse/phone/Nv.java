package kr.newniverse.phone;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telecom.TelecomManager;
import android.webkit.*;

/** 공용 도구: 알림 채널, 권한·기본 앱 확인, 연락처 이름 찾기, 번호 모양, WebView 설정 */
final class Nv {
  static final String CH_IN="incoming2", CH_ON="ongoing", CH_MISS="missed";
  static final String VER="4-test";

  static void channels(Context c){
    NotificationManager nm=c.getSystemService(NotificationManager.class);
    if(nm==null)return;
    // 벨소리·진동은 시스템(Telecom)이 울림 → 알림 채널은 소리 없음 (두 번 울리지 않게)
    NotificationChannel a=new NotificationChannel(CH_IN,"걸려오는 전화",NotificationManager.IMPORTANCE_HIGH);
    a.setSound(null,null); a.enableVibration(false); a.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
    nm.createNotificationChannel(a);
    NotificationChannel b=new NotificationChannel(CH_ON,"통화 중",NotificationManager.IMPORTANCE_LOW);
    b.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); nm.createNotificationChannel(b);
    nm.createNotificationChannel(new NotificationChannel(CH_MISS,"부재중 전화",NotificationManager.IMPORTANCE_DEFAULT));
  }

  static boolean has(Context c,String p){ return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED; }

  static boolean isDefault(Context c){
    try{
      if(Build.VERSION.SDK_INT>=29){ RoleManager rm=c.getSystemService(RoleManager.class); if(rm!=null) return rm.isRoleHeld(RoleManager.ROLE_DIALER); }
      TelecomManager tm=c.getSystemService(TelecomManager.class);
      return tm!=null&&c.getPackageName().equals(tm.getDefaultDialerPackage());
    }catch(Exception e){ return false; }
  }

  static Intent roleIntent(Context c){
    if(Build.VERSION.SDK_INT>=29){ RoleManager rm=c.getSystemService(RoleManager.class); if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER); return null; }
    return new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,c.getPackageName());
  }

  static String lookupName(Context c,String num){
    if(num==null||num.isEmpty()||!has(c,android.Manifest.permission.READ_CONTACTS)) return null;
    Uri u=Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(num));
    try(Cursor q=c.getContentResolver().query(u,new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME},null,null,null)){
      if(q!=null&&q.moveToFirst()) return q.getString(0);
    }catch(Exception e){}
    return null;
  }

  /** 한국 번호 모양: 010-1234-5678, 02-123-4567, 1588-0000 */
  static String fmt(String n){
    if(n==null) return "";
    String d=n.replaceAll("[^0-9]","");
    if(n.trim().startsWith("+82")&&d.startsWith("82")) d="0"+d.substring(2);
    int L=d.length();
    if(L==0) return n;
    if(d.startsWith("02")&&L>=9&&L<=10) return d.substring(0,2)+"-"+d.substring(2,L-4)+"-"+d.substring(L-4);
    if(d.matches("1[5-9]\\d{6}")) return d.substring(0,4)+"-"+d.substring(4);
    if(d.startsWith("0")&&(L==10||L==11)) return d.substring(0,3)+"-"+d.substring(3,L-4)+"-"+d.substring(L-4);
    return d;
  }

  static String esc(String s){ return org.json.JSONObject.quote(s==null?"":s); }

  static void setupWeb(Activity a,WebView w,Object bridge,String url){
    WebSettings s=w.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setTextZoom(100);
    s.setAllowFileAccess(false); s.setAllowContentAccess(false);
    s.setMediaPlaybackRequiresUserGesture(false);
    w.setBackgroundColor(0xFF05040F);
    w.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    w.addJavascriptInterface(bridge,"NVPhone");
    w.setWebChromeClient(new WebChromeClient());
    // v4: 화면이 상태 표시줄·내비게이션 바 뒤까지 그려지는 폰이면 그 높이를 CSS(--sat/--sab)로 알려 줌. 보통은 0이라 아무 변화 없음.
    final int[] ins={0,0};
    w.setOnApplyWindowInsetsListener((v,wi)->{ float d=v.getResources().getDisplayMetrics().density;
      ins[0]=Math.round(wi.getSystemWindowInsetTop()/d); ins[1]=Math.round(wi.getSystemWindowInsetBottom()/d); pushInsets((WebView)v,ins); return v.onApplyWindowInsets(wi); });
    w.setWebViewClient(new WebViewClient(){
      @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){ return !"file".equals(r.getUrl().getScheme()); }
      @Override public void onPageFinished(WebView v,String u){ pushInsets(v,ins); }
    });
    w.loadUrl(url);
  }
  static void pushInsets(WebView w,int[] ins){ if(ins[0]<=0&&ins[1]<=0) return; int t=Math.max(0,Math.min(ins[0],120)), b=Math.max(0,Math.min(ins[1],120));
    w.evaluateJavascript("(function(){var r=document.documentElement.style;r.setProperty('--sat','max(env(safe-area-inset-top,0px), "+t+"px)');r.setProperty('--sab','max(env(safe-area-inset-bottom,0px), "+b+"px)')})()",null); }
}

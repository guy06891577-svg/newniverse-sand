package kr.newniverse.sms;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.webkit.*;
import java.util.ArrayList;

/** 공용 도구: 알림, 권한·기본 문자 앱 확인, 이름 찾기, 번호 모양, 문자 보내기·저장, WebView 설정 */
final class Nv {
  static final String CH_MSG="sms", VER="11-test";
  static final String ACTION_OPEN="kr.newniverse.sms.OPEN", ACTION_MONEY="kr.newniverse.sms.MONEY";

  static void channels(Context c){
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    NotificationChannel ch=new NotificationChannel(CH_MSG,"받은 문자",NotificationManager.IMPORTANCE_HIGH);
    ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE); ch.enableVibration(true);
    nm.createNotificationChannel(ch);
  }
  static boolean has(Context c,String p){ return c.checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED; }

  static boolean isDefault(Context c){
    try{
      if(Build.VERSION.SDK_INT>=29){ RoleManager rm=c.getSystemService(RoleManager.class); if(rm!=null) return rm.isRoleHeld(RoleManager.ROLE_SMS); }
      return c.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(c));
    }catch(Exception e){ return false; }
  }
  static Intent roleIntent(Context c){
    if(Build.VERSION.SDK_INT>=29){ RoleManager rm=c.getSystemService(RoleManager.class); if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_SMS)) return rm.createRequestRoleIntent(RoleManager.ROLE_SMS); return null; }
    return new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,c.getPackageName());
  }

  static String lookupName(Context c,String num){
    if(num==null||num.isEmpty()||!has(c,Manifest.permission.READ_CONTACTS)) return null;
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
    int L=d.length(); if(L==0) return n;
    if(d.startsWith("02")&&L>=9&&L<=10) return d.substring(0,2)+"-"+d.substring(2,L-4)+"-"+d.substring(L-4);
    if(d.matches("1[5-9]\\d{6}")) return d.substring(0,4)+"-"+d.substring(4);
    if(d.startsWith("0")&&(L==10||L==11)) return d.substring(0,3)+"-"+d.substring(3,L-4)+"-"+d.substring(L-4);
    return d;
  }
  static String digits(String n){ return n==null?"":n.replaceAll("[^0-9+*#]",""); }
  static int notifId(String addr){ String d=digits(addr); if(d.startsWith("+82")) d="0"+d.substring(3); return 1000+(d.hashCode()&0x7ffff); }

  static SmsManager smsManager(Context c){
    if(Build.VERSION.SDK_INT>=31){ SmsManager m=c.getSystemService(SmsManager.class); if(m!=null) return m; }
    return SmsManager.getDefault();
  }

  /** 문자 보내기. 기본 앱이면 직접 '보내는 중'으로 저장했다가 결과에 따라 '보냄/실패'로 바꿈. 아니면 시스템이 저장. 반환 "ok"|"perm"|"empty"|"err" */
  static String send(Context c,String addr,String text){
    String to=digits(addr); if(to.isEmpty()||text==null||text.trim().isEmpty()) return "empty";
    if(!has(c,Manifest.permission.SEND_SMS)) return "perm";
    if(MmsLogic.useLms(segments(text))) return MmsOutbox.send(c,to,text); // v9: 한 통을 넘으면 장문(LMS) 한 통으로
    try{
      Uri row=null;
      if(isDefault(c)){
        ContentValues v=new ContentValues(); v.put(Telephony.Sms.ADDRESS,to); v.put(Telephony.Sms.BODY,text);
        v.put(Telephony.Sms.DATE,System.currentTimeMillis()); v.put(Telephony.Sms.READ,1); v.put(Telephony.Sms.SEEN,1);
        try{ row=c.getContentResolver().insert(Telephony.Sms.Outbox.CONTENT_URI,v); }catch(Exception e){}
      }
      SmsManager m=smsManager(c);
      ArrayList<String> parts=m.divideMessage(text);
      ArrayList<PendingIntent> sent=new ArrayList<>();
      int rq=(int)(System.nanoTime()&0x3fffffff);
      for(int i=0;i<parts.size();i++){
        Intent si=new Intent(c,SentReceiver.class).setAction("kr.newniverse.sms.SENT."+rq+"."+i)
          .putExtra("row",row==null?null:row.toString()).putExtra("last",i==parts.size()-1);
        sent.add(PendingIntent.getBroadcast(c,rq+i,si,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_ONE_SHOT));
      }
      if(parts.size()>1) m.sendMultipartTextMessage(to,null,parts,sent,null);
      else m.sendTextMessage(to,null,text,sent.get(0),null);
      return "ok";
    }catch(Exception e){ return "err"; }
  }

  /** 문자 몇 통으로 나뉘는지(폰 계산, 안 되면 직접 계산) */
  static int segments(String text){
    try{ int[] r=android.telephony.SmsMessage.calculateLength(text==null?"":text,false); if(r!=null&&r.length>0&&r[0]>0) return r[0]; }catch(Throwable e){}
    return MmsLogic.smsSegments(text);
  }
  static void setupWeb(Activity a,WebView w,Object bridge,String url){
    WebSettings s=w.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setTextZoom(100);
    s.setAllowFileAccess(false); s.setAllowContentAccess(false);
    w.setBackgroundColor(0xFF05040F);
    w.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    w.addJavascriptInterface(bridge,"NVSms");
    w.setWebChromeClient(new WebChromeClient());
    // 상태 표시줄·내비게이션 바 높이를 CSS(--sat/--sab)로 알려 줌 (전화 v4와 같은 방식)
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

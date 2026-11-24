package kr.newniverse.calc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.webkit.JavascriptInterface;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 화면(JS) ↔ 안드로이드 다리. JS에서는 window.NVCalc.이름() */
class Bridge {
  final Activity a;
  static final Set<String> HOSTS=new HashSet<>(Arrays.asList(
    "open.er-api.com","latest.currency-api.pages.dev","cdn.jsdelivr.net"
  ));
  Bridge(Activity a){ this.a=a; }

  @JavascriptInterface public String version(){ return Nv.VER; }

  @JavascriptInterface public void copy(String text){
    a.runOnUiThread(()->{
      try{ ClipboardManager cm=a.getSystemService(ClipboardManager.class); cm.setPrimaryClip(ClipData.newPlainText("계산",text==null?"":text)); }catch(Exception e){}
    });
  }

  @JavascriptInterface public void vibrate(int ms){
    try{ Vibrator v=a.getSystemService(Vibrator.class); if(v!=null) v.vibrate(VibrationEffect.createOneShot(Math.max(5,Math.min(ms,60)),VibrationEffect.DEFAULT_AMPLITUDE)); }catch(Exception e){}
  }

  /** 허용된 주소만 HTTPS로 읽음. 성공 시 본문, 실패 시 "ERR:"+이유 */
  @JavascriptInterface public String httpGet(String url){
    HttpURLConnection c=null;
    try{
      if(url==null) return "ERR:empty";
      URI u=URI.create(url);
      if(!"https".equalsIgnoreCase(u.getScheme())) return "ERR:scheme";
      String host=u.getHost(); if(host==null) return "ERR:host";
      host=host.toLowerCase();
      if(!HOSTS.contains(host)) return "ERR:host";
      c=(HttpURLConnection)u.toURL().openConnection();
      c.setInstanceFollowRedirects(true);
      c.setConnectTimeout(10000); c.setReadTimeout(15000);
      c.setRequestMethod("GET");
      c.setRequestProperty("Accept","application/json");
      c.setRequestProperty("User-Agent","NewniverseCalc/1");
      int code=c.getResponseCode();
      InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
      ByteArrayOutputStream out=new ByteArrayOutputStream();
      if(in!=null){ byte[] buf=new byte[4096]; int n; while((n=in.read(buf))>0) out.write(buf,0,n); }
      String body=new String(out.toByteArray(),StandardCharsets.UTF_8);
      if(code<200||code>=300) return "ERR:http "+code;
      return body;
    }catch(Exception e){ return "ERR:net"; }
    finally{ if(c!=null) c.disconnect(); }
  }
}

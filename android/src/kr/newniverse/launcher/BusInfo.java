package kr.newniverse.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

// v28 가까운 정류장 버스 도착 정보 (국토교통부 TAGO 공공 API)
// - 위치는 런처 화면이 떠 있을 때 웹이 부를 때만 한 번씩 읽음 (계속 추적하지 않음)
// - 인증키는 이 폰 안(SharedPreferences)에만 저장하고 웹 쪽에는 가린 모양만 돌려줌
// - 주소는 apis.data.go.kr 두 가지 조회로만 고정 (아무 주소나 부르는 통로가 되지 않게)
final class BusInfo {
  static final String FINE=android.Manifest.permission.ACCESS_FINE_LOCATION, COARSE=android.Manifest.permission.ACCESS_COARSE_LOCATION;
  static final String BASE="https://apis.data.go.kr/1613000/";
  interface Cb { void done(String js); }

  static SharedPreferences sp(Context c){ return c.getSharedPreferences("nvbus",Context.MODE_PRIVATE); }
  static String perm(Context c){
    if(c.checkSelfPermission(FINE)==PackageManager.PERMISSION_GRANTED) return "fine";
    if(c.checkSelfPermission(COARSE)==PackageManager.PERMISSION_GRANTED) return "coarse";
    return "none";
  }
  static void setKey(Context c,String k){ k=k==null?"":k.trim().replaceAll("\\s+",""); if(k.length()>400) k=k.substring(0,400); sp(c).edit().putString("k",k).apply(); }
  static String key(Context c){ return sp(c).getString("k",""); }
  static String keyInfo(Context c){ String k=key(c); if(k.isEmpty()) return ""; int n=k.length(); return (n>8?k.substring(0,4)+"…"+k.substring(n-4):"••••")+" ("+n+"자)"; }

  static String locJson(Location l){ if(l==null) return "null";
    return "{\"lat\":"+l.getLatitude()+",\"lng\":"+l.getLongitude()+",\"acc\":"+(l.hasAccuracy()?Math.round(l.getAccuracy()):-1)+",\"age\":"+Math.max(0,(System.currentTimeMillis()-l.getTime())/1000)+"}"; }

  @SuppressWarnings({"deprecation","MissingPermission"})
  static void locate(final Activity a,final Handler ui,final Cb cb){
    if("none".equals(perm(a))){ cb.done("{\"err\":\"perm\"}"); return; }
    final LocationManager lm=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);
    if(lm==null){ cb.done("{\"err\":\"nolm\"}"); return; }
    boolean gps=false,net=false; try{ gps=lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception e){} try{ net=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}catch(Exception e){}
    if(!gps&&!net){ cb.done("{\"err\":\"off\"}"); return; }
    Location best=null;
    for(String p:lm.getProviders(true)){ try{ Location l=lm.getLastKnownLocation(p); if(l!=null&&(best==null||l.getTime()>best.getTime())) best=l; }catch(Exception e){} }
    if(best!=null&&System.currentTimeMillis()-best.getTime()<60000&&(!best.hasAccuracy()||best.getAccuracy()<150)){ cb.done(locJson(best)); return; }
    final Location fb=best; final boolean[] sent={false};
    final String prov=net?LocationManager.NETWORK_PROVIDER:LocationManager.GPS_PROVIDER;
    final LocationListener ll=new LocationListener(){
      public void onLocationChanged(Location l){ if(sent[0]) return; sent[0]=true; try{ lm.removeUpdates(this);}catch(Exception e){} cb.done(locJson(l)); }
      public void onStatusChanged(String p,int s,Bundle b){} public void onProviderEnabled(String p){} public void onProviderDisabled(String p){} };
    try{ lm.requestSingleUpdate(prov,ll,Looper.getMainLooper()); }catch(Exception e){ sent[0]=true; cb.done(fb!=null?locJson(fb):"{\"err\":\"req\"}"); return; }
    ui.postDelayed(()->{ if(sent[0]) return; sent[0]=true; try{ lm.removeUpdates(ll);}catch(Exception e){} cb.done(fb!=null?locJson(fb):"{\"err\":\"timeout\"}"); },20000);
  }

  static boolean safe(String s){ return s!=null&&s.matches("[A-Za-z0-9.\\-_]{1,40}"); }
  // op: near(위도,경도) / arr(도시코드,정류소ID)
  static void fetch(final Context c,final String op,final String p1,final String p2,final Cb cb){
    new Thread(()->{ int code=0; String body="";
      try{
        String k=key(c); if(k.isEmpty()){ cb.done("0,'nokey'"); return; }
        if(!safe(p1)||!safe(p2)){ cb.done("0,'bad'"); return; }
        String ek=k.contains("%")?k:URLEncoder.encode(k,"UTF-8");
        String u;
        if("near".equals(op)) u=BASE+"BusSttnInfoInqireService/getCrdntPrxmtSttnList?serviceKey="+ek+"&gpsLati="+p1+"&gpsLong="+p2+"&numOfRows=40&pageNo=1&_type=json";
        else if("arr".equals(op)) u=BASE+"ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList?serviceKey="+ek+"&cityCode="+p1+"&nodeId="+p2+"&numOfRows=60&pageNo=1&_type=json";
        else { cb.done("0,'bad'"); return; }
        HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection(); h.setConnectTimeout(8000); h.setReadTimeout(10000); h.setRequestProperty("Accept","application/json");
        code=h.getResponseCode(); InputStream in=code>=400?h.getErrorStream():h.getInputStream();
        ByteArrayOutputStream bo=new ByteArrayOutputStream(); if(in!=null){ byte[] buf=new byte[8192]; int n,t=0; while((n=in.read(buf))>0&&t<600000){ bo.write(buf,0,n); t+=n; } in.close(); }
        body=bo.toString("UTF-8"); h.disconnect();
      }catch(Exception e){ code=-1; body=String.valueOf(e.getClass().getSimpleName()); }
      String k2=key(c); if(!k2.isEmpty()) body=body.replace(k2,"(키)");
      cb.done(code+","+org.json.JSONObject.quote(body));
    }).start();
  }
}

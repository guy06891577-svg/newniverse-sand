package kr.newniverse.settings;

import android.app.Activity;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 화면(JS) ↔ 안드로이드 다리. JS에서는 window.NVSet.이름().
 * 모든 메서드는 예외를 밖으로 던지지 않고, 실패하면 문자열 코드("err" 등)나 기본값을 돌려준다.
 */
class Bridge {
  final Activity a;
  final Handler main=new Handler(Looper.getMainLooper());
  CameraManager cam; String camId; volatile boolean torchOn=false; CameraManager.TorchCallback tcb;

  static final String[][] NV_APPS={
    {"launcher","뉴니버스 런처","kr.newniverse.launcher"},
    {"phone","뉴니버스 전화","kr.newniverse.phone"},
    {"sms","뉴니버스 문자","kr.newniverse.sms"},
    {"calc","뉴니버스 계산기","kr.newniverse.calc"}
  };

  Bridge(Activity a){
    this.a=a;
    try{
      cam=(CameraManager)a.getSystemService(Context.CAMERA_SERVICE);
      if(cam!=null){
        for(String id: cam.getCameraIdList()){
          CameraCharacteristics c=cam.getCameraCharacteristics(id);
          Boolean f=c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
          Integer face=c.get(CameraCharacteristics.LENS_FACING);
          if(Boolean.TRUE.equals(f) && face!=null && face==CameraCharacteristics.LENS_FACING_BACK){ camId=id; break; }
        }
        if(camId!=null){
          tcb=new CameraManager.TorchCallback(){
            @Override public void onTorchModeChanged(String id,boolean enabled){ if(id.equals(camId)) torchOn=enabled; }
          };
          cam.registerTorchCallback(tcb,main);
        }
      }
    }catch(Throwable e){ camId=null; }
  }
  void close(){ try{ if(cam!=null&&tcb!=null) cam.unregisterTorchCallback(tcb); }catch(Throwable e){} }

  @JavascriptInterface public String version(){ return Nv.VER; }

  @JavascriptInterface public void vibrate(int ms){
    try{ Vibrator v=a.getSystemService(Vibrator.class); if(v!=null) v.vibrate(VibrationEffect.createOneShot(Math.max(5,Math.min(ms,60)),VibrationEffect.DEFAULT_AMPLITUDE)); }catch(Throwable e){}
  }

  /* ---------- 읽기: 권한 없이 가능한 값만 ---------- */

  /** 위쪽 카드용: 배터리 %, 충전 중, 저장공간(바이트), 기종, 안드로이드 버전 */
  @JavascriptInterface public String info(){
    JSONObject o=new JSONObject();
    try{
      Intent b=a.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      if(b!=null){
        int lv=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1), sc=b.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        int st=b.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
        if(lv>=0&&sc>0) o.put("bat",Math.round(lv*100f/sc));
        o.put("charging",st==BatteryManager.BATTERY_STATUS_CHARGING||st==BatteryManager.BATTERY_STATUS_FULL);
      }
    }catch(Throwable e){}
    try{
      StatFs fs=new StatFs(Environment.getDataDirectory().getPath());
      long total=fs.getTotalBytes(), free=fs.getAvailableBytes();
      o.put("stTotal",total); o.put("stUsed",Math.max(0,total-free));
    }catch(Throwable e){}
    try{ o.put("model",Build.MODEL); o.put("android",Build.VERSION.RELEASE); o.put("sdk",Build.VERSION.SDK_INT); }catch(Throwable e){}
    return o.toString();
  }

  /** 타일 상태. 모르는 값은 -1 */
  @JavascriptInterface public String state(){
    try{ return stateImpl(); }catch(Throwable e){ return "{}"; }
  }
  String stateImpl() throws Exception {
    JSONObject o=new JSONObject();
    try{ o.put("canWrite",canWrite()); }catch(Throwable e){}
    try{
      int raw=Settings.System.getInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128);
      o.put("bright",Math.max(0,Math.min(100,Math.round(raw*100f/255f))));
    }catch(Throwable e){}
    try{ o.put("autoBright",Settings.System.getInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0)==Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC); }catch(Throwable e){}
    try{ o.put("rotate",Settings.System.getInt(a.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,0)==1); }catch(Throwable e){}
    try{
      AudioManager am=a.getSystemService(AudioManager.class);
      int m=am.getRingerMode();
      o.put("ringer",m==AudioManager.RINGER_MODE_NORMAL?"sound":m==AudioManager.RINGER_MODE_VIBRATE?"vibrate":"silent");
    }catch(Throwable e){}
    try{ NotificationManager nm=a.getSystemService(NotificationManager.class); o.put("dndGranted",nm.isNotificationPolicyAccessGranted()); }catch(Throwable e){}
    o.putOpt("dnd",tri(()->{ NotificationManager nm=a.getSystemService(NotificationManager.class); int f=nm.getCurrentInterruptionFilter(); return f==NotificationManager.INTERRUPTION_FILTER_UNKNOWN?-1:(f==NotificationManager.INTERRUPTION_FILTER_ALL?0:1); }));
    o.putOpt("wifi",tri(()->{ WifiManager w=(WifiManager)a.getApplicationContext().getSystemService(Context.WIFI_SERVICE); return w==null?-1:(w.isWifiEnabled()?1:0); }));
    o.putOpt("bt",tri(()->{ BluetoothManager bm=a.getSystemService(BluetoothManager.class); BluetoothAdapter ad=bm==null?null:bm.getAdapter(); return ad==null?-1:(ad.isEnabled()?1:0); }));
    o.putOpt("airplane",tri(()->Settings.Global.getInt(a.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)==1?1:0));
    o.putOpt("loc",tri(()->{ LocationManager lm=a.getSystemService(LocationManager.class); return lm==null?-1:(lm.isLocationEnabled()?1:0); }));
    try{ o.put("hasFlash",camId!=null); o.put("flash",torchOn); }catch(Throwable e){}
    return o.toString();
  }
  interface IntSup{ int get() throws Throwable; }
  static Integer tri(IntSup s){ try{ return s.get(); }catch(Throwable e){ return -1; } }

  boolean canWrite(){ try{ return Settings.System.canWrite(a); }catch(Throwable e){ return false; } }

  /* ---------- 바로 바꾸기 (허용이 있어야 하는 것은 코드로 알려 줌) ---------- */

  /** 0~100. "ok" | "nowrite" | "err" */
  @JavascriptInterface public String setBrightness(int pct){
    try{
      if(!canWrite()) return "nowrite";
      int raw=Math.max(1,Math.min(255,Math.round(Math.max(0,Math.min(100,pct))*2.55f)));
      Settings.System.putInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
      Settings.System.putInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,raw);
      return "ok";
    }catch(Throwable e){ return "err"; }
  }
  @JavascriptInterface public String setAutoBrightness(boolean on){
    try{
      if(!canWrite()) return "nowrite";
      Settings.System.putInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,on?Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC:Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
      return "ok";
    }catch(Throwable e){ return "err"; }
  }
  @JavascriptInterface public String setRotate(boolean on){
    try{
      if(!canWrite()) return "nowrite";
      Settings.System.putInt(a.getContentResolver(),Settings.System.ACCELEROMETER_ROTATION,on?1:0);
      return "ok";
    }catch(Throwable e){ return "err"; }
  }
  /** mode: sound | vibrate | silent. "ok" | "needdnd" | "err" */
  @JavascriptInterface public String setRinger(String mode){
    try{
      AudioManager am=a.getSystemService(AudioManager.class);
      NotificationManager nm=a.getSystemService(NotificationManager.class);
      boolean granted=nm!=null&&nm.isNotificationPolicyAccessGranted();
      if("silent".equals(mode)){
        if(!granted) return "needdnd";
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT); return "ok";
      }
      try{
        am.setRingerMode("vibrate".equals(mode)?AudioManager.RINGER_MODE_VIBRATE:AudioManager.RINGER_MODE_NORMAL);
      }catch(SecurityException se){ return "needdnd"; }
      return am.getRingerMode()==("vibrate".equals(mode)?AudioManager.RINGER_MODE_VIBRATE:AudioManager.RINGER_MODE_NORMAL)?"ok":"needdnd";
    }catch(Throwable e){ return "err"; }
  }
  /** "ok" | "none" | "err" */
  @JavascriptInterface public String setFlash(boolean on){
    try{
      if(cam==null||camId==null) return "none";
      cam.setTorchMode(camId,on); torchOn=on; return "ok";
    }catch(Throwable e){ return "err"; }
  }

  /* ---------- 허용 화면 열기 ---------- */
  @JavascriptInterface public String requestWrite(){
    try{
      Intent i=new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+a.getPackageName()));
      return start(i)?"ok":open("all");
    }catch(Throwable e){ return "err"; }
  }
  @JavascriptInterface public String requestDnd(){
    try{
      Intent i=new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
      return start(i)?"ok":open("all");
    }catch(Throwable e){ return "err"; }
  }

  /* ---------- 시스템 설정 화면 열기 ---------- */
  boolean start(Intent i){
    try{ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(i); return true; }
    catch(Throwable e){ return false; }
  }
  static Intent act(String action){ return new Intent(action); }

  Intent[] cands(String key){
    switch(key){
      // 연결 (Android 10+ 에서는 앱이 켜고 끌 수 없어 패널/설정 화면을 연다)
      case "wifi": return new Intent[]{act("android.settings.panel.action.WIFI"),act(Settings.ACTION_WIFI_SETTINGS)};
      case "data": return new Intent[]{act("android.settings.panel.action.INTERNET_CONNECTIVITY"),act("android.settings.DATA_ROAMING_SETTINGS"),act(Settings.ACTION_WIRELESS_SETTINGS)};
      case "bluetooth": return new Intent[]{act(Settings.ACTION_BLUETOOTH_SETTINGS)};
      case "nfc": return new Intent[]{act("android.settings.panel.action.NFC"),act(Settings.ACTION_NFC_SETTINGS)};
      case "airplane": return new Intent[]{act(Settings.ACTION_AIRPLANE_MODE_SETTINGS),act(Settings.ACTION_WIRELESS_SETTINGS)};
      case "location": return new Intent[]{act(Settings.ACTION_LOCATION_SOURCE_SETTINGS)};
      case "hotspot": return new Intent[]{new Intent().setComponent(new ComponentName("com.android.settings","com.android.settings.TetherSettings")),act(Settings.ACTION_WIRELESS_SETTINGS)};
      case "datausage": return new Intent[]{act("android.settings.DATA_USAGE_SETTINGS"),act(Settings.ACTION_WIRELESS_SETTINGS)};
      case "vpn": return new Intent[]{act(Settings.ACTION_VPN_SETTINGS),act(Settings.ACTION_WIRELESS_SETTINGS)};
      case "wireless": return new Intent[]{act(Settings.ACTION_WIRELESS_SETTINGS)};
      // 소리·진동
      case "sound": return new Intent[]{act(Settings.ACTION_SOUND_SETTINGS)};
      case "volume": return new Intent[]{act("android.settings.panel.action.VOLUME"),act(Settings.ACTION_SOUND_SETTINGS)};
      case "ringtone": return new Intent[]{new Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,RingtoneManager.TYPE_RINGTONE),act(Settings.ACTION_SOUND_SETTINGS)};
      case "dnd": return new Intent[]{act("android.settings.ZEN_MODE_SETTINGS"),act(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),act(Settings.ACTION_SOUND_SETTINGS)};
      // 디스플레이
      case "display": return new Intent[]{act(Settings.ACTION_DISPLAY_SETTINGS)};
      case "night": return new Intent[]{act("android.settings.NIGHT_DISPLAY_SETTINGS"),act(Settings.ACTION_DISPLAY_SETTINGS)};
      case "fontsize": return new Intent[]{act("android.settings.TEXT_READING_SETTINGS"),act(Settings.ACTION_DISPLAY_SETTINGS)};
      // 배경화면·테마
      case "wallpaper": return new Intent[]{act(Intent.ACTION_SET_WALLPAPER),act(Settings.ACTION_DISPLAY_SETTINGS)};
      // 알림
      case "notif": return new Intent[]{act("android.settings.ALL_APPS_NOTIFICATION_SETTINGS"),act("android.settings.NOTIFICATION_SETTINGS"),act(Settings.ACTION_SOUND_SETTINGS)};
      case "notiflisten": return new Intent[]{act(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)};
      // 배터리
      case "battery": return new Intent[]{act(Intent.ACTION_POWER_USAGE_SUMMARY),act(Settings.ACTION_BATTERY_SAVER_SETTINGS)};
      case "batsaver": return new Intent[]{act(Settings.ACTION_BATTERY_SAVER_SETTINGS),act(Intent.ACTION_POWER_USAGE_SUMMARY)};
      case "batopt": return new Intent[]{act(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),act(Intent.ACTION_POWER_USAGE_SUMMARY)};
      // 저장공간
      case "storage": return new Intent[]{act(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),act(Settings.ACTION_MEMORY_CARD_SETTINGS)};
      // 앱
      case "apps": return new Intent[]{act(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS),act(Settings.ACTION_APPLICATION_SETTINGS)};
      case "perms": return new Intent[]{act("android.intent.action.MANAGE_PERMISSIONS"),act(Settings.ACTION_APPLICATION_SETTINGS)};
      // 기본 앱
      case "defaults": return new Intent[]{act(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),act(Settings.ACTION_APPLICATION_SETTINGS)};
      case "home": return new Intent[]{act(Settings.ACTION_HOME_SETTINGS),act(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)};
      // 보안·개인정보
      case "security": return new Intent[]{act(Settings.ACTION_SECURITY_SETTINGS)};
      case "privacy": return new Intent[]{act(Settings.ACTION_PRIVACY_SETTINGS),act(Settings.ACTION_SECURITY_SETTINGS)};
      case "lock": return new Intent[]{act("android.app.action.SET_NEW_PASSWORD"),act(Settings.ACTION_SECURITY_SETTINGS)};
      // 접근성
      case "access": return new Intent[]{act(Settings.ACTION_ACCESSIBILITY_SETTINGS)};
      // 휴대전화 정보·일반
      case "about": return new Intent[]{act(Settings.ACTION_DEVICE_INFO_SETTINGS)};
      case "locale": return new Intent[]{act(Settings.ACTION_LOCALE_SETTINGS)};
      case "date": return new Intent[]{act(Settings.ACTION_DATE_SETTINGS)};
      case "ime": return new Intent[]{act(Settings.ACTION_INPUT_METHOD_SETTINGS)};
      case "update": return new Intent[]{act("android.settings.SYSTEM_UPDATE_SETTINGS"),act(Settings.ACTION_DEVICE_INFO_SETTINGS)};
      case "all": return new Intent[]{act(Settings.ACTION_SETTINGS)};
      default: return null;
    }
  }

  /** key로 시스템 설정 화면을 연다. "ok" | "fallback"(설정 첫 화면) | "unknown" | "err" */
  @JavascriptInterface public String open(String key){
    try{
      if(key==null) return "unknown";
      Intent[] c=cands(key);
      if(c==null) return "unknown";
      for(Intent i: c) if(start(i)) return "ok";
      if(!"all".equals(key) && start(new Intent(Settings.ACTION_SETTINGS))) return "fallback";
      return "err";
    }catch(Throwable e){ return "err"; }
  }

  /** 뉴니버스 앱을 기본 전화/문자/홈으로: "ok" | "notinstalled" | "fallback" | "err" */
  @JavascriptInterface public String setDefault(String role){
    try{
      String pkg="phone".equals(role)?"kr.newniverse.phone":"sms".equals(role)?"kr.newniverse.sms":"kr.newniverse.launcher";
      if(!installed(pkg)) return "notinstalled";
      Intent i=null;
      if("phone".equals(role)){
        i=new Intent("android.telecom.action.CHANGE_DEFAULT_DIALER").putExtra("android.telecom.extra.CHANGE_DEFAULT_DIALER_PACKAGE_NAME",pkg);
      }else if("sms".equals(role)){
        i=new Intent("android.provider.Telephony.ACTION_CHANGE_DEFAULT").putExtra("package",pkg);
      }
      if(i!=null && start(i)) return "ok";
      String r=open("home".equals(role)?"home":"defaults");
      return "ok".equals(r)?"fallback":r;
    }catch(Throwable e){ return "err"; }
  }

  /* ---------- 뉴니버스 앱 ---------- */
  boolean installed(String pkg){
    try{ a.getPackageManager().getPackageInfo(pkg,0); return true; }catch(Throwable e){ return false; }
  }
  @JavascriptInterface public String nvApps(){
    JSONArray arr=new JSONArray();
    for(String[] n: NV_APPS){
      try{
        JSONObject o=new JSONObject(); o.put("id",n[0]); o.put("name",n[1]); o.put("pkg",n[2]);
        String vn=null;
        try{ PackageInfo p=a.getPackageManager().getPackageInfo(n[2],0); vn=p.versionName; o.put("installed",true); }catch(Throwable e){ o.put("installed",false); }
        if(vn!=null) o.put("ver",vn);
        arr.put(o);
      }catch(Throwable e){}
    }
    return arr.toString();
  }
  /** "ok" | "notinstalled" | "err" */
  @JavascriptInterface public String openApp(String pkg){
    try{
      boolean ok=false; for(String[] n: NV_APPS) if(n[2].equals(pkg)) ok=true;
      if(!ok) return "err";
      if(!installed(pkg)) return "notinstalled";
      PackageManager pm=a.getPackageManager();
      Intent i=pm.getLaunchIntentForPackage(pkg);
      if(i==null){ i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg); }
      return start(i)?"ok":"err";
    }catch(Throwable e){ return "err"; }
  }
}

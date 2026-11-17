package kr.newniverse.launcher;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class MainActivity extends Activity {
  WebView web; boolean pageReady=false; boolean torchOn=false;
  final Handler ui=new Handler(Looper.getMainLooper());

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    Window w=getWindow();
    if(Build.VERSION.SDK_INT>=30) w.setDecorFitsSystemWindows(false);
    WebView.setWebContentsDebuggingEnabled(true);
    // v22: 하드웨어 가속을 창에 확실히 켜고, 창 배경(웹뷰 밑에 한 번 더 칠해지는 층)을 없앰
    w.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
    w.setBackgroundDrawable(null);
    web=new WebView(this);
    web.setBackgroundColor(Color.BLACK);
    web.setLayerType(View.LAYER_TYPE_NONE,null);            // 웹뷰를 따로 한 장 더 그리는 화면 밖 층을 만들지 않음
    web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT,false);  // 화면 그리는 엔진을 중요 우선순위로
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
    s.setAllowFileAccess(true); s.setAllowFileAccessFromFileURLs(true); s.setAllowUniversalAccessFromFileURLs(true);
    s.setMediaPlaybackRequiresUserGesture(false); s.setTextZoom(100);
    s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true);
    web.setOverScrollMode(View.OVER_SCROLL_NEVER);
    web.setVerticalScrollBarEnabled(false); web.setHorizontalScrollBarEnabled(false);
    web.addJavascriptInterface(new Bridge(),"NVNative");
    web.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onConsoleMessage(ConsoleMessage m){ android.util.Log.i("NVWeb", m.messageLevel()+" "+m.message()+" @"+m.sourceId()+":"+m.lineNumber()); return true; }
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p){ fileCb=cb; try{ startActivityForResult(p.createIntent(), 77);}catch(Exception e){fileCb=null;return false;} return true; }
    });
    web.setWebViewClient(new WebViewClient(){
      @Override public void onPageFinished(WebView v,String u){ pageReady=true; }
      // 화면 그리는 엔진이 죽어도 런처 전체가 꺼지지 않게: 새 WebView로 다시 띄움
      @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d){ android.util.Log.w("NVWeb","renderer gone crash="+d.didCrash()); ui.post(()->recreate()); return true; }
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r){ Uri u=r.getUrl(); if("file".equals(u.getScheme()))return false; try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){} return true; }
    });
    setContentView(web);
    applyBars();
    web.loadUrl("file:///android_asset/www/index.html?native=1");
    IntentFilter f=new IntentFilter(); f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addAction(Intent.ACTION_PACKAGE_REMOVED); f.addAction(Intent.ACTION_PACKAGE_CHANGED); f.addDataScheme("package");
    registerReceiver(pkgRx,f);
    regLauncherApps();
  }
  ValueCallback<Uri[]> fileCb;
  @Override protected void onActivityResult(int req,int res,Intent data){ if(req==77&&fileCb!=null){ fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res,data)); fileCb=null; } else super.onActivityResult(req,res,data); }

  // v21: 앱 설치·삭제·업데이트를 바로 반영 (브로드캐스트 + LauncherApps 콜백, 같은 변화가 겹치면 JS 쪽에서 한 번만 처리)
  final BroadcastReceiver pkgRx=new BroadcastReceiver(){ @Override public void onReceive(Context c,Intent i){
    String pkg=i.getData()!=null?i.getData().getSchemeSpecificPart():""; String a=i.getAction(); boolean rep=i.getBooleanExtra(Intent.EXTRA_REPLACING,false);
    String kind=Intent.ACTION_PACKAGE_ADDED.equals(a)?(rep?"update":"add"):Intent.ACTION_PACKAGE_REMOVED.equals(a)?(rep?"update":"remove"):"change";
    appsChanged(pkg,kind); } };
  void appsChanged(String pkg,String kind){ js("window.NV_onAppsChanged&&NV_onAppsChanged('"+(pkg==null?"":pkg.replace("'",""))+"','"+kind+"')"); }
  android.content.pm.LauncherApps.Callback laCb;
  void regLauncherApps(){
    try{ android.content.pm.LauncherApps la=(android.content.pm.LauncherApps)getSystemService(Context.LAUNCHER_APPS_SERVICE);
      laCb=new android.content.pm.LauncherApps.Callback(){
        @Override public void onPackageRemoved(String p,android.os.UserHandle u){ appsChanged(p,"remove"); }
        @Override public void onPackageAdded(String p,android.os.UserHandle u){ appsChanged(p,"add"); }
        @Override public void onPackageChanged(String p,android.os.UserHandle u){ appsChanged(p,"update"); }
        @Override public void onPackagesAvailable(String[] ps,android.os.UserHandle u,boolean r){ appsChanged("","change"); }
        @Override public void onPackagesUnavailable(String[] ps,android.os.UserHandle u,boolean r){ appsChanged("","change"); }
      };
      la.registerCallback(laCb,ui);
    }catch(Exception e){ android.util.Log.w("NVWeb","launcherapps",e); }
  }
  @Override protected void onDestroy(){ try{unregisterReceiver(pkgRx);}catch(Exception e){}
    try{ if(laCb!=null) ((android.content.pm.LauncherApps)getSystemService(Context.LAUNCHER_APPS_SERVICE)).unregisterCallback(laCb);}catch(Exception e){} super.onDestroy(); }

  // 상태 표시줄(시계·배터리)은 폰 것을 그대로 보이고, 시스템 내비 바는 숨겨 뉴니버스 내비 바와 겹치지 않게 함.
  // 아래에서 쓸어 올리면 시스템 바가 잠깐 나타났다 사라짐.
  @SuppressWarnings("deprecation")
  void applyBars(){
    if(Build.VERSION.SDK_INT<30){ getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY); return; }
    WindowInsetsController c=getWindow().getInsetsController();
    if(c!=null){ c.hide(WindowInsets.Type.navigationBars()); c.show(WindowInsets.Type.statusBars());
      c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS); }
  }
  @Override public void onWindowFocusChanged(boolean h){ super.onWindowFocusChanged(h); if(h) applyBars(); }
  @Override protected void onResume(){ super.onResume(); applyBars(); js("window.NV_onResume&&NV_onResume()"); }

  @Override protected void onNewIntent(Intent i){ super.onNewIntent(i);
    if(Intent.ACTION_MAIN.equals(i.getAction())&&i.hasCategory(Intent.CATEGORY_HOME)) js("window.NV_onHome&&NV_onHome()"); }
  @SuppressWarnings("deprecation")
  @Override public void onBackPressed(){ js("window.NV_onBack&&NV_onBack()"); }

  void js(final String code){ ui.post(()->{ if(web!=null) web.evaluateJavascript(code,null); }); }

  String pkgOf(Intent i){ try{ ResolveInfo r=getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY); if(r!=null&&r.activityInfo!=null&&!"android".equals(r.activityInfo.packageName)) return r.activityInfo.packageName; }catch(Exception e){} return ""; }

  class Bridge {
    @JavascriptInterface public String getApps(){
      JSONArray arr=new JSONArray(); PackageManager pm=getPackageManager();
      Intent q=new Intent(Intent.ACTION_MAIN); q.addCategory(Intent.CATEGORY_LAUNCHER);
      List<ResolveInfo> l=pm.queryIntentActivities(q,0);
      for(ResolveInfo r:l){ if(r.activityInfo==null)continue; if(getPackageName().equals(r.activityInfo.packageName))continue;
        try{ JSONObject o=new JSONObject(); o.put("pkg",r.activityInfo.packageName); o.put("act",r.activityInfo.name); o.put("label",String.valueOf(r.loadLabel(pm))); arr.put(o);}catch(Exception e){} }
      return arr.toString();
    }
    @JavascriptInterface public String getRoles(){
      JSONObject o=new JSONObject();
      try{
        o.put("phone",pkgOf(new Intent(Intent.ACTION_DIAL)));
        String sms=""; try{ sms=Telephony.Sms.getDefaultSmsPackage(MainActivity.this);}catch(Exception e){}
        if(sms==null||sms.isEmpty()) sms=pkgOf(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")));
        o.put("sms",sms==null?"":sms);
        o.put("camera",pkgOf(new Intent(MediaStore.ACTION_IMAGE_CAPTURE)));
        o.put("browser",pkgOf(new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"))));
        o.put("maps",pkgOf(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:37.5,127.0"))));
        o.put("settings","com.android.settings");
      }catch(Exception e){}
      return o.toString();
    }
    @JavascriptInterface public String getIcon(String pkg,String act,int px){
      try{ PackageManager pm=getPackageManager(); Drawable d;
        try{ d=pm.getActivityIcon(new ComponentName(pkg,act)); }catch(Exception e){ d=pm.getApplicationIcon(pkg); }
        Bitmap bm=Bitmap.createBitmap(px,px,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
        if(d instanceof AdaptiveIconDrawable){ // 바탕+앞그림을 합쳐 원래 모양 그대로
          AdaptiveIconDrawable a=(AdaptiveIconDrawable)d; Path mask=a.getIconMask();
          int ext=(int)(px*0.25f); Drawable bg=a.getBackground(), fg=a.getForeground();
          c.save();
          Matrix m=new Matrix(); m.setScale(px/100f,px/100f); Path pm2=new Path(); mask.transform(m,pm2); c.clipPath(pm2);
          if(bg!=null){ bg.setBounds(-ext,-ext,px+ext,px+ext); bg.draw(c);} if(fg!=null){ fg.setBounds(-ext,-ext,px+ext,px+ext); fg.draw(c);} c.restore();
        } else { d.setBounds(0,0,px,px); d.draw(c); }
        ByteArrayOutputStream bo=new ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.PNG,100,bo);
        return "data:image/png;base64,"+Base64.encodeToString(bo.toByteArray(),Base64.NO_WRAP);
      }catch(Exception e){ return ""; }
    }
    @JavascriptInterface public boolean launch(String pkg,String act){
      try{ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_LAUNCHER); i.setComponent(new ComponentName(pkg,act));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); startActivity(i); return true; }
      catch(Exception e){ try{ Intent i=getPackageManager().getLaunchIntentForPackage(pkg); if(i!=null){ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; } }catch(Exception e2){} }
      return false;
    }
    @JavascriptInterface public void openSettings(String which){
      String a=Settings.ACTION_SETTINGS;
      if("wifi".equals(which)) a=Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS;
      else if("bt".equals(which)) a=Settings.ACTION_BLUETOOTH_SETTINGS;
      else if("air".equals(which)) a=Settings.ACTION_AIRPLANE_MODE_SETTINGS;
      else if("home".equals(which)) a=Settings.ACTION_HOME_SETTINGS;
      else if("display".equals(which)) a=Settings.ACTION_DISPLAY_SETTINGS;
      else if("sound".equals(which)) a=Settings.ACTION_SOUND_SETTINGS;
      else if("loc".equals(which)) a=Settings.ACTION_LOCATION_SOURCE_SETTINGS;
      else if("hot".equals(which)) a=Settings.ACTION_WIRELESS_SETTINGS;
      else if("dnd".equals(which)) a=Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS;
      else if("battery".equals(which)) a=Intent.ACTION_POWER_USAGE_SUMMARY;
      else if("access".equals(which)) a=Settings.ACTION_ACCESSIBILITY_SETTINGS;
      else if("apps".equals(which)) a=Settings.ACTION_APPLICATION_SETTINGS;
      try{ Intent i=new Intent(a); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);}catch(Exception e){ try{ Intent i=new Intent(Settings.ACTION_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);}catch(Exception e2){} }
    }
    @JavascriptInterface public void appInfo(String pkg){ try{ Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);}catch(Exception e){} }
    @JavascriptInterface public boolean torch(boolean on){
      try{ CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE); for(String id:cm.getCameraIdList()){ Boolean f=cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE); if(f!=null&&f){ cm.setTorchMode(id,on); torchOn=on; return true; } } }catch(Exception e){} return false;
    }
    @JavascriptInterface public void brightness(final float v){ ui.post(()->{ WindowManager.LayoutParams lp=getWindow().getAttributes(); lp.screenBrightness=Math.max(0.02f,Math.min(1f,v)); getWindow().setAttributes(lp); }); }
    @JavascriptInterface public void volume(float v){ try{ AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); int mx=am.getStreamMaxVolume(AudioManager.STREAM_MUSIC); am.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(v*mx),0);}catch(Exception e){} }
    @JavascriptInterface public float getVolume(){ try{ AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); return am.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);}catch(Exception e){return .5f;} }
    @JavascriptInterface public void vibrate(int ms){ try{ Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE); if(v!=null) v.vibrate(VibrationEffect.createOneShot(Math.max(1,ms),VibrationEffect.DEFAULT_AMPLITUDE)); }catch(Exception e){} }
    @JavascriptInterface public void expandNotifications(){
      try{ Object sb=getSystemService("statusbar"); Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);}catch(Exception e){} }
    @JavascriptInterface public String info(){ return "{\"model\":\""+Build.MANUFACTURER+" "+Build.MODEL+"\",\"android\":\""+Build.VERSION.RELEASE+"\",\"sdk\":"+Build.VERSION.SDK_INT+"}"; }
    /** 두 번 톡톡 화면 끄기: "ok"=꺼짐, "old"=안드로이드 9 미만, "need"=접근성 권한 필요 */
    @JavascriptInterface public String lockScreen(){
      if(Flavor.BASIC) return "dim";   // 기본판: 접근성 서비스 없음 → 화면을 어둡게만
      if(Build.VERSION.SDK_INT<28) return "old";
      try{ if((Boolean)Class.forName(getPackageName()+".LockService").getMethod("lock").invoke(null)) return "ok"; }catch(Throwable e){ return "dim"; }
      return lockEnabled()?"wait":"need";
    }
    @JavascriptInterface public String flavor(){ return Flavor.BASIC?"basic":"plus"; }
    @JavascriptInterface public boolean lockEnabled(){
      if(Flavor.BASIC) return false;
      try{ String v=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return v!=null&&v.toLowerCase().contains((getPackageName()+"/").toLowerCase())&&v.toLowerCase().contains("lockservice"); }catch(Exception e){ return false; }
    }
    @JavascriptInterface public void openA11y(){ try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);}catch(Exception e){} }
    /** v20: 웹에서 그린 정지 이미지(data URL)를 폰 잠금화면 배경으로 지정. 결과는 JS NV_onLockWp(ok,msg) */
    @JavascriptInterface public void setLockWallpaper(final String dataUrl){
      new Thread(()->{ boolean ok=false; String msg="";
        try{
          String b64=dataUrl.substring(dataUrl.indexOf(',')+1);
          byte[] bytes=android.util.Base64.decode(b64,android.util.Base64.DEFAULT);
          Bitmap bmp=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
          if(bmp==null) throw new IllegalArgumentException("image");
          WallpaperManager wm=WallpaperManager.getInstance(MainActivity.this);
          int r=wm.setBitmap(bmp,null,true,WallpaperManager.FLAG_LOCK);
          ok=r!=0; if(!ok) msg="거부됨";
        }catch(Throwable e){ msg=e.getClass().getSimpleName(); android.util.Log.w("NVWeb","lock wp fail",e); }
        final boolean fok=ok; final String fm=msg.replace("'","");
        ui.post(()->{ if(web!=null) web.evaluateJavascript("window.NV_onLockWp&&NV_onLockWp("+fok+",'"+fm+"')",null); });
      }).start();
    }
    @JavascriptInterface public int screenW(){ android.util.DisplayMetrics m=new android.util.DisplayMetrics(); getWindowManager().getDefaultDisplay().getRealMetrics(m); return m.widthPixels; }
    /** v21: 배치·폴더·설정을 앱 전용 저장소(파일)에 영구 저장 — 재부팅·WebView 초기화에도 유지 */
    @JavascriptInterface public boolean saveState(String json){
      try{ java.io.File f=new java.io.File(getFilesDir(),"nv_state.json"), t=new java.io.File(getFilesDir(),"nv_state.tmp");
        java.io.FileOutputStream o=new java.io.FileOutputStream(t); o.write(json.getBytes("UTF-8")); o.getFD().sync(); o.close(); return t.renameTo(f); }catch(Exception e){ return false; } }
    @JavascriptInterface public String loadState(){
      try{ java.io.File f=new java.io.File(getFilesDir(),"nv_state.json"); if(!f.exists()) return "";
        byte[] b=new byte[(int)f.length()]; java.io.FileInputStream in=new java.io.FileInputStream(f); int n=0; while(n<b.length){int r=in.read(b,n,b.length-n); if(r<0)break; n+=r;} in.close(); return new String(b,0,n,"UTF-8"); }catch(Exception e){ return ""; } }
    /** 삭제 요청: 시스템 삭제 확인 창을 띄움. 기본(시스템) 앱이면 "system" */
    @JavascriptInterface public String uninstall(String pkg){
      try{ ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0);
        if((ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0&&(ai.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)==0) return "system";
        if(Flavor.BASIC){ // 기본판: 삭제 권한(REQUEST_DELETE_PACKAGES) 없이 앱 정보 화면에서 직접 삭제
          Intent d=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+pkg)); d.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(d); return "info"; }
        Intent i=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return "ok";
      }catch(Exception e){ return "fail"; } }
    @JavascriptInterface public boolean isSystem(String pkg){ try{ ApplicationInfo ai=getPackageManager().getApplicationInfo(pkg,0); return (ai.flags&ApplicationInfo.FLAG_SYSTEM)!=0; }catch(Exception e){ return false; } }
    @JavascriptInterface public boolean isDefaultHome(){ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); ResolveInfo r=getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY); return r!=null&&r.activityInfo!=null&&getPackageName().equals(r.activityInfo.packageName); }
  }
}

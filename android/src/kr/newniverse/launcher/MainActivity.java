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
import android.widget.FrameLayout;
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
    initWidgets();
    root=new FrameLayout(this); root.addView(web,new FrameLayout.LayoutParams(-1,-1));
    wlayer=new FrameLayout(this); root.addView(wlayer,new FrameLayout.LayoutParams(-1,-1));
    setContentView(root);
    applyBars();
    web.loadUrl("file:///android_asset/www/index.html?native=1");
    IntentFilter f=new IntentFilter(); f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addAction(Intent.ACTION_PACKAGE_REMOVED); f.addAction(Intent.ACTION_PACKAGE_CHANGED); f.addDataScheme("package");
    registerReceiver(pkgRx,f);
    regLauncherApps();
  }
  ValueCallback<Uri[]> fileCb;
  @Override protected void onActivityResult(int req,int res,Intent data){ if(widgetResult(req,res)) return; if(req==77&&fileCb!=null){ fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res,data)); fileCb=null; } else super.onActivityResult(req,res,data); }

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

  // ===================== v23 안드로이드 앱 위젯 호스팅 =====================
  // 웹 홈(WebView) 위에 투명한 층(wlayer)을 겹치고, 그 위에 진짜 위젯 뷰를 웹이 알려 준 칸 좌표에 맞춰 놓음.
  // 위젯이 없는 곳을 누르면 터치는 아래 WebView로 그대로 내려감. 모래 전환 중에는 웹이 스냅샷 그림으로 대신 그리고 진짜 뷰는 숨김.
  static final int HOST_ID=0x4E56, REQ_BIND=101, REQ_CONF=102, REQ_RECONF=103;
  android.appwidget.AppWidgetHost whost; android.appwidget.AppWidgetManager wmgr;
  FrameLayout root, wlayer; final Map<Integer,NVHostView> wviews=new HashMap<>(); final Map<Integer,Integer> wpage=new HashMap<>();
  float wdpr=1f; int wvis=-1; String pendReq=null; int pendId=-1; android.appwidget.AppWidgetProviderInfo pendInfo=null;
  void initWidgets(){
    wmgr=android.appwidget.AppWidgetManager.getInstance(this);
    whost=new android.appwidget.AppWidgetHost(this,HOST_ID){
      @Override protected android.appwidget.AppWidgetHostView onCreateView(Context c,int id,android.appwidget.AppWidgetProviderInfo i){ return new NVHostView(c); } };
  }
  @Override protected void onStart(){ super.onStart(); try{ if(whost!=null) whost.startListening(); }catch(Exception e){ android.util.Log.w("NVWeb","widget listen",e); } }
  @Override protected void onStop(){ super.onStop(); try{ if(whost!=null) whost.stopListening(); }catch(Exception e){} }
  android.appwidget.AppWidgetProviderInfo provInfo(String flat){ ComponentName cn=ComponentName.unflattenFromString(flat); if(cn==null) return null;
    for(android.appwidget.AppWidgetProviderInfo i:wmgr.getInstalledProviders()) if(cn.equals(i.provider)) return i; return null; }
  boolean needsConfig(android.appwidget.AppWidgetProviderInfo i){ if(i==null||i.configure==null) return false;
    if(Build.VERSION.SDK_INT>=28&&(i.widgetFeatures&android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL)!=0) return false; return true; }
  void widgetAfterBind(int id){
    if(needsConfig(pendInfo)){ try{ whost.startAppWidgetConfigureActivityForResult(this,id,0,REQ_CONF,null); return; }catch(Exception e){ android.util.Log.w("NVWeb","widget conf",e); } }
    widgetDone(id);
  }
  void widgetDone(int id){ String r=pendReq; pendReq=null; pendId=-1; js("window.NV_onWidgetAdded&&NV_onWidgetAdded('"+r+"',1,"+id+",'')"); }
  void widgetFail(String why){ if(pendId>=0){ try{ whost.deleteAppWidgetId(pendId);}catch(Exception e){} } String r=pendReq; pendReq=null; pendId=-1;
    js("window.NV_onWidgetAdded&&NV_onWidgetAdded('"+r+"',0,-1,'"+why+"')"); }
  boolean widgetResult(int req,int res){
    if(req==REQ_BIND){ if(res==RESULT_OK&&pendId>=0) widgetAfterBind(pendId); else widgetFail("denied"); return true; }
    if(req==REQ_CONF){ if(res==RESULT_OK&&pendId>=0) widgetDone(pendId); else widgetFail("cancel"); return true; }
    if(req==REQ_RECONF){ return true; }
    return false;
  }
  void applyWidgetVis(){ for(Map.Entry<Integer,NVHostView> e:wviews.entrySet()){ Integer p=wpage.get(e.getKey()); e.getValue().setVisibility(wvis>=0&&p!=null&&p==wvis?View.VISIBLE:View.INVISIBLE); } }
  /** 위젯 칸: 길게 누르면 웹 메뉴, 옆으로 밀면 그 터치를 WebView로 넘겨 화면 넘기기(모래 전환) */
  class NVHostView extends android.appwidget.AppWidgetHostView {
    float dx,dy; boolean lpFired=false, fwd=false; final int slop; final Runnable lpR;
    NVHostView(Context c){ super(c); slop=ViewConfiguration.get(c).getScaledTouchSlop();
      lpR=()->{ lpFired=true; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); js("window.NV_onWidgetLP&&NV_onWidgetLP("+getAppWidgetId()+")"); }; }
    void toWeb(MotionEvent src,int action,float x,float y){ MotionEvent m=MotionEvent.obtain(src.getDownTime(),src.getEventTime(),action,x+getLeft(),y+getTop(),0); m.setSource(InputDevice.SOURCE_TOUCHSCREEN); if(web!=null) web.dispatchTouchEvent(m); m.recycle(); }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
      switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN: dx=e.getX(); dy=e.getY(); lpFired=false; fwd=false; removeCallbacks(lpR); postDelayed(lpR,ViewConfiguration.getLongPressTimeout()); break;
        case MotionEvent.ACTION_MOVE:{ float mx=e.getX()-dx, my=e.getY()-dy;
          if(Math.abs(mx)>slop||Math.abs(my)>slop) removeCallbacks(lpR);
          if(!lpFired&&Math.abs(mx)>slop*1.5f&&Math.abs(mx)>Math.abs(my)*1.2f){ fwd=true; toWeb(e,MotionEvent.ACTION_DOWN,dx,dy); toWeb(e,MotionEvent.ACTION_MOVE,e.getX(),e.getY()); return true; }
          break; }
        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: removeCallbacks(lpR); break;
      }
      return lpFired;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
      int a=e.getActionMasked(); boolean end=a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL;
      if(fwd){ toWeb(e,a==MotionEvent.ACTION_CANCEL?MotionEvent.ACTION_CANCEL:a==MotionEvent.ACTION_UP?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,e.getX(),e.getY()); if(end) fwd=false; return true; }
      if(lpFired){ if(end) lpFired=false; return true; }
      return super.onTouchEvent(e);
    }
  }

  class Bridge {
    // ---------- v23 위젯 ----------
    @JavascriptInterface public String widgetProviders(){
      JSONArray arr=new JSONArray(); PackageManager pm=getPackageManager(); float d=getResources().getDisplayMetrics().density;
      try{ for(android.appwidget.AppWidgetProviderInfo i:wmgr.getInstalledProviders()){
          if((i.widgetCategory&android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)==0) continue;
          try{ JSONObject o=new JSONObject(); String pkg=i.provider.getPackageName(); o.put("prov",i.provider.flattenToString()); o.put("label",String.valueOf(i.loadLabel(pm)));
            String app=pkg; try{ app=String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg,0))); }catch(Exception e){}
            o.put("app",app); o.put("minW",Math.round(i.minWidth/d)); o.put("minH",Math.round(i.minHeight/d));
            if(Build.VERSION.SDK_INT>=31){ o.put("tcw",i.targetCellWidth); o.put("tch",i.targetCellHeight); }
            o.put("rz",i.resizeMode); o.put("cfg",i.configure!=null); arr.put(o); }catch(Exception e){} } }catch(Exception e){ android.util.Log.w("NVWeb","providers",e); }
      return arr.toString();
    }
    @JavascriptInterface public String widgetPreview(String prov,int px){
      try{ android.appwidget.AppWidgetProviderInfo i=provInfo(prov); if(i==null) return ""; int dpi=getResources().getDisplayMetrics().densityDpi;
        Drawable dr=i.loadPreviewImage(MainActivity.this,dpi); if(dr==null) dr=i.loadIcon(MainActivity.this,dpi); if(dr==null) return "";
        int w=Math.max(1,dr.getIntrinsicWidth()), h=Math.max(1,dr.getIntrinsicHeight()); float k=Math.min(1f,px/(float)Math.max(w,h)); int bw=Math.max(1,Math.round(w*k)), bh=Math.max(1,Math.round(h*k));
        Bitmap bm=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm); dr.setBounds(0,0,bw,bh); dr.draw(c);
        ByteArrayOutputStream bo=new ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.PNG,100,bo); return "data:image/png;base64,"+Base64.encodeToString(bo.toByteArray(),Base64.NO_WRAP);
      }catch(Throwable e){ return ""; }
    }
    /** 위젯 추가: 허락(바인딩) → 설정 화면 → 결과는 JS NV_onWidgetAdded(req, ok, id, why) */
    @JavascriptInterface public String widgetAdd(final String prov,final String req){
      final android.appwidget.AppWidgetProviderInfo i=provInfo(prov); if(i==null) return "fail";
      ui.post(()->{ try{ pendReq=req; pendInfo=i; pendId=whost.allocateAppWidgetId();
          boolean ok=wmgr.bindAppWidgetIdIfAllowed(pendId,i.getProfile(),i.provider,null);
          if(ok) widgetAfterBind(pendId);
          else{ Intent b=new Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND);
            b.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,pendId); b.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,i.provider);
            b.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,i.getProfile()); startActivityForResult(b,REQ_BIND); }
        }catch(Exception e){ android.util.Log.w("NVWeb","widget add",e); widgetFail("fail"); } });
      return "ok";
    }
    @JavascriptInterface public void widgetConfigure(final int id){ ui.post(()->{ try{ whost.startAppWidgetConfigureActivityForResult(MainActivity.this,id,0,REQ_RECONF,null);}catch(Exception e){} }); }
    /** 웹 칸 좌표(CSS px)에 맞춰 위젯 뷰를 놓음: {dpr, list:[{id,p,x,y,w,h}]} */
    @JavascriptInterface public void widgetPlace(final String json){
      ui.post(()->{ try{ JSONObject o=new JSONObject(json); wdpr=(float)o.optDouble("dpr",1); JSONArray l=o.getJSONArray("list"); Set<Integer> keep=new HashSet<>();
          float dens=getResources().getDisplayMetrics().density;
          for(int k=0;k<l.length();k++){ JSONObject w=l.getJSONObject(k); int id=w.getInt("id"); keep.add(id); wpage.put(id,w.getInt("p"));
            NVHostView v=wviews.get(id);
            if(v==null){ android.appwidget.AppWidgetProviderInfo info=wmgr.getAppWidgetInfo(id); if(info==null) continue;
              v=(NVHostView)whost.createView(MainActivity.this,id,info); v.setVisibility(View.INVISIBLE); wlayer.addView(v); wviews.put(id,v); }
            int pw=Math.round((float)w.getDouble("w")*wdpr), ph=Math.round((float)w.getDouble("h")*wdpr);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(pw,ph); lp.leftMargin=Math.round((float)w.getDouble("x")*wdpr); lp.topMargin=Math.round((float)w.getDouble("y")*wdpr); v.setLayoutParams(lp);
            int wd=Math.round(pw/dens), hd=Math.round(ph/dens);
            try{ v.updateAppWidgetSize(null,wd,hd,wd,hd); }catch(Exception e){} }
          for(Integer id:new ArrayList<>(wviews.keySet())) if(!keep.contains(id)){ wlayer.removeView(wviews.get(id)); wviews.remove(id); wpage.remove(id); }
          applyWidgetVis();
        }catch(Exception e){ android.util.Log.w("NVWeb","widget place",e); } });
    }
    /** 보일 페이지(-1이면 모두 숨김). 화면에 반영된 뒤 JS NV_onWidgetVis(page) */
    @JavascriptInterface public void widgetVis(final int page){ ui.post(()->{ wvis=page; applyWidgetVis(); wlayer.postOnAnimation(()->js("window.NV_onWidgetVis&&NV_onWidgetVis("+page+")")); }); }
    /** 위젯 모습을 그림으로: 결과는 JS NV_onWidgetSnap(id, dataUrl) — 전환 때 모래알로 흩어질 그림 */
    @JavascriptInterface public void widgetSnap(final String ids,final float scale){
      ui.post(()->{ try{ JSONArray a=new JSONArray(ids);
          for(int k=0;k<a.length();k++){ final int id=a.getInt(k); NVHostView v=wviews.get(id); if(v==null||v.getWidth()<=0||v.getHeight()<=0) continue;
            float s=Math.min(1f,scale/Math.max(.5f,wdpr)); int bw=Math.max(1,Math.round(v.getWidth()*s)), bh=Math.max(1,Math.round(v.getHeight()*s));
            final Bitmap bm; try{ bm=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm); c.scale(s,s); v.draw(c); }catch(Throwable e){ continue; }
            new Thread(()->{ try{ ByteArrayOutputStream bo=new ByteArrayOutputStream(); bm.compress(Bitmap.CompressFormat.PNG,100,bo); bm.recycle();
                js("window.NV_onWidgetSnap&&NV_onWidgetSnap("+id+",'data:image/png;base64,"+Base64.encodeToString(bo.toByteArray(),Base64.NO_WRAP)+"')"); }catch(Throwable e){} }).start(); }
        }catch(Exception e){} });
    }
    @JavascriptInterface public void widgetRemove(final int id){ ui.post(()->{ NVHostView v=wviews.remove(id); if(v!=null) wlayer.removeView(v); wpage.remove(id); try{ whost.deleteAppWidgetId(id);}catch(Exception e){} }); }
    @JavascriptInterface public String widgetInfo(int id){ try{ android.appwidget.AppWidgetProviderInfo i=wmgr.getAppWidgetInfo(id); return i==null?"":i.provider.flattenToString(); }catch(Exception e){ return ""; } }
    /** 저장된 배치에 없는(주인 없는) 위젯 번호 정리 */
    @JavascriptInterface public void widgetGc(String json){ try{ JSONArray a=new JSONArray(json); Set<Integer> keep=new HashSet<>(); for(int k=0;k<a.length();k++) keep.add(a.getInt(k));
        for(int id:whost.getAppWidgetIds()) if(!keep.contains(id)&&id!=pendId) whost.deleteAppWidgetId(id); }catch(Exception e){} }
    @JavascriptInterface public String battery(){ try{ BatteryManager bm=(BatteryManager)getSystemService(BATTERY_SERVICE); int lv=bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return "{\"level\":"+lv+",\"charging\":"+bm.isCharging()+"}"; }catch(Exception e){ return ""; } }
    @JavascriptInterface public String storage(){ try{ StatFs st=new StatFs(Environment.getDataDirectory().getPath()); return "{\"total\":"+st.getTotalBytes()+",\"free\":"+st.getAvailableBytes()+"}"; }catch(Exception e){ return ""; } }
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
      else if("storage".equals(which)) a=Settings.ACTION_INTERNAL_STORAGE_SETTINGS;
      else if("clock".equals(which)) a=android.provider.AlarmClock.ACTION_SHOW_ALARMS;
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

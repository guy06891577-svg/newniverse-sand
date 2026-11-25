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
    // v27: 아래 시스템 제스처/내비 영역을 읽어 두고, 바뀌면 웹에 알림(독이 그 위로 오게)
    root.setOnApplyWindowInsetsListener((v,ins)->{ readInsets(ins); return v.onApplyWindowInsets(ins); });
    try{ getContentResolver().registerContentObserver(Settings.Secure.getUriFor("navigation_mode"),false,new android.database.ContentObserver(ui){
      @Override public void onChange(boolean self){ js("window.NV_onInsets&&NV_onInsets()"); } }); }catch(Exception e){}
    applyBars();
    web.loadUrl("file:///android_asset/www/index.html?native=1");
    IntentFilter f=new IntentFilter(); f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addAction(Intent.ACTION_PACKAGE_REMOVED); f.addAction(Intent.ACTION_PACKAGE_CHANGED); f.addDataScheme("package");
    registerReceiver(pkgRx,f);
    regLauncherApps();
    regTorch();
    NotifService.onChange=()->{ ui.removeCallbacks(notifPing); ui.postDelayed(notifPing,250); };
  }
  final Runnable notifPing=()->js("window.NV_onNotif&&NV_onNotif()");
  ValueCallback<Uri[]> fileCb;
  @Override protected void onActivityResult(int req,int res,Intent data){ if(widgetResult(req,res)) return; if(req==REQ_EXP||req==REQ_IMP){ bakResult(req,res,data); return; } if(req==77&&fileCb!=null){ fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res,data)); fileCb=null; } else super.onActivityResult(req,res,data); }

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
  @Override protected void onPause(){ super.onPause(); speechCancel(); js("window.NV_onPause&&NV_onPause()"); }
  static final int REQ_LOC=7301;
  static final int REQ_EXP=7302, REQ_IMP=7303, REQ_CON=7304, REQ_MIC=7305;
  String pendExport=null;
  // v29 홈 배치 백업: 저장소 접근 권한 없이 안드로이드 파일 고르기(SAF) 창으로 내보내기/가져오기
  void bakResult(int req,int res,Intent data){
    if(res!=RESULT_OK||data==null||data.getData()==null){ js("window.NV_onBak&&NV_onBak('"+(req==REQ_EXP?"exp":"imp")+"',0,'')"); pendExport=null; return; }
    final Uri u=data.getData();
    new Thread(()->{ boolean ok=false; String body="";
      try{ if(req==REQ_EXP){ java.io.OutputStream o=getContentResolver().openOutputStream(u,"wt"); o.write((pendExport==null?"":pendExport).getBytes("UTF-8")); o.close(); ok=true; }
        else{ java.io.InputStream in=getContentResolver().openInputStream(u); ByteArrayOutputStream bo=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n,t=0; while((n=in.read(b))>0&&t<4000000){ bo.write(b,0,n); t+=n; } in.close(); body=bo.toString("UTF-8"); ok=true; } }catch(Exception e){ ok=false; }
      pendExport=null; final String q=org.json.JSONObject.quote(body); final boolean fok=ok;
      js("window.NV_onBak&&NV_onBak('"+(req==REQ_EXP?"exp":"imp")+"',"+(fok?1:0)+","+q+")"); }).start();
  }
  // v29 빠른 설정: 실제 상태 읽기
  boolean torchState=false;
  void regTorch(){ try{ CameraManager cm=(CameraManager)getSystemService(CAMERA_SERVICE); cm.registerTorchCallback(new CameraManager.TorchCallback(){ @Override public void onTorchModeChanged(String id,boolean en){ torchState=en; torchOn=en; js("window.NV_onQS&&NV_onQS()"); } },ui); }catch(Exception e){} }
  String qsJson(){ JSONObject o=new JSONObject(); ContentResolver cr=getContentResolver();
    try{ android.net.wifi.WifiManager wm=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE); o.put("wifi",wm!=null&&wm.isWifiEnabled()); }catch(Exception e){ try{ o.put("wifi",Settings.Global.getInt(cr,"wifi_on",0)>0);}catch(Exception e2){} }
    try{ o.put("bt",Settings.Global.getInt(cr,"bluetooth_on",0)!=0); }catch(Exception e){}
    try{ o.put("air",Settings.Global.getInt(cr,Settings.Global.AIRPLANE_MODE_ON,0)!=0); }catch(Exception e){}
    try{ o.put("data",Settings.Global.getInt(cr,"mobile_data",0)!=0); }catch(Exception e){}
    try{ android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE); o.put("loc",Build.VERSION.SDK_INT>=28?lm.isLocationEnabled():Settings.Secure.getInt(cr,Settings.Secure.LOCATION_MODE,0)!=0); }catch(Exception e){}
    try{ o.put("save",((PowerManager)getSystemService(POWER_SERVICE)).isPowerSaveMode()); }catch(Exception e){}
    try{ o.put("rot",Settings.System.getInt(cr,Settings.System.ACCELEROMETER_ROTATION,0)!=0); }catch(Exception e){}
    try{ AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); o.put("ring",am.getRingerMode()); o.put("sound",am.getRingerMode()==AudioManager.RINGER_MODE_NORMAL); }catch(Exception e){}
    try{ android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE); o.put("dndAccess",nm.isNotificationPolicyAccessGranted()); o.put("dnd",nm.getCurrentInterruptionFilter()>android.app.NotificationManager.INTERRUPTION_FILTER_ALL); }catch(Exception e){}
    try{ o.put("writeSettings",Settings.System.canWrite(this)); }catch(Exception e){}
    try{ o.put("flash",torchState); o.put("bright",Settings.System.getInt(cr,Settings.System.SCREEN_BRIGHTNESS,128)/255.0); o.put("autoBright",Settings.System.getInt(cr,Settings.System.SCREEN_BRIGHTNESS_MODE,0)==1); }catch(Exception e){}
    return o.toString(); }
  void startI(Intent i){ try{ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);}catch(Exception e){ try{ Intent j=new Intent(Settings.ACTION_SETTINGS); j.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(j);}catch(Exception e2){} } }
  boolean notifGranted(){ try{ ComponentName cn=new ComponentName(this,NotifService.class);
      if(Build.VERSION.SDK_INT>=27) return ((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).isNotificationListenerAccessGranted(cn);
      String v=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners"); return v!=null&&v.contains(cn.flattenToString()); }catch(Exception e){ return false; } }
  // 연락처 (켤 때만 권한 요청) · 이름·번호만 읽어 웹 화면 메모리에서 검색, 저장·전송 없음
  String contactsJson(){ JSONArray a=new JSONArray(); if(checkSelfPermission(android.Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) return "null";
    android.database.Cursor c=null; try{ c=getContentResolver().query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID,android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER},null,null,android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC");
      Set<String> seen=new HashSet<>(); while(c!=null&&c.moveToNext()&&a.length()<5000){ String id=c.getString(0),n=c.getString(1),num=c.getString(2); if(n==null||num==null) continue; String k=id+"|"+num.replaceAll("[^0-9+]",""); if(!seen.add(k)) continue;
        JSONObject o=new JSONObject(); o.put("id",id); o.put("n",n); o.put("p",num); a.put(o); } }catch(Exception e){} finally{ if(c!=null) c.close(); }
    return a.toString(); }
  @Override public void onRequestPermissionsResult(int req,String[] p,int[] g){ super.onRequestPermissionsResult(req,p,g);
    if(req==REQ_LOC) js("window.NV_onBusPerm&&NV_onBusPerm('"+BusInfo.perm(this)+"')");
    if(req==REQ_MIC){ if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) speechGo(true); else spJs("error","perm"); }
    if(req==REQ_CON) js("window.NV_onConPerm&&NV_onConPerm('"+(checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED?"yes":"no")+"')"); }

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

  volatile int insNav=0, insGest=0, insMand=0;
  void readInsets(WindowInsets ins){
    try{ float d=getResources().getDisplayMetrics().density; int n,g,m;
      if(Build.VERSION.SDK_INT>=30){ n=ins.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom;
        g=ins.getInsets(WindowInsets.Type.systemGestures()).bottom; m=ins.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom; }
      else if(Build.VERSION.SDK_INT>=29){ n=ins.getStableInsetBottom(); g=ins.getSystemGestureInsets().bottom; m=ins.getMandatorySystemGestureInsets().bottom; }
      else { n=ins.getStableInsetBottom(); g=0; m=0; }
      int a=Math.round(n/d), b=Math.round(g/d), c=Math.round(m/d);
      if(a!=insNav||b!=insGest||c!=insMand){ insNav=a; insGest=b; insMand=c; js("window.NV_onInsets&&NV_onInsets()"); }
    }catch(Throwable e){}
  }
  // ===================== v38b 앱 없는 홈: 말로 찾기 =====================
  // 마이크 버튼을 누른 동안만 듣고, 폰의 음성 인식(가능하면 기기 안 인식)으로 글자만 받아 웹 화면의 같은 의도 엔진에 넘김. 녹음·저장 없음.
  android.speech.SpeechRecognizer sr=null; boolean srOnDev=false;
  void spJs(String type,String text){ js("window.NV_onSpeech&&NV_onSpeech("+JSONObject.quote(type)+","+JSONObject.quote(text==null?"":text)+")"); }
  void speechCancel(){ ui.post(()->{ if(sr!=null){ try{ sr.cancel(); sr.destroy(); }catch(Exception e){} sr=null; } }); }
  void speechGo(final boolean allowOnDev){ ui.post(()->{ try{
      if(sr!=null){ try{ sr.cancel(); sr.destroy(); }catch(Exception e){} sr=null; }
      srOnDev=false;
      if(allowOnDev&&Build.VERSION.SDK_INT>=31){ try{ if(android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(MainActivity.this)){ sr=android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(MainActivity.this); srOnDev=true; } }catch(Exception e){ sr=null; srOnDev=false; } }
      if(sr==null){ if(!android.speech.SpeechRecognizer.isRecognitionAvailable(MainActivity.this)){ spJs("error","none"); return; }
        sr=android.speech.SpeechRecognizer.createSpeechRecognizer(MainActivity.this); }
      final boolean od=srOnDev;
      sr.setRecognitionListener(new android.speech.RecognitionListener(){
        public void onReadyForSpeech(Bundle b){ spJs("ready",od?"device":"service"); }
        public void onBeginningOfSpeech(){}
        public void onRmsChanged(float v){}
        public void onBufferReceived(byte[] b){}
        public void onEndOfSpeech(){ spJs("end",""); }
        public void onError(int e){
          // 기기 안 인식에 한국어가 없으면 폰 기본 음성 인식으로 한 번만 다시
          if(od&&(e==12||e==13||e==android.speech.SpeechRecognizer.ERROR_CLIENT)){ speechGo(false); return; }
          String c=e==android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"perm":(e==android.speech.SpeechRecognizer.ERROR_NO_MATCH||e==android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT)?"nomatch":(e==android.speech.SpeechRecognizer.ERROR_NETWORK||e==android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT)?"network":"e"+e;
          spJs("error",c); }
        public void onResults(Bundle b){ ArrayList<String> r=b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION); spJs("final",r!=null&&!r.isEmpty()?r.get(0):""); }
        public void onPartialResults(Bundle b){ ArrayList<String> r=b.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION); if(r!=null&&!r.isEmpty()&&r.get(0)!=null&&r.get(0).length()>0) spJs("partial",r.get(0)); }
        public void onEvent(int t,Bundle b){}
      });
      Intent i=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
      i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
      i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");
      i.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ko-KR");
      i.putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
      i.putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
      i.putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS,1);
      i.putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE,getPackageName());
      sr.startListening(i);
    }catch(Exception e){ spJs("error","e-start"); } }); }

  class Bridge {
    // v29 알림 배지·미리보기
    @JavascriptInterface public boolean notifAccess(){ return notifGranted(); }
    @JavascriptInterface public boolean notifConnected(){ return NotifService.inst!=null; }
    @JavascriptInterface public void notifOpenAccess(){ ui.post(()->{ Intent i;
      if(Build.VERSION.SDK_INT>=30){ i=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS); i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,new ComponentName(MainActivity.this,NotifService.class).flattenToString()); }
      else i=new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
      try{ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);}catch(Exception e){ startI(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); } }); }
    @JavascriptInterface public String notifSummary(){ return NotifService.summary(); }
    @JavascriptInterface public String notifList(String pkg){ return NotifService.list(pkg); }
    @JavascriptInterface public boolean notifOpen(String key){
      android.service.notification.StatusBarNotification s=NotifService.byKey(key); if(s==null) return false;
      android.app.PendingIntent pi=s.getNotification().contentIntent;
      if(pi!=null){ try{ Bundle opt=null; if(Build.VERSION.SDK_INT>=34){ android.app.ActivityOptions ao=android.app.ActivityOptions.makeBasic(); ao.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED); opt=ao.toBundle(); }
          pi.send(MainActivity.this,0,null,null,null,null,opt);
          if((s.getNotification().flags&android.app.Notification.FLAG_AUTO_CANCEL)!=0){ try{ NotifService.inst.cancelNotification(key);}catch(Exception e){} } return true; }catch(Exception e){} }
      try{ Intent i=getPackageManager().getLaunchIntentForPackage(s.getPackageName()); if(i!=null){ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; } }catch(Exception e){} return false; }
    // v29 빠른 설정
    @JavascriptInterface public String qsState(){ return qsJson(); }
    @JavascriptInterface public String qsAct(String k){
      if("wifi".equals(k)){ startI(new Intent(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_WIFI:Settings.ACTION_WIFI_SETTINGS)); return "panel"; }
      if("data".equals(k)){ startI(new Intent(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_INTERNET_CONNECTIVITY:Settings.ACTION_DATA_ROAMING_SETTINGS)); return "panel"; }
      if("bt".equals(k)){ startI(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return "settings"; }
      if("air".equals(k)){ startI(new Intent(Build.VERSION.SDK_INT>=29?Settings.Panel.ACTION_INTERNET_CONNECTIVITY:Settings.ACTION_AIRPLANE_MODE_SETTINGS)); return "panel"; }
      if("loc".equals(k)){ startI(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); return "settings"; }
      if("save".equals(k)){ startI(new Intent(Build.VERSION.SDK_INT>=22?Settings.ACTION_BATTERY_SAVER_SETTINGS:Intent.ACTION_POWER_USAGE_SUMMARY)); return "settings"; }
      if("hot".equals(k)){ startI(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); return "settings"; }
      if("sound".equals(k)){ try{ AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE); am.setRingerMode(am.getRingerMode()==AudioManager.RINGER_MODE_NORMAL?AudioManager.RINGER_MODE_VIBRATE:AudioManager.RINGER_MODE_NORMAL); return "ok"; }catch(Exception e){ startI(new Intent(Settings.ACTION_SOUND_SETTINGS)); return "settings"; } }
      if("rot".equals(k)){ if(!Settings.System.canWrite(MainActivity.this)) return "needWrite";
        try{ ContentResolver cr=getContentResolver(); int v=Settings.System.getInt(cr,Settings.System.ACCELEROMETER_ROTATION,0); Settings.System.putInt(cr,Settings.System.ACCELEROMETER_ROTATION,v!=0?0:1); return "ok"; }catch(Exception e){ return "fail"; } }
      if("dnd".equals(k)){ android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE); if(!nm.isNotificationPolicyAccessGranted()) return "needDnd";
        try{ boolean on=nm.getCurrentInterruptionFilter()>android.app.NotificationManager.INTERRUPTION_FILTER_ALL; nm.setInterruptionFilter(on?android.app.NotificationManager.INTERRUPTION_FILTER_ALL:android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY); return "ok"; }catch(Exception e){ return "fail"; } }
      return "no"; }
    @JavascriptInterface public void qsGrant(String which){ ui.post(()->{ if("write".equals(which)){ startI(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName()))); }
      else if("dnd".equals(which)) startI(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); }); }
    /** 화면 밝기: 시스템 설정 쓰기 허용이면 폰 전체 밝기, 아니면 이 화면만 */
    @JavascriptInterface public String sysBright(final float v){ if(Settings.System.canWrite(MainActivity.this)){ try{ ContentResolver cr=getContentResolver(); Settings.System.putInt(cr,Settings.System.SCREEN_BRIGHTNESS_MODE,0); Settings.System.putInt(cr,Settings.System.SCREEN_BRIGHTNESS,Math.max(3,Math.min(255,Math.round(v*255))));
        ui.post(()->{ WindowManager.LayoutParams lp=getWindow().getAttributes(); lp.screenBrightness=-1f; getWindow().setAttributes(lp); }); return "sys"; }catch(Exception e){} }
      brightness(v); return "win"; }
    // v29 홈 배치 백업·복원 (SAF)
    @JavascriptInterface public void bakExport(final String name,final String json){ pendExport=json; ui.post(()->{ try{ Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json"); i.putExtra(Intent.EXTRA_TITLE,name); startActivityForResult(i,REQ_EXP);}catch(Exception e){ pendExport=null; js("window.NV_onBak&&NV_onBak('exp',0,'')"); } }); }
    @JavascriptInterface public void bakImport(){ ui.post(()->{ try{ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*"); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"}); startActivityForResult(i,REQ_IMP);}catch(Exception e){ js("window.NV_onBak&&NV_onBak('imp',0,'')"); } }); }
    @JavascriptInterface public boolean autoBakSave(String json){ try{ java.io.FileOutputStream o=openFileOutput("autobackup.json",MODE_PRIVATE); o.write(json.getBytes("UTF-8")); o.close(); return true; }catch(Exception e){ return false; } }
    @JavascriptInterface public String autoBakLoad(){ try{ java.io.FileInputStream in=openFileInput("autobackup.json"); ByteArrayOutputStream bo=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))>0) bo.write(b,0,n); in.close(); return bo.toString("UTF-8"); }catch(Exception e){ return ""; } }
    // v29 연락처 검색
    @JavascriptInterface public String contactsPerm(){ return checkSelfPermission(android.Manifest.permission.READ_CONTACTS)==PackageManager.PERMISSION_GRANTED?"yes":"no"; }
    @JavascriptInterface public void contactsAsk(){ ui.post(()->{ try{ requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS},REQ_CON);}catch(Exception e){ js("window.NV_onConPerm&&NV_onConPerm('no')"); } }); }
    @JavascriptInterface public String contactsAll(){ return contactsJson(); }
    @JavascriptInterface public void dial(String num){ String n=num==null?"":num.replaceAll("[^0-9+*#]",""); Intent i=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+n));
      // v33: 뉴니버스 전화가 깔려 있으면 그 앱 키패드로 (기본 전화 앱이 아니어도)
      try{ Intent t=new Intent(i).setPackage("kr.newniverse.phone"); if(t.resolveActivity(getPackageManager())!=null) i=t; }catch(Exception e){}
      startI(i); }
    @JavascriptInterface public void sms(String num){ String n=num==null?"":num.replaceAll("[^0-9+]",""); Intent i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+n));
      // v37: 뉴니버스 문자가 깔려 있으면 그 앱으로 (기본 문자 앱이 아니어도) · 전화와 같은 방식
      try{ Intent t=new Intent(i).setPackage("kr.newniverse.sms"); if(t.resolveActivity(getPackageManager())!=null) i=t; }catch(Exception e){}
      startI(i); }
    @JavascriptInterface public void openContact(String id){ try{ startI(new Intent(Intent.ACTION_VIEW,Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_URI,id.replaceAll("[^0-9]","")))); }catch(Exception e){} }
    /** 안드로이드 설정 화면 바로가기 (액션 이름으로) */
    @JavascriptInterface public void openAction(String act){ if(act==null||!act.startsWith("android.settings.")) return; startI(new Intent(act)); }
    // v29 최근 앱: 추가판에서 접근성이 켜져 있으면 폰의 진짜 최근 앱 화면을 열 수 있음
    @JavascriptInterface public boolean sysRecents(){ if(Flavor.BASIC) return false;
      try{ return (Boolean)Class.forName(getPackageName()+".LockService").getMethod("recents").invoke(null); }catch(Throwable e){ return false; } }
    // v28 가까운 정류장 버스 도착
    @JavascriptInterface public String busPerm(){ return BusInfo.perm(MainActivity.this); }
    @JavascriptInterface public void busAskPerm(){ ui.post(()->{ try{ requestPermissions(new String[]{BusInfo.FINE,BusInfo.COARSE},REQ_LOC);}catch(Exception e){ js("window.NV_onBusPerm&&NV_onBusPerm('none')"); } }); }
    @JavascriptInterface public String busKeyInfo(){ return BusInfo.keyInfo(MainActivity.this); }
    @JavascriptInterface public void busKeySet(String k){ BusInfo.setKey(MainActivity.this,k); }
    @JavascriptInterface public void busLocate(final String id){ final String sid=id.replaceAll("[^A-Za-z0-9]",""); ui.post(()->BusInfo.locate(MainActivity.this,ui,r->js("window.NV_onBusLoc&&NV_onBusLoc('"+sid+"',"+r+")"))); }
    @JavascriptInterface public void busFetch(final String id,String op,String a,String b){ final String sid=id.replaceAll("[^A-Za-z0-9]",""); BusInfo.fetch(MainActivity.this,op,a,b,r->js("window.NV_onBusData&&NV_onBusData('"+sid+"',"+r+")")); }
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
    /** 시스템 내비 방식(0=3버튼 1=2버튼 2=제스처)과 아래 인셋(dp = 웹 CSS px) */
    @JavascriptInterface public String sysNav(){ int mode=-1; try{ mode=Settings.Secure.getInt(getContentResolver(),"navigation_mode",-1); }catch(Exception e){}
      return "{\"mode\":"+mode+",\"nav\":"+insNav+",\"gest\":"+insGest+",\"mand\":"+insMand+"}"; }
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
    @JavascriptInterface public String info(){ String vn=""; try{ vn=getPackageManager().getPackageInfo(getPackageName(),0).versionName; }catch(Exception e){} if(vn==null) vn=""; vn=vn.replaceAll("[^A-Za-z0-9._\\-]","");
      return "{\"model\":\""+Build.MANUFACTURER+" "+Build.MODEL+"\",\"android\":\""+Build.VERSION.RELEASE+"\",\"sdk\":"+Build.VERSION.SDK_INT+",\"app\":\""+vn+"\"}"; }
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
    @JavascriptInterface public String speechAvail(){ try{ boolean od=Build.VERSION.SDK_INT>=31&&android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(MainActivity.this); if(od) return "device"; return android.speech.SpeechRecognizer.isRecognitionAvailable(MainActivity.this)?"service":"none"; }catch(Exception e){ return "none"; } }
    @JavascriptInterface public String micPerm(){ return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?"yes":"no"; }
    @JavascriptInterface public String speechStart(){ try{
        if(!android.speech.SpeechRecognizer.isRecognitionAvailable(MainActivity.this)&&!(Build.VERSION.SDK_INT>=31&&android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(MainActivity.this))) return "none";
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ ui.post(()->{ try{ requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},REQ_MIC);}catch(Exception e){ spJs("error","perm"); } }); return "perm"; }
        speechGo(true); return "ok"; }catch(Exception e){ return "none"; } }
    @JavascriptInterface public void speechStop(){ ui.post(()->{ if(sr!=null){ try{ sr.stopListening(); }catch(Exception e){} } }); }
    @JavascriptInterface public void speechCancelJs(){ speechCancel(); }
    @JavascriptInterface public boolean isDefaultHome(){ Intent i=new Intent(Intent.ACTION_MAIN); i.addCategory(Intent.CATEGORY_HOME); ResolveInfo r=getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY); return r!=null&&r.activityInfo!=null&&getPackageName().equals(r.activityInfo.packageName); }
  }
}

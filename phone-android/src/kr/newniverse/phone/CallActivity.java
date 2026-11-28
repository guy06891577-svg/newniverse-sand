package kr.newniverse.phone;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.view.WindowManager;
import android.webkit.WebView;
import org.json.*;

/** 걸려오는 전화 · 통화 중 화면. 잠금 화면 위에도 뜨고, 귀에 대면 화면을 끔 */
public class CallActivity extends Activity implements CallService.Listener {
  static final String ACTION_ANSWER="kr.newniverse.phone.ANSWER";
  WebView w; PowerManager.WakeLock prox; boolean hold; final Handler h=new Handler(Looper.getMainLooper());

  @SuppressWarnings("deprecation")
  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    if(Build.VERSION.SDK_INT>=27){ setShowWhenLocked(true); setTurnScreenOn(true); }
    else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    try{ PowerManager pm=getSystemService(PowerManager.class); if(pm!=null&&pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) prox=pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,"nvphone:prox"); }catch(Exception e){}
    w=new WebView(this); setContentView(w);
    Nv.setupWeb(this,w,new Bridge(this),"file:///android_asset/www/index.html?mode=call");
    CallService.listener=this;
    handle(getIntent());
  }
  @Override protected void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); handle(i); push(); }
  private void handle(Intent i){ if(i!=null&&ACTION_ANSWER.equals(i.getAction())) CallService.answer(); }
  @Override protected void onResume(){ super.onResume(); CallService.listener=this; push(); }
  @Override public void onCallsChanged(){ h.post(this::push); }
  private void push(){
    if(w==null) return;
    String s=CallService.stateJson(this);
    w.evaluateJavascript("window.NV_call&&NV_call("+s+")",null);
    boolean wantProx=false;
    try{ JSONObject o=new JSONObject(s); JSONArray a=o.getJSONArray("calls"); String route=o.getJSONObject("audio").optString("route","earpiece");
      boolean active=false,ring=o.optLong("ringing")!=0; for(int k=0;k<a.length();k++) if("active".equals(a.getJSONObject(k).optString("state"))||"dialing".equals(a.getJSONObject(k).optString("state"))) active=true;
      wantProx=active&&!ring&&"earpiece".equals(route); }catch(Exception e){}
    if(prox!=null){ try{ if(wantProx&&!prox.isHeld()) prox.acquire(3*60*60*1000L); else if(!wantProx&&prox.isHeld()) prox.release(); }catch(Exception e){} }
    if(!CallService.inCall()){ h.removeCallbacks(fin); if(!hold) h.postDelayed(fin,1800); } else h.removeCallbacks(fin);
  }
  private final Runnable fin=()->{ if(!CallService.inCall()&&!hold) finish(); };
  // v6 통화 메모 카드가 떠 있는 동안은 닫지 않음 (안전하게 3분 뒤에는 닫음)
  private final Runnable holdTimeout=()->{ hold=false; VoiceNote.cancel(); if(!CallService.inCall()) finish(); };
  void setHold(boolean b){ hold=b; h.removeCallbacks(holdTimeout); if(b){ h.removeCallbacks(fin); h.postDelayed(holdTimeout,3*60000L); } else if(!CallService.inCall()){ h.removeCallbacks(fin); h.postDelayed(fin,300); } }
  void js(String s){ if(w!=null) w.post(()->{ if(w!=null) w.evaluateJavascript(s,null); }); }
  @Override public void onRequestPermissionsResult(int rq,String[] p,int[] g){
    boolean ok=g!=null&&g.length>0; if(g!=null) for(int x:g) if(x!=android.content.pm.PackageManager.PERMISSION_GRANTED) ok=false;
    if(rq==MainActivity.RQ_MIC) js("window.NV_perm&&NV_perm('mic',"+ok+")");
  }
  @SuppressWarnings("deprecation")
  @Override public void onBackPressed(){ moveTaskToBack(true); }   // 뒤로 가기로 통화가 끊기지 않게
  @Override protected void onDestroy(){
    if(CallService.listener==this) CallService.listener=null;
    VoiceNote.cancel();
    try{ if(prox!=null&&prox.isHeld()) prox.release(); }catch(Exception e){}
    h.removeCallbacksAndMessages(null);
    if(w!=null){ w.destroy(); w=null; }
    super.onDestroy();
  }
}

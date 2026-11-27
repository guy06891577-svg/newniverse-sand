package kr.newniverse.memo;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.WebView;
import java.io.File;
import java.util.*;
import org.json.JSONObject;

/**
 * 뉴니버스 빠른 메모. 측면 버튼 두 번 누르기로 열리면 (잠금 화면 위에서도) 바로 듣기 시작해요.
 * 화면은 assets/www/index.html (하이브리드 WebView). 지난 메모를 보는 화면(목록·설정)은 잠금을 푼 뒤에만 열려요.
 */
public class MainActivity extends Activity implements Dictation.Events {
  static final String EXTRA_NOTE="noteId";
  static final int REQ_MIC=41, REQ_NOTIF=42, REQ_CAL=43;

  /** 잠금 확인·풀기 (시험에서 바꿔 끼울 수 있게 분리) */
  interface Gate {
    boolean unlocked(Activity a);
    void requestUnlock(Activity a,Runnable ok,Runnable no);
  }
  static Gate gate=new Gate(){
    @Override public boolean unlocked(Activity a){ KeyguardManager km=(KeyguardManager)a.getSystemService(KEYGUARD_SERVICE); return km==null||!km.isKeyguardLocked(); }
    @Override public void requestUnlock(Activity a,Runnable ok,Runnable no){
      KeyguardManager km=(KeyguardManager)a.getSystemService(KEYGUARD_SERVICE);
      if(km==null||!km.isKeyguardLocked()){ ok.run(); return; }
      try{
        km.requestDismissKeyguard(a,new KeyguardManager.KeyguardDismissCallback(){
          @Override public void onDismissSucceeded(){ ok.run(); }
          @Override public void onDismissCancelled(){ no.run(); }
          @Override public void onDismissError(){ no.run(); }
        });
      }catch(Throwable e){ no.run(); }
    }
  };

  WebView w; Bridge br; NoteStore store; Prefs prefs; Dictation dict;
  boolean pageReady, resumed, autoStart, restartOnResume, listenKeepOn;
  final ArrayList<String> pending=new ArrayList<>();
  final Set<Long> recent=new LinkedHashSet<>(); // 이번에 방금 저장한 메모(잠금 화면에서도 알림 맞추기 가능)
  String view="rec", dstate="idle", committed="", partial="", lastSaved="";
  long noteToOpen; long createdAt, lastLevelPush; long pendingRemind, pendingCal;
  /** 시험에서 확인하는 값 */
  boolean showWhenLockedSet, turnScreenOnSet; int unlockRequests; String lastNotice="";
  final ArrayList<JSONObject> log=new ArrayList<>(); // 마지막 이벤트들(시험이 확인)
  final Handler main=new Handler(Looper.getMainLooper());

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    createdAt=System.currentTimeMillis();
    prefs=new Prefs(this); store=new NoteStore(getFilesDir());
    Dictation.recoverMute(this,prefs);
    applyLockFlags();
    w=new WebView(this); setContentView(w);
    br=new Bridge(this);
    dict=new Dictation(this,this,prefs,store);
    Nv.setupWeb(this,w,br,"file:///android_asset/www/index.html");
    readIntent(getIntent());
  }

  /** 잠금 화면 위에 띄우고 화면 켜기 */
  void applyLockFlags(){
    try{
      if(Build.VERSION.SDK_INT>=27){ setShowWhenLocked(true); setTurnScreenOn(true); }
      else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }catch(Throwable e){}
    showWhenLockedSet=true; turnScreenOnSet=true;
  }
  /** 지난 메모를 보는 동안에는 잠금 화면 위 표시를 끔(다시 잠기면 화면이 가려지게) */
  void dropLockFlags(){
    try{ if(Build.VERSION.SDK_INT>=27) setShowWhenLocked(false); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED); }catch(Throwable e){}
    showWhenLockedSet=false;
  }

  void readIntent(Intent i){
    long n=i!=null?i.getLongExtra(EXTRA_NOTE,0):0;
    if(n>0){ noteToOpen=n; autoStart=false; }
    else { noteToOpen=0; autoStart=true; }
  }

  @Override protected void onNewIntent(Intent i){
    super.onNewIntent(i); setIntent(i);
    applyLockFlags();
    long n=i!=null?i.getLongExtra(EXTRA_NOTE,0):0;
    if(n>0){ noteToOpen=n; if(pageReady) openPrivate("note"); return; }
    if(dict.listening()){ // 또 두 번 누르면 끝내고 저장 (켜자마자 들어온 중복 신호는 무시)
      if(dict.sinceStart()>1500) dict.stopFromUser();
      return;
    }
    if(!view.equals("rec")) leavePrivate();
    autoStart=true;
    if(resumed) startDictation();
  }

  @Override protected void onResume(){
    super.onResume(); resumed=true;
    if(autoStart||restartOnResume){
      autoStart=false; restartOnResume=false;
      if(view.equals("rec")&&!dict.busy()) startDictation();
    }
    maybeAskNotif();
    maybeAskCal();
    if(!locked()&&MemoCal.granted(this)) retryPendingCal();
    if(!gate.unlocked(this)&&!view.equals("rec")) leavePrivate(); // 다시 잠겼는데 지난 메모가 남아 있으면 가림
  }

  @Override protected void onStop(){
    resumed=false;
    boolean early=System.currentTimeMillis()-createdAt<8000;
    boolean had=dict!=null&&dict.busy();
    if(dict!=null){
      boolean nothing=dict.committed.length()==0&&dict.partial.isEmpty()&&!dict.audioMode;
      dict.stopAndSave();
      if(had&&nothing&&early) restartOnResume=true; // Google 음성 안내 같은 다른 화면이 잠깐 가린 경우: 돌아오면 다시 듣기
    }
    if(!view.equals("rec")) leavePrivate();
    if(br!=null) br.stopPlay();
    keepOn(false);
    super.onStop();
  }

  @Override protected void onDestroy(){
    if(dict!=null) dict.release();
    if(br!=null) br.close();
    if(w!=null){ w.destroy(); w=null; }
    super.onDestroy();
  }

  @SuppressWarnings("deprecation")
  @Override public void onBackPressed(){
    if(w==null){ super.onBackPressed(); return; }
    w.evaluateJavascript("window.NV_back?NV_back():false",r->{ if(!"true".equals(r)) { if(dict!=null) dict.stopAndSave(); finish(); } });
  }

  // ---------- 듣기 ----------
  void startDictation(){
    if(!view.equals("rec")) return;
    if(!dict.hasMic()){ onNeedMic(); return; }
    committed=""; partial=""; dict.start();
  }

  boolean locked(){ return !gate.unlocked(this); }

  /** 처음 잠금을 푼 뒤에 알림 표시 권한을 한 번 물어요(알람은 권한이 없어도 걸어요). */
  void maybeAskNotif(){
    if(Build.VERSION.SDK_INT<33) return;
    if(locked()||Reminders.notifGranted(this)||prefs.askedNotif()) return;
    prefs.setAskedNotif();
    try{ requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF); }catch(Throwable e){}
  }
  /** 처음 잠금을 푼 뒤에 달력 권한을 한 번 물어요. */
  void maybeAskCal(){
    if(locked()||MemoCal.granted(this)||prefs.askedCal()) return;
    prefs.setAskedCal();
    try{ requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},REQ_CAL); }catch(Throwable e){}
  }

  @Override public void onNeedMic(){
    dstate="needmic"; boolean ask=!prefs.askedMic();
    boolean blocked=!ask&&Build.VERSION.SDK_INT>=23&&!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
    push(j("t","needmic","canAsk",ask,"blocked",blocked));
    // 잠금 화면 위에서 권한 창을 띄우면 삼성이 잠금 해제를 물을 수 있어요. 잠금을 푼 뒤에만 물어요.
    if(ask&&!locked()&&Build.VERSION.SDK_INT>=23){ prefs.setAskedMic(); try{ requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC); }catch(Throwable e){} }
  }
  /** 마이크 허용 창을 다시 띄움(‘허용하기’ 단추) */
  void askMic(){
    try{ requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC); }catch(Throwable e){}
  }

  @Override public void onRequestPermissionsResult(int code,String[] perms,int[] res){
    boolean ok=res!=null&&res.length>0&&res[0]==PackageManager.PERMISSION_GRANTED;
    if(code==REQ_MIC){
      if(ok){ if(view.equals("rec")&&!dict.busy()) startDictation(); }
      else { dstate="needmic"; push(j("t","needmic","canAsk",false,"blocked",!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))); }
    } else if(code==REQ_NOTIF){
      long id=pendingRemind; pendingRemind=0;
      if(id==0) return;
      if(ok) push(br.remindJson(id,true,true)); else push(j("t","remind","ok",false,"state","notif-denied","id",String.valueOf(id)));
    } else if(code==REQ_CAL){
      boolean calOk=true;
      if(res!=null) for(int v:res) if(v!=PackageManager.PERMISSION_GRANTED) calOk=false;
      long id=pendingCal; pendingCal=0;
      if(calOk){ retryPendingCal(); if(id>0) push(br.calJson(id)); }
      else if(id>0) push(j("t","cal","ok",false,"state","need-perm","id",String.valueOf(id),"line","달력 권한 필요"));
    }
  }

  @Override public void onStatus(String s,String detail){
    dstate=s;
    if(s.equals("listening")||s.equals("audio")||s.equals("starting")) keepOn(true);
    push(j("t","status","state",s,"detail",detail));
  }
  @Override public void onLevel(float lv){
    long n=System.currentTimeMillis(); if(n-lastLevelPush<60) return; lastLevelPush=n;
    push(j("t","level","v",Math.round(lv*100)/100.0));
  }
  @Override public void onText(String c,String p){ committed=c; partial=p; push(j("t","text","c",c,"p",p)); }

  @Override public void onFinished(Dictation.Result r){
    keepOn(false);
    if(r.kind.equals("speech")||r.kind.equals("audio")){
      Note n=new Note(); n.text=r.text; n.createdAt=System.currentTimeMillis(); n.durationMs=r.durationMs; n.seg=r.seg;
      n.kind=r.kind.equals("audio")?Note.AUDIO:Note.SPEECH; n.audio=r.audio!=null?r.audio.getName():"";
      try{ store.add(n); }catch(Throwable e){ push(j("t","error","reason","save")); dstate="idle"; return; }
      recent.add(n.id);
      Vib.saved(this,prefs);
      JSONObject o=j("t","saved","id",String.valueOf(n.id),"text",n.text,"dur",n.durationMs,"kind",n.kind,"seg",n.seg,"cont",r.cont,"autoClose",prefs.autoClose());
      try{
        if(n.kind.equals(Note.SPEECH)&&!r.cont){
          JSONObject rem=autoRemind(n); if(rem!=null) o.put("remind",rem);
          JSONObject cal=autoCal(n); if(cal!=null) o.put("cal",cal);
        }
        if(r.reason!=null&&!r.reason.isEmpty()) o.put("reason",r.reason);
      }catch(Exception e){}
      lastSaved=o.toString(); dstate=r.cont?"listening":"saved"; committed=""; partial="";
      push(o);
    } else if(r.kind.equals("none")){
      dstate="none"; Vib.once(this,prefs,120); push(j("t","none","reason",r.reason==null?"":r.reason));
    } else { dstate="error"; Vib.once(this,prefs,200); push(j("t","error","reason",r.reason)); }
  }
  @Override public void onNotice(String code,String msg){
    lastNotice=code; if(code.equals("consent")) restartOnResume=true;
    push(j("t","notice","code",code,"msg",msg));
  }

  void keepOn(boolean on){
    if(on==listenKeepOn) return; listenKeepOn=on;
    try{ if(on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }catch(Throwable e){}
  }

  // ---------- 지난 메모 보기(잠금 풀어야 함) ----------
  void openPrivate(String v){
    if(dict.listening()) dict.stopFromUser();
    unlockRequests++;
    gate.requestUnlock(this,()->{ dropLockFlags(); view=v; push(j("t","unlocked","view",v,"note",String.valueOf(noteToOpen))); noteToOpen=0; },
      ()->push(j("t","lockdenied")));
  }
  void leavePrivate(){
    view="rec"; applyLockFlags(); push(j("t","left"));
  }

  /** 시간 말이나 ‘N초/분 뒤’가 있으면 묻지 않고 알림을 맞추고, 확인 카드에 넣을 결과를 돌려줘요. 없으면 「알림은 맞추지 않았어요」. 잠금 화면에서도 잠금을 풀지 않아요. */
  JSONObject autoRemind(Note n){
    TimePhrase.Hit h=TimePhrase.find(n.text,n.createdAt);
    if(h==null) return j("id",String.valueOf(n.id),"ok",false,"state","notime","line","알림은 맞추지 않았어요","when",0,"label","");
    JSONObject o=j("id",String.valueOf(n.id),"label",TimePhrase.confirmWhen(h.when),"defaulted",h.defaulted,"rolled",h.rolled,"past",h.past,"when",h.when);
    try{
      if(h.past){ o.put("ok",false).put("state","past").put("line",h.confirmPast()); return o; }
      boolean notif=Reminders.notifGranted(this);
      if(!notif&&!locked()){ pendingRemind=n.id; try{ requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIF); }catch(Throwable e){} }
      int r=Reminders.schedule(this,n,h.when,true); // 알림 표시 권한이 없어도 알람은 걸어요
      if(r==Reminders.OK_EXACT||r==Reminders.OK_INEXACT) store.update(n);
      AlarmLog.add(this,n,h,r,Reminders.lastMethod,notif,n.remindExact);
      if(!notif){
        String line=h.confirmSet();
        if(r==Reminders.OK_EXACT||r==Reminders.OK_INEXACT) line+=" · 알람은 맞춰 두었어요. 알림 표시가 꺼져 있으면 소리가 안 날 수 있어요";
        o.put("ok",false).put("state","need-notif").put("askSettings",!locked()).put("line",line).put("method",Reminders.lastMethod); return o;
      }
      switch(r){
        case Reminders.OK_EXACT:
          o.put("ok",true).put("state","exact").put("line",h.confirmSet()).put("method",Reminders.lastMethod); return o;
        case Reminders.OK_INEXACT:
          o.put("ok",true).put("state","inexact").put("line",h.confirmSet()+" · 정확한 알람 허용이 없어 몇 분 어긋날 수 있어요").put("method",Reminders.lastMethod); return o;
        case Reminders.PAST:
          o.put("ok",false).put("state","past").put("line",h.confirmPast()); return o;
        default:
          o.put("ok",false).put("state","error").put("line",h.confirmSet()); return o;
      }
    }catch(Exception e){ try{ o.put("ok",false).put("state","error"); }catch(Exception x){} return o; }
  }

  /** 일정 말이 있으면 달력에 넣고, 확인 카드에 넣을 결과를 돌려줘요. 알림은 따로 맞춰 둬요. */
  JSONObject autoCal(Note n){
    ApptPhrase.Hit h=ApptPhrase.find(n.text,n.createdAt);
    if(h==null) return null;
    JSONObject o=j("id",String.valueOf(n.id),"title",h.title,"location",h.location,"when",h.when.when,"allDay",h.allDay,"line",h.line());
    try{
      if(!MemoCal.granted(this)){
        n.calNeedPerm=true; n.calTitle=h.title; n.calLocation=h.location; n.calStart=h.when.when; store.update(n);
        AlarmLog.addCal(this,n,"need-perm",h.title,h.when.when,false,"");
        if(!locked()){ pendingCal=n.id; try{ requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR},REQ_CAL); }catch(Throwable e){} }
        o.put("ok",false).put("state","need-perm").put("askSettings",!locked()).put("line","달력 권한 필요");
        return o;
      }
      long eid=MemoCal.insert(this,h);
      if(eid>0){
        n.calEventId=eid; n.calNeedPerm=false; n.calTitle=h.title; n.calLocation=h.location; n.calStart=h.when.when; store.update(n);
        AlarmLog.addCal(this,n,"ok",h.title,h.when.when,true,"");
        o.put("ok",true).put("state","ok").put("eventId",eid); return o;
      }
      AlarmLog.addCal(this,n,"error",h.title,h.when.when,false,MemoCal.err(this));
      o.put("ok",false).put("state","error").put("line",h.line()); return o;
    }catch(Exception e){ try{ o.put("ok",false).put("state","error"); }catch(Exception x){} return o; }
  }

  /** 권한이 생긴 뒤, ‘달력 권한 필요’로 남겨 둔 메모를 달력에 넣어요. */
  void retryPendingCal(){
    if(!MemoCal.granted(this)) return;
    try{
      for(Note n:store.all()){
        if(!n.calNeedPerm||n.calEventId>0) continue;
        ApptPhrase.Hit h=ApptPhrase.find(n.text,n.createdAt);
        if(h==null){ n.calNeedPerm=false; store.update(n); continue; }
        long eid=MemoCal.insert(this,h);
        if(eid>0){ n.calEventId=eid; n.calNeedPerm=false; n.calTitle=h.title; n.calLocation=h.location; n.calStart=h.when.when; store.update(n); AlarmLog.addCal(this,n,"ok",h.title,h.when.when,true,""); }
      }
    }catch(Throwable e){}
  }

  void close(){ if(dict!=null) dict.stopAndSave(); finishAndRemoveTask(); }

  // ---------- 화면(JS) 쪽으로 보내기 ----------
  static JSONObject j(Object... kv){
    JSONObject o=new JSONObject();
    try{ for(int i=0;i+1<kv.length;i+=2) o.put((String)kv[i],kv[i+1]); }catch(Exception e){}
    return o;
  }
  void onPageReady(){
    pageReady=true;
    for(String s:pending) run(s);
    pending.clear();
    if(noteToOpen>0&&view.equals("rec")) openPrivate("note");
  }
  void push(JSONObject o){
    if(!"level".equals(o.optString("t"))){ log.add(o); if(log.size()>200) log.remove(0); }
    String js="window.NV_ev&&NV_ev("+o.toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029")+")";
    if(!pageReady){ if(!"level".equals(o.optString("t"))&&pending.size()<60) pending.add(js); return; }
    run(js);
  }
  void run(String js){ final WebView x=w; if(x!=null) x.post(()->{ if(w!=null) w.evaluateJavascript(js,null); }); }

  String snapshot(){
    JSONObject o=j("state",dstate,"c",committed,"p",partial,"view",view,"autoClose",prefs.autoClose(),"ver",Nv.VER);
    try{ if(!lastSaved.isEmpty()&&dstate.equals("saved")) o.put("last",new JSONObject(lastSaved)); }catch(Exception e){}
    return o.toString();
  }
}

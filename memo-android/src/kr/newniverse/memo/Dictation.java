package kr.newniverse.memo;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.io.File;
import java.util.ArrayList;

/**
 * 듣고 받아 적는 일꾼. 한 번 시작하면 말이 끊겨도(인식기가 알아서 끝내도) 바로 다시 들어서 이어 붙이고,
 * ‘말 끊긴 뒤 silenceMs’ · 누름 · ‘한 덩이 60초(자동 이어 받기, 최대 5덩이)’ 중 먼저 오는 때 저장해요.
 * 받아쓰기를 못 쓰면(인식기 없음·인터넷/언어 팩 없음) 소리 파일(m4a)로만 저장해요.
 * 잠금 화면이거나 비행기 모드(네트워크 없음)에서는 클라우드 인식기를 시작하지 않아요.
 * (클라우드 음성 서비스가 잠금 화면용이 아닌 안내 창을 띄워 삼성이 잠금 해제를 물을 수 있기 때문)
 * 그때는 폰 안 인식기만 쓰고, 없거나 실패하면 잠금을 풀지 않은 채 소리로 저장해요.
 * 모든 호출은 메인 스레드에서.
 */
final class Dictation {
  interface Events {
    void onStatus(String state,String detail); // starting · listening · audio · stopping
    void onLevel(float lv);                    // 0~1 소리 크기
    void onText(String committed,String partial);
    void onFinished(Result r);
    void onNeedMic();
    void onNotice(String code,String msg);
  }
  static final class Result {
    String kind="none";      // speech · audio · none · error
    String text="", reason="";
    long durationMs; File audio; boolean cont; int seg;
  }
  static final int IDLE=0, STARTING=1, LISTENING=2, STOPPING=3;

  final Context ctx; final Events ev; final Prefs p; final NoteStore store;
  final Handler h=new Handler(Looper.getMainLooper());
  int state=IDLE; boolean audioMode, forceAudio, onDevice;
  SpeechRecognizer sr; Intent recIntent;
  final StringBuilder committed=new StringBuilder(); String partial="";
  ListenPolicy pol; AudioCapture cap=new AudioCapture(); File capFile; boolean capWanted;
  long segStart, stopDeadline, cycleStart; int seg, busyRetry, clientRetry; boolean readyOnce, cont;
  boolean muted; String audioReason="";
  /** 시험에서 시계를 바꿔 끼우기 위한 구멍 */
  long fakeNow=-1; long now(){ return fakeNow>=0?fakeNow:android.os.SystemClock.elapsedRealtime(); } // 시계를 바꿔도 영향 없게 단조 시계

  Dictation(Context c,Events e,Prefs p,NoteStore s){ ctx=c; ev=e; this.p=p; store=s; }

  boolean hasMic(){ return ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED; }
  boolean busy(){ return state!=IDLE; }
  boolean listening(){ return state==LISTENING||state==STARTING; }
  long sinceStart(){ return state==IDLE?0:now()-segStart; }

  static boolean recognizerAvailable(Context c){
    try{ if(SpeechRecognizer.isRecognitionAvailable(c)) return true; }catch(Throwable e){}
    try{ if(Build.VERSION.SDK_INT>=33&&SpeechRecognizer.isOnDeviceRecognitionAvailable(c)) return true; }catch(Throwable e){}
    return false;
  }

  /** 새로 시작(처음부터) */
  void start(){
    if(state!=IDLE) return;
    if(!hasMic()){ ev.onNeedMic(); return; }
    forceAudio=false; onDeviceOff=false; seg=0; busyRetry=0; clientRetry=0; audioReason="";
    beginSegment();
  }

  void beginSegment(){
    committed.setLength(0); partial=""; cont=false; readyOnce=false;
    pol=ListenPolicy.standard(p.silenceMs()); pol.begin(now()); segStart=now();
    state=STARTING; capWanted=false;
    h.removeCallbacks(unmuteR);
    if(p.quiet()) mute();
    boolean speech=!forceAudio&&willUseSpeech();
    if(speech){
      audioMode=false;
      if(p.keepAudio()) startCapture();
      ev.onStatus("starting","");
      startCycle();
    } else {
      audioMode=true;
      if(audioReason.isEmpty()) audioReason=speechFallbackReason();
      if(!startCapture()){ fail("audio-start"); return; }
      state=LISTENING; vibrate(45);
      ev.onStatus("audio",audioReason);
      if("no-offline".equals(audioReason)||"lang".equals(audioReason)) ev.onNotice("no-offline",NO_OFFLINE_MSG);
      else ev.onNotice("fallback-audio",audioReason);
    }
    h.removeCallbacks(tick); h.postDelayed(tick,150);
  }

  boolean startCapture(){
    capFile=new File(store.audioDir,"a"+now()+"_"+seg+".m4a");
    capWanted=cap.start(ctx,capFile);
    return capWanted;
  }

  // ---------- 인식기 ----------
  static final String NO_OFFLINE_MSG="인터넷 없이 쓰는 한국어 받아쓰기가 없어 소리로 저장했어요. 설정 › 일반 › 언어팩에서 한국어를 받을 수 있어요.";

  /** 잠금 화면이거나 비행기 모드/네트워크 없음이면 클라우드 인식기를 쓰지 않음 */
  boolean keyguardLocked(){
    try{ KeyguardManager km=(KeyguardManager)ctx.getSystemService(Context.KEYGUARD_SERVICE); return km!=null&&km.isKeyguardLocked(); }catch(Throwable e){ return false; }
  }
  boolean airplaneOn(){
    try{ return Settings.Global.getInt(ctx.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=0; }catch(Throwable e){ return false; }
  }
  boolean networkOk(){ return !airplaneOn(); } // 비행기 모드를 네트워크 없음으로 봄(잠금 화면에서는 canUseCloud 가 따로 막아요)
  boolean canUseCloud(){ return !keyguardLocked()&&networkOk(); }
  boolean onDevicePossible(){
    if(Build.VERSION.SDK_INT<33||!p.offline()||onDeviceOff) return false;
    try{ return SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx); }catch(Throwable e){ return false; }
  }
  boolean willUseSpeech(){ return onDevicePossible()||(recognizerAvailable(ctx)&&canUseCloud()); }
  String speechFallbackReason(){
    if(!onDevicePossible()&&!canUseCloud()) return "no-offline";
    if(airplaneOn()||!networkOk()) return "net";
    return "unavailable";
  }

  SpeechRecognizer make(){
    if(sr!=null) return sr;
    SpeechRecognizer r=null; onDevice=false;
    if(onDevicePossible()){
      try{ r=SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx); onDevice=true; }catch(Throwable e){ r=null; onDevice=false; }
    }
    if(r==null&&canUseCloud()){ try{ r=SpeechRecognizer.createSpeechRecognizer(ctx); }catch(Throwable e){ r=null; } }
    if(r!=null) r.setRecognitionListener(listener);
    sr=r; return r;
  }
  Intent intent(){
    Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ko-KR");
    i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
    i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);
    i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,p.offline());
    i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,ctx.getPackageName());
    i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,p.silenceMs());
    i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,p.silenceMs());
    return i;
  }
  void startCycle(){
    if(state!=STARTING&&state!=LISTENING) return;
    SpeechRecognizer r=make();
    if(r==null){ toAudio(speechFallbackReason()); return; }
    recIntent=intent(); cycleStart=now();
    try{ r.startListening(recIntent); }catch(Throwable e){ toAudio("unavailable"); }
  }

  final RecognitionListener listener=new RecognitionListener(){
    @Override public void onReadyForSpeech(Bundle b){
      if(state==IDLE) return;
      if(state==STARTING) state=LISTENING;
      if(!readyOnce){ readyOnce=true; vibrate(45); ev.onStatus("listening",onDevice?"offline":"online"); }
    }
    @Override public void onBeginningOfSpeech(){ if(pol!=null&&state!=IDLE) pol.voice(now()); }
    @Override public void onRmsChanged(float rms){
      if(state==IDLE) return;
      if(rms>5f&&pol!=null) pol.voice(now());
      ev.onLevel(Math.max(0f,Math.min(1f,(rms+2f)/12f)));
    }
    @Override public void onBufferReceived(byte[] b){}
    @Override public void onEndOfSpeech(){}
    @Override public void onPartialResults(Bundle b){
      if(state==IDLE) return;
      String t=first(b); if(t.isEmpty()||t.equals(partial)) return;
      partial=t; pol.voice(now()); ev.onText(committed.toString(),partial);
    }
    @Override public void onResults(Bundle b){
      if(state==IDLE) return;
      String t=first(b); if(t.isEmpty()) t=partial;
      if(!t.isEmpty()){ if(committed.length()>0) committed.append(' '); committed.append(t); pol.voice(now()); }
      partial=""; ev.onText(committed.toString(),"");
      if(state==STOPPING){ complete(); return; }
      next();
    }
    @Override public void onError(int code){
      if(state==IDLE) return;
      if(state==STOPPING){ complete(); return; }
      switch(code){
        case SpeechRecognizer.ERROR_NO_MATCH: case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
          if(!partial.isEmpty()){ if(committed.length()>0) committed.append(' '); committed.append(partial); partial=""; ev.onText(committed.toString(),""); }
          next(); return;
        case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
          if(busyRetry++<2){ reset(); h.postDelayed(()->startCycle(),400); return; }
          toAudio("busy"); return;
        case SpeechRecognizer.ERROR_CLIENT:
          if(clientRetry++<2){ reset(); h.postDelayed(()->startCycle(),300); return; }
          consentHint(); toAudio("client"); return;
        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
          if(!hasMic()){ cancel(); ev.onNeedMic(); return; }
          consentHint(); toAudio("perm"); return;
        case SpeechRecognizer.ERROR_AUDIO:
          if(capWanted){ // 소리 보관과 받아쓰기가 마이크를 두고 부딪힘 → 글 쪽을 살림
            stopCapture(true); capWanted=false; ev.onNotice("audio-conflict","소리 보관을 켜면 받아쓰기가 끊기는 폰이라, 이번엔 글만 저장해요.");
            reset(); h.postDelayed(()->startCycle(),250); return; }
          toAudio("audio"); return;
        case 12: case 13: // LANGUAGE_NOT_SUPPORTED · LANGUAGE_UNAVAILABLE
          if(onDevice&&canUseCloud()){ releaseSr(); onDeviceOff=true; h.postDelayed(()->startCycle(),100); return; }
          toAudio("lang"); return;
        default: // 네트워크·서버·너무 많은 요청 …
          toAudio("net"); return;
      }
    }
    @Override public void onEvent(int t,Bundle b){}
  };
  boolean onDeviceOff;
  static String first(Bundle b){
    if(b==null) return "";
    ArrayList<String> l=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    return l==null||l.isEmpty()||l.get(0)==null?"":l.get(0).trim();
  }
  void reset(){ try{ if(sr!=null) sr.cancel(); }catch(Throwable e){} }
  void releaseSr(){ try{ if(sr!=null){ sr.cancel(); sr.destroy(); } }catch(Throwable e){} sr=null; }

  /** 한 번 듣기가 끝났을 때: 계속할지 끝낼지 */
  void next(){
    int d=pol.check(now());
    if(d==ListenPolicy.STOP_SILENCE||d==ListenPolicy.STOP_NOSPEECH){ complete(); return; }
    if(d==ListenPolicy.SEGMENT_MAX){ cont=true; complete(); return; }
    h.postDelayed(()->startCycle(),60);
  }

  /** 받아쓰기를 포기하고 소리 파일로 */
  void toAudio(String why){
    if(state==IDLE) return;
    releaseSr(); forceAudio=true; audioMode=true; audioReason=why;
    if(!partial.isEmpty()){ if(committed.length()>0) committed.append(' '); committed.append(partial); partial=""; ev.onText(committed.toString(),""); }
    if(!capWanted){ if(!startCapture()){ // 소리도 못 받으면: 글이 있으면 그것만 저장
        if(committed.length()>0){ complete(); return; } fail("audio-start"); return; } }
    state=LISTENING; pol.begin(now()); // 소리 모드에서 다시 기다림(처음 7초·끊김 2.5초)
    if(committed.length()>0) pol.voice(now());
    ev.onStatus("audio",why);
    if("no-offline".equals(why)||"lang".equals(why)) ev.onNotice("no-offline",NO_OFFLINE_MSG);
    else ev.onNotice("fallback-audio",why);
  }
  void consentHint(){ ev.onNotice("consent","Google 음성 안내가 떴다면 ‘동의’를 눌러 주세요. 안 뜨면 설정에서 음성 입력을 확인해 주세요."); }

  // ---------- 때 맞추기 ----------
  final Runnable tick=new Runnable(){ @Override public void run(){
    if(state==IDLE) return;
    long n=now();
    if(audioMode&&state==LISTENING&&cap.running){ int a=cap.amp(); if(a>1200) pol.voice(n); ev.onLevel(Math.min(1f,a/12000f)); }
    if(state==STARTING&&n-cycleStart>9000&&!audioMode&&!readyOnce){ toAudio("timeout"); }
    else if(state==STOPPING){ if(n>=stopDeadline) complete(); }
    else if(state==LISTENING){
      int d=pol.check(n);
      if(d==ListenPolicy.STOP_SILENCE||d==ListenPolicy.STOP_NOSPEECH) requestFinish(false);
      else if(d==ListenPolicy.SEGMENT_MAX) requestFinish(true);
    }
    if(state!=IDLE) h.postDelayed(this,150);
  }};

  /** 사용자가 눌러서 끝내기(남은 말까지 받고 저장) */
  void stopFromUser(){ if(state==STARTING||state==LISTENING) requestFinish(false); }

  void requestFinish(boolean c){
    if(state!=STARTING&&state!=LISTENING) return;
    cont=c; state=STOPPING; stopDeadline=now()+1600; ev.onStatus("stopping","");
    if(audioMode){ complete(); return; }
    try{ if(sr!=null) sr.stopListening(); }catch(Throwable e){ complete(); }
  }

  void complete(){
    if(state==IDLE) return;
    h.removeCallbacksAndMessages(null);
    reset();
    String text=(committed+(partial.isEmpty()?"":(committed.length()>0?" ":"")+partial)).trim();
    boolean keepWanted=audioMode||p.keepAudio();
    File f=capWanted||cap.running?cap.stop():null; capWanted=false;
    long dur=Math.max(0,now()-segStart);
    Result r=new Result(); r.durationMs=dur; r.seg=seg;
    if(!text.isEmpty()){ r.kind="speech"; r.text=text; r.audio=keepWanted?f:null; if(!keepWanted&&f!=null) f.delete(); }
    else if(f!=null&&audioMode&&pol!=null&&pol.heard){ r.kind="audio"; r.audio=f; r.reason=audioReason; }
    else { r.kind="none"; if(f!=null) f.delete(); r.reason=audioMode?audioReason:""; }
    boolean again=cont&&!r.kind.equals("none")&&seg+1<ListenPolicy.MAX_SEGMENTS;
    r.cont=again;
    state=IDLE;
    ev.onFinished(r);
    if(again){ seg++; beginSegment(); }
    else { teardown(); }
  }

  void fail(String why){
    h.removeCallbacksAndMessages(null);
    File f=capWanted||cap.running?cap.stop():null; if(f!=null) f.delete(); capWanted=false;
    state=IDLE; Result r=new Result(); r.kind="error"; r.reason=why; ev.onFinished(r); teardown();
  }

  /** 버리고 끝내기(저장 안 함) */
  void cancel(){
    if(state==IDLE){ teardown(); return; }
    h.removeCallbacksAndMessages(null);
    File f=cap.running?cap.stop():null; if(f!=null) f.delete(); capWanted=false;
    state=IDLE; teardown();
  }
  /** 화면을 떠날 때: 지금까지 들은 것을 저장하고 끝내기 */
  void stopAndSave(){
    if(state==IDLE){ teardown(); return; }
    boolean has=committed.length()>0||!partial.isEmpty()||audioMode||capWanted;
    if(has){ cont=false; state=STOPPING; complete(); } else cancel();
  }

  void teardown(){
    h.removeCallbacksAndMessages(null);
    releaseSr();
    if(muted) h.postDelayed(unmuteR,700);
  }
  final Runnable unmuteR=new Runnable(){ @Override public void run(){ unmute(); } };
  void release(){ h.removeCallbacksAndMessages(null); state=IDLE; releaseSr(); cap.release(); unmute(); }

  // ---------- 소리 줄이기 · 진동 ----------
  void mute(){
    try{
      AudioManager am=(AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
      if(am!=null&&!muted&&!am.isStreamMute(AudioManager.STREAM_MUSIC)){ am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0); muted=true; p.setMuted(true); }
    }catch(Throwable e){}
  }
  void unmute(){
    if(!muted) return;
    try{ AudioManager am=(AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE); if(am!=null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_UNMUTE,0); }catch(Throwable e){}
    muted=false; p.setMuted(false);
  }
  /** 앱이 갑자기 꺼져서 음악 소리가 꺼진 채로 남았다면 되돌리기 */
  static void recoverMute(Context c,Prefs p){
    if(!p.muted()) return;
    try{ AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE); if(am!=null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_UNMUTE,0); }catch(Throwable e){}
    p.setMuted(false);
  }
  void stopCapture(boolean del){ File f=cap.running?cap.stop():null; if(del&&f!=null) f.delete(); }
  void vibrate(int ms){ Vib.once(ctx,p,ms); }
}

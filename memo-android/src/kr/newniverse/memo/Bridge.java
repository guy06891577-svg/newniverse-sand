package kr.newniverse.memo;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import java.io.File;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 화면(JS) ↔ 안드로이드 다리. JS에서는 window.NVMemo.이름().
 * 지난 메모를 돌려주는 메서드(list/get/edit/del/copy/share/play, 설정)는 잠금이 풀려 있을 때만 일해요(자바 쪽에서 막음).
 * 잠금 화면에서는 방금 저장한 메모의 알림 맞추기만 돼요. 예외는 밖으로 던지지 않고 {"ok":false,...} 로 돌려줘요.
 */
class Bridge {
  final MainActivity a; MediaPlayer mp; long playing;
  Bridge(MainActivity a){ this.a=a; }
  void close(){ stopPlay(); }

  static String err(String e){ return MainActivity.j("ok",false,"err",e).toString(); }
  static String locked(){ return MainActivity.j("ok",false,"locked",true).toString(); }
  boolean open(){ return MainActivity.gate.unlocked(a); }
  static long id(String s){ try{ return Long.parseLong(s); }catch(Exception e){ return 0; } }

  @JavascriptInterface public String version(){ return Nv.VER; }
  @JavascriptInterface public void ready(){ a.main.post(a::onPageReady); }
  @JavascriptInterface public String snapshot(){ return a.snapshot(); }

  // ---------- 듣기 ----------
  @JavascriptInterface public void start(){ a.main.post(()->{ if(a.view.equals("rec")&&!a.dict.busy()) a.startDictation(); }); }
  @JavascriptInterface public void stop(){ a.main.post(()->a.dict.stopFromUser()); }
  @JavascriptInterface public void cancel(){ a.main.post(()->{ a.dict.cancel(); a.dstate="idle"; }); }
  @JavascriptInterface public void closeApp(){ a.main.post(a::close); }
  @JavascriptInterface public void reqMic(){ a.main.post(a::askMic); }

  // ---------- 잠금 풀고 보기 ----------
  @JavascriptInterface public void openView(String v){ a.main.post(()->{ if(v.equals("list")||v.equals("settings")||v.equals("note")) a.openPrivate(v); }); }
  @JavascriptInterface public void leave(){ a.main.post(a::leavePrivate); }

  @JavascriptInterface public String list(String q){
    try{
      if(!open()) return locked();
      List<Note> l=a.store.search(q); JSONArray arr=new JSONArray();
      for(Note n:l){ JSONObject o=n.toView(); o.put("id",String.valueOf(n.id)); arr.put(o); }
      return MainActivity.j("ok",true,"notes",arr,"total",a.store.all().size()).toString();
    }catch(Throwable e){ return err("list"); }
  }
  @JavascriptInterface public String get(String id){
    try{
      if(!open()) return locked();
      Note n=a.store.get(id(id)); if(n==null) return err("nonote");
      JSONObject o=n.toView(); o.put("id",String.valueOf(n.id));
      TimePhrase.Hit h=n.text.isEmpty()?null:TimePhrase.find(n.text,n.createdAt);
      JSONObject r=MainActivity.j("ok",true,"note",o);
      if(h!=null&&n.remindAt==0) r.put("sug",MainActivity.j("line",h.line(),"label",h.label()));
      if(n.remindAt>0) r.put("remindLabel",TimePhrase.label(n.remindAt,true));
      return r.toString();
    }catch(Throwable e){ return err("get"); }
  }
  @JavascriptInterface public String edit(String id,String text){
    try{
      if(!open()) return locked();
      Note n=a.store.get(id(id)); if(n==null) return err("nonote");
      n.text=text==null?"":text.trim(); if(!n.text.isEmpty()&&n.audio.isEmpty()) n.kind=Note.SPEECH;
      a.store.update(n); return MainActivity.j("ok",true).toString();
    }catch(Throwable e){ return err("edit"); }
  }
  @JavascriptInterface public String del(String id){
    try{
      if(!open()) return locked();
      Note n=a.store.get(id(id)); if(n==null) return err("nonote");
      if(playing==n.id) stopPlay();
      Reminders.cancel(a,n);
      a.store.delete(n.id); a.recent.remove(n.id);
      return MainActivity.j("ok",true).toString();
    }catch(Throwable e){ return err("del"); }
  }
  @JavascriptInterface public String copy(String id){
    try{
      if(!open()) return locked();
      Note n=a.store.get(id(id)); if(n==null) return err("nonote");
      ClipboardManager cm=(ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);
      if(cm==null) return err("clip");
      cm.setPrimaryClip(ClipData.newPlainText("뉴니버스 빠른 메모",n.text)); return MainActivity.j("ok",true).toString();
    }catch(Throwable e){ return err("copy"); }
  }
  @JavascriptInterface public String share(String id){
    try{
      if(!open()) return locked();
      Note n=a.store.get(id(id)); if(n==null) return err("nonote");
      Intent s=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,n.text);
      a.startActivity(Intent.createChooser(s,"메모 공유"));
      return MainActivity.j("ok",true).toString();
    }catch(Throwable e){ return err("share"); }
  }

  // ---------- 소리 다시 듣기 ----------
  @JavascriptInterface public String play(String id){
    try{
      if(!open()) return locked();
      Note n=a.store.get(id(id)); if(n==null||n.audio.isEmpty()) return err("noaudio");
      File f=a.store.audioFile(n.audio); if(!f.exists()) return err("nofile");
      stopPlay();
      mp=new MediaPlayer(); mp.setDataSource(f.getAbsolutePath()); mp.prepare();
      final long pid=n.id; playing=pid;
      mp.setOnCompletionListener(m->{ a.push(MainActivity.j("t","played","id",String.valueOf(pid))); stopPlay(); });
      mp.start(); return MainActivity.j("ok",true).toString();
    }catch(Throwable e){ stopPlay(); return err("play"); }
  }
  @JavascriptInterface public void stopPlay(){
    try{ if(mp!=null){ try{ mp.stop(); }catch(Throwable e){} mp.release(); } }catch(Throwable e){} mp=null; playing=0;
  }

  // ---------- 알림 맞추기 (사용자가 단추를 눌러야만) ----------
  boolean canTouch(long id){ return open()||a.recent.contains(id); }

  @JavascriptInterface public String suggest(String sid){
    try{
      long id=id(sid); if(!canTouch(id)) return locked();
      Note n=a.store.get(id); if(n==null) return err("nonote");
      TimePhrase.Hit h=TimePhrase.find(n.text,n.createdAt);
      if(h==null) return err("notime");
      return MainActivity.j("ok",true,"line",h.line(),"label",h.label(),"defaulted",h.defaulted,"rolled",h.rolled).toString();
    }catch(Throwable e){ return err("suggest"); }
  }
  /** 알림 맞추기 단추. inexact=true 면 정확한 알람 허용이 없어도 대략적으로 */
  @JavascriptInterface public String remind(String sid,boolean inexact){
    try{
      long id=id(sid);
      if(!canTouch(id)) return locked();
      return remindJson(id,inexact,false).toString();
    }catch(Throwable e){ return err("remind"); }
  }
  /** 알림 맞추기 결과. viaPermission=true 면 알림 허용 창을 거쳐 온 결과(이벤트로도 보냄) */
  JSONObject remindJson(long id,boolean inexact,boolean viaPermission){
    JSONObject out=MainActivity.j("t","remind","id",String.valueOf(id));
    try{
      Note n=a.store.get(id);
      if(n==null){ out.put("ok",false).put("state","nonote"); return out; }
      TimePhrase.Hit h=TimePhrase.find(n.text,n.createdAt);
      if(h==null){ out.put("ok",false).put("state","notime"); return out; }
      if(!Reminders.notifGranted(a)){
        if(viaPermission){ out.put("ok",false).put("state","notif-denied"); return out; }
        a.pendingRemind=id; out.put("ok",false).put("state","need-notif");
        try{ a.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},MainActivity.REQ_NOTIF); }catch(Throwable e){ out.put("state","notif-denied"); a.pendingRemind=0; }
        return out;
      }
      int r=Reminders.schedule(a,n,h.when,inexact);
      switch(r){
        case Reminders.OK_EXACT: case Reminders.OK_INEXACT:
          a.store.update(n);
          out.put("ok",true).put("state",r==Reminders.OK_EXACT?"exact":"inexact").put("line",h.line()).put("when",h.when); return out;
        case Reminders.NEED_EXACT: out.put("ok",false).put("state","need-exact").put("line",h.line()); return out;
        case Reminders.NEED_NOTIF: out.put("ok",false).put("state","notif-denied"); return out;
        case Reminders.PAST: out.put("ok",false).put("state","past"); return out;
        default: out.put("ok",false).put("state","error"); return out;
      }
    }catch(Throwable e){ try{ out.put("ok",false).put("state","error"); }catch(Exception x){} return out; }
  }
  @JavascriptInterface public String unremind(String sid){
    try{
      long id=id(sid); if(!canTouch(id)) return locked();
      Note n=a.store.get(id); if(n==null) return err("nonote");
      Reminders.cancel(a,n); a.store.update(n); return MainActivity.j("ok",true).toString();
    }catch(Throwable e){ return err("unremind"); }
  }

  // ---------- 설정·상태 ----------
  @JavascriptInterface public String settings(){ try{ if(!open()) return locked(); return a.prefs.json().put("ok",true).toString(); }catch(Throwable e){ return err("settings"); } }
  @JavascriptInterface public String setSetting(String k,String v){
    try{ if(!open()) return locked(); return MainActivity.j("ok",a.prefs.set(k,v)).toString(); }catch(Throwable e){ return err("set"); }
  }
  @JavascriptInterface public String status(){
    try{
      if(!open()) return locked();
      boolean on=false; try{ on=Build.VERSION.SDK_INT>=33&&SpeechRecognizer.isOnDeviceRecognitionAvailable(a); }catch(Throwable e){}
      return MainActivity.j("ok",true,"ver",Nv.VER,"sdk",Build.VERSION.SDK_INT,"mic",a.dict.hasMic(),"notif",Reminders.notifGranted(a),
        "exact",Reminders.canExact(a),"speech",Dictation.recognizerAvailable(a),"onDevice",on,"online",online()).toString();
    }catch(Throwable e){ return err("status"); }
  }
  boolean online(){
    try{
      ConnectivityManager cm=(ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE); if(cm==null) return false;
      Network n=cm.getActiveNetwork(); if(n==null) return false;
      NetworkCapabilities c=cm.getNetworkCapabilities(n); return c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }catch(Throwable e){ return false; }
  }

  // ---------- 시스템 설정 화면 열기 ----------
  boolean go(Intent i){ try{ i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); a.startActivity(i); return true; }catch(Throwable e){ return false; } }
  @JavascriptInterface public void openExactSettings(){ a.main.post(()->{ if(!go(Reminders.exactSettings(a))) go(new Intent(Settings.ACTION_SETTINGS)); }); }
  @JavascriptInterface public void openNotifSettings(){ a.main.post(()->{ if(!go(Reminders.notifSettings(a))) go(new Intent(Settings.ACTION_SETTINGS)); }); }
  @JavascriptInterface public void openVoiceSettings(){ a.main.post(()->{ if(!go(new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))) go(new Intent(Settings.ACTION_SETTINGS)); }); }
  @JavascriptInterface public void openAppSettings(){ a.main.post(()->{ if(!go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName())))) go(new Intent(Settings.ACTION_SETTINGS)); }); }
}

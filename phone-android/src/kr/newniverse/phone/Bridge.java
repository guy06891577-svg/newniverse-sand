package kr.newniverse.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.telecom.TelecomManager;
import android.webkit.JavascriptInterface;
import org.json.*;
import java.util.*;

/** 화면(JS) ↔ 안드로이드 다리. JS에서는 window.NVPhone.이름() */
class Bridge {
  final Activity a;
  Bridge(Activity a){ this.a=a; }
  static final String[] PERMS_BASE={Manifest.permission.CALL_PHONE,Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG,Manifest.permission.READ_CONTACTS};

  static String[] missing(Context c){
    List<String> m=new ArrayList<>();
    for(String p:PERMS_BASE) if(!Nv.has(c,p)) m.add(p);
    if(Build.VERSION.SDK_INT>=33&&!Nv.has(c,Manifest.permission.POST_NOTIFICATIONS)) m.add(Manifest.permission.POST_NOTIFICATIONS);
    return m.toArray(new String[0]);
  }

  @JavascriptInterface public String status(){
    JSONObject o=new JSONObject();
    try{
      o.put("def",Nv.isDefault(a)); o.put("canRole",Nv.roleIntent(a)!=null);
      JSONObject p=new JSONObject();
      p.put("phone",Nv.has(a,Manifest.permission.CALL_PHONE)); p.put("log",Nv.has(a,Manifest.permission.READ_CALL_LOG));
      p.put("wlog",Nv.has(a,Manifest.permission.WRITE_CALL_LOG)); p.put("contacts",Nv.has(a,Manifest.permission.READ_CONTACTS));
      p.put("notif",Build.VERSION.SDK_INT<33||Nv.has(a,Manifest.permission.POST_NOTIFICATIONS));
      o.put("perms",p);
      boolean fsi=true; if(Build.VERSION.SDK_INT>=34){ NotificationManager nm=a.getSystemService(NotificationManager.class); fsi=nm!=null&&nm.canUseFullScreenIntent(); }
      o.put("fsi",fsi); o.put("inCall",CallService.inCall()); o.put("sdk",Build.VERSION.SDK_INT); o.put("ver",Nv.VER);
      o.put("maker",Build.MANUFACTURER); o.put("now",System.currentTimeMillis());
    }catch(Exception e){}
    return o.toString();
  }

  @JavascriptInterface public void requestPerms(){ a.runOnUiThread(()->{ String[] m=missing(a); if(m.length>0) a.requestPermissions(m,MainActivity.RQ_PERM); else MainActivity.jsResume(a); }); }
  @JavascriptInterface public void requestRole(){ a.runOnUiThread(()->{ Intent i=Nv.roleIntent(a); if(i==null){ openDefaultApps(); return; } try{ a.startActivityForResult(i,MainActivity.RQ_ROLE); }catch(Exception e){ openDefaultApps(); } }); }
  @JavascriptInterface public void openDefaultApps(){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }catch(Exception e){ try{ a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception x){} } }); }
  @JavascriptInterface public void openAppSettings(){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.getPackageName()))); }catch(Exception e){} }); }
  @JavascriptInterface public void openFsiSettings(){ a.runOnUiThread(()->{ try{ if(Build.VERSION.SDK_INT>=34) a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+a.getPackageName()))); else openAppSettings(); }catch(Exception e){ openAppSettings(); } }); }

  // ---------- 상황 모드 (운전 중 · 회의 중) ----------
  @JavascriptInterface public String modes(){ return ModeManager.state(a).toString(); }
  /** 켜기. timerMin: 회의 자동 끝내기 분(0 = 직접). 반환 {ok,dnd,keep,sms,def} */
  @JavascriptInterface public String modeStart(String id,int timerMin){ return ModeManager.start(a,id,timerMin).toString(); }
  /** 끄기. 반환: 끝난 모드의 요약 머리글({}이면 켜진 모드 없었음) */
  @JavascriptInterface public String modeStop(){ JSONObject s=ModeManager.stop(a,"manual"); return s==null?"{}":s.toString(); }
  /** 설정 저장 (자동 답장 문구·울릴 사람·타이머 …). 켜져 있는 동안 바꾸면 바로 반영 */
  @JavascriptInterface public String modeSave(String id,String json){
    try{ if(!ModeDefs.valid(id)) return "{}";
      JSONObject s=ModeStore.saveCfg(a,id,new JSONObject(json==null?"{}":json));
      JSONObject act=ModeStore.active(a);
      if(act!=null&&id.equals(act.optString("id"))&&act.optBoolean("dnd")&&!act.optBoolean("keep")) Dnd.reapply(a,s.optString("allow"),s.optBoolean("repeat"));
      return s.toString();
    }catch(Exception e){ return "{}"; } }
  @JavascriptInterface public String modeSummary(){ JSONObject s=ModeManager.summaryFull(a); return s==null?"{}":s.toString(); }
  @JavascriptInterface public void modeDismiss(){ ModeStore.clearSummary(a); NotificationManager nm=a.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(ModeManager.N_DONE); }
  /** 모드에 필요한 허용 열기: dnd = 방해 금지 접근 화면, sms = 문자 보내기(+전화 상태), readsms = 받은 문자 읽기 */
  @JavascriptInterface public void modeAsk(String what){ a.runOnUiThread(()->{
    try{
      if("dnd".equals(what)){ a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); return; }
      List<String> m=new ArrayList<>();
      if("sms".equals(what)){ if(!Nv.has(a,Manifest.permission.SEND_SMS)) m.add(Manifest.permission.SEND_SMS); if(!Nv.isDefault(a)&&!Nv.has(a,Manifest.permission.READ_PHONE_STATE)) m.add(Manifest.permission.READ_PHONE_STATE); }
      else if("readsms".equals(what)){ if(!Nv.has(a,Manifest.permission.READ_SMS)) m.add(Manifest.permission.READ_SMS); }
      if(m.isEmpty()) MainActivity.jsResume(a); else a.requestPermissions(m.toArray(new String[0]),MainActivity.RQ_PERM);
    }catch(Exception e){ try{ a.startActivity(new Intent(Settings.ACTION_SETTINGS)); }catch(Exception x){} } }); }

  /** 최근 통화 (days일, 최대 4000건, 최신 순) */
  @JavascriptInterface public String getCalls(int days){
    JSONArray arr=new JSONArray();
    if(!Nv.has(a,Manifest.permission.READ_CALL_LOG)) return arr.toString();
    long since=System.currentTimeMillis()-Math.max(1,Math.min(days,90))*86400000L;
    Uri u=CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY,"4000").build();
    String[] pr={CallLog.Calls._ID,CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.TYPE,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.NUMBER_PRESENTATION};
    try(Cursor q=a.getContentResolver().query(u,pr,CallLog.Calls.DATE+">=?",new String[]{String.valueOf(since)},CallLog.Calls.DATE+" DESC")){
      while(q!=null&&q.moveToNext()){
        JSONObject j=new JSONObject(); int t=q.getInt(3);
        String ty= t==CallLog.Calls.INCOMING_TYPE||t==CallLog.Calls.ANSWERED_EXTERNALLY_TYPE?"in": t==CallLog.Calls.OUTGOING_TYPE?"out": t==CallLog.Calls.MISSED_TYPE?"miss":
                   t==CallLog.Calls.REJECTED_TYPE?"rej": t==CallLog.Calls.BLOCKED_TYPE?"block": t==CallLog.Calls.VOICEMAIL_TYPE?"vm":"in";
        j.put("id",q.getLong(0)); j.put("num",q.getString(1)==null?"":q.getString(1)); j.put("name",q.getString(2)==null?"":q.getString(2));
        j.put("type",ty); j.put("date",q.getLong(4)); j.put("dur",q.getLong(5)); j.put("pres",q.getInt(6)==CallLog.Calls.PRESENTATION_ALLOWED?"ok":"hidden");
        arr.put(j);
      }
    }catch(Exception e){}
    return arr.toString();
  }

  /** 주소록 (이름·번호) */
  @JavascriptInterface public String getContacts(){
    JSONArray arr=new JSONArray();
    if(!Nv.has(a,Manifest.permission.READ_CONTACTS)) return arr.toString();
    String[] pr={ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER};
    Set<String> seen=new HashSet<>();
    try(Cursor q=a.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,pr,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC")){
      while(q!=null&&q.moveToNext()&&arr.length()<8000){
        String n=q.getString(0),p=q.getString(1); if(n==null||p==null) continue;
        String k=n+"|"+p.replaceAll("[^0-9+]",""); if(!seen.add(k)) continue;
        JSONObject j=new JSONObject(); j.put("name",n); j.put("num",p); arr.put(j);
      }
    }catch(Exception e){}
    return arr.toString();
  }

  /** 묶음 삭제 (WRITE_CALL_LOG). ids = "1,2,3" */
  @JavascriptInterface public int deleteCalls(String ids){
    if(!Nv.has(a,Manifest.permission.WRITE_CALL_LOG)||ids==null) return -1;
    StringBuilder sb=new StringBuilder(); for(String s:ids.split(",")){ s=s.trim(); if(s.matches("\\d+")){ if(sb.length()>0) sb.append(','); sb.append(s);} }
    if(sb.length()==0) return 0;
    try{ return a.getContentResolver().delete(CallLog.Calls.CONTENT_URI,CallLog.Calls._ID+" IN ("+sb+")",null); }catch(Exception e){ return -1; }
  }

  /** 전화 걸기. 반환: "ok" | "perm" | "empty" | "err" */
  @JavascriptInterface public String call(String num){
    String n=num==null?"":num.replaceAll("[^0-9+*#,;]","");
    if(n.isEmpty()) return "empty";
    if(!Nv.has(a,Manifest.permission.CALL_PHONE)&&!Nv.isDefault(a)){ requestPerms(); return "perm"; }
    final String nn=n;
    a.runOnUiThread(()->{
      try{ TelecomManager tm=a.getSystemService(TelecomManager.class); tm.placeCall(Uri.fromParts("tel",nn,null),new Bundle()); }
      catch(SecurityException se){ try{ a.startActivity(new Intent(Intent.ACTION_CALL,Uri.fromParts("tel",nn,null))); }catch(Exception e){} }
      catch(Exception e){}
    });
    return "ok";
  }
  @JavascriptInterface public void sms(String num){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(num==null?"":num)))); }catch(Exception e){} }); }
  @JavascriptInterface public void addContact(String num){ a.runOnUiThread(()->{ try{ Intent i=new Intent(ContactsContract.Intents.Insert.ACTION); i.setType(ContactsContract.RawContacts.CONTENT_TYPE); i.putExtra(ContactsContract.Intents.Insert.PHONE,num); a.startActivity(i); }catch(Exception e){} }); }

  /** 부재중 알림 지우기 + 부재중 기록을 '봤음'으로 */
  @JavascriptInterface public void clearMissed(){
    NotificationManager nm=a.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(MissedCallReceiver.N_MISS);
    try{ if(Nv.isDefault(a)){ TelecomManager tm=a.getSystemService(TelecomManager.class); tm.cancelMissedCallsNotification(); } }catch(Exception e){}
    if(Nv.has(a,Manifest.permission.WRITE_CALL_LOG)){ try{ ContentValues v=new ContentValues(); v.put(CallLog.Calls.NEW,0); v.put(CallLog.Calls.IS_READ,1);
      a.getContentResolver().update(CallLog.Calls.CONTENT_URI,v,CallLog.Calls.TYPE+"=? AND "+CallLog.Calls.NEW+"=1",new String[]{String.valueOf(CallLog.Calls.MISSED_TYPE)}); }catch(Exception e){} }
  }
  @JavascriptInterface public String takeDial(){ if(a instanceof MainActivity){ String s=((MainActivity)a).pendingDial; ((MainActivity)a).pendingDial=null; return s==null?"":s; } return ""; }
  @JavascriptInterface public String takeView(){ if(a instanceof MainActivity){ String s=((MainActivity)a).pendingView; ((MainActivity)a).pendingView=null; return s==null?"":s; } return ""; }
  @JavascriptInterface public void openCall(){ if(CallService.inCall()) CallService.openUi(a); }

  // ---------- 통화 조작 ----------
  @JavascriptInterface public String state(){ return CallService.stateJson(a); }
  @JavascriptInterface public void answer(){ CallService.answer(); }
  @JavascriptInterface public void reject(){ CallService.reject(null); }
  @JavascriptInterface public void rejectMsg(String m){ CallService.reject(m); }
  @JavascriptInterface public void hangup(){ CallService.hangup(); }
  @JavascriptInterface public void mute(boolean b){ CallService.mute(b); }
  @JavascriptInterface public void speaker(boolean b){ CallService.speaker(b); }
  @JavascriptInterface public void dtmf(String c){ if(c!=null&&c.length()==1&&"0123456789*#".contains(c)) CallService.dtmf(c.charAt(0)); }
  @JavascriptInterface public void hold(boolean b){ CallService.hold(b); }
  @JavascriptInterface public void swap(){ CallService.swap(); }
  @JavascriptInterface public void addCall(){ a.runOnUiThread(()->{ try{ a.startActivity(new Intent(a,MainActivity.class).setAction(MainActivity.ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }catch(Exception e){} }); }
  @JavascriptInterface public void close(){ a.runOnUiThread(()->{ if(a instanceof CallActivity){ if(!CallService.inCall()) a.finish(); else a.moveTaskToBack(true); } }); }
  @JavascriptInterface public void vibrate(int ms){ try{ Vibrator v=a.getSystemService(Vibrator.class); if(v!=null) v.vibrate(VibrationEffect.createOneShot(Math.max(5,Math.min(ms,60)),VibrationEffect.DEFAULT_AMPLITUDE)); }catch(Exception e){} }
}

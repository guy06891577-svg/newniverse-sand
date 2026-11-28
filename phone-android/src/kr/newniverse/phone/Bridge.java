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
      p.put("mic",Nv.has(a,Manifest.permission.RECORD_AUDIO)); p.put("wcontacts",Nv.has(a,Manifest.permission.WRITE_CONTACTS)); p.put("cal",Nv.has(a,Manifest.permission.READ_CALENDAR));
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

  // ================= v6 =================
  void js(String s){ if(a instanceof MainActivity) ((MainActivity)a).js(s); else if(a instanceof CallActivity) ((CallActivity)a).js(s); }
  @JavascriptInterface public boolean locked(){ return VoiceNote.locked(a); }

  // ---------- 통화 메모 ----------
  @JavascriptInterface public String memoAdd(String num,String name,String text,String src,long dur){ JSONObject o=MemoStore.add(a,num,name,text,src,dur); return o==null?"{}":o.toString(); }
  @JavascriptInterface public boolean memoEdit(long id,String text){ return MemoStore.edit(a,id,text); }
  /** 지운 메모를 돌려줌(되돌리기용) */
  @JavascriptInterface public String memoDel(long id){ JSONObject o=MemoStore.del(a,id); return o==null?"{}":o.toString(); }
  @JavascriptInterface public boolean memoRestore(String json){ try{ return MemoStore.restore(a,new JSONObject(json)); }catch(Exception e){ return false; } }
  @JavascriptInterface public String memoList(String num){ JSONArray r=new JSONArray(); for(JSONObject o:MemoStore.forNum(a,num)) r.put(o); return r.toString(); }
  @JavascriptInterface public String memoPeople(){ return MemoStore.people(a).toString(); }

  /** 단골 한 줄: 잠금 화면이면 {locked:true}만 (메모 내용 숨김). 아니면 {memo,memoAgo,lastAgo,n15} */
  @JavascriptInterface public String callerLine(String num){
    JSONObject o=new JSONObject();
    try{
      if(VoiceNote.locked(a)){ o.put("locked",true); return o.toString(); }
      o.put("locked",false); o.put("log",Nv.has(a,Manifest.permission.READ_CALL_LOG));
      String k=ModeRules.key(num); if(k.isEmpty()) return o.toString();
      long now=System.currentTimeMillis(); TimeZone tz=TimeZone.getDefault();
      JSONObject m=MemoStore.latest(a,num);
      if(m!=null){ o.put("memo",MemoRules.snippet(m.optString("text"),60)); o.put("memoAgo",MemoRules.ago(now,m.optLong("at"),tz)); o.put("memoN",MemoStore.forNum(a,num).size()); }
      long[] lg=lastCall(k);
      if(lg[0]>0){ o.put("lastAgo",MemoRules.ago(now,lg[0],tz)); o.put("last",lg[0]); }
      o.put("n15",lg[1]);
    }catch(Exception e){}
    return o.toString();
  }
  static final Map<String,long[]> lineCache=new HashMap<>();
  /** {마지막 통화 시각, 15일 통화 수} (20초 캐시) */
  long[] lastCall(String k){
    long now=System.currentTimeMillis();
    synchronized(lineCache){ long[] c=lineCache.get(k); if(c!=null&&now-c[2]<20000) return c; }
    long last=0,n15=0;
    if(Nv.has(a,Manifest.permission.READ_CALL_LOG)){
      Uri u=CallLog.Calls.CONTENT_URI.buildUpon().appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY,"3000").build();
      try(Cursor q=a.getContentResolver().query(u,new String[]{CallLog.Calls.NUMBER,CallLog.Calls.DATE},CallLog.Calls.DATE+">=?",new String[]{String.valueOf(now-365L*86400000L)},CallLog.Calls.DATE+" DESC")){
        while(q!=null&&q.moveToNext()){ if(!k.equals(ModeRules.key(q.getString(0)))) continue; long d=q.getLong(1); if(last==0) last=d; if(now-d<=15L*86400000L) n15++; else if(last>0) break; }
      }catch(Exception e){}
    }
    long[] r={last,n15,now}; synchronized(lineCache){ if(lineCache.size()>50) lineCache.clear(); lineCache.put(k,r); } return r;
  }

  // ---------- 음성 메모 (결과는 NV_voice) ----------
  @JavascriptInterface public String voicePlan(){ return VoiceNote.plan(a); }
  @JavascriptInterface public void voiceStart(){ a.runOnUiThread(()->{ String r=VoiceNote.start(a,j->js("window.NV_voice&&NV_voice("+j+")"));
    js("window.NV_voice&&NV_voice({st:'start',res:"+JSONObject.quote(r)+"})"); }); }
  @JavascriptInterface public void voiceStop(){ a.runOnUiThread(VoiceNote::stop); }
  @JavascriptInterface public void voiceCancel(){ a.runOnUiThread(VoiceNote::cancel); }
  /** 통화 끝난 뒤 메모 카드가 떠 있는 동안 화면을 닫지 않음 */
  @JavascriptInterface public void holdOpen(boolean b){ a.runOnUiThread(()->{ if(a instanceof CallActivity) ((CallActivity)a).setHold(b); }); }

  // ---------- 차단 ----------
  @JavascriptInterface public String blockInfo(){ JSONObject o=new JSONObject(); try{ o.put("def",Nv.isDefault(a)); o.put("can",Blocker.can(a)); }catch(Exception e){} return o.toString(); }
  @JavascriptInterface public String block(String num){ String r=Blocker.block(a,num); synchronized(lineCache){ lineCache.clear(); } return r; }
  @JavascriptInterface public int unblock(String num){ return Blocker.unblock(a,num); }
  @JavascriptInterface public String blockList(){ return Blocker.can(a)?Blocker.list(a).toString():"[]"; }
  @JavascriptInterface public String blockKeys(){ return Blocker.can(a)?new JSONArray(Blocker.keys(a)).toString():"[]"; }
  @JavascriptInterface public boolean isEmergency(String num){ return Blocker.emergency(a,num); }

  // ---------- 명함 → 연락처 (결과는 NV_card) ----------
  @JavascriptInterface public void cardScan(String src){ a.runOnUiThread(()->{
    try{ Intent i=new Intent().setClassName(a,"kr.newniverse.phone.CardScanActivity").putExtra("src","camera".equals(src)?"camera":"gallery");
      a.startActivityForResult(i,MainActivity.RQ_CARD); }
    catch(Exception e){ js("window.NV_card&&NV_card({ok:false,err:'start'})"); } }); }
  /** 주소록에 바로 저장. 반환 ok · perm(허용 창 띄움 → NV_perm('contacts',…)) · empty · fail */
  @JavascriptInterface public String cardSave(String json){
    try{ JSONObject f=new JSONObject(json);
      String r=ContactSaver.save(a,f);
      if("perm".equals(r)) a.runOnUiThread(()->a.requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS},MainActivity.RQ_CONTACTS));
      return r;
    }catch(Exception e){ return "fail"; } }
  /** 허용을 안 주면: 휴대폰 주소록 앱의 「새 연락처」 화면에 채워서 열기 */
  @JavascriptInterface public void cardInsert(String json){ a.runOnUiThread(()->{ try{ JSONObject f=new JSONObject(json);
    Intent i=new Intent(ContactsContract.Intents.Insert.ACTION); i.setType(ContactsContract.RawContacts.CONTENT_TYPE);
    String n=ContactSaver.t(f,"name"),m=ContactSaver.t(f,"mobile"),t=ContactSaver.t(f,"tel"),co=ContactSaver.t(f,"company"),ti=ContactSaver.t(f,"title"),em=ContactSaver.t(f,"email");
    if(!n.isEmpty()) i.putExtra(ContactsContract.Intents.Insert.NAME,n);
    if(!m.isEmpty()){ i.putExtra(ContactsContract.Intents.Insert.PHONE,m); i.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE); }
    if(!t.isEmpty()){ if(m.isEmpty()){ i.putExtra(ContactsContract.Intents.Insert.PHONE,t); i.putExtra(ContactsContract.Intents.Insert.PHONE_TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_WORK); }
      else { i.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE,t); i.putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,ContactsContract.CommonDataKinds.Phone.TYPE_WORK); } }
    if(!co.isEmpty()) i.putExtra(ContactsContract.Intents.Insert.COMPANY,co);
    if(!ti.isEmpty()) i.putExtra(ContactsContract.Intents.Insert.JOB_TITLE,ti);
    if(!em.isEmpty()){ i.putExtra(ContactsContract.Intents.Insert.EMAIL,em); i.putExtra(ContactsContract.Intents.Insert.EMAIL_TYPE,ContactsContract.CommonDataKinds.Email.TYPE_WORK); }
    a.startActivity(i); }catch(Exception e){} }); }

  // ---------- 일정 전 회의 모드 ----------
  @JavascriptInterface public String calInfo(){ return CalWatch.soonJson(a).toString(); }
  @JavascriptInterface public void calMode(String m){ CalWatch.setMode(a,m); }
  @JavascriptInterface public void calAsk(){ a.runOnUiThread(()->{ if(Nv.has(a,Manifest.permission.READ_CALENDAR)){ CalWatch.reschedule(a); js("window.NV_perm&&NV_perm('cal',true)"); } else a.requestPermissions(new String[]{Manifest.permission.READ_CALENDAR},MainActivity.RQ_CAL); }); }
  @JavascriptInterface public String calOn(String key,long end){ return CalWatch.turnOn(a,key,end).toString(); }
  @JavascriptInterface public void calNo(String key){ if(key!=null) CalWatch.dismiss(a,key); }
}

package kr.newniverse.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.Telephony;
import android.telephony.SmsManager;
import org.json.*;
import java.util.*;

/**
 * 상황 모드(운전 중·회의 중) 본체.
 *  켜면: 방해 금지(허용 시) + 안 받은 전화에 문자 자동 답장 + 조용한 알림(끄기 버튼) + (회의) 자동 끝내기 알람.
 *  끄면: 방해 금지를 이전 상태로 되돌리고, 모드 동안 놓친 전화·받은 문자를 요약으로 모아 둠.
 *  항공기 모드는 일반 앱이 켜고 끌 수 없어서 건드리지 않아요.
 */
final class ModeManager {
  private ModeManager(){}
  static final Object LOCK=new Object();
  static final String ACT_END="kr.newniverse.phone.MODE_END", ACT_STOP="kr.newniverse.phone.MODE_STOP", ACT_SMS_SENT="kr.newniverse.phone.MODE_SMS_SENT";
  static final int N_MODE=31, N_DONE=32;
  static final long REPEAT_MS=15*60*1000L;
  /** 시험에서 시계를 바꿔 끼우려고 */
  static volatile java.util.function.LongSupplier clock=System::currentTimeMillis;
  static long now(){ return clock.getAsLong(); }
  /** 시험에서 문자 나누기를 바꿔 끼우려고 (진짜 폰에서는 null = 안드로이드가 나눔) */
  static volatile java.util.function.Function<String,ArrayList<String>> splitter=null;

  // ============ 켜기 / 끄기 ============
  /** 모드 켜기. timerMin: 회의 자동 끝내기(0 = 직접 끌 때까지). 반환 {ok,dnd,keep,sms,def} */
  static JSONObject start(Context c,String id,int timerMin){
    JSONObject r=new JSONObject();
    synchronized(LOCK){
      try{
        if(!ModeDefs.valid(id)){ r.put("ok",false); return r; }
        JSONObject cur=ModeStore.active(c);
        if(cur!=null){ if(id.equals(cur.optString("id"))){ r.put("ok",true); r.put("already",true); return r; } stopLocked(c,"switch",false); }
        JSONObject cfg=ModeStore.cfg(c,id);
        int tm=ModeDefs.timers(id)?ModeDefs.timer(timerMin):0;
        if(ModeDefs.timers(id)&&tm!=cfg.optInt("timer")){ cfg.put("timer",tm); ModeStore.saveCfg(c,id,cfg); }
        JSONObject a=new JSONObject(); long t=now();
        a.put("id",id); a.put("start",t); a.put("end",tm>0?t+tm*60000L:0); a.put("sent",0);
        a.put("replied",new JSONArray()); a.put("log",new JSONArray()); a.put("skips",new JSONObject()); a.put("seen",new JSONObject());
        JSONObject prev=Dnd.apply(c,cfg.optString("allow"),cfg.optBoolean("repeat"));
        a.put("dnd",prev!=null); a.put("keep",prev!=null&&prev.optBoolean("keep"));
        if(prev!=null) a.put("prev",prev);
        ModeStore.clearSummary(c);   // 새로 켜면 전의 요약은 치움(통화 기록에는 그대로 있음)
        ModeStore.putActive(c,a);
        schedule(c,a); postOngoing(c,a);
        r.put("ok",true); r.put("dnd",prev!=null); r.put("keep",a.optBoolean("keep"));
        r.put("sms",Nv.has(c,Manifest.permission.SEND_SMS)); r.put("def",Nv.isDefault(c));
      }catch(Exception e){ try{ r.put("ok",false); }catch(Exception x){} }
    }
    return r;
  }

  /** 모드 끄기(수동·시간 끝·알림 버튼). 요약을 저장하고 알림을 띄움. 켜져 있지 않으면 null */
  static JSONObject stop(Context c,String why){ synchronized(LOCK){ return stopLocked(c,why,true); } }

  private static JSONObject stopLocked(Context c,String why,boolean notify){
    JSONObject a=ModeStore.active(c); if(a==null) return null;
    try{
      Dnd.restore(c,a.optJSONObject("prev"));
      cancelAlarm(c);
      NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(N_MODE);
      JSONObject s=new JSONObject();
      s.put("id",a.optString("id")); s.put("start",a.optLong("start")); s.put("end",now()); s.put("why",why);
      s.put("log",a.optJSONArray("log")==null?new JSONArray():a.optJSONArray("log")); s.put("skips",a.optJSONObject("skips")==null?new JSONObject():a.optJSONObject("skips"));
      s.put("dnd",a.optBoolean("dnd"));
      ModeStore.clearActive(c); ModeStore.putSummary(c,s);
      if(notify) postDone(c,s);
      return s;
    }catch(Exception e){ ModeStore.clearActive(c); return null; }
  }

  /** 시간이 다 됐으면 끔 (앱을 열 때·전화가 올 때·알람·부팅 때 부름) */
  static void tick(Context c){
    synchronized(LOCK){
      JSONObject a=ModeStore.active(c); if(a==null) return;
      long e=a.optLong("end"); if(e>0&&now()>=e) stopLocked(c,"timer",true);
    }
  }

  /** 켜진 채로 폰이 꺼졌다 켜진 뒤·앱이 새로 깔린 뒤: 알람과 알림을 다시 걸어 둠 */
  static void resume(Context c){
    synchronized(LOCK){
      JSONObject a=ModeStore.active(c); if(a==null) return;
      long e=a.optLong("end"); if(e>0&&now()>=e){ stopLocked(c,"timer",true); return; }
      schedule(c,a); postOngoing(c,a);
    }
  }

  // ============ 전화가 왔을 때 ============
  /**
   * 걸려오는 전화를 조용히 받을지: 방해 금지가 실제로 켜져 있고 이 번호가 울릴 허용 대상이 아니면 true.
   * (전화 화면을 갑자기 띄우지 않고 알림으로만 알려요. 받고 싶으면 알림에서 받을 수 있어요)
   */
  static boolean quiet(Context c,String num){
    synchronized(LOCK){
      JSONObject a=ModeStore.active(c); if(a==null||!a.optBoolean("dnd")) return false;
      if(a.optBoolean("keep")) return true;
      JSONObject cfg=ModeStore.cfg(c,a.optString("id"));
      String k=ModeRules.key(num);
      if(cfg.optBoolean("repeat")&&!k.isEmpty()){ long at=a.optJSONObject("seen")==null?0:a.optJSONObject("seen").optLong(k); if(at>0&&now()-at<=REPEAT_MS) return false; }
      String allow=cfg.optString("allow");
      if(ModeDefs.ALLOW_NONE.equals(allow)) return true;
      int f=Nv.contactFlags(c,num);
      return ModeDefs.ALLOW_STARRED.equals(allow)?f<2:f<1;
    }
  }

  /** 안 받은 전화(부재중·거절)가 하나 끝났을 때. userAnswered: 받았거나 이미 문자로 거절했으면 true → 답장 안 함 */
  static void onUnanswered(Context c,String num,boolean userAnswered){
    synchronized(LOCK){
      tick(c);
      JSONObject a=ModeStore.active(c); if(a==null) return;
      if(userAnswered) return;
      try{
        String id=a.optString("id"); JSONObject cfg=ModeStore.cfg(c,id);
        String k=ModeRules.key(num);
        if(!k.isEmpty()){ JSONObject seen=a.optJSONObject("seen"); if(seen==null){ seen=new JSONObject(); a.put("seen",seen); } seen.put(k,now()); }
        Set<String> done=new HashSet<>(); JSONArray rep=a.optJSONArray("replied"); if(rep!=null) for(int i=0;i<rep.length();i++) done.add(rep.optString(i));
        boolean needContact=cfg.optBoolean("contactsOnly");
        boolean isContact=!needContact||Nv.contactFlags(c,num)>0;
        ModeRules.Why w=ModeRules.decide(cfg.optBoolean("reply",true),cfg.optBoolean("other"),needContact,isContact,num,false,done,a.optInt("sent"),cfg.optString("text"));
        String label=null;
        if(w==ModeRules.Why.SEND&&!Nv.has(c,Manifest.permission.SEND_SMS)) label="문자 보내기 허용이 없음";
        else if(w!=ModeRules.Why.SEND&&w!=ModeRules.Why.OFF) label=ModeRules.skipLabel(w,ModeRules.kind(num));
        if(label!=null){ JSONObject sk=a.optJSONObject("skips"); if(sk==null){ sk=new JSONObject(); a.put("skips",sk); } sk.put(label,sk.optInt(label)+1); }
        if(w==ModeRules.Why.SEND&&label==null){
          if(rep==null){ rep=new JSONArray(); a.put("replied",rep); }
          rep.put(k);                                  // 보내기 전에 먼저 적어서 같은 번호에 두 번 가지 않게
          a.put("sent",a.optInt("sent")+1);
          String nm=Nv.lookupName(c,num);
          JSONObject e=new JSONObject(); e.put("k",k); e.put("num",num); e.put("name",nm==null?"":nm); e.put("at",now());
          boolean ok=sendReply(c,num,cfg.optString("text"),k);
          e.put("st",ok?"wait":"fail");
          JSONArray log=a.optJSONArray("log"); if(log==null){ log=new JSONArray(); a.put("log",log); }
          log.put(e); if(log.length()>ModeStore.LOG_MAX){ JSONArray n=new JSONArray(); for(int i=log.length()-ModeStore.LOG_MAX;i<log.length();i++) n.put(log.get(i)); a.put("log",n); }
        }
        ModeStore.putActive(c,a);
        postOngoing(c,a);
      }catch(Exception e){}
    }
  }

  /** 기본 전화 앱이 아닐 때의 대안: 안드로이드가 알려 주는 전화 상태(울림→끝)로 「안 받은 전화」를 알아냄 */
  static void onPhoneState(Context c,String state,String number){
    SharedPreferences sp=c.getSharedPreferences("nvp_ps",Context.MODE_PRIVATE);
    if(ModeStore.active(c)==null||Nv.isDefault(c)){ if(sp.getBoolean("ring",false)) sp.edit().clear().apply(); return; }
    if("RINGING".equals(state)){
      SharedPreferences.Editor ed=sp.edit().putBoolean("ring",true);
      if(!sp.getBoolean("ring",false)) ed.putBoolean("off",false).putString("num","");
      if(number!=null&&!number.isEmpty()) ed.putString("num",number);
      ed.apply();
    } else if("OFFHOOK".equals(state)){
      if(sp.getBoolean("ring",false)) sp.edit().putBoolean("off",true).apply();
    } else if("IDLE".equals(state)){
      boolean ring=sp.getBoolean("ring",false), off=sp.getBoolean("off",false); String n=sp.getString("num","");
      sp.edit().clear().apply();
      if(ring) onUnanswered(c,n,off);
    }
  }

  // ============ 문자 보내기 ============
  static boolean sendReply(Context c,String to,String text,String key){
    try{
      SmsManager sm=Build.VERSION.SDK_INT>=31?c.getSystemService(SmsManager.class):SmsManager.getDefault();
      if(sm==null) return false;
      ArrayList<String> parts=splitter!=null?splitter.apply(text):sm.divideMessage(text);
      Intent in=new Intent(c,ModeActionReceiver.class).setAction(ACT_SMS_SENT).setData(Uri.parse("nvmode://sent/"+Uri.encode(key))).putExtra("k",key);
      PendingIntent pi=PendingIntent.getBroadcast(c,Math.abs(key.hashCode()%100000),in,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
      if(parts.size()<=1) sm.sendTextMessage(to,null,text,pi,null);
      else{ ArrayList<PendingIntent> sent=new ArrayList<>(); for(int i=0;i<parts.size();i++) sent.add(pi); sm.sendMultipartTextMessage(to,null,parts,sent,null); }
      return true;
    }catch(Exception e){ return false; }
  }

  // ============ 알람·알림 ============
  static PendingIntent bc(Context c,String act,int rq){
    return PendingIntent.getBroadcast(c,rq,new Intent(c,ModeActionReceiver.class).setAction(act),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  }
  static void schedule(Context c,JSONObject a){
    try{
      AlarmManager am=c.getSystemService(AlarmManager.class); if(am==null) return;
      long e=a.optLong("end"); PendingIntent pi=bc(c,ACT_END,41);
      am.cancel(pi);
      if(e>0) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,e,pi);   // 정확한 알람 권한 없이도 되는 방식(깊은 잠 중엔 몇 분 늦을 수 있음)
    }catch(Exception x){}
  }
  static void cancelAlarm(Context c){ try{ AlarmManager am=c.getSystemService(AlarmManager.class); if(am!=null) am.cancel(bc(c,ACT_END,41)); }catch(Exception x){} }

  static PendingIntent openApp(Context c,String view,int rq){
    Intent i=new Intent(c,MainActivity.class).setAction(MainActivity.ACTION_MODES).putExtra("view",view).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return PendingIntent.getActivity(c,rq,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  }

  static void postOngoing(Context c,JSONObject a){
    try{
      NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
      Nv.channels(c);
      String id=a.optString("id"); long end=a.optLong("end");
      StringBuilder tx=new StringBuilder();
      tx.append(a.optBoolean("dnd")?(a.optBoolean("keep")?"방해 금지는 원래 설정 그대로":"방해 금지 켜짐"):"방해 금지 허용이 없어 소리는 그대로");
      int n=a.optInt("sent"); if(n>0) tx.append(" · 답장 ").append(n).append("명");
      if(end>0) tx.append(" · ").append(ModeDefs.hm(end)).append("에 끝나요");
      Notification.Builder b=new Notification.Builder(c,Nv.CH_MODE).setSmallIcon(R.drawable.ic_mode)
        .setContentTitle(ModeDefs.name(id)+" 켜져 있어요").setContentText(tx.toString()).setCategory(Notification.CATEGORY_STATUS)
        .setOngoing(true).setOnlyAlertOnce(true).setWhen(a.optLong("start")).setShowWhen(true).setColor(0xFF8F6BFF)
        .setContentIntent(openApp(c,"modes",51))
        .addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(c,R.drawable.ic_end),"끄기",bc(c,ACT_STOP,42)).build());
      nm.notify(N_MODE,b.build());
    }catch(Exception e){}
  }

  static void postDone(Context c,JSONObject s){
    try{
      NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
      Nv.channels(c);
      JSONObject f=summaryFull(c,s);
      int calls=f.optJSONArray("calls")==null?0:f.optJSONArray("calls").length(), texts=f.optJSONArray("texts")==null?0:f.optJSONArray("texts").length(), rep=f.optJSONArray("log")==null?0:f.optJSONArray("log").length();
      List<String> parts=new ArrayList<>();
      if(calls>0) parts.add("부재중 "+calls+"명"); if(texts>0) parts.add("문자 "+texts+"명"); if(rep>0) parts.add("자동 답장 "+rep+"명");
      String body=parts.isEmpty()?"놓친 연락이 없어요":TextUtilJoin(parts);
      Notification.Builder b=new Notification.Builder(c,Nv.CH_MODE_END).setSmallIcon(R.drawable.ic_mode)
        .setContentTitle("「"+ModeDefs.name(s.optString("id"))+"」이 끝났어요").setContentText(body).setColor(0xFF8F6BFF)
        .setContentIntent(openApp(c,"summary",52)).setAutoCancel(true);
      nm.notify(N_DONE,b.build());
    }catch(Exception e){}
  }
  static String TextUtilJoin(List<String> l){ StringBuilder sb=new StringBuilder(); for(int i=0;i<l.size();i++){ if(i>0) sb.append(" · "); sb.append(l.get(i)); } return sb.toString(); }

  // ============ 화면에 넘겨 줄 상태 ============
  static JSONObject state(Context c){
    tick(c);
    JSONObject o=new JSONObject();
    try{
      o.put("now",now());
      JSONObject cf=new JSONObject(); JSONArray defs=new JSONArray();
      for(String id:ModeDefs.IDS){ cf.put(id,ModeStore.cfg(c,id));
        JSONObject d=new JSONObject(); d.put("id",id); d.put("name",ModeDefs.name(id)); d.put("timers",ModeDefs.timers(id)); d.put("text",ModeDefs.defaultText(id)); defs.put(d); }
      o.put("cfg",cf); o.put("defs",defs);
      JSONObject a=ModeStore.active(c);
      if(a!=null){ JSONObject x=new JSONObject(); x.put("id",a.optString("id")); x.put("start",a.optLong("start")); x.put("end",a.optLong("end"));
        x.put("dnd",a.optBoolean("dnd")); x.put("keep",a.optBoolean("keep")); x.put("sent",a.optInt("sent")); o.put("active",x); }
      JSONObject s=ModeStore.summary(c);
      if(s!=null){ JSONObject x=new JSONObject(); x.put("id",s.optString("id")); x.put("start",s.optLong("start")); x.put("end",s.optLong("end")); o.put("pending",x); }
      JSONObject p=new JSONObject();
      p.put("dnd",Dnd.granted(c)); p.put("sms",Nv.has(c,Manifest.permission.SEND_SMS)); p.put("readSms",Nv.has(c,Manifest.permission.READ_SMS));
      p.put("phone",Nv.has(c,Manifest.permission.READ_PHONE_STATE)); p.put("log",Nv.has(c,Manifest.permission.READ_CALL_LOG)); p.put("contacts",Nv.has(c,Manifest.permission.READ_CONTACTS));
      p.put("notif",Build.VERSION.SDK_INT<33||Nv.has(c,Manifest.permission.POST_NOTIFICATIONS));
      o.put("perms",p); o.put("def",Nv.isDefault(c));
    }catch(Exception e){}
    return o;
  }

  /** 끝난 모드의 요약: 놓친 전화(부재중·거절) · 받은 문자 · 보낸 자동 답장 · 안 보낸 이유 */
  static JSONObject summaryFull(Context c){ JSONObject s=ModeStore.summary(c); return s==null?null:summaryFull(c,s); }
  static JSONObject summaryFull(Context c,JSONObject s){
    JSONObject o=new JSONObject();
    try{
      long st=s.optLong("start"), en=s.optLong("end")>0?s.optLong("end"):now();
      o.put("id",s.optString("id")); o.put("name",ModeDefs.name(s.optString("id"))); o.put("start",st); o.put("end",en); o.put("why",s.optString("why"));
      o.put("durText",ModeDefs.fmtDur(en-st)); o.put("dnd",s.optBoolean("dnd"));
      o.put("log",s.optJSONArray("log")==null?new JSONArray():s.optJSONArray("log")); o.put("skips",s.optJSONObject("skips")==null?new JSONObject():s.optJSONObject("skips"));
      // 놓친 전화
      boolean hasLog=Nv.has(c,Manifest.permission.READ_CALL_LOG); o.put("needLog",!hasLog);
      List<ModeSummary.Row> rows=new ArrayList<>();
      if(hasLog){
        String[] pr={CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,CallLog.Calls.DATE,CallLog.Calls.NUMBER_PRESENTATION};
        String sel=CallLog.Calls.DATE+">=? AND "+CallLog.Calls.DATE+"<=? AND "+CallLog.Calls.TYPE+" IN (?,?)";
        String[] args={String.valueOf(st),String.valueOf(en+60000),String.valueOf(CallLog.Calls.MISSED_TYPE),String.valueOf(CallLog.Calls.REJECTED_TYPE)};
        try(Cursor q=c.getContentResolver().query(CallLog.Calls.CONTENT_URI,pr,sel,args,CallLog.Calls.DATE+" DESC")){
          while(q!=null&&q.moveToNext()){ boolean hid=q.getInt(3)!=CallLog.Calls.PRESENTATION_ALLOWED; String num=hid||q.getString(0)==null?"":q.getString(0);
            rows.add(new ModeSummary.Row(num,q.getString(1),q.getLong(2),"")); }
        }catch(Exception e){}
      }
      o.put("calls",groups(c,ModeSummary.group(rows)));
      // 받은 문자 (문자 읽기 허용이 있을 때만)
      boolean hasSms=Nv.has(c,Manifest.permission.READ_SMS); o.put("needSms",!hasSms);
      List<ModeSummary.Row> srows=new ArrayList<>();
      if(hasSms&&ModeStore.cfg(c,s.optString("id")).optBoolean("texts",true)){
        String[] pr={Telephony.Sms.ADDRESS,Telephony.Sms.DATE,Telephony.Sms.BODY};
        try(Cursor q=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,pr,Telephony.Sms.DATE+">=? AND "+Telephony.Sms.DATE+"<=?",new String[]{String.valueOf(st),String.valueOf(en+60000)},Telephony.Sms.DATE+" DESC")){
          while(q!=null&&q.moveToNext()) srows.add(new ModeSummary.Row(q.getString(0),null,q.getLong(1),ModeDefs.clip(q.getString(2),40)));
        }catch(Exception e){}
      }
      o.put("texts",groups(c,ModeSummary.group(srows)));
      o.put("textsOn",ModeStore.cfg(c,s.optString("id")).optBoolean("texts",true));
    }catch(Exception e){}
    return o;
  }
  private static JSONArray groups(Context c,List<ModeSummary.Grp> g) throws JSONException {
    JSONArray a=new JSONArray();
    for(ModeSummary.Grp x:g){
      JSONObject j=new JSONObject(); String nm=x.name;
      if((nm==null||nm.isEmpty())&&!x.num.isEmpty()){ String l=Nv.lookupName(c,x.num); if(l!=null) nm=l; }
      j.put("key",x.key); j.put("num",x.num); j.put("name",nm==null?"":nm); j.put("n",x.n); j.put("last",x.last); j.put("x",x.text);
      j.put("callable",!ModeRules.key(x.num).isEmpty());
      a.put(j);
    }
    return a;
  }
}

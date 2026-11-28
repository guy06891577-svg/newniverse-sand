package kr.newniverse.phone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.provider.CalendarContract;
import org.json.*;
import java.util.*;

/** v6 일정 10분 전 「회의 모드 켤까요?」. 폰 달력(구글·삼성·「뉴니버스」 로컬 달력 모두)의 다음 일정을 보고 알람을 걸어 둬요.
 *  기본은 「묻기」(알림의 켜기 단추), 설정에서 「자동으로 켜기」·「끄기」. 운전 중이면 띄우지 않고, 종일·3시간 넘는 일정은 빼요. */
final class CalWatch {
  private CalWatch(){}
  static final String PREF="nvp_cal", ACT_ALARM="kr.newniverse.phone.CAL_ALARM", ACT_ON="kr.newniverse.phone.CAL_ON", ACT_NO="kr.newniverse.phone.CAL_NO";
  static final int N_CAL=33, RQ_ALARM=71;
  static final long RESCAN=30*60000L, AHEAD=26*3600000L;
  static volatile java.util.function.LongSupplier clock=null;
  static long now(){ java.util.function.LongSupplier c=clock; return c!=null?c.getAsLong():System.currentTimeMillis(); }

  static SharedPreferences p(Context c){ return c.getSharedPreferences(PREF,0); }
  static String mode(Context c){ return CalRules.mode(p(c).getString("mode",CalRules.ASK)); }
  static void setMode(Context c,String m){ p(c).edit().putString("mode",CalRules.mode(m)).apply(); reschedule(c); }
  static boolean perm(Context c){ return Nv.has(c,Manifest.permission.READ_CALENDAR); }

  static final class Ev { long id,begin,end; String title; Ev(long id,long b,long e,String t){ this.id=id; begin=b; end=e; title=t==null?"":t; } String key(){ return CalRules.key(id,begin); } }

  /** from~to 사이에 시작하는, 묻기 대상 일정 (시작 순) */
  static List<Ev> upcoming(Context c,long from,long to){
    List<Ev> l=new ArrayList<>(); if(!perm(c)) return l;
    Uri.Builder b=CalendarContract.Instances.CONTENT_URI.buildUpon(); ContentUris.appendId(b,from); ContentUris.appendId(b,to);
    String[] pr={CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.TITLE,CalendarContract.Instances.ALL_DAY,CalendarContract.Instances.SELF_ATTENDEE_STATUS,CalendarContract.Instances.VISIBLE};
    try(Cursor q=c.getContentResolver().query(b.build(),pr,null,null,CalendarContract.Instances.BEGIN+" ASC")){
      while(q!=null&&q.moveToNext()){
        long id=q.getLong(0),bg=q.getLong(1),en=q.getLong(2); boolean all=q.getInt(4)==1; int st=q.isNull(5)?0:q.getInt(5); boolean vis=q.isNull(6)||q.getInt(6)==1;
        if(!vis||bg<from||bg>to) continue;
        if(!CalRules.eligible(all,bg,en,st)) continue;
        l.add(new Ev(id,bg,en,q.getString(3)));
      }
    }catch(Exception e){}
    return l;
  }
  static boolean done(Context c,String key){ return p(c).getStringSet("done",new HashSet<>()).contains(key); }
  static void markDone(Context c,String key){
    Set<String> s=new HashSet<>(p(c).getStringSet("done",new HashSet<>())); s.add(key);
    if(s.size()>200){ List<String> l=new ArrayList<>(s); Collections.sort(l); s=new HashSet<>(l.subList(l.size()-100,l.size())); }
    p(c).edit().putStringSet("done",s).apply();
  }

  /** 다음 확인 시각에 알람 (다음 일정 10분 전, 늦어도 30분 뒤에는 다시 훑음) */
  static long reschedule(Context c){
    AlarmManager am=c.getSystemService(AlarmManager.class); if(am==null) return 0;
    PendingIntent pi=alarmPi(c);
    if(CalRules.OFF.equals(mode(c))||!perm(c)){ am.cancel(pi); return 0; }
    long t=now(), at=t+RESCAN; boolean evAlarm=false;
    for(Ev e:upcoming(c,t+60000L,t+AHEAD)){ if(done(c,e.key())) continue; long x=CalRules.alarmAt(t,e.begin); if(x<at){ at=x; evAlarm=true; } break; }
    try{
      boolean exact=evAlarm&&(android.os.Build.VERSION.SDK_INT<31||am.canScheduleExactAlarms());   // 일정 알림만 정확히, 30분마다 훑기는 느슨하게
      if(exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi); else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
    }catch(Exception e){ try{ am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi); }catch(Exception x){} }
    return at;
  }
  static PendingIntent alarmPi(Context c){ return PendingIntent.getBroadcast(c,RQ_ALARM,new Intent(c,ModeActionReceiver.class).setAction(ACT_ALARM),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT); }

  /** 지금 물을 일정 (앱 안 카드도 이걸 씀). 운전 중·회의 중이면 null */
  static Ev soon(Context c){
    if(CalRules.OFF.equals(mode(c))||!perm(c)) return null;
    JSONObject a=ModeStore.active(c); if(CalRules.blockedByMode(a==null?null:a.optString("id"))) return null;
    long t=now();
    for(Ev e:upcoming(c,t,t+CalRules.WINDOW)) if(CalRules.due(t,e.begin)&&!dismissed(c,e.key())) return e;
    return null;
  }
  static boolean dismissed(Context c,String key){ return p(c).getStringSet("no",new HashSet<>()).contains(key); }
  static void dismiss(Context c,String key){ Set<String> s=new HashSet<>(p(c).getStringSet("no",new HashSet<>())); s.add(key); if(s.size()>100) s.clear(); s.add(key); p(c).edit().putStringSet("no",s).apply();
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(N_CAL); }

  /** 알람이 울렸을 때 */
  static void onAlarm(Context c){
    try{
      if(!CalRules.OFF.equals(mode(c))&&perm(c)){
        long t=now(); JSONObject a=ModeStore.active(c); boolean blocked=CalRules.blockedByMode(a==null?null:a.optString("id"));
        for(Ev e:upcoming(c,t,t+CalRules.WINDOW)){
          if(!CalRules.due(t,e.begin)||done(c,e.key())) continue;
          markDone(c,e.key());                       // 묻든 안 묻든 한 일정은 한 번만 (운전 중이라 건너뛴 것도)
          if(blocked||dismissed(c,e.key())) continue;
          if(CalRules.AUTO.equals(mode(c))) turnOn(c,e.key(),e.end); else post(c,e);
          break;
        }
      }
    }catch(Exception x){}
    reschedule(c);
  }
  static JSONObject turnOn(Context c,String key,long end){
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(N_CAL);
    if(key!=null) dismiss(c,key);
    return ModeManager.start(c,ModeDefs.MEETING,0,end);
  }
  static void post(Context c,Ev e){
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    Nv.channels(c);
    String t=e.title.isEmpty()?"일정":ModeDefs.clip(e.title,24);
    Intent on=new Intent(c,ModeActionReceiver.class).setAction(ACT_ON).putExtra("key",e.key()).putExtra("end",e.end);
    Intent no=new Intent(c,ModeActionReceiver.class).setAction(ACT_NO).putExtra("key",e.key());
    PendingIntent pOn=PendingIntent.getBroadcast(c,72,on,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent pNo=PendingIntent.getBroadcast(c,73,no,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    PendingIntent open=PendingIntent.getActivity(c,74,new Intent(c,MainActivity.class).setAction(MainActivity.ACTION_RECENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b=new Notification.Builder(c,Nv.CH_CAL).setSmallIcon(R.drawable.ic_mode)
      .setContentTitle("곧 일정 · "+t+" ("+ModeDefs.hm(e.begin)+")")
      .setContentText("회의 모드 켤까요? 끝나는 "+ModeDefs.hm(e.end)+"에 자동으로 꺼져요")
      .setContentIntent(open).setAutoCancel(true).setColor(0xFF8F6BFF).setCategory(Notification.CATEGORY_REMINDER)
      .setVisibility(Notification.VISIBILITY_PRIVATE)
      .addAction(new Notification.Action.Builder(Icon.createWithResource(c,R.drawable.ic_mode),"켜기",pOn).build())
      .addAction(new Notification.Action.Builder(Icon.createWithResource(c,R.drawable.ic_mode),"괜찮아요",pNo).build());
    long left=e.begin-now()+5*60000L; if(left>0) b.setTimeoutAfter(left);
    try{ nm.notify(N_CAL,b.build()); }catch(Exception x){}
  }
  /** 앱 안 카드용 */
  static JSONObject soonJson(Context c){
    JSONObject o=new JSONObject();
    try{ o.put("mode",mode(c)); o.put("perm",perm(c));
      Ev e=soon(c); if(e!=null){ o.put("key",e.key()); o.put("title",e.title); o.put("begin",e.begin); o.put("end",e.end); } }catch(Exception x){}
    return o;
  }
}

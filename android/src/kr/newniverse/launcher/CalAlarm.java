package kr.newniverse.launcher;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/**
 * v47 음력 기념일 알림 (전날·당일 오전 9시). 날짜 계산은 화면(kcal.js)이 하고, 여기는 받은 목록을
 * 이 폰 안에 저장해 두었다가 시각이 되면 알림만 띄운다. 인터넷·연락처·문자 권한을 쓰지 않는다.
 * 목록 한 줄: {id, at(밀리초), t(제목), x(내용)}. 알람은 setAndAllowWhileIdle(정확 알람 권한 불필요) — 절전 중엔 몇 분 늦을 수 있음.
 * 폰을 다시 켜거나 앱을 업데이트하면 다시 걸고, 꺼져 있던 동안 지난 알림(18시간 안)은 켜자마자 한 번 띄운다.
 */
public class CalAlarm extends BroadcastReceiver {
  static final String PREF="nv_cal", KEY_LIST="list", KEY_DONE="done", CH="nv_cal", ACT="kr.newniverse.launcher.CAL_ALARM";
  static final long LATE_MS=18L*3600*1000;

  static SharedPreferences sp(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

  /** 화면에서 받은 목록을 저장하고 다음 알람을 건다. 반환: 저장한 개수(-1 = 형식 오류) */
  static int save(Context c,String json){
    try{ JSONArray in=new JSONArray(json==null?"[]":json), out=new JSONArray();
      for(int i=0;i<in.length()&&out.length()<80;i++){ JSONObject o=in.optJSONObject(i); if(o==null) continue;
        String id=o.optString("id",""); long at=o.optLong("at",0); if(id.isEmpty()||at<=0) continue;
        JSONObject n=new JSONObject(); n.put("id",id); n.put("at",at); n.put("t",clip(o.optString("t",""),60)); n.put("x",clip(o.optString("x",""),120)); out.put(n); }
      sp(c).edit().putString(KEY_LIST,out.toString()).apply();
      schedule(c); return out.length();
    }catch(Exception e){ return -1; } }
  static String clip(String s,int n){ return s.length()>n?s.substring(0,n):s; }

  static JSONArray load(Context c){ try{ return new JSONArray(sp(c).getString(KEY_LIST,"[]")); }catch(Exception e){ return new JSONArray(); } }

  /** 앞으로 가장 가까운 알림 시각에 알람 하나만 건다(울리면 다음 것을 다시 건다) */
  static void schedule(Context c){
    try{ AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE); if(am==null) return;
      PendingIntent pi=pending(c); long now=System.currentTimeMillis(), next=Long.MAX_VALUE; JSONArray a=load(c); Set<String> done=done(c);
      for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o==null) continue; long at=o.optLong("at"); if(done.contains(o.optString("id"))) continue; if(at>now&&at<next) next=at; }
      am.cancel(pi);
      if(next!=Long.MAX_VALUE){ if(Build.VERSION.SDK_INT>=23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pi); else am.set(AlarmManager.RTC_WAKEUP,next,pi); }
    }catch(Exception e){} }
  static PendingIntent pending(Context c){ Intent i=new Intent(c,CalAlarm.class).setAction(ACT);
    return PendingIntent.getBroadcast(c,4701,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
  static Set<String> done(Context c){ return new HashSet<>(sp(c).getStringSet(KEY_DONE,new HashSet<String>())); }

  /** 시각이 지난(18시간 안) 알림을 한 번씩만 띄움 */
  static int fire(Context c){
    int n=0;
    try{ long now=System.currentTimeMillis(); JSONArray a=load(c); Set<String> done=done(c); Set<String> keep=new HashSet<>();
      NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
      for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o==null) continue; String id=o.optString("id"); long at=o.optLong("at");
        if(at>now+86400_000L*400) continue;
        if(at<=now&&now-at<=LATE_MS&&!done.contains(id)){ if(post(c,nm,o)) n++; done.add(id); }
        if(done.contains(id)&&now-at<3*86400_000L) keep.add(id); }
      sp(c).edit().putStringSet(KEY_DONE,keep).apply();
    }catch(Exception e){}
    schedule(c); return n; }

  static boolean post(Context c,NotificationManager nm,JSONObject o){
    try{ if(nm==null) return false;
      if(Build.VERSION.SDK_INT>=26){ NotificationChannel ch=new NotificationChannel(CH,"음력 기념일 알림",NotificationManager.IMPORTANCE_DEFAULT); ch.setDescription("등록한 음력 생일·기념일의 전날과 당일 아침"); nm.createNotificationChannel(ch); }
      Intent open=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()); if(open!=null) open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
      PendingIntent pi=open==null?null:PendingIntent.getActivity(c,4702,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CH):new Notification.Builder(c);
      b.setSmallIcon(c.getApplicationInfo().icon).setContentTitle(o.optString("t")).setContentText(o.optString("x")).setAutoCancel(true).setWhen(o.optLong("at")).setShowWhen(true);
      if(pi!=null) b.setContentIntent(pi);
      nm.notify(o.optString("id").hashCode(),b.build()); return true;
    }catch(Exception e){ return false; } }

  @Override public void onReceive(Context c,Intent i){ try{ fire(c); }catch(Exception e){} }
}

package kr.newniverse.sms;

import android.app.*;
import android.content.*;
import java.util.*;

/**
 * v10: 출금 예정(공과금·자동이체·카드 대금) 하루 전 오전 9시에 「내일 출금 예상 · 기관 · 금액」 알림.
 * 정확한 알람 권한 없이 setAndAllowWhileIdle(몇 분 늦을 수 있음). 알람에 담기는 것은 기관·금액·날짜뿐(문자 본문 없음).
 * 문자가 오거나 앱을 열거나 폰이 켜질 때 다시 계산해서 맞춤. 이미 알린 건 다시 알리지 않음.
 */
final class BillScheduler {
  static final String CH="money", ACTION="kr.newniverse.sms.action.BILL_TOMORROW", PREF="nv_money";

  static void channel(Context c){
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    NotificationChannel ch=new NotificationChannel(CH,"출금 예정 알림",NotificationManager.IMPORTANCE_DEFAULT);
    ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE); nm.createNotificationChannel(ch);
  }
  static String key(MoneyLogic.BillItem b){ return b.org+"|"+b.due+"|"+b.amt; }
  static int code(String k){ return 0x4d0000|(k.hashCode()&0xffff); }

  static void refresh(Context c){ refresh(c,System.currentTimeMillis()); }
  static void refresh(Context c,long now){
    try{
      if(!MoneyFeed.on(c)||!Nv.has(c,android.Manifest.permission.READ_SMS)) { cancelAll(c); return; }
      MoneyFeed.Data d=MoneyFeed.load(c,now);
      SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
      Set<String> sched=new HashSet<>(p.getStringSet("sched",new HashSet<>()));
      Set<String> notified=new HashSet<>(p.getStringSet("notified",new HashSet<>()));
      AlarmManager am=c.getSystemService(AlarmManager.class);
      Set<String> keep=new HashSet<>();
      for(MoneyLogic.BillItem b:d.bills){
        String k=key(b); keep.add(k);
        long at=MoneyLogic.alertAt(b.due,now);
        if(at>0&&am!=null){
          PendingIntent pi=PendingIntent.getBroadcast(c,code(k),intent(c,b),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
          am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);
        } else if(at<=0&&MoneyLogic.tomorrow(Collections.singletonList(b),now).size()==1&&!notified.contains(k)){
          // 문자가 하루 전 오전 9시보다 늦게 와서 알람 시각이 지났다면 지금 한 번만 알림
          post(c,b.org,b.amt,b.cat); notified.add(k);
        }
      }
      for(String k:sched) if(!keep.contains(k)&&am!=null){ cancel(c,k); }
      notified.retainAll(keep);
      p.edit().putStringSet("sched",keep).putStringSet("notified",notified).apply();
    }catch(Throwable e){}
  }
  static Intent intent(Context c,MoneyLogic.BillItem b){
    return new Intent(c,BillReceiver.class).setAction(ACTION).putExtra("org",b.org).putExtra("amt",b.amt).putExtra("cat",b.cat).putExtra("due",b.due);
  }
  static void cancel(Context c,String k){
    try{ AlarmManager am=c.getSystemService(AlarmManager.class); Intent i=new Intent(c,BillReceiver.class).setAction(ACTION);
      PendingIntent pi=PendingIntent.getBroadcast(c,code(k),i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_UPDATE_CURRENT);
      if(pi!=null&&am!=null) am.cancel(pi); }catch(Throwable e){}
  }
  static void markNotified(Context c,String k){ try{ SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); Set<String> n=new HashSet<>(p.getStringSet("notified",new HashSet<>())); n.add(k); p.edit().putStringSet("notified",n).apply(); }catch(Throwable e){} }
  static void cancelAll(Context c){
    try{ SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); for(String k:p.getStringSet("sched",new HashSet<>())) cancel(c,k); p.edit().remove("sched").apply(); }catch(Throwable e){}
  }
  static void post(Context c,String org,long amt,String cat){
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    channel(c);
    Intent open=new Intent(c,MainActivity.class).setAction(Nv.ACTION_MONEY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    int id=0x4e0000|((org+amt).hashCode()&0xffff);
    PendingIntent pi=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    String text=org+" · "+MoneyLogic.won(amt)+(cat==null||cat.isEmpty()?"":" · "+cat);
    Notification pub=new Notification.Builder(c,CH).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF).setContentTitle("내일 출금 예상").build();
    Notification.Builder b=new Notification.Builder(c,CH).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF)
      .setContentTitle("내일 출금 예상").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text+"\n빠른 확인용이에요. 은행·기관 안내를 꼭 확인해 주세요."))
      .setCategory(Notification.CATEGORY_REMINDER).setContentIntent(pi).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(pub);
    try{ nm.notify(id,b.build()); }catch(Exception e){}
  }
}

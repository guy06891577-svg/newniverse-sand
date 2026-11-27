package kr.newniverse.memo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * 알림 맞추기. 받아쓴 메모에 때 말이 있으면 저장과 함께 자동으로 걸어요. 확인 카드의 ‘취소’나 목록에서 끌 수 있어요.
 * 정확한 시각은 setAlarmClock 을 먼저 쓰고(잠자기 모드에서도 바로 울리게), 안 되면 setExactAndAllowWhileIdle,
 * 정확한 알람 허용이 없으면 대략적으로 맞추고 그 뜻을 확인 카드에 적어요.
 * 알림 표시 권한이 없어도 알람 시계는 걸어 두고, 카드에 그 사실을 적어요.
 */
final class Reminders {
  static final String CHANNEL="memo_reminder_v3", ACTION="kr.newniverse.memo.REMIND", EXTRA_ID="noteId";
  static final int OK_EXACT=0, OK_INEXACT=1, NEED_EXACT=2, NEED_NOTIF=3, PAST=4, ERROR=5;
  /** 마지막으로 쓴 알람 API 이름(진단·시험용) */
  static String lastMethod="";

  static AlarmManager am(Context c){ return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE); }
  static boolean canExact(Context c){
    if(Build.VERSION.SDK_INT<31) return true;
    try{ AlarmManager a=am(c); return a!=null&&a.canScheduleExactAlarms(); }catch(Throwable e){ return false; }
  }
  static boolean notifGranted(Context c){
    if(Build.VERSION.SDK_INT<33) return true;
    return c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
  }
  /** 알람·미리보기·알림 탭용으로 서로 다른 요청 코드 */
  static int req(long id,int salt){ return (int)((id^(id>>>32)^salt)&0x7fffffff); }
  static PendingIntent pi(Context c,long id,int flags){
    Intent i=new Intent(ACTION).setComponent(new ComponentName(c,ReminderReceiver.class)).putExtra(EXTRA_ID,id);
    return PendingIntent.getBroadcast(c,req(id,0x11),i,flags|PendingIntent.FLAG_IMMUTABLE);
  }
  static PendingIntent activityPi(Context c,long id,int salt){
    Intent open=new Intent(c,MainActivity.class).putExtra(MainActivity.EXTRA_NOTE,id)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
    return PendingIntent.getActivity(c,req(id,salt),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  }

  /** 알람을 걸어요. allowInexact=true 이면 정확한 알람 허용이 없어도 대략적으로 걸어요. 알림 표시 권한이 없어도 걸어요. */
  static int schedule(Context c,Note n,long when,boolean allowInexact){
    lastMethod="";
    long now=System.currentTimeMillis();
    if(when<=now) return PAST;
    AlarmManager a=am(c); if(a==null) return ERROR;
    boolean exact=canExact(c);
    if(!exact&&!allowInexact) return NEED_EXACT;
    try{
      PendingIntent p=pi(c,n.id,PendingIntent.FLAG_UPDATE_CURRENT);
      if(exact){
        try{
          AlarmManager.AlarmClockInfo info=new AlarmManager.AlarmClockInfo(when,activityPi(c,n.id,0x22));
          a.setAlarmClock(info,p);
          lastMethod="setAlarmClock";
        }catch(SecurityException se){
          try{ a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p); lastMethod="setExactAndAllowWhileIdle"; }
          catch(SecurityException se2){
            if(!allowInexact) return NEED_EXACT;
            exact=false; a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p); lastMethod="setAndAllowWhileIdle";
          }
        }
      } else { a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,p); lastMethod="setAndAllowWhileIdle"; }
    }catch(Throwable e){ lastMethod=""; return ERROR; }
    n.remindAt=when; n.remindExact=exact; n.reminded=false;
    return exact?OK_EXACT:OK_INEXACT;
  }
  static void cancel(Context c,Note n){
    try{
      AlarmManager a=am(c); if(a!=null){
        a.cancel(pi(c,n.id,PendingIntent.FLAG_UPDATE_CURRENT));
        try{ a.cancel(activityPi(c,n.id,0x22)); }catch(Throwable e){}
      }
    }catch(Throwable e){}
    n.remindAt=0; n.remindExact=false; n.reminded=false;
  }

  static Intent exactSettings(Context c){
    return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }
  static Intent notifSettings(Context c){
    return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  }
  static Intent batterySettings(Context c){
    try{ return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }
    catch(Throwable e){ return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }
  }

  static void ensureChannel(Context c){
    if(Build.VERSION.SDK_INT<26) return;
    NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE); if(nm==null) return;
    NotificationChannel ch=new NotificationChannel(CHANNEL,"메모 알림",NotificationManager.IMPORTANCE_HIGH);
    ch.setDescription("메모에 적힌 때에 맞춰 알려 줘요");
    ch.enableVibration(true); ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
    try{
      Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
      if(sound==null) sound=Settings.System.DEFAULT_NOTIFICATION_URI;
      AudioAttributes aa=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
      ch.setSound(sound,aa);
    }catch(Throwable e){}
    nm.createNotificationChannel(ch);
  }

  /** 알림 띄우기. 잠금 화면에는 내용을 숨기고 ‘뉴니버스 메모 알림’만 보여요. 전체 화면 안내도 함께 걸어요. */
  static boolean post(Context c,Note n,boolean late){
    if(!notifGranted(c)) return false;
    ensureChannel(c);
    NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE); if(nm==null) return false;
    PendingIntent cp=activityPi(c,n.id,0x33);
    PendingIntent fs=activityPi(c,n.id,0x44);
    String body=n.text.isEmpty()?"소리로 저장한 메모":(n.text.length()>120?n.text.substring(0,120)+"…":n.text);
    Notification.Builder pub=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle("뉴니버스 메모 알림").setContentText("알림을 확인하려면 잠금을 풀어 주세요");
    Notification.Builder b=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_stat)
      .setContentTitle(late?"뉴니버스 빠른 메모 · 놓친 알림":"뉴니버스 빠른 메모").setContentText(body)
      .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(cp).setAutoCancel(true)
      .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(pub.build())
      .setDefaults(Notification.DEFAULT_SOUND|Notification.DEFAULT_VIBRATE)
      .setFullScreenIntent(fs,true)
      .setWhen(n.remindAt>0?n.remindAt:System.currentTimeMillis()).setShowWhen(true);
    try{ nm.notify(req(n.id,0x55),b.build()); return true; }catch(Throwable e){ return false; }
  }
}

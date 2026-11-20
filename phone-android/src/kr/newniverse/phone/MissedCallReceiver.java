package kr.newniverse.phone;

import android.app.*;
import android.content.*;
import android.telecom.TelecomManager;

/** 기본 전화 앱일 때 시스템이 "부재중 알림을 보여 달라"고 보내는 신호를 받아 알림을 띄움 */
public class MissedCallReceiver extends BroadcastReceiver {
  static final int N_MISS=13;
  @Override public void onReceive(Context c,Intent i){
    if(!TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION.equals(i.getAction())) return;
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    int n=i.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT,0);
    if(n<=0){ nm.cancel(N_MISS); return; }
    Nv.channels(c);
    String num=i.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER);
    String who=null; if(num!=null&&!num.isEmpty()){ who=Nv.lookupName(c,num); if(who==null) who=Nv.fmt(num); }
    Intent open=new Intent(c,MainActivity.class).setAction(MainActivity.ACTION_RECENT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent pi=PendingIntent.getActivity(c,20,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b=new Notification.Builder(c,Nv.CH_MISS).setSmallIcon(R.drawable.ic_call).setColor(0xFFFF4D6D)
      .setContentTitle("부재중 전화 "+n+"통").setContentText(who==null?"최근 통화에서 확인하세요":(n==1?who:who+" 외")).setCategory(Notification.CATEGORY_MISSED_CALL)
      .setContentIntent(pi).setAutoCancel(true).setNumber(n);
    try{ nm.notify(N_MISS,b.build()); }catch(Exception e){}
  }
}

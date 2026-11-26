package kr.newniverse.sms;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** 기본 문자 앱일 때: 받은 문자를 폰 문자 저장소에 직접 저장하고 알림 */
public class SmsDeliverReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    if(!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(i.getAction())) return;
    Msg m=parse(i); if(m==null) return;
    try{
      ContentValues v=new ContentValues();
      v.put(Telephony.Sms.ADDRESS,m.addr); v.put(Telephony.Sms.BODY,m.body);
      v.put(Telephony.Sms.DATE,System.currentTimeMillis()); v.put(Telephony.Sms.DATE_SENT,m.sent);
      v.put(Telephony.Sms.READ,0); v.put(Telephony.Sms.SEEN,0);
      int sub=i.getIntExtra("subscription",-1); if(sub>=0) v.put(Telephony.Sms.SUBSCRIPTION_ID,sub);
      c.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI,v);
    }catch(Exception e){}
    notify(c,m.addr,m.body);
    FeedProvider.ping(c);
    AutoCal.onSms(c,m.addr,m.body,System.currentTimeMillis());
    MainActivity.refresh();
  }
  static final class Msg{ String addr="",body=""; long sent; }
  static Msg parse(Intent i){
    SmsMessage[] ms; try{ ms=Telephony.Sms.Intents.getMessagesFromIntent(i); }catch(Exception e){ return null; }
    if(ms==null||ms.length==0) return null;
    Msg m=new Msg(); StringBuilder sb=new StringBuilder();
    for(SmsMessage s:ms){ if(s==null) continue; if(m.addr.isEmpty()&&s.getDisplayOriginatingAddress()!=null) m.addr=s.getDisplayOriginatingAddress(); if(m.sent==0) m.sent=s.getTimestampMillis(); String b=s.getDisplayMessageBody(); if(b!=null) sb.append(b); }
    m.body=sb.toString(); return m;
  }
  /** 알림: 잠금 화면에는 '새 문자'만 보이고 내용은 숨김 */
  static void notify(Context c,String addr,String body){
    if(MainActivity.resumed&&addr!=null&&Nv.digits(addr).equals(MainActivity.viewing)) return; // 지금 그 대화를 보고 있음
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    Nv.channels(c);
    String who=Nv.lookupName(c,addr); if(who==null) who=Nv.fmt(addr);
    Intent open=new Intent(c,MainActivity.class).setAction(Nv.ACTION_OPEN).putExtra("addr",addr).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    int id=Nv.notifId(addr);
    PendingIntent pi=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification pub=new Notification.Builder(c,Nv.CH_MSG).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF).setContentTitle("새 문자").build();
    Notification.Builder b=new Notification.Builder(c,Nv.CH_MSG).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF)
      .setContentTitle(who).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
      .setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(pi).setAutoCancel(true)
      .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(pub).setWhen(System.currentTimeMillis()).setShowWhen(true);
    try{ nm.notify(id,b.build()); }catch(Exception e){}
  }
}

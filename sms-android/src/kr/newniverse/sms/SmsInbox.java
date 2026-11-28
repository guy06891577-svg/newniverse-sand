package kr.newniverse.sms;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** v3: 기본 문자 앱으로서 받은 문자를 폰 저장소에 넣고, 실패하면 다시 시도·기록 */
final class SmsInbox {

  static long subIdFrom(Intent i){
    if(i==null||i.getExtras()==null) return InboxLogic.subId(null);
    return InboxLogic.subId(i.getExtras().get("subscription"));
  }

  static long threadId(Context c,String addr){
    if(addr==null||addr.trim().isEmpty()) return -1;
    try{ return Telephony.Threads.getOrCreateThreadId(c,addr); }catch(Throwable e){ return -1; }
  }

  static ContentValues cv(Map<String,Object> m){
    ContentValues v=new ContentValues();
    for(Map.Entry<String,Object> e:m.entrySet()){
      Object o=e.getValue();
      if(o instanceof Long) v.put(e.getKey(),(Long)o);
      else if(o instanceof Integer) v.put(e.getKey(),(Integer)o);
      else if(o instanceof String) v.put(e.getKey(),(String)o);
      else if(o!=null) v.put(e.getKey(),String.valueOf(o));
    }
    return v;
  }

  static boolean exists(Context c,String addr,String body,long now){
    if(!Nv.has(c,android.Manifest.permission.READ_SMS)&&!Nv.isDefault(c)) return false;
    List<InboxLogic.Row> rows=new ArrayList<>();
    long since=now-InboxLogic.DEDUP_MS;
    try(Cursor q=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
        new String[]{Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE},
        Telephony.Sms.DATE+">?",new String[]{String.valueOf(since)},Telephony.Sms.DATE+" DESC")){
      while(q!=null&&q.moveToNext()) rows.add(new InboxLogic.Row(q.getString(0),q.getString(1),q.getLong(2)));
    }catch(Exception e){}
    return InboxLogic.isDup(addr,body,now,rows);
  }

  /**
   * 받은 문자를 inbox 에 넣는다. 이미 있으면 dup.
   * 반환: 저장된 행 URI, 이미 있으면 기존을 의미하는 Inbox URI, 실패하면 null.
   */
  static Uri save(Context c,String addr,String body,long sent,long subId,String source){
    long now=System.currentTimeMillis();
    if(exists(c,addr,body,now)){ SmsDiag.incoming(c,source,"dup",""); return Telephony.Sms.Inbox.CONTENT_URI; }
    long tid=threadId(c,addr);
    String err="";
    try{
      ContentValues v=cv(InboxLogic.valuesFull(addr,body,now,sent,tid,subId));
      Uri row=c.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI,v);
      if(row!=null){ SmsDiag.incoming(c,source,"ok",""); return row; }
    }catch(Exception e){ err=InboxLogic.errClass(e); }
    try{
      ContentValues v=cv(InboxLogic.valuesMinimal(addr,body,now,tid));
      Uri row=c.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI,v);
      if(row!=null){ SmsDiag.incoming(c,source,"retry-ok",err); return row; }
    }catch(Exception e){ err=err.isEmpty()?InboxLogic.errClass(e):err; }
    SmsDiag.incoming(c,source,"fail",err);
    return null;
  }

  /** 저장 성공/실패에 맞는 알림 + 런처 신호 + 달력 + 화면 새로 고침 */
  static void announce(Context c,String addr,String body,boolean saved){
    // v8: 알림·신호가 던져도 달력 처리는 건너뛰지 않게 각각 따로 감쌈
    try{ notify(c,addr,body,saved); }catch(Throwable e){}
    if(saved){ try{ FeedProvider.ping(c); }catch(Throwable e){} }
    if(body!=null&&!body.isEmpty()){ try{ AutoCal.onSms(c,addr,body,System.currentTimeMillis(),"SMS"); }catch(Throwable e){} } // v8: saved 와 무관(저장이 실패한 문자도 일정은 읽음)
    try{ BillScheduler.refresh(c); }catch(Throwable e){} // v10: 출금 예정 문자가 왔으면 하루 전 알림 맞춤
    MainActivity.refresh();
  }

  static void notify(Context c,String addr,String body,boolean saved){
    if(saved&&MainActivity.resumed&&addr!=null&&Nv.digits(addr).equals(MainActivity.viewing)) return;
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    Nv.channels(c);
    String who=saved?Nv.lookupName(c,addr):null; if(who==null&&saved) who=Nv.fmt(addr);
    String title=InboxLogic.notifyWho(saved,who);
    String text=saved?(body==null?"":body):InboxLogic.notifyFailText();
    Intent open=new Intent(c,MainActivity.class).setAction(Nv.ACTION_OPEN).putExtra("addr",addr==null?"":addr).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    int id=saved?Nv.notifId(addr):1999;
    PendingIntent pi=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification pub=new Notification.Builder(c,Nv.CH_MSG).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF).setContentTitle(InboxLogic.notifyPublic(saved)).build();
    Notification.Builder b=new Notification.Builder(c,Nv.CH_MSG).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF)
      .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
      .setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(pi).setAutoCancel(true)
      .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(pub).setWhen(System.currentTimeMillis()).setShowWhen(true);
    try{ nm.notify(id,b.build()); }catch(Exception e){}
  }
}

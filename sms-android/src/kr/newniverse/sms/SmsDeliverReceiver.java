package kr.newniverse.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** 기본 문자 앱일 때: 받은 문자를 폰 문자 저장소에 직접 저장하고 알림 (실패하면 다시 시도·정직하게 알림) */
public class SmsDeliverReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    if(!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(i.getAction())) return;
    PendingResult pr=goAsync();
    try{
      Msg m=parse(i);
      if(m==null){ SmsDiag.incoming(c,"deliver","parse",""); return; }
      Uri row=SmsInbox.save(c,m.addr,m.body,m.sent,SmsInbox.subIdFrom(i),"deliver");
      SmsInbox.announce(c,m.addr,m.body,row!=null);
    }catch(Exception e){
      try{ SmsDiag.incoming(c,"deliver","fail",InboxLogic.errClass(e)); }catch(Exception x){}
    }finally{ try{ pr.finish(); }catch(Exception e){} }
  }
  static final class Msg{ String addr="",body=""; long sent; }
  /** 여러 조각(PDU)의 본문을 한 통으로 이어 붙임 */
  static Msg parse(Intent i){
    SmsMessage[] ms; try{ ms=Telephony.Sms.Intents.getMessagesFromIntent(i); }catch(Exception e){ return null; }
    if(ms==null||ms.length==0) return null;
    Msg m=new Msg();
    String[] parts=new String[ms.length];
    for(int k=0;k<ms.length;k++){
      SmsMessage s=ms[k]; if(s==null) continue;
      if(m.addr.isEmpty()&&s.getDisplayOriginatingAddress()!=null) m.addr=s.getDisplayOriginatingAddress();
      if(m.sent==0) m.sent=s.getTimestampMillis();
      parts[k]=s.getDisplayMessageBody();
    }
    m.body=InboxLogic.concatParts(parts);
    return m;
  }
}

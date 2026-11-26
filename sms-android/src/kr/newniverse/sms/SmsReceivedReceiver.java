package kr.newniverse.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;

/**
 * 기본 앱이 아닐 때: 저장은 원래 문자 앱이 하고, 여기선 알림·화면 새로 고침만.
 * 기본 앱일 때: SMS_DELIVER 저장이 빠졌으면 잠시 뒤 한 번 더 넣어 보는 안전망(이미 있으면 건너뜀).
 */
public class SmsReceivedReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(i.getAction())) return;
    SmsDeliverReceiver.Msg m=SmsDeliverReceiver.parse(i); if(m==null) return;
    if(!Nv.isDefault(c)){
      if(!c.getSharedPreferences("nv",0).getBoolean("notifyNonDefault",true)){ MainActivity.refresh(); return; }
      SmsInbox.notify(c,m.addr,m.body,true);
      new Handler(Looper.getMainLooper()).postDelayed(MainActivity::refresh,1500);
      return;
    }
    final Context app=c.getApplicationContext();
    final String addr=m.addr, body=m.body; final long sent=m.sent; final long sub=SmsInbox.subIdFrom(i);
    new Handler(Looper.getMainLooper()).postDelayed(()->{
      try{
        if(SmsInbox.exists(app,addr,body,System.currentTimeMillis())) return;
        Uri row=SmsInbox.save(app,addr,body,sent,sub,"received");
        if(row!=null) SmsInbox.announce(app,addr,body,true);
      }catch(Exception e){
        try{ SmsDiag.incoming(app,"received","fail",InboxLogic.errClass(e)); }catch(Exception x){}
      }
    },1500);
  }
}

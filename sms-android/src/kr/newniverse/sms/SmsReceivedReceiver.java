package kr.newniverse.sms;

import android.content.*;
import android.provider.Telephony;

/** 기본 앱이 아닐 때: 저장은 원래 문자 앱이 하고, 여기선 알림·화면 새로 고침만 (기본 앱이면 SMS_DELIVER 쪽이 처리) */
public class SmsReceivedReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    if(!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(i.getAction())||Nv.isDefault(c)) return;
    if(!c.getSharedPreferences("nv",0).getBoolean("notifyNonDefault",true)) { MainActivity.refresh(); return; }
    SmsDeliverReceiver.Msg m=SmsDeliverReceiver.parse(i); if(m==null) return;
    SmsDeliverReceiver.notify(c,m.addr,m.body);
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(MainActivity::refresh,1500); // 원래 앱이 저장할 시간
  }
}

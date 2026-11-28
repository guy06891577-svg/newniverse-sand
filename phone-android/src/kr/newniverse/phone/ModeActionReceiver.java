package kr.newniverse.phone;

import android.app.Activity;
import android.content.*;

/** 모드 알림의 「끄기」 · 자동 끝내기 알람 · 답장 문자가 나갔는지 결과. 앱 밖에서는 보낼 수 없게 exported=false */
public class ModeActionReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    String a=i==null?null:i.getAction(); if(a==null) return;
    if(ModeManager.ACT_STOP.equals(a)) ModeManager.stop(c,"manual");
    else if(ModeManager.ACT_END.equals(a)) ModeManager.tick(c);
    else if(CalWatch.ACT_ALARM.equals(a)) CalWatch.onAlarm(c);
    else if(CalWatch.ACT_ON.equals(a)) CalWatch.turnOn(c,i.getStringExtra("key"),i.getLongExtra("end",0));
    else if(CalWatch.ACT_NO.equals(a)){ String k=i.getStringExtra("key"); if(k!=null) CalWatch.dismiss(c,k); }
    else if(ModeManager.ACT_SMS_SENT.equals(a)){ String k=i.getStringExtra("k"); if(k!=null) ModeStore.markSent(c,k,getResultCode()==Activity.RESULT_OK); }
  }
}

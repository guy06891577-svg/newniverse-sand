package kr.newniverse.sms;

import android.content.*;

/** v10: 폰이 켜지거나 앱이 새로 깔린 뒤 출금 예정 알람을 다시 맞춤 (시스템 신호만 처리) */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    try{ String a=i==null?null:i.getAction(); if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) BillScheduler.refresh(c); }catch(Throwable e){}
  }
}

package kr.newniverse.phone;

import android.content.*;
import android.telephony.TelephonyManager;

/** 시스템이 보내는 신호: 부팅 끝 · 앱 업데이트 끝(켜져 있던 모드의 알람·알림 다시 걸기) · 전화 상태(기본 전화 앱이 아닐 때 안 받은 전화 알아내기) */
public class ModeSystemReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    String a=i==null?null:i.getAction(); if(a==null) return;
    if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) ModeManager.resume(c);
    else if(TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(a)){
      String st=i.getStringExtra(TelephonyManager.EXTRA_STATE), num=null;
      try{ num=i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER); }catch(Exception e){}
      if(st!=null) ModeManager.onPhoneState(c,st,num);
    }
  }
}

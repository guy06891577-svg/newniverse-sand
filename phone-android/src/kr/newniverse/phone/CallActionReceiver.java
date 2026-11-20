package kr.newniverse.phone;

import android.content.*;

/** 알림의 '거절' · '끊기' 버튼 */
public class CallActionReceiver extends BroadcastReceiver {
  static final String DECLINE="kr.newniverse.phone.DECLINE", HANGUP="kr.newniverse.phone.HANGUP";
  @Override public void onReceive(Context c,Intent i){
    String a=i.getAction();
    if(DECLINE.equals(a)) CallService.reject(null);
    else if(HANGUP.equals(a)) CallService.hangup();
  }
}

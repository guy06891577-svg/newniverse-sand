package kr.newniverse.sms;

import android.content.*;

/** v10: 출금 하루 전 알람 도착 → 「내일 출금 예상」 알림. (앱 안에서만 받음, 밖에서 못 보냄) */
public class BillReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    try{
      String a=i==null?null:i.getAction();
      if(BillScheduler.ACTION.equals(a)){
        if(!MoneyFeed.on(c)) return;
        String org=i.getStringExtra("org"); long amt=i.getLongExtra("amt",0); String due=i.getStringExtra("due");
        if(org==null||amt<=0) return;
        // 알람이 늦게 울려도 ‘내일’이 아니면 알리지 않음(날짜 어긋남 방지)
        if(due!=null&&!MoneyLogic.day(System.currentTimeMillis()).plusDays(1).toString().equals(due)) return;
        BillScheduler.post(c,org,amt,i.getStringExtra("cat")); BillScheduler.markNotified(c,org+"|"+due+"|"+amt);
      }
    }catch(Throwable e){}
  }
}

package kr.newniverse.memo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 알람 시각이 되면 알림을 띄워요(메모가 지워졌거나 알림을 취소했으면 아무것도 안 함) */
public class ReminderReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    try{
      long id=i.getLongExtra(Reminders.EXTRA_ID,0);
      NoteStore s=new NoteStore(c.getFilesDir());
      Note n=s.get(id);
      if(n==null||n.remindAt<=0||n.reminded) return;
      if(Reminders.post(c,n,false)){ n.reminded=true; s.update(n); }
    }catch(Throwable e){}
  }
}

package kr.newniverse.memo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 폰을 껐다 켜거나 앱을 업데이트하면 알람이 지워지니, 남은 알림을 다시 걸어요. 그 사이에 놓친 알림은 ‘놓친 알림’으로 바로 띄워요 */
public class BootReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    try{ rearm(c); }catch(Throwable e){}
  }
  static int rearm(Context c){
    NoteStore s=new NoteStore(c.getFilesDir()); int k=0; long now=System.currentTimeMillis();
    for(Note n:s.pendingReminders()){
      if(n.remindAt<=now){ if(Reminders.post(c,n,true)){ n.reminded=true; s.update(n); } }
      else { long w=n.remindAt; int r=Reminders.schedule(c,n,w,true); if(r==Reminders.OK_EXACT||r==Reminders.OK_INEXACT){ s.update(n); k++; } }
    }
    return k;
  }
}

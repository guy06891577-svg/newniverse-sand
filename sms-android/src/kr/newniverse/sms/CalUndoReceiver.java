package kr.newniverse.sms;

import android.app.NotificationManager;
import android.content.*;
import android.widget.Toast;

/** 「달력에 넣었어요」 알림의 취소 단추: 넣은 일정을 지우고(고친 건 되돌리고, 뺀 건 다시 넣음) */
public class CalUndoReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    try{ CalLogic.undo(new CalStore(c),i.getStringExtra("op"),AutoCal.get(i,"e"),AutoCal.get(i,"p"));
      NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(i.getIntExtra("nid",0));
      Toast.makeText(c,"취소했어요",Toast.LENGTH_SHORT).show();
    }catch(Exception e){}
  }
}

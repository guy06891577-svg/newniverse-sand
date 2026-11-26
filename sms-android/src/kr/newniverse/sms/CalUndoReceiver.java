package kr.newniverse.sms;

import android.app.NotificationManager;
import android.content.*;
import android.widget.Toast;

/** 「달력에 넣었어요」 알림의 취소 단추: 넣은 일정을 지우고(고친 건 되돌리고, 뺀 건 다시 넣음) */
public class CalUndoReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    String act=i.getAction();
    if(AutoCal.ACT_ASK_NO.equals(act)){ try{ SmsDiag.cal(c,"ask>no","declined"); NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(i.getIntExtra("nid",0)); }catch(Exception e){} return; }
    if(AutoCal.ACT_ASK_ADD.equals(act)){
      final PendingResult pr=goAsync(); final Context app=c.getApplicationContext();
      final String addr=i.getStringExtra("addr"), body=i.getStringExtra("body"); final long at=i.getLongExtra("at",System.currentTimeMillis()); final int nid=i.getIntExtra("nid",0);
      new Thread(()->{ try{
        String r=AutoCal.manual(app,addr,body,at);
        NotificationManager nm=app.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(nid);
        if("ok".equals(r)||"dup".equals(r)) SmsDiag.cal(app,"ask>ok","dup".equals(r)?"dup":"");
        else if("perm".equals(r)){ SmsDiag.cal(app,"ask>fail","noperm"); try{ app.startActivity(new Intent(app,MainActivity.class).putExtra("calperm",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }catch(Exception e){} }
        else SmsDiag.cal(app,"ask>fail","fail".equals(r)?"insert":"noparse");
      }catch(Exception e){} finally{ try{ pr.finish(); }catch(Exception e){} } }).start();
      return; }
    try{ CalLogic.undo(new CalStore(c),i.getStringExtra("op"),AutoCal.get(i,"e"),AutoCal.get(i,"p"));
      NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null) nm.cancel(i.getIntExtra("nid",0));
      Toast.makeText(c,"취소했어요",Toast.LENGTH_SHORT).show();
    }catch(Exception e){}
  }
}

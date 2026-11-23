package kr.newniverse.sms;

import android.app.*;
import android.content.*;

/** 사진·장문(MMS) 도착 신호. v1은 내려받기를 아직 못 함 → 알림으로만 알림 (다음 판에서 내려받기·저장) */
public class MmsReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context c,Intent i){
    NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
    Nv.channels(c);
    Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    PendingIntent pi=PendingIntent.getActivity(c,31,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b=new Notification.Builder(c,Nv.CH_MSG).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF)
      .setContentTitle("사진·장문 문자가 왔어요").setContentText("뉴니버스 문자 v1은 아직 받지 못해요. 원래 문자 앱을 기본으로 돌려 주세요.")
      .setStyle(new Notification.BigTextStyle().bigText("뉴니버스 문자 v1은 사진·장문(MMS)을 아직 받지 못해요. ⋮ › 원래 문자 앱으로 돌아가기에서 원래 앱을 기본으로 돌려 주세요."))
      .setCategory(Notification.CATEGORY_MESSAGE).setContentIntent(pi).setAutoCancel(true);
    try{ nm.notify(30,b.build()); }catch(Exception e){}
  }
}

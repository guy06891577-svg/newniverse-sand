package kr.newniverse.sms;

import android.app.*;
import android.content.*;
import android.Manifest;
import java.util.Calendar;

/**
 * v2: 받은 문자에서 일정을 뽑아 「뉴니버스」 달력에 자동으로 넣기 (끄고 켤 수 있음).
 * 안전: 광고·의심 링크 문자는 건너뛰고, 주소록에 있는 사람이나 병원·택배·공공기관처럼 분명한 곳의 문자만 자동으로 넣음.
 * 넣을 때마다 「달력에 넣었어요」 알림 + 취소 단추. 인터넷 권한 없음.
 */
final class AutoCal {
  static final String PREF="nv_cal", CH="cal";
  static boolean on(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("on",false); }
  static void setOn(Context c,boolean v){ c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("on",v).putBoolean("asked",true).apply(); }
  static boolean asked(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("asked",false); }
  static boolean perm(Context c){ return Nv.has(c,Manifest.permission.READ_CALENDAR)&&Nv.has(c,Manifest.permission.WRITE_CALENDAR); }

  /** 새 문자 받을 때(기본 문자 앱) */
  static void onSms(Context c,String addr,String body,long at){
    try{ if(!on(c)||!perm(c)) return;
      if(SmsSafety.isAd(body)||SmsSafety.suspicious(body)) return;
      String name=null; try{ name=Nv.lookupName(c,addr); }catch(Exception e){}
      ApptParser.R r=ApptParser.parse(body,at,name); if(r==null||!r.trusted) return;
      CalLogic.Out o=CalLogic.apply(new CalStore(c),r,addr,false); notifyOut(c,o);
    }catch(Exception e){}
  }
  /** 카드의 「달력에 추가」: ok / dup / perm / unsafe / none */
  static String manual(Context c,String addr,String body,long at){
    try{ if(!perm(c)) return "perm";
      if(SmsSafety.isAd(body)||SmsSafety.suspicious(body)) return "unsafe";
      String name=null; try{ name=Nv.lookupName(c,addr); }catch(Exception e){}
      ApptParser.R r=ApptParser.parse(body,at,name); if(r==null) return "none";
      if(r.org.isEmpty()) r.title="예약";
      CalLogic.Out o=CalLogic.apply(new CalStore(c),r,addr,true); notifyOut(c,o);
      return "dup".equals(o.op)?"dup":"none".equals(o.op)?"none":"ok";
    }catch(Exception e){ return "none"; }
  }
  /** 이미 달력에 넣은 일정인지(카드의 단추 모양 결정) */
  static boolean has(Context c,String addr,String body,long at){
    try{ if(!perm(c)) return false; String name=null; try{ name=Nv.lookupName(c,addr); }catch(Exception e){}
      ApptParser.R r=ApptParser.parse(body,at,name); if(r==null||r.kind==ApptParser.CANCEL) return false;
      boolean all=!r.hasTime||r.kind==ApptParser.PARCEL;
      for(CalLogic.Entry e:new CalStore(c).load()) if(!e.undone&&e.org.equals(r.org)&&e.start==r.when&&e.allDay==all) return true;
    }catch(Exception e){}
    return false;
  }
  static String when(long ms,boolean allDay){
    Calendar c=ApptParser.cal(ms); String[] w={"일","월","화","수","목","금","토"};
    String s=(c.get(Calendar.MONTH)+1)+"월 "+c.get(Calendar.DATE)+"일("+w[c.get(Calendar.DAY_OF_WEEK)-1]+")";
    if(!allDay){ int h=c.get(Calendar.HOUR_OF_DAY),m=c.get(Calendar.MINUTE); s+=" "+(h<12?"오전 ":"오후 ")+(h%12==0?12:h%12)+":"+(m<10?"0":"")+m; }
    return s;
  }
  static void notifyOut(Context c,CalLogic.Out o){
    if(o==null||"none".equals(o.op)||"dup".equals(o.op)) return;
    try{ NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return;
      nm.createNotificationChannel(new NotificationChannel(CH,"달력에 넣은 일정",NotificationManager.IMPORTANCE_LOW));
      CalLogic.Entry e=o.e!=null?o.e:o.prev; if(e==null) return;
      String head="add".equals(o.op)?"달력에 넣었어요":"update".equals(o.op)?"달력 일정을 바꿨어요":"달력에서 뺐어요";
      Intent u=new Intent(c,CalUndoReceiver.class).setAction("kr.newniverse.sms.CAL_UNDO").putExtra("op",o.op);
      put(u,"e",e); if(o.prev!=null) put(u,"p",o.prev);
      int id=(int)(System.currentTimeMillis()&0x7fffff); u.putExtra("nid",id);
      PendingIntent pi=PendingIntent.getBroadcast(c,id,u,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
      Notification.Builder b=new Notification.Builder(c,CH).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF).setContentTitle(head)
        .setContentText(e.title+" · "+when(e.start,e.allDay)).setAutoCancel(true).setTimeoutAfter(6*3600*1000L).setCategory(Notification.CATEGORY_EVENT)
        .setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"취소",pi).build());
      nm.notify(id,b.build());
    }catch(Exception x){}
  }
  static void put(Intent i,String p,CalLogic.Entry e){ i.putExtra(p+"id",e.id).putExtra(p+"s",e.sender).putExtra(p+"o",e.org).putExtra(p+"t",e.title).putExtra(p+"st",e.start).putExtra(p+"ad",e.allDay); }
  static CalLogic.Entry get(Intent i,String p){ if(!i.hasExtra(p+"id")) return null; CalLogic.Entry e=new CalLogic.Entry();
    e.id=i.getLongExtra(p+"id",0); e.sender=nz(i.getStringExtra(p+"s")); e.org=nz(i.getStringExtra(p+"o")); e.title=nz(i.getStringExtra(p+"t")); e.start=i.getLongExtra(p+"st",0); e.allDay=i.getBooleanExtra(p+"ad",false); return e; }
  static String nz(String s){ return s==null?"":s; }
}

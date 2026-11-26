package kr.newniverse.sms;

import android.app.*;
import android.content.*;
import android.Manifest;
import android.content.SharedPreferences;
import java.util.Calendar;

/**
 * v2: 받은 문자에서 일정을 뽑아 「뉴니버스」 달력에 자동으로 넣기 (끄고 켤 수 있음).
 * 안전: 광고·의심 링크 문자는 건너뛰고, 주소록에 있는 사람이나 병원·택배·공공기관처럼 분명한 곳의 문자만 자동으로 넣음.
 * 넣을 때마다 「달력에 넣었어요」 알림 + 취소 단추. 인터넷 권한 없음.
 */
final class AutoCal {
  static final String PREF="nv_cal", CH="cal";
  /**
   * v5: 끈 적이 없고 달력 권한이 있으면 켜진 것으로 본다. (v4까지는 런처 안내로 권한만 허용하고 앱 안에서 ‘켜기’를 안 누르면
   * 권한이 있어도 조용히 꺼져 있었음.) 사용자가 설정에서 끄거나 ‘안 할래요’를 누르면 그 선택이 우선.
   */
  static boolean on(Context c){ SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); return p.contains("on")?p.getBoolean("on",false):perm(c); }
  static void setOn(Context c,boolean v){ c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("on",v).putBoolean("asked",true).apply(); }
  static boolean asked(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("asked",false); }
  static boolean perm(Context c){ return Nv.has(c,Manifest.permission.READ_CALENDAR)&&Nv.has(c,Manifest.permission.WRITE_CALENDAR); }

  static String contactName(Context c,String addr){ try{ return Nv.lookupName(c,addr); }catch(Exception e){ return null; } }

  /** 새 문자·장문을 받을 때(기본 문자 앱). 결과는 설정 › 달력 등록 진단에 남김 */
  static void onSms(Context c,String addr,String body,long at){ process(c,addr,body,at,false); }

  /** 처리하고 {단계, 이유} 를 돌려줌. recordNoDate: 날짜 없는 문자도 기록(‘다시 읽기’ 때) */
  static String[] process(Context c,String addr,String body,long at,boolean recordNoDate){
    try{
      CalDecide.D d=CalDecide.decide(on(c),perm(c),body,at,contactName(c,addr),addr);
      if("nodate".equals(d.reason)&&d.r==null){ if(recordNoDate) SmsDiag.cal(c,"skip","nodate"); return new String[]{"skip","nodate"}; }
      if("skip".equals(d.act)){ SmsDiag.cal(c,"skip",d.reason); return new String[]{"skip",d.reason}; }
      if("ask".equals(d.act)){ SmsDiag.cal(c,"ask",""); notifyAsk(c,d.r,addr,body,at); return new String[]{"ask",""}; }
      return insertAuto(c,d.r,addr);
    }catch(Exception e){ String why="insert:"+InboxLogic.errClass(e); try{ SmsDiag.cal(c,"try>fail",why); }catch(Exception x){} return new String[]{"try>fail",why}; }
  }

  /** 시험용 ‘마지막 문자 다시 읽기’: 받은 문자(SMS·장문) 중 숫자 날짜가 있는 가장 최근 것으로 달력 단계를 다시 돌림. 본문은 어디에도 저장하지 않음 */
  static String reread(Context c){
    java.util.List<Object[]> l=new java.util.ArrayList<>(); // {date, addr, body}
    android.content.ContentResolver cr=c.getContentResolver();
    try(android.database.Cursor q=cr.query(android.provider.Telephony.Sms.Inbox.CONTENT_URI,new String[]{"address","body","date"},null,null,"date DESC")){
      int n=0; while(q!=null&&q.moveToNext()&&n++<30) l.add(new Object[]{q.getLong(2),q.getString(0),q.getString(1)});
    }catch(Exception e){}
    try(android.database.Cursor q=cr.query(MmsInbox.MMS_INBOX,new String[]{"_id","date","sub","sub_cs"},null,null,"date DESC")){
      int n=0; while(q!=null&&q.moveToNext()&&n++<30){ long id=q.getLong(0); String[] tx=MmsInbox.text(cr,id);
        String sub=MmsLogic.fromIso(q.getString(2),q.isNull(3)?0:q.getInt(3));
        l.add(new Object[]{MmsLogic.msFromMmsDate(q.getLong(1)),MmsInbox.from(cr,id,1),MmsLogic.feedText(sub,tx[0])}); }
    }catch(Exception e){}
    l.sort((x,y)->Long.compare((Long)y[0],(Long)x[0]));
    Object[] pick=null; for(Object[] m:l) if(ApptParser.hasDate((String)m[2],(Long)m[0])){ pick=m; break; }
    if(pick==null&&!l.isEmpty()) pick=l.get(0);
    if(pick==null){ SmsDiag.cal(c,"skip","nodate"); return "{\"ok\":false,\"label\":\"받은 문자가 없어요\"}"; }
    String[] r=process(c,(String)pick[1],(String)pick[2],(Long)pick[0],true);
    String why=r[1]==null?"":r[1]; String label=CalDecide.stepsLabel(r[0],why.contains(":")?why.substring(0,why.indexOf(':')):why)+(why.contains(":")?" · "+why.substring(why.indexOf(':')+1):"");
    return "{\"ok\":true,\"steps\":\""+r[0]+"\",\"label\":\""+label.replace("\"","'")+"\"}";
  }
  private static String[] insertAuto(Context c,ApptParser.R r,String addr){
    if(!CalStore.hasProvider(c)){ SmsDiag.cal(c,"try>fail","nocalapp"); warnNoCalendar(c,true); return new String[]{"try>fail","nocalapp"}; }
    CalStore st=new CalStore(c);
    CalLogic.Out o=CalLogic.apply(st,r,addr,false);
    if("fail".equals(o.op)){ String why="insert"+(st.err.isEmpty()?"":":"+st.err); SmsDiag.cal(c,"try>fail",why); noticeFail(c); return new String[]{"try>fail",why}; }
    if("dup".equals(o.op)){ SmsDiag.cal(c,"skip","dup"); return new String[]{"skip","dup"}; }
    if("none".equals(o.op)){ SmsDiag.cal(c,"skip","noparse"); return new String[]{"skip","noparse"}; }
    SmsDiag.cal(c,"try>ok",""); notifyOut(c,o);
    if(!CalStore.hasViewer(c)) warnNoCalendar(c,false);
    return new String[]{"try>ok",""};
  }
  /** 카드의 「달력에 추가」·‘넣기’ 단추: ok / dup / perm / unsafe / none / fail */
  static String manual(Context c,String addr,String body,long at){
    try{ if(!perm(c)) return "perm";
      if(SmsSafety.isAd(body)||SmsSafety.suspicious(body)) return "unsafe";
      ApptParser.R r=ApptParser.parse(body,at,contactName(c,addr),addr); if(r==null) return "none";
      if(r.org.isEmpty()) r.title="예약";
      if(!CalStore.hasProvider(c)){ warnNoCalendar(c,true); return "fail"; }
      CalStore st=new CalStore(c);
      CalLogic.Out o=CalLogic.apply(st,r,addr,true);
      if("fail".equals(o.op)){ noticeFail(c); return "fail"; }
      notifyOut(c,o);
      if(!"dup".equals(o.op)&&!"none".equals(o.op)&&!CalStore.hasViewer(c)) warnNoCalendar(c,false);
      return "dup".equals(o.op)?"dup":"none".equals(o.op)?"none":"ok";
    }catch(Exception e){ return "none"; }
  }
  /** 이미 달력에 넣은 일정인지(카드의 단추 모양 결정) */
  static boolean has(Context c,String addr,String body,long at){
    try{ if(!perm(c)) return false; String name=contactName(c,addr);
      ApptParser.R r=ApptParser.parse(body,at,name,addr); if(r==null||r.kind==ApptParser.CANCEL) return false;
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
    if(o==null||"none".equals(o.op)||"dup".equals(o.op)||"fail".equals(o.op)) return;
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
  static final String CH_ASK="calask";
  static final String ACT_ASK_ADD="kr.newniverse.sms.CAL_ASK_ADD", ACT_ASK_NO="kr.newniverse.sms.CAL_ASK_NO";
  static NotificationManager nm(Context c){ NotificationManager n=c.getSystemService(NotificationManager.class); if(n!=null){ try{ n.createNotificationChannel(new NotificationChannel(CH,"달력에 넣은 일정",NotificationManager.IMPORTANCE_LOW)); n.createNotificationChannel(new NotificationChannel(CH_ASK,"달력에 넣을까요?",NotificationManager.IMPORTANCE_DEFAULT)); }catch(Exception e){} } return n; }

  /** 신청·모집 안내는 자동으로 넣지 않고 물어봄: 「달력에 넣을까요?」 [넣기] [아니요] */
  static void notifyAsk(Context c,ApptParser.R r,String addr,String body,long at){
    NotificationManager nm=nm(c); if(nm==null) return;
    String x=body==null?"":body.trim(); if(x.length()>1500) x=x.substring(0,1500);
    int id=0x500000|((r.title+r.when).hashCode()&0xfffff);
    Intent add=new Intent(c,CalUndoReceiver.class).setAction(ACT_ASK_ADD).putExtra("addr",addr==null?"":addr).putExtra("body",x).putExtra("at",at).putExtra("nid",id);
    Intent no=new Intent(c,CalUndoReceiver.class).setAction(ACT_ASK_NO).putExtra("nid",id);
    int fl=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
    String line=r.title+" · "+when(r.when,!r.hasTime)+(r.location.isEmpty()?"":" · "+r.location);
    Notification.Builder b=new Notification.Builder(c,CH_ASK).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF)
      .setContentTitle("달력에 넣을까요?").setContentText(line).setStyle(new Notification.BigTextStyle().bigText(line))
      .setAutoCancel(true).setCategory(Notification.CATEGORY_EVENT).setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(24*3600*1000L)
      .addAction(new Notification.Action.Builder(null,"넣기",PendingIntent.getBroadcast(c,id,add,fl)).build())
      .addAction(new Notification.Action.Builder(null,"아니요",PendingIntent.getBroadcast(c,id+1,no,fl)).build());
    try{ nm.notify(id,b.build()); }catch(Exception e){}
  }
  /** 달력 앱이 없으면(또는 보여 줄 앱이 없으면) 한 번만 알림 */
  static void warnNoCalendar(Context c,boolean cannotInsert){
    SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); if(p.getBoolean("warned_noapp",false)&&!cannotInsert) return;
    if(!cannotInsert) p.edit().putBoolean("warned_noapp",true).apply();
    NotificationManager nm=nm(c); if(nm==null) return;
    String t=cannotInsert?"이 폰에 달력 앱이 없어서 일정을 넣지 못했어요":"일정은 넣었지만 달력을 보여 줄 앱이 없어요";
    String d=cannotInsert?"삼성 달력 같은 달력 앱을 켜거나 설치한 뒤 다시 시도해 주세요. 뉴니버스는 폰의 달력 저장소에 일정을 넣어요.":"삼성 달력이나 구글 캘린더 같은 달력 앱을 설치하면 ‘뉴니버스’ 달력의 일정이 보여요.";
    try{ nm.notify(0x5F0001,new Notification.Builder(c,CH).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF).setContentTitle(t).setContentText(d).setStyle(new Notification.BigTextStyle().bigText(d)).setAutoCancel(true).build()); }catch(Exception e){}
  }
  static void noticeFail(Context c){
    NotificationManager nm=nm(c); if(nm==null) return;
    String d="달력에 쓰지 못했어요. 설정 › 달력 등록 기록에서 이유를 볼 수 있어요.";
    try{ nm.notify(0x5F0002,new Notification.Builder(c,CH).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF).setContentTitle("일정을 달력에 넣지 못했어요").setContentText(d).setAutoCancel(true).build()); }catch(Exception e){}
  }
  static void put(Intent i,String p,CalLogic.Entry e){ i.putExtra(p+"id",e.id).putExtra(p+"s",e.sender).putExtra(p+"o",e.org).putExtra(p+"t",e.title).putExtra(p+"st",e.start).putExtra(p+"ad",e.allDay); }
  static CalLogic.Entry get(Intent i,String p){ if(!i.hasExtra(p+"id")) return null; CalLogic.Entry e=new CalLogic.Entry();
    e.id=i.getLongExtra(p+"id",0); e.sender=nz(i.getStringExtra(p+"s")); e.org=nz(i.getStringExtra(p+"o")); e.title=nz(i.getStringExtra(p+"t")); e.start=i.getLongExtra(p+"st",0); e.allDay=i.getBooleanExtra(p+"ad",false); return e; }
  static String nz(String s){ return s==null?"":s; }
}

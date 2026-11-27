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
  static void onSms(Context c,String addr,String body,long at){ onSms(c,addr,body,at,"SMS"); }
  /**
   * v8: 받는 순간의 자동 처리 — 어디서 불렸는지(SMS/MMS)·글자 수·읽기 단계·결과를 ‘자동 처리’ 기록(auto_*)에 남김
   * (‘다시 읽기’ 기록과 따로). 어떤 오류도 밖으로 던지지 않음.
   */
  static void onSms(Context c,String addr,String body,long at,String source){
    int len=body==null?0:body.length(); String stage="";
    try{ stage=ApptParser.stage(body,at,contactName(c,addr),addr); }catch(Throwable e){ stage="parse:error"; }
    boolean nd="parse:no-date".equals(stage); // 날짜가 없는 평범한 문자는 ‘마지막 자동 처리’ 기록을 덮어쓰지 않음(시각만)
    try{
      if(nd) SmsDiag.autoNoDate(c,source); else SmsDiag.autoPhase(c,source,"enter");
      String[] r=process(c,addr,body,at,false);
      if(!nd) SmsDiag.auto(c,source,len,stage,r[0],r[1]);
    }catch(Throwable e){ try{ SmsDiag.auto(c,source,len,stage.isEmpty()?"error":stage,"try>fail","error:"+errDesc(e)); }catch(Throwable x){} }
  }

  /** 처리하고 {단계, 이유} 를 돌려줌. recordNoDate: 날짜 없는 문자도 기록(‘다시 읽기’ 때) */
  static String[] process(Context c,String addr,String body,long at,boolean recordNoDate){ return process(c,addr,body,at,recordNoDate,false); }
  /** force: 시험용 다시 읽기 — 보낸 곳을 못 믿어도 자동 대신 ‘넣을까요?’ 알림 */
  static String[] process(Context c,String addr,String body,long at,boolean recordNoDate,boolean force){
    try{
      CalDecide.D d=CalDecide.decide(on(c),perm(c),body,at,contactName(c,addr),addr,force);
      if("nodate".equals(d.reason)&&d.r==null){ if(recordNoDate) SmsDiag.cal(c,"skip","nodate"); return new String[]{"skip","nodate"}; }
      if("skip".equals(d.act)){ SmsDiag.cal(c,"skip",d.reason); return new String[]{"skip",d.reason}; }
      if("ask".equals(d.act)){
        String st=notifyAsk(c,d.r,addr,body,at); SmsDiag.calNotif(c,st);
        if("posted".equals(st)){ SmsDiag.cal(c,"ask",d.reason); return new String[]{"ask",d.reason}; }
        pendStore(c,d.r,addr,at); // 알림을 못 띄웠으면 앱 안 카드로 대신 물어봄
        String why="notif-"+st; SmsDiag.cal(c,"ask>blocked",why); return new String[]{"ask>blocked",why}; }
      return insertAuto(c,d.r,addr);
    }catch(Throwable e){ String why="error:"+errDesc(e); try{ SmsDiag.cal(c,"try>fail",why); }catch(Throwable x){} return new String[]{"try>fail",why}; } // Error 도 잡음: 받기 처리가 죽지 않게
  }

  static String labelOf(String steps,String why){
    why=why==null?"":why;
    return CalDecide.stepsLabel(steps,why.contains(":")?why.substring(0,why.indexOf(':')):why)+(why.contains(":")?" · "+why.substring(why.indexOf(':')+1):"");
  }
  /** 오류 종류(+원인 종류). 정규식 문법 오류만 설명도 붙임(문자 내용 아님) */
  static String errDesc(Throwable e){
    if(e==null) return "";
    String s=e.getClass().getSimpleName(); Throwable k=e.getCause();
    if(k!=null){ s+="←"+k.getClass().getSimpleName(); if(k instanceof java.util.regex.PatternSyntaxException){ java.util.regex.PatternSyntaxException pe=(java.util.regex.PatternSyntaxException)k; s+=" "+pe.getDescription()+"@"+pe.getIndex(); } }
    else if(e instanceof java.util.regex.PatternSyntaxException){ java.util.regex.PatternSyntaxException pe=(java.util.regex.PatternSyntaxException)e; s+=" "+pe.getDescription()+"@"+pe.getIndex(); }
    return s.length()>120?s.substring(0,120):s;
  }
  static String jq(String s){ return s==null?"":s.replace("\\","/").replace("\"","'").replace("\n"," ").replace("\r"," "); }

  static final int SCAN_SHOW=10, SCAN_LOOK=20, BUDGET_MS=5000, SCAN_MAX=30;
  static String reread(Context c){ return reread(c,System.currentTimeMillis()); }
  /**
   * 시험용 ‘마지막 문자 다시 읽기’ (v8): 받은 문자(SMS·장문)를 섞어 최근 순으로 훑어
   *  ① 최근 10통은 한 통마다 한 줄(받은 때·종류·글자 수·읽기 단계·장문 조각 구성·‘일시/장소’ 이름표 유무)을 진단에 남기고
   *  ② 그중(최대 30통까지) 일정으로 읽히는 가장 최근 문자를 골라 달력 단계로 넘김 — 없으면 가장 최근 날짜 문자로 이유만 기록.
   * 본문·번호는 어디에도 저장하지 않음. 어떤 오류가 나도 ‘기록 + 라벨’을 돌려줌. now: 이 시각 기준으로 지난 일정인지 판단.
   */
  static String reread(Context c,long now){
    SmsDiag.rrPhase(c,"start");
    int nSms=0,nMms=0; String qerr=""; String type="", stage="", steps="", reason=""; long msgAt=0; int len=0; String label; String lines="[]"; int rank=0, nScan=0;
    try{
      SmsDiag.rrPhase(c,"init");
      ApptParser.stage("2026. 1. 1. 오후 1시",0,null,null); // 읽기 도구(정규식)가 이 폰에서 켜지는지 먼저 확인
      java.util.List<Object[]> l=new java.util.ArrayList<>(); // {date(ms), addr, body, type, mmsId, subjectLen}
      android.content.ContentResolver cr=c.getContentResolver();
      SmsDiag.rrPhase(c,"sms");
      try(android.database.Cursor q=cr.query(android.provider.Telephony.Sms.Inbox.CONTENT_URI,new String[]{"address","body","date"},null,null,"date DESC")){
        while(q!=null&&q.moveToNext()&&nSms<SCAN_MAX){ nSms++; l.add(new Object[]{q.getLong(2),q.getString(0),q.getString(1),"SMS",-1L,0}); }
      }catch(Exception e){ qerr+="sms:"+InboxLogic.errClass(e)+" "; }
      SmsDiag.rrPhase(c,"mms");
      try(android.database.Cursor q=cr.query(MmsInbox.MMS_INBOX,new String[]{"_id","date","sub","sub_cs"},null,null,"date DESC")){
        while(q!=null&&q.moveToNext()&&nMms<SCAN_MAX){ nMms++; long id=q.getLong(0); String[] tx=MmsInbox.text(cr,id);
          String sub=MmsLogic.fromIso(q.getString(2),q.isNull(3)?0:q.getInt(3));
          l.add(new Object[]{MmsLogic.msFromMmsDate(q.getLong(1)),MmsInbox.from(cr,id,1),MmsLogic.feedText(sub,tx[0]),"MMS",id,sub==null?0:sub.length()}); }
      }catch(Exception e){ qerr+="mms:"+InboxLogic.errClass(e)+" "; }
      SmsDiag.rrPhase(c,"pick");
      l.sort((x,y)->Long.compare((Long)y[0],(Long)x[0]));
      nScan=Math.min(SCAN_SHOW,l.size());
      SmsDiag.rrPhase(c,"parse");
      org.json.JSONArray arr=new org.json.JSONArray();
      Object[] pick=null; int pickIdx=-1; String[] stages=new String[l.size()];
      long t0=System.currentTimeMillis(); int limit=Math.min(l.size(),SCAN_LOOK); // 읽는 데 오래 걸려도 멈추지 않게: 최대 SCAN_LOOK 통까지, 그리고 시간 제한
      for(int i=0;i<limit;i++){
        if(i>=SCAN_SHOW&&System.currentTimeMillis()-t0>BUDGET_MS) break;
        Object[] m=l.get(i); String body=(String)m[2], addr=(String)m[1];
        if(i<SCAN_SHOW||pick==null){ stages[i]=ApptParser.stage(body,now,contactName(c,addr),addr); }
        if(pick==null&&"parse:ok".equals(stages[i])){ pick=m; pickIdx=i; }
        if(i<SCAN_SHOW){
          org.json.JSONObject o=new org.json.JSONObject();
          o.put("t",(Long)m[0]); o.put("ty",(String)m[3]); o.put("len",body==null?0:body.length()); o.put("st",stages[i]);
          o.put("lb",ApptParser.labels(body,now));
          if("MMS".equals(m[3])){ o.put("pc",MmsInbox.partsSummary(cr,(Long)m[4])); o.put("sl",(Integer)m[5]); }
          arr.put(o);
        }
        if(pick!=null&&i>=SCAN_SHOW-1) break;
      }
      boolean found=pick!=null;
      if(!found){ for(int i=0;i<Math.min(l.size(),SCAN_SHOW);i++) if(ApptParser.hasDate((String)l.get(i)[2],now)){ pick=l.get(i); pickIdx=i; break; }
        if(pick==null&&!l.isEmpty()){ pick=l.get(0); pickIdx=0; } }
      for(int i=0;i<arr.length()&&pickIdx>=0;i++) if(i==pickIdx) arr.getJSONObject(i).put("sel",true);
      lines=arr.toString();
      if(pick==null){
        steps="skip"; reason="nodate"; stage="none";
        SmsDiag.cal(c,"skip","nodate");
        label="받은 문자를 못 찾았어요 (SMS "+nSms+"통 · MMS "+nMms+"통 확인"+(qerr.isEmpty()?"":" · 읽기 오류 "+qerr.trim())+")";
      } else {
        type=(String)pick[3]; msgAt=(Long)pick[0]; String body=(String)pick[2]; String addr=(String)pick[1]; len=body==null?0:body.length(); rank=pickIdx+1;
        stage=stages[pickIdx]!=null?stages[pickIdx]:ApptParser.stage(body,now,contactName(c,addr),addr);
        SmsDiag.rrPhase(c,"decide");
        String[] r=process(c,addr,body,now,true,true); steps=r[0]; reason=r[1]==null?"":r[1];
        label=found?(type+" "+len+"자 · "+stage+" · "+labelOf(steps,reason)+" (최근 "+rank+"번째 문자)")
                   :("최근 "+nScan+"통 확인 · 넣을 일정 없음 · 가장 최근 날짜 문자: "+type+" "+len+"자 "+stage+" · "+labelOf(steps,reason));
      }
    }catch(Throwable e){
      steps="try>fail"; reason="reread:"+errDesc(e); stage=stage.isEmpty()?"error":stage;
      label="다시 읽다가 오류가 났어요 ("+errDesc(e)+")";
      try{ SmsDiag.cal(c,steps,reason); }catch(Throwable x){}
    }
    SmsDiag.rr(c,type,msgAt,len,nSms,nMms,qerr.trim(),stage,steps,reason,lines,rank);
    return "{\"ok\":true,\"steps\":\""+jq(steps)+"\",\"stage\":\""+jq(stage)+"\",\"type\":\""+jq(type)+"\",\"len\":"+len+",\"sms\":"+nSms+",\"mms\":"+nMms+",\"rank\":"+rank+",\"label\":\""+jq(label)+"\"}";
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
      String blk=notifBlock(c,CH); if(!blk.isEmpty()){ SmsDiag.calNotif(c,blk); return; } // 일정은 이미 달력에 들어감. 알림만 못 띄운 것을 기록
      CalLogic.Entry e=o.e!=null?o.e:o.prev; if(e==null) return;
      String head="add".equals(o.op)?"달력에 넣었어요":"update".equals(o.op)?"달력 일정을 바꿨어요":"달력에서 뺐어요";
      Intent u=new Intent(c,CalUndoReceiver.class).setAction("kr.newniverse.sms.CAL_UNDO").putExtra("op",o.op);
      put(u,"e",e); if(o.prev!=null) put(u,"p",o.prev);
      int id=(int)(System.currentTimeMillis()&0x7fffff); u.putExtra("nid",id);
      PendingIntent pi=PendingIntent.getBroadcast(c,id,u,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
      Notification.Builder b=new Notification.Builder(c,CH).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF).setContentTitle(head)
        .setContentText(e.title+" · "+when(e.start,e.allDay)).setAutoCancel(true).setTimeoutAfter(6*3600*1000L).setCategory(Notification.CATEGORY_EVENT)
        .setVisibility(Notification.VISIBILITY_PRIVATE).addAction(new Notification.Action.Builder(null,"취소",pi).build());
      nm.notify(id,b.build()); SmsDiag.calNotif(c,isShown(nm,id)?"posted":"notshown");
    }catch(Throwable x){ SmsDiag.calNotif(c,"error:"+errDesc(x)); }
  }
  static final String CH_ASK="calask8"; // v8: 새 채널(중요도 높음) — 예전 ‘calask’ 채널 설정이 꺼져 있어도 영향 없게
  static final String ACT_ASK_ADD="kr.newniverse.sms.CAL_ASK_ADD", ACT_ASK_NO="kr.newniverse.sms.CAL_ASK_NO";
  static NotificationManager nm(Context c){ NotificationManager n=c.getSystemService(NotificationManager.class); if(n!=null){ try{ n.createNotificationChannel(new NotificationChannel(CH,"달력에 넣은 일정",NotificationManager.IMPORTANCE_LOW)); n.createNotificationChannel(new NotificationChannel(CH_ASK,"달력에 넣을까요?",NotificationManager.IMPORTANCE_HIGH)); }catch(Exception e){} } return n; }

  /**
   * 알림을 실제로 보낼 수 있는지: "" = 가능 · perm(알림 권한 없음) · off(이 앱 알림이 꺼져 있음) · channel(이 알림 종류가 꺼져 있음) · nomanager
   */
  static String notifBlock(Context c,String ch){
    try{
      if(android.os.Build.VERSION.SDK_INT>=33&&!Nv.has(c,Manifest.permission.POST_NOTIFICATIONS)) return "perm";
      NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm==null) return "nomanager";
      if(!nm.areNotificationsEnabled()) return "off";
      NotificationChannel x=nm.getNotificationChannel(ch); if(x!=null&&x.getImportance()==NotificationManager.IMPORTANCE_NONE) return "channel";
      return "";
    }catch(Throwable e){ return "error:"+errDesc(e); }
  }
  /** 방금 보낸 알림이 실제로 떠 있는지(다른 앱이 막았거나 시스템이 버렸으면 false). 확인을 못 하면 true */
  static boolean isShown(NotificationManager nm,int id){
    try{ for(android.service.notification.StatusBarNotification s:nm.getActiveNotifications()) if(s.getId()==id) return true; return false; }catch(Throwable e){ return true; }
  }
  /** v9: 묻는 알림·카드의 한 줄. 상대 날짜(다음주 목요일 …)도 실제 날짜로 풀어 씀 */
  static String askLine(ApptParser.R r){ return Casual.askLine(r); }
  static String notifLabel(String st){
    if(st==null||st.isEmpty()||"posted".equals(st)) return "알림을 띄움";
    if(st.startsWith("perm")) return "알림 권한이 없음";
    if(st.startsWith("off")) return "이 앱의 알림이 꺼져 있음";
    if(st.startsWith("channel")) return "‘달력에 넣을까요?’ 알림 종류가 꺼져 있음";
    if(st.startsWith("notshown")) return "알림을 보냈지만 떠 있지 않음";
    if(st.startsWith("nomanager")) return "알림 관리자를 못 찾음";
    return "알림 오류 "+st;
  }

  /**
   * 신청·모집 안내는 자동으로 넣지 않고 물어봄: 「달력에 넣을까요?」 [넣기] [아니요]
   * v8: 돌려주는 값 = posted(실제로 떠 있음) · perm · off · channel · notshown · error:… — ‘성공’은 posted 일 때만.
   */
  static String notifyAsk(Context c,ApptParser.R r,String addr,String body,long at){
    try{
      NotificationManager nm=nm(c); if(nm==null) return "nomanager";
      String blk=notifBlock(c,CH_ASK); if(!blk.isEmpty()) return blk;
      String x=body==null?"":body.trim(); if(x.length()>1500) x=x.substring(0,1500);
      int id=0x500000|((r.title+r.when).hashCode()&0xfffff);
      Intent add=new Intent(c,CalUndoReceiver.class).setAction(ACT_ASK_ADD).putExtra("addr",addr==null?"":addr).putExtra("body",x).putExtra("at",at).putExtra("nid",id);
      Intent no=new Intent(c,CalUndoReceiver.class).setAction(ACT_ASK_NO).putExtra("nid",id);
      int fl=PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
      String line=askLine(r); // v9: 풀어 쓴 날짜 ‘12월 3일(목) 오후 2시 · 안동대학교 — 달력에 넣을까요?’
      Notification.Builder b=new Notification.Builder(c,CH_ASK).setSmallIcon(R.drawable.ic_msg).setColor(0xFF8F6BFF)
        .setContentTitle("달력에 넣을까요?").setContentText(line).setStyle(new Notification.BigTextStyle().bigText(r.title+"\n"+line))
        .setAutoCancel(true).setCategory(Notification.CATEGORY_EVENT).setVisibility(Notification.VISIBILITY_PRIVATE).setTimeoutAfter(24*3600*1000L)
        .addAction(new Notification.Action.Builder(null,"넣기",PendingIntent.getBroadcast(c,id,add,fl)).build())
        .addAction(new Notification.Action.Builder(null,"아니요",PendingIntent.getBroadcast(c,id+1,no,fl)).build());
      nm.notify(id,b.build());
      return isShown(nm,id)?"posted":"notshown";
    }catch(Throwable e){ return "error:"+errDesc(e); }
  }

  // ---------- v8: 알림이 막혔을 때 앱 안 카드 ----------
  /** 앱 안 카드용 보관(알림을 못 띄운 ‘넣을까요?’). 일정 정보(제목·때·장소)만, 최대 5개. 진단 기록과는 별개의 앱 전용 저장소 */
  static void pendStore(Context c,ApptParser.R r,String addr,long at){
    try{ SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
      org.json.JSONArray a=new org.json.JSONArray(p.getString("pend","[]")); org.json.JSONArray n=new org.json.JSONArray();
      long id=0x500000L|((r.title+r.when).hashCode()&0xfffff);
      org.json.JSONObject o=new org.json.JSONObject(); o.put("id",id); o.put("title",r.title); o.put("org",r.org); o.put("when",r.when); o.put("end",r.endMs); o.put("hasTime",r.hasTime); o.put("loc",r.location==null?"":r.location); o.put("label",askLine(r)); o.put("addr",addr==null?"":addr); o.put("made",System.currentTimeMillis());
      n.put(o); for(int i=0;i<a.length()&&n.length()<5;i++){ org.json.JSONObject x=a.getJSONObject(i); if(x.optLong("id")!=id) n.put(x); }
      p.edit().putString("pend",n.toString()).apply();
    }catch(Throwable e){}
  }
  static String pendJson(Context c){
    try{ org.json.JSONArray a=new org.json.JSONArray(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("pend","[]")); long now=System.currentTimeMillis(); org.json.JSONArray n=new org.json.JSONArray();
      for(int i=0;i<a.length();i++){ org.json.JSONObject x=a.getJSONObject(i); if(x.optLong("when")>=now-3600L*1000&&now-x.optLong("made")<7*24*3600*1000L) n.put(x); } // 지난 일정·일주일 지난 카드는 뺌
      return n.toString(); }catch(Throwable e){ return "[]"; }
  }
  /** 카드의 넣기(add=true)/아니요. 돌려줌: ok · dup · perm · fail · none */
  static String pendAct(Context c,long id,boolean add){
    String res="none";
    try{ SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); org.json.JSONArray a=new org.json.JSONArray(p.getString("pend","[]")); org.json.JSONArray n=new org.json.JSONArray(); org.json.JSONObject hit=null;
      for(int i=0;i<a.length();i++){ org.json.JSONObject x=a.getJSONObject(i); if(x.optLong("id")==id) hit=x; else n.put(x); }
      if(hit==null) return "none";
      if(!add){ p.edit().putString("pend",n.toString()).apply(); SmsDiag.cal(c,"ask>no","declined"); return "no"; }
      if(!perm(c)) return "perm";
      ApptParser.R r=new ApptParser.R(); r.kind=ApptParser.NEW; r.org=hit.optString("org"); r.title=hit.optString("title"); r.when=hit.optLong("when"); r.endMs=hit.optLong("end"); r.hasTime=hit.optBoolean("hasTime"); r.location=hit.optString("loc"); r.trusted=true;
      if(!CalStore.hasProvider(c)){ warnNoCalendar(c,true); SmsDiag.cal(c,"ask>fail","nocalapp"); return "fail"; }
      CalStore st=new CalStore(c); CalLogic.Out o=CalLogic.apply(st,r,hit.optString("addr"),true);
      if("fail".equals(o.op)){ SmsDiag.cal(c,"ask>fail","insert"+(st.err.isEmpty()?"":":"+st.err)); return "fail"; }
      p.edit().putString("pend",n.toString()).apply();
      SmsDiag.cal(c,"ask>ok","dup".equals(o.op)?"dup":""); res="dup".equals(o.op)?"dup":"ok";
      if(!CalStore.hasViewer(c)&&"ok".equals(res)) warnNoCalendar(c,false);
    }catch(Throwable e){ res="fail"; }
    return res;
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

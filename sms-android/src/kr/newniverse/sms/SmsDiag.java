package kr.newniverse.sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * v3: 설정 화면에 보여주는 짧은 저장 기록. 문자 본문·번호·비밀은 남기지 않음.
 * (시각, 저장 결과/예외 종류, 받은 편지함 개수, 기본 앱 여부, 권한, 런처의 마지막 조회)
 * v4: 장문·사진(MMS) — 마지막 도착 신호 시각, 내려받기 결과·오류 번호·HTTP 상태, 저장 여부, ‘받았어요’ 응답, 못 받은 장문 수
 */
final class SmsDiag {
  static final String PREF="nv_diag";

  static SharedPreferences p(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }

  static int inboxCount(Context c){
    if(!Nv.has(c,android.Manifest.permission.READ_SMS)) return -1;
    try(Cursor q=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,new String[]{"_id"},null,null,null)){
      return q==null?-1:q.getCount();
    }catch(Exception e){ return -1; }
  }

  static void snapshotPerms(SharedPreferences.Editor e,Context c){
    e.putBoolean("def",Nv.isDefault(c));
    e.putBoolean("read",Nv.has(c,android.Manifest.permission.READ_SMS));
    e.putBoolean("recv",Nv.has(c,android.Manifest.permission.RECEIVE_SMS));
    e.putBoolean("send",Nv.has(c,android.Manifest.permission.SEND_SMS));
    e.putInt("inbox",inboxCount(c));
  }

  /** 받은 문자 처리 결과. insert = ok|retry-ok|dup|fail|parse , err = 예외 클래스 이름만 */
  static void incoming(Context c,String source,String insert,String err){
    try{
      SharedPreferences.Editor e=p(c).edit();
      e.putLong("at",System.currentTimeMillis());
      e.putString("source",source==null?"":source);
      e.putString("insert",insert==null?"":insert);
      e.putString("err",err==null?"":err);
      snapshotPerms(e,c);
      e.apply();
    }catch(Exception x){}
  }

  /** 런처가 FeedProvider 를 호출했을 때. method/ok/rows/err 만 남김(본문 없음) */
  static void feed(Context c,String method,boolean ok,int rows,String err){
    try{
      SharedPreferences.Editor e=p(c).edit();
      e.putLong("feed_at",System.currentTimeMillis());
      e.putString("feed_method",method==null?"":method);
      e.putBoolean("feed_ok",ok);
      e.putInt("feed_rows",rows);
      e.putString("feed_err",err==null?"":err);
      e.apply();
    }catch(Exception x){}
  }

  /** v4: 장문 처리. at>0 이면 도착 신호 시각도 갱신. state = pending|retry|ok|dup|fail|parse|ignored */
  static void mms(Context c,long at,String state,int code,int http,boolean saved,String err,Object unused){
    try{
      SharedPreferences.Editor e=p(c).edit();
      if(at>0){ e.putLong("mms_at",at); e.putBoolean("mms_saved",false); e.putInt("mms_code",0); e.putInt("mms_http",0); e.putString("mms_err",""); e.putString("mms_ack",""); }
      e.putLong("mms_upd",System.currentTimeMillis());
      e.putString("mms_state",state==null?"":state);
      if(at<=0||code!=0) e.putInt("mms_code",code);
      if(http!=0) e.putInt("mms_http",http);
      if(saved) e.putBoolean("mms_saved",true);
      if(err!=null&&!err.isEmpty()) e.putString("mms_err",err);
      e.apply();
    }catch(Exception x){}
  }
  /** v5: 달력 등록 단계. steps = skip | try>ok | try>fail | ask | ask>ok | ask>no | ask>fail, reason = CalDecide 이유 코드 (본문·번호 없음) */
  static void cal(Context c,String steps,String reason){
    try{ p(c).edit().putLong("cal_at",System.currentTimeMillis()).putString("cal_steps",steps==null?"":steps).putString("cal_reason",reason==null?"":reason).putString("cal_ver",Nv.VER).apply(); }catch(Exception x){}
  }
  /** v7: 다시 읽기 진행 표시 — 각 단계에 들어가기 *전에* 남겨서, 멈추거나 죽어도 어느 단계였는지 남음. phase=start 면 요청 시각도 갱신 */
  static void rrPhase(Context c,String phase){
    try{ SharedPreferences.Editor e=p(c).edit(); if("start".equals(phase)) e.putLong("rr_start",System.currentTimeMillis()); e.putString("rr_phase",phase==null?"":phase).apply(); }catch(Exception x){}
  }
  /** v7: ‘마지막 문자 다시 읽기’ 기록. 문자 종류·시각·글자 수·확인한 개수·읽기 단계·결과만 (본문·번호 없음) */
  static void rr(Context c,String type,long msgAt,int len,int nSms,int nMms,String qerr,String stage,String steps,String reason,String lines,int rank){
    try{ p(c).edit().putLong("rr_at",System.currentTimeMillis()).putString("rr_type",type==null?"":type).putLong("rr_msg_at",msgAt).putInt("rr_len",len)
      .putInt("rr_sms",nSms).putInt("rr_mms",nMms).putString("rr_qerr",qerr==null?"":qerr).putString("rr_stage",stage==null?"":stage)
      .putString("rr_steps",steps==null?"":steps).putString("rr_reason",reason==null?"":reason).putString("rr_lines",lines==null?"[]":lines).putInt("rr_rank",rank).putString("rr_ver",Nv.VER).putString("rr_phase","done").apply(); }catch(Exception x){}
  }
  /** v8: 알림 상태(posted·perm·off·channel·notshown·error:…) — ‘성공’이라고 말하기 전에 알림이 정말 떴는지 */
  static void calNotif(Context c,String st){ try{ p(c).edit().putString("cal_notif",st==null?"":st).putLong("cal_notif_at",System.currentTimeMillis()).apply(); }catch(Throwable x){} }
  /** v8: 받는 순간의 자동 처리 기록(‘다시 읽기’ 기록 rr_* 와 따로). 본문·번호 없음 */
  static void autoPhase(Context c,String src,String phase){ try{ SharedPreferences.Editor e=p(c).edit(); if("enter".equals(phase)){ e.putLong("auto_enter",System.currentTimeMillis()); e.putString("auto_src",src==null?"":src); } e.putString("auto_phase",phase==null?"":phase).apply(); }catch(Throwable x){} }
  static void autoNoDate(Context c,String src){ try{ p(c).edit().putLong("auto_nd_at",System.currentTimeMillis()).putString("auto_nd_src",src==null?"":src).apply(); }catch(Throwable x){} }
  static void auto(Context c,String src,int len,String stage,String steps,String reason){
    try{ p(c).edit().putLong("auto_at",System.currentTimeMillis()).putString("auto_src",src==null?"":src).putInt("auto_len",len).putString("auto_stage",stage==null?"":stage)
      .putString("auto_steps",steps==null?"":steps).putString("auto_reason",reason==null?"":reason).putString("auto_phase","done").putString("auto_ver",Nv.VER).apply(); }catch(Throwable x){}
  }
  static void mmsParts(Context c,String sum){ try{ p(c).edit().putString("mms_parts",sum==null?"":sum).apply(); }catch(Exception x){} }
  static void mmsAck(Context c,String st){ try{ p(c).edit().putString("mms_ack",st==null?"":st).apply(); }catch(Exception x){} }

  static JSONObject json(Context c){
    JSONObject o=new JSONObject();
    try{
      SharedPreferences p=p(c);
      o.put("at",p.getLong("at",0));
      o.put("source",p.getString("source",""));
      o.put("insert",p.getString("insert",""));
      o.put("err",p.getString("err",""));
      o.put("inbox",inboxCount(c));
      o.put("def",Nv.isDefault(c));
      JSONObject pe=new JSONObject();
      pe.put("read",Nv.has(c,android.Manifest.permission.READ_SMS));
      pe.put("recv",Nv.has(c,android.Manifest.permission.RECEIVE_SMS));
      pe.put("send",Nv.has(c,android.Manifest.permission.SEND_SMS));
      o.put("perms",pe);
      o.put("feedAt",p.getLong("feed_at",0));
      o.put("feedMethod",p.getString("feed_method",""));
      o.put("feedOk",p.contains("feed_at")&&p.getBoolean("feed_ok",false));
      o.put("feedSeen",p.contains("feed_at")||p.getLong("feed_at",0)>0);
      o.put("feedRows",p.getInt("feed_rows",0));
      o.put("feedErr",p.getString("feed_err",""));
      String cs=p.getString("cal_steps",""), cr=p.getString("cal_reason","");
      o.put("calAt",p.getLong("cal_at",0)); o.put("calSteps",cs); o.put("calReason",cr);
      o.put("calLabel",cs.isEmpty()?"":AutoCal.labelOf(cs,cr)); o.put("calGood",CalDecide.isGood(cs));
      String cv=p.getString("cal_ver","");
      o.put("calVer",cv); o.put("calCurrent",cv.equals(Nv.VER)); // 이 버전이 남긴 기록인지(아니면 예전 버전 기록)
      String rs=p.getString("rr_steps",""), rr=p.getString("rr_reason","");
      o.put("rrAt",p.getLong("rr_at",0)); o.put("rrType",p.getString("rr_type","")); o.put("rrMsgAt",p.getLong("rr_msg_at",0)); o.put("rrLen",p.getInt("rr_len",0));
      o.put("rrSms",p.getInt("rr_sms",0)); o.put("rrMms",p.getInt("rr_mms",0)); o.put("rrQerr",p.getString("rr_qerr","")); o.put("rrStage",p.getString("rr_stage",""));
      o.put("rrRank",p.getInt("rr_rank",0)); try{ o.put("rrLines",new JSONArray(p.getString("rr_lines","[]"))); }catch(Exception x){ o.put("rrLines",new JSONArray()); }
      o.put("rrStart",p.getLong("rr_start",0)); o.put("rrPhase",p.getString("rr_phase",""));
      o.put("rrSteps",rs); o.put("rrReason",rr); o.put("rrLabel",rs.isEmpty()?"":AutoCal.labelOf(rs,rr)); o.put("rrGood",CalDecide.isGood(rs));
      String cn=p.getString("cal_notif",""); o.put("calNotif",cn); o.put("calNotifAt",p.getLong("cal_notif_at",0)); o.put("calNotifLabel",cn.isEmpty()?"":AutoCal.notifLabel(cn));
      String as=p.getString("auto_steps",""), ar=p.getString("auto_reason","");
      o.put("autoNdAt",p.getLong("auto_nd_at",0)); o.put("autoNdSrc",p.getString("auto_nd_src",""));
      o.put("autoAt",p.getLong("auto_at",0)); o.put("autoEnter",p.getLong("auto_enter",0)); o.put("autoSrc",p.getString("auto_src","")); o.put("autoLen",p.getInt("auto_len",0)); o.put("autoStage",p.getString("auto_stage",""));
      o.put("autoSteps",as); o.put("autoReason",ar); o.put("autoLabel",as.isEmpty()?"":AutoCal.labelOf(as,ar)); o.put("autoGood",CalDecide.isGood(as)); o.put("autoPhase",p.getString("auto_phase",""));
      o.put("notifOk",AutoCal.notifBlock(c,AutoCal.CH_ASK)); o.put("pendCount",new JSONArray(AutoCal.pendJson(c)).length());
      o.put("calOn",AutoCal.on(c)); o.put("calPerm",AutoCal.perm(c));
      o.put("mmsAt",p.getLong("mms_at",0));
      o.put("mmsUpd",p.getLong("mms_upd",0));
      o.put("mmsState",p.getString("mms_state",""));
      o.put("mmsCode",p.getInt("mms_code",0));
      o.put("mmsCodeLabel",p.getInt("mms_code",0)==0?"":MmsLogic.errLabel(p.getInt("mms_code",0)));
      o.put("mmsHttp",p.getInt("mms_http",0));
      o.put("mmsSaved",p.getBoolean("mms_saved",false));
      o.put("mmsErr",p.getString("mms_err",""));
      o.put("mmsAck",p.getString("mms_ack","")); o.put("mmsParts",p.getString("mms_parts",""));
      o.put("mmsPending",MmsInbox.pendingCount(c));
      o.put("ver",Nv.VER);
    }catch(Exception e){}
    return o;
  }

  @SuppressWarnings("unused")
  static Uri inboxUri(){ return Telephony.Sms.Inbox.CONTENT_URI; }
}

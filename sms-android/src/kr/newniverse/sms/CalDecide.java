package kr.newniverse.sms;

/**
 * v5: 받은 문자를 달력에 어떻게 할지 정하는 순수 논리(안드로이드 없음). 시험·진단 문구도 여기서.
 * act: auto(자동으로 넣기) · ask(‘달력에 넣을까요?’ 알림) · skip(건너뜀, reason 에 이유)
 * 이유 코드: ad 광고 · suspicious 수상한 링크 · nodate 날짜 없음(기록 안 함) · noparse 일정으로 안 보임 · untrusted 믿을 수 있는 보낸 곳 아님
 *          · off 자동 넣기 꺼짐 · noperm 달력 권한 없음 · dup 이미 있음 · nocalapp · insert 달력에 쓰기 실패
 */
final class CalDecide {
  static final class D { String act="skip", reason=""; ApptParser.R r; }

  static D decide(boolean on,boolean perm,String body,long at,String contact,String addr){
    D d=new D();
    if(body==null||body.trim().isEmpty()){ d.reason="nodate"; return d; }
    if(SmsSafety.isAd(body)){ d.reason="ad"; return d; }
    if(SmsSafety.suspicious(body)){ d.reason="suspicious"; return d; }
    ApptParser.R r=ApptParser.parse(body,at,contact,addr);
    if(r==null){ d.reason=ApptParser.hasDate(body,at)?"noparse:"+ApptParser.why(body,at):"nodate"; return d; }
    d.r=r;
    if(!r.trusted){ d.reason="untrusted"; return d; }
    if(!on){ d.reason="off"; return d; }
    if(!perm){ d.reason="noperm"; return d; }
    d.act=r.ask?"ask":"auto";
    return d;
  }

  static String reasonLabel(String r){
    if(r==null) return "";
    switch(r){
      case "ad": return "광고 문자";
      case "suspicious": return "수상한 링크";
      case "nodate": return "날짜가 없음";
      case "noparse": return "일정으로 읽지 못함";
      case "untrusted": return "믿을 수 있는 보낸 곳이 아님";
      case "off": return "자동 넣기가 꺼져 있음";
      case "noperm": return "달력 권한이 없음";
      case "dup": return "이미 달력에 있음";
      case "nocalapp": return "달력 앱이 없음";
      case "insert": return "달력에 쓰지 못함";
      case "declined": return "‘아니요’를 누름";
      default: return r;
    }
  }
  /** 진단 한 줄: steps = skip | try>ok | try>fail | ask | ask>ok | ask>no | ask>fail */
  static String stepsLabel(String steps,String reason){
    if(steps==null||steps.isEmpty()) return "아직 없음";
    String why=reason==null||reason.isEmpty()?"":"("+reasonLabel(reason)+")";
    switch(steps){
      case "skip": return "건너뜀"+why;
      case "try>ok": return "시도 → 성공";
      case "try>fail": return "시도 → 실패"+why;
      case "ask": return "‘넣을까요?’ 알림을 띄움";
      case "ask>ok": return "‘넣을까요?’ → 넣기 → 성공";
      case "ask>no": return "‘넣을까요?’ → 아니요";
      case "ask>fail": return "‘넣을까요?’ → 넣기 → 실패"+why;
      default: return steps;
    }
  }
  static boolean isGood(String steps){ return "try>ok".equals(steps)||"ask>ok".equals(steps)||"ask".equals(steps); }
}

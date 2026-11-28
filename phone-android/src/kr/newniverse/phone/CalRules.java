package kr.newniverse.phone;

/** v6 일정 10분 전 「회의 모드 켤까요?」 규칙 (순수 자바) */
final class CalRules {
  private CalRules(){}
  static final long LEAD=10*60000L, WINDOW=15*60000L, LONG=3*60*60000L;
  static final int DECLINED=2; // CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
  static final String ASK="ask", AUTO="auto", OFF="off";
  static String mode(String s){ return AUTO.equals(s)||OFF.equals(s)?s:ASK; }

  /** 묻기 대상 일정인지: 종일 아님 · 3시간 미만 · 끝이 시작보다 뒤 · 내가 거절한 일정 아님 */
  static boolean eligible(boolean allDay,long begin,long end,int selfStatus){
    if(allDay) return false;
    if(end<=begin) return false;
    if(end-begin>=LONG) return false;
    return selfStatus!=DECLINED;
  }
  /** 지금 물을 때인지: 시작 15분 전 ~ 시작 1분 전 사이 */
  static boolean due(long now,long begin){ long d=begin-now; return d>60000L&&d<=WINDOW; }
  /** 이 일정 알림을 걸 시각 (시작 10분 전, 이미 지났으면 곧바로) */
  static long alarmAt(long now,long begin){ return Math.max(now+5000L,begin-LEAD); }
  /** 모드 상태 때문에 띄우지 않을 때: 운전 중이면 안 띄움, 회의 중이 이미 켜져 있으면 물을 필요 없음 */
  static boolean blockedByMode(String activeId){ return ModeDefs.DRIVE.equals(activeId)||ModeDefs.MEETING.equals(activeId); }
  static String key(long eventId,long begin){ return eventId+"@"+begin; }
}

package kr.newniverse.memo;

import java.util.regex.*;

/**
 * 메모가 ‘달력에 넣을 일정’인지 가리는 규칙(순수 자바).
 * 날짜·시각(또는 상대 시각)이 있고, 미팅·약속·모임·만나·봐·보자·회의·병원·예약·일정 같은 말이 있거나
 * ‘서/에서/앞에서’ 앞의 장소와 시각이 있으면 일정이에요. 깨워 줘·알려 줘·알람만 있으면 알림만 맞추고 달력에는 넣지 않아요.
 */
final class ApptPhrase {
  static final Pattern EVENT_CUE=Pattern.compile("미팅|약속|모임|만나|보자|회의|병원|예약|일정|(?<![가-힣])봐(?![가-힣])");
  /** 장소: ‘서/에서/앞에서’ 바로 앞의 이름(띄어쓴 두 낱말까지). 문자 앱 Casual 과 같은 말맛 */
  static final Pattern PLACE=Pattern.compile("((?:[가-힣A-Za-z0-9]{1,15}\\s)?[가-힣A-Za-z0-9]{1,15}?)\\s*(?:앞에서|에서|서)(?![가-힣])");
  static final Pattern NOT_PLACE=Pattern.compile("(?:\\d|시$|분$|반$|요일|요$|주$|오늘|내일|낼|모레|글피|오전|오후|아침|점심|저녁|밤|새벽|낮|^거기|^여기|^저기|^그때|^이따|^있다|^(?:네|세|두|한|그|이|저|좀|꼭)$)");

  static final class Hit {
    TimePhrase.Hit when; String title="", location=""; boolean allDay;
    String line(){
      String t=title==null||title.isEmpty()?"":" "+title;
      return "달력에 넣음 · "+(allDay?TimePhrase.confirmDate(when.when):TimePhrase.confirmWhen(when.when))+t;
    }
  }

  /** 일정이면 Hit, 알림만 이거나 때가 없으면 null */
  static Hit find(String text,long now){
    TimePhrase.Hit t=TimePhrase.find(text,now);
    if(t==null||t.past) return null;
    String loc=place(text);
    boolean cue=EVENT_CUE.matcher(text==null?"":text).find();
    boolean placeAndTime=!loc.isEmpty()&&t.explicitTime;
    if(!cue&&!placeAndTime) return null;
    Hit h=new Hit(); h.when=t; h.location=loc; h.allDay=!t.explicitTime||t.defaulted;
    String title=TimePhrase.stripTime(text);
    if(title.isEmpty()) title=loc.isEmpty()?"일정":loc;
    h.title=title;
    return h;
  }

  static String place(String s){
    if(s==null) return "";
    Matcher m=PLACE.matcher(s); String best="";
    while(m.find()){
      String[] ws=m.group(1).trim().split("\\s+"); StringBuilder sb=new StringBuilder();
      for(String x:ws){
        String y=x.replaceAll("(에|에서)$","");
        if(y.equals("앞")) continue;
        if(y.isEmpty()||NOT_PLACE.matcher(y).find()){ sb.setLength(0); continue; }
        if(sb.length()>0) sb.append(' ');
        sb.append(y);
      }
      if(sb.length()>=1) best=sb.toString();
    }
    return best;
  }
}

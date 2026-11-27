package kr.newniverse.memo;

import org.json.JSONObject;

/** 메모 한 개. 글(text)·시각·길이 + 선택 항목(소리 파일, 알림 시각). */
final class Note {
  static final String SPEECH="speech", AUDIO="audio";
  long id;            // 만든 시각(ms) — 겹치면 +1 해서 고유하게
  String text="";
  long createdAt;
  long durationMs;
  String kind=SPEECH; // speech = 받아쓴 글 · audio = 소리로만 저장(나중에 글로 옮기기)
  String audio="";    // files/audio/ 안의 파일 이름(없으면 빈 글자)
  int seg;            // 길게 말해서 60초마다 이어 저장된 경우 0,1,2…
  long remindAt;      // 0 = 알림 없음
  boolean remindExact;
  boolean reminded;   // 알림이 이미 울렸음

  JSONObject toJson(){
    try{
      JSONObject o=new JSONObject();
      o.put("id",id); o.put("text",text); o.put("createdAt",createdAt); o.put("durationMs",durationMs);
      o.put("kind",kind); o.put("audio",audio); o.put("seg",seg);
      o.put("remindAt",remindAt); o.put("remindExact",remindExact); o.put("reminded",reminded);
      return o;
    }catch(Exception e){ throw new RuntimeException(e); }
  }
  static Note fromJson(JSONObject o){
    Note n=new Note();
    n.id=o.optLong("id"); n.text=o.optString("text",""); n.createdAt=o.optLong("createdAt"); n.durationMs=o.optLong("durationMs");
    n.kind=AUDIO.equals(o.optString("kind"))?AUDIO:SPEECH; n.audio=o.optString("audio",""); n.seg=o.optInt("seg");
    n.remindAt=o.optLong("remindAt"); n.remindExact=o.optBoolean("remindExact"); n.reminded=o.optBoolean("reminded");
    return n;
  }
  /** 목록·화면용 (소리 파일 경로는 숨김) */
  JSONObject toView(){
    JSONObject o=toJson(); o.remove("audio");
    try{
      o.put("hasAudio",!audio.isEmpty());
      if(remindAt>0) o.put("remindLabel",TimePhrase.confirmWhen(remindAt));
    }catch(Exception e){}
    return o;
  }
}

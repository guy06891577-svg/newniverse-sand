package kr.newniverse.memo;

/**
 * 듣기를 언제 끝낼지 정하는 규칙(순수 자바).
 * 말이 끊기면 silenceMs 동안 기다리고, 처음 말이 안 나오면 firstMs 까지, 한 번에 maxMs 까지.
 * 주변 소음(rms)은 타이머를 되돌리지 않아요. 글(부분 결과)이 바뀐 뒤 silenceMs, 말 끝(onEndOfSpeech) 뒤 약 1.2초,
 * 마지막 글 뒤 8초(hard) 중 먼저 오는 때 저장해요.
 */
final class ListenPolicy {
  static final int GO=0, STOP_SILENCE=1, STOP_NOSPEECH=2, SEGMENT_MAX=3;
  static final long SILENCE_MS=2500, FIRST_MS=7000, MAX_MS=60000, TEXT_IDLE_MS=2000, END_GRACE_MS=1200, HARD_MS=8000;
  static final int MAX_SEGMENTS=5; // 60초씩 이어 받아 최대 5덩이(5분)
  final long silenceMs, firstMs, maxMs;
  long start, lastVoice, lastText, endedAt; boolean heard;
  ListenPolicy(long silenceMs,long firstMs,long maxMs){ this.silenceMs=silenceMs; this.firstMs=firstMs; this.maxMs=maxMs; }
  static ListenPolicy standard(long silenceMs){ return new ListenPolicy(silenceMs,FIRST_MS,MAX_MS); }
  void begin(long now){ start=now; lastVoice=now; lastText=0; endedAt=0; heard=false; }
  /** 주변 소음·말 시작. 들은 표시만 하고, 끊김 시계는 돌리지 않아요 */
  void sound(long now){ /* rms 는 타이머를 되돌리지 않음 */ }
  /** 소리 크기(소리 저장 모드)나 시험용. 글이 없으면 이 시각을 기준으로 끊김을 재요 */
  void voice(long now){ heard=true; lastVoice=now; }
  /** 부분·최종 글이 바뀌었을 때. 끊김·hard stop 의 기준 */
  void text(long now){ heard=true; lastText=now; lastVoice=now; endedAt=0; }
  /** 인식기가 말 끝을 알렸을 때. 새 글이 없으면 잠깐 뒤 저장 */
  void endSpeech(long now){ heard=true; endedAt=now; }
  int check(long now){
    if(now-start>=maxMs) return SEGMENT_MAX;
    if(lastText>0&&now-lastText>=HARD_MS) return STOP_SILENCE;
    if(endedAt>0&&now-endedAt>=END_GRACE_MS&&(lastText==0||lastText<=endedAt))
      return lastText>0?STOP_SILENCE:STOP_NOSPEECH;
    if(!heard) return now-start>=firstMs?STOP_NOSPEECH:GO;
    long idleFrom=lastText>0?lastText:lastVoice;
    return now-idleFrom>=silenceMs?STOP_SILENCE:GO;
  }
}

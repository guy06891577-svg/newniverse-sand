package kr.newniverse.memo;

/** 듣기를 언제 끝낼지 정하는 규칙(순수 자바). 말이 끊겨도 silenceMs 동안은 기다리고, 처음 말이 안 나오면 firstMs 까지, 한 번에 maxMs 까지. */
final class ListenPolicy {
  static final int GO=0, STOP_SILENCE=1, STOP_NOSPEECH=2, SEGMENT_MAX=3;
  static final long SILENCE_MS=2500, FIRST_MS=7000, MAX_MS=60000;
  static final int MAX_SEGMENTS=5; // 60초씩 이어 받아 최대 5덩이(5분)
  final long silenceMs, firstMs, maxMs;
  long start, lastVoice; boolean heard;
  ListenPolicy(long silenceMs,long firstMs,long maxMs){ this.silenceMs=silenceMs; this.firstMs=firstMs; this.maxMs=maxMs; }
  static ListenPolicy standard(long silenceMs){ return new ListenPolicy(silenceMs,FIRST_MS,MAX_MS); }
  void begin(long now){ start=now; lastVoice=now; heard=false; }
  void voice(long now){ heard=true; lastVoice=now; }
  int check(long now){
    if(now-start>=maxMs) return SEGMENT_MAX;
    if(!heard) return now-start>=firstMs?STOP_NOSPEECH:GO;
    return now-lastVoice>=silenceMs?STOP_SILENCE:GO;
  }
}

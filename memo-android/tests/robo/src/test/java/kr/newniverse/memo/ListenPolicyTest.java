package kr.newniverse.memo;

import static org.junit.Assert.*;
import org.junit.Test;

/** 언제 듣기를 끝낼지: 말 끊김 2.5초 · 처음 말 없음 7초 · 한 덩이 60초 */
public class ListenPolicyTest {
  @Test public void noSpeechAtAllEndsAfterFirstWindow(){
    ListenPolicy p=ListenPolicy.standard(2500); p.begin(0);
    assertEquals(ListenPolicy.GO,p.check(6999)); assertEquals(ListenPolicy.STOP_NOSPEECH,p.check(7000));
  }
  @Test public void silenceAfterSpeechEndsAt2500(){
    ListenPolicy p=ListenPolicy.standard(2500); p.begin(0); p.voice(3000);
    assertEquals(ListenPolicy.GO,p.check(5499)); assertEquals(ListenPolicy.STOP_SILENCE,p.check(5500));
  }
  @Test public void shortPausesKeepListening(){
    ListenPolicy p=ListenPolicy.standard(2500); p.begin(0);
    long t=1000; for(int k=0;k<10;k++){ p.voice(t); assertEquals(ListenPolicy.GO,p.check(t+2400)); t+=2400; } // 2.4초씩 쉬어도 계속
    assertEquals(ListenPolicy.GO,p.check(t-100));
  }
  @Test public void maxSixtySecondsThenSegment(){
    ListenPolicy p=ListenPolicy.standard(2500); p.begin(0); p.voice(59000);
    assertEquals(ListenPolicy.GO,p.check(59999)); assertEquals(ListenPolicy.SEGMENT_MAX,p.check(60000));
  }
  @Test public void segmentLimit(){ assertEquals(5,ListenPolicy.MAX_SEGMENTS); assertEquals(60000,ListenPolicy.MAX_MS); assertEquals(2500,ListenPolicy.SILENCE_MS); }
  @Test public void customSilence(){
    ListenPolicy p=ListenPolicy.standard(1500); p.begin(0); p.voice(1000);
    assertEquals(ListenPolicy.GO,p.check(2499)); assertEquals(ListenPolicy.STOP_SILENCE,p.check(2500));
  }
}

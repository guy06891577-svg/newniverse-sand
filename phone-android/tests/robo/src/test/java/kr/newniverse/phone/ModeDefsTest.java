package kr.newniverse.phone;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class ModeDefsTest {
  @Test public void defaultTexts(){
    assertEquals("지금 운전 중이라 전화를 못 받아요. 도착하면 연락드릴게요.",ModeDefs.defaultText("drive"));
    assertTrue(ModeDefs.defaultText("meeting").contains("회의 중"));
    for(String id:ModeDefs.IDS){ assertFalse(ModeDefs.defaultText(id).contains("유니")); assertFalse(ModeDefs.name(id).contains("유니")); }
  }
  @Test public void onlyMeetingHasTimers(){
    assertFalse(ModeDefs.timers("drive")); assertTrue(ModeDefs.timers("meeting"));
    assertEquals(30,ModeDefs.timer(30)); assertEquals(60,ModeDefs.timer(60)); assertEquals(120,ModeDefs.timer(120));
    assertEquals(0,ModeDefs.timer(45)); assertEquals(0,ModeDefs.timer(-5));
  }
  @Test public void speechMapping(){
    assertEquals("drive",ModeDefs.fromSpeech("운전 중"));
    assertEquals("drive",ModeDefs.fromSpeech("운전중"));
    assertEquals("drive",ModeDefs.fromSpeech("운전 모드 켜 줘"));
    assertEquals("meeting",ModeDefs.fromSpeech("회의 중"));
    assertEquals("meeting",ModeDefs.fromSpeech("회의모드"));
    assertNull(ModeDefs.fromSpeech("밝기 올려"));
    assertNull(ModeDefs.fromSpeech(null));
  }
  @Test public void cleanText(){
    assertEquals("안녕 하세요",ModeDefs.cleanText("  안녕\n 하세요  "));
    assertEquals("",ModeDefs.cleanText(null));
    StringBuilder sb=new StringBuilder(); for(int i=0;i<300;i++) sb.append("가");
    assertEquals(ModeDefs.MAX_TEXT,ModeDefs.cleanText(sb.toString()).length());
  }
  @Test public void allowNormalizes(){ assertEquals("none",ModeDefs.allow("x")); assertEquals("starred",ModeDefs.allow("starred")); assertEquals("contacts",ModeDefs.allow("contacts")); assertEquals("none",ModeDefs.allow(null)); }
  @Test public void durations(){
    assertEquals("1분 안",ModeDefs.fmtDur(20000)); assertEquals("42분",ModeDefs.fmtDur(42*60000L));
    assertEquals("1시간",ModeDefs.fmtDur(3600000L)); assertEquals("1시간 5분",ModeDefs.fmtDur(65*60000L));
  }
  @Test public void summaryGrouping(){
    List<ModeSummary.Row> rows=new ArrayList<>();
    rows.add(new ModeSummary.Row("010-1111-2222","",1000,"a"));
    rows.add(new ModeSummary.Row("+82 10 1111 2222","김사장",3000,"b"));
    rows.add(new ModeSummary.Row("010-3333-4444","",2000,"c"));
    rows.add(new ModeSummary.Row("","",500,"d"));
    rows.add(new ModeSummary.Row("-2","",400,"e"));
    List<ModeSummary.Grp> g=ModeSummary.group(rows);
    assertEquals(3,g.size());
    assertEquals("01011112222",g.get(0).key); assertEquals(2,g.get(0).n); assertEquals(3000,g.get(0).last); assertEquals("김사장",g.get(0).name); assertEquals("b",g.get(0).text);
    assertEquals("01033334444",g.get(1).key); assertEquals(1,g.get(1).n);
    assertEquals("hidden",g.get(2).key); assertEquals(2,g.get(2).n); assertEquals("",g.get(2).num);
  }
}

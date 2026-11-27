package kr.newniverse.memo;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.*;

/** 말 속 ‘때’ 읽기. 지금은 2026-11-27(금) 15:00 (Asia/Seoul) */
public class TimePhraseTest {
  static TimeZone old;
  @BeforeClass public static void tz(){ old=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul")); }
  @AfterClass public static void untz(){ TimeZone.setDefault(old); }

  static long ms(int y,int mo,int d,int h,int mi){ Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")); c.clear(); c.set(y,mo-1,d,h,mi,0); return c.getTimeInMillis(); }
  static final long NOW=ms(2026,11,27,15,0);

  static void at(String text,long want){ at(text,NOW,want); }
  static void at(String text,long now,long want){
    TimePhrase.Hit h=TimePhrase.find(text,now);
    assertNotNull("‘"+text+"’ 를 읽어야 해요",h);
    assertFalse("‘"+text+"’ 는 지난 때가 아니어야 해요",h.past);
    assertEquals("‘"+text+"’ → "+TimePhrase.label(want,true)+" 인데 "+TimePhrase.label(h.when,true),want,h.when);
  }
  static void none(String text){ none(text,NOW); }
  static void none(String text,long now){ TimePhrase.Hit h=TimePhrase.find(text,now); assertTrue("‘"+text+"’ 는 때로 치면 안 돼요 → "+(h==null?"":TimePhrase.label(h.when,true)),h==null); }
  static void past(String text,long want){ past(text,NOW,want); }
  static void past(String text,long now,long want){
    TimePhrase.Hit h=TimePhrase.find(text,now);
    assertNotNull("‘"+text+"’ 를 읽어야 해요",h);
    assertTrue("‘"+text+"’ 는 이미 지난 때여야 해요",h.past);
    assertEquals("‘"+text+"’ → "+TimePhrase.label(want,true)+" 인데 "+TimePhrase.label(h.when,true),want,h.when);
  }

  @Test public void nowIsFriday(){ assertEquals(Calendar.FRIDAY,TimePhrase.cal(NOW).get(Calendar.DAY_OF_WEEK)); }

  // ---- 사용자 문장 ----
  @Test public void userExamples(){
    at("내일 9시에 알려줘",ms(2026,11,28,9,0));
    at("목요일 2시",ms(2026,12,3,14,0));
    at("다음주 목요일 2시에 안동대학교서 봐",ms(2026,12,3,14,0));
    TimePhrase.Hit h=TimePhrase.find("30분 뒤에 알려줘",NOW); assertNotNull(h); assertEquals(NOW+30*60000L,h.when); assertTrue(h.relative);
  }
  @Test public void v2RequiredSentences(){
    at("내일 아침 9시에 알려줘",ms(2026,11,28,9,0));
    at("다음주 목요일 2시 안동대학교 미팅",ms(2026,12,3,14,0));
    TimePhrase.Hit h=TimePhrase.find("모레 약 사기",NOW);
    assertNotNull(h); assertEquals(ms(2026,11,29,9,0),h.when); assertTrue(h.defaulted); assertFalse(h.past);
    assertTrue(h.confirmSet().contains("오전 9:00 알림 맞춤"));
    assertTrue(h.confirmSet().contains("오전 9시로 정했어요"));
    past("오늘 오후 2시에 알려줘",ms(2026,11,27,14,0));
    h=TimePhrase.find("오늘 오후 2시에 알려줘",NOW);
    assertTrue(h.confirmPast().contains("이미 지난 때"));
  }
  /** 실제 폰 메모 여섯 문장(있는 그대로). 「광개토대왕」만 알림 없음 */
  @Test public void v3RealPhoneSentences(){
    at("내일 아침 9시에 깨워 줘",ms(2026,11,28,9,0));
    at("내일 아침 10시에 깨워 줘",ms(2026,11,28,10,0));
    at("내일 10시에 나한테 전화하라고 말해 줘",ms(2026,11,28,10,0)); // 맨 숫자 10시 = 오전
    none("광개토대왕");
    TimePhrase.Hit h=TimePhrase.find("10초 후에 알람",NOW);
    assertNotNull(h); assertEquals(NOW+10000L,h.when); assertTrue(h.relative); assertFalse(h.past);
    h=TimePhrase.find("10초 뒤에 알람",NOW);
    assertNotNull(h); assertEquals(NOW+10000L,h.when); assertTrue(h.relative);
    assertTrue(TimePhrase.confirmWhen(h.when).contains(":10"));
  }
  @Test public void v3SecondsAndCues(){
    TimePhrase.Hit h;
    h=TimePhrase.find("10초 뒤에 알려줘",NOW); assertNotNull(h); assertEquals(NOW+10000L,h.when);
    h=TimePhrase.find("십 초 뒤",NOW); assertNotNull(h); assertEquals(NOW+10000L,h.when);
    h=TimePhrase.find("1분 뒤",NOW); assertNotNull(h); assertEquals(NOW+60000L,h.when);
    h=TimePhrase.find("10초뒤 알람",NOW); assertNotNull("띄어쓰기 없는 10초뒤",h); assertEquals(NOW+10000L,h.when);
    at("내일 10시",ms(2026,11,28,10,0)); // 알림 말이 없어도 날짜+시각이면 알림
    none("오늘 날씨 좋네");
  }
  // ---- 오늘·내일·모레·글피 ----
  @Test public void relativeDays(){
    at("오늘 저녁 7시에 알려줘",ms(2026,11,27,19,0));
    at("낼 아침 7시 반에 깨워줘",ms(2026,11,28,7,30));
    at("모레 오후 3시 약속",ms(2026,11,29,15,0));
    at("글피 오전 10시에 알려줘",ms(2026,11,30,10,0));
    at("내일모레 2시에 알려줘",ms(2026,11,29,14,0));
    at("내일 12시에 점심",ms(2026,11,28,12,0));
    at("모레 밤 12시에 알려줘",ms(2026,11,30,0,0)); // 밤 12시 = 그날 자정(다음날 0시)
  }
  // ---- 주 단위 ----
  @Test public void weeks(){
    at("이번주 일요일 12시에 알려줘",ms(2026,11,29,12,0));
    at("이번 주 토요일 6시",ms(2026,11,28,18,0));
    at("다음주 월요일 10시",ms(2026,11,30,10,0));
    at("담주 화요일 오후 4시",ms(2026,12,1,16,0));
    at("다다음주 화요일 저녁 7시",ms(2026,12,8,19,0));
    at("차주 수요일 9시",ms(2026,12,2,9,0));
    at("다음 주 목 오후 3시",ms(2026,12,3,15,0));
    past("이번주 수요일 2시",ms(2026,11,25,14,0)); // 이미 지남
  }
  // ---- 요일만: 다가오는 그 요일 ----
  @Test public void bareWeekday(){
    at("토요일 10시에 알려줘",ms(2026,11,28,10,0));       // 내일
    at("월욜 3시",ms(2026,11,30,15,0));
    at("금요일 5시에 알려줘",ms(2026,11,27,17,0));        // 오늘 17:00 아직 남음
    at("금요일 2시",ms(2026,12,4,14,0));                  // 오늘 14:00은 지남 → 다음 주
    at("금요일에 알려줘",ms(2026,12,4,9,0));              // 시각 없으면 다음 주(오늘 제외)
  }
  // ---- 시각 규칙 ----
  @Test public void clock(){
    at("내일 2시",ms(2026,11,28,14,0));       // 1~7시 = 오후
    at("내일 7시 반",ms(2026,11,28,19,30));
    at("내일 8시",ms(2026,11,28,8,0));        // 8~11시 = 오전
    at("내일 11시 45분",ms(2026,11,28,11,45));
    at("내일 12시",ms(2026,11,28,12,0));      // 12시 = 낮 12시
    at("내일 오전 12시",ms(2026,11,28,0,0));
    at("내일 오후 12시 30분",ms(2026,11,28,12,30));
    at("내일 새벽 5시",ms(2026,11,28,5,0));
    at("내일 점심 1시",ms(2026,11,28,13,0));
    at("내일 저녁 6시 10분",ms(2026,11,28,18,10));
    at("내일 14:30",ms(2026,11,28,14,30));
    at("내일 세 시 반",ms(2026,11,28,15,30));
    at("내일 열두 시",ms(2026,11,28,12,0));
  }
  @Test public void dateOnlyDefaultsNine(){
    TimePhrase.Hit h=TimePhrase.find("내일 병원 가야 돼",NOW); assertNotNull(h);
    assertEquals(ms(2026,11,28,9,0),h.when); assertTrue(h.defaulted); assertFalse(h.explicitTime);
    assertTrue(h.line().contains("오전 9시로"));
  }
  @Test public void clockOnlyRollsToTomorrow(){
    TimePhrase.Hit h=TimePhrase.find("9시에 알려줘",NOW); // 오전 9시는 이미 지남
    assertNotNull(h); assertEquals(ms(2026,11,28,9,0),h.when); assertTrue(h.rolled);
    h=TimePhrase.find("5시에 알려줘",NOW); assertEquals(ms(2026,11,27,17,0),h.when); assertFalse(h.rolled);
  }
  // ---- N분/시간 뒤 ----
  @Test public void afterOffsets(){
    long m=60000L;
    TimePhrase.Hit h;
    h=TimePhrase.find("30분 뒤",NOW); assertEquals(NOW+30*m,h.when);
    h=TimePhrase.find("삼십 분 뒤",NOW); assertEquals(NOW+30*m,h.when);
    h=TimePhrase.find("10분 후에 알려줘",NOW); assertEquals(NOW+10*m,h.when);
    h=TimePhrase.find("한 시간 뒤에 알려줘",NOW); assertEquals(NOW+60*m,h.when);
    h=TimePhrase.find("두 시간 반 후",NOW); assertEquals(NOW+150*m,h.when);
    h=TimePhrase.find("1시간 반 뒤",NOW); assertEquals(NOW+90*m,h.when);
    h=TimePhrase.find("1시간 30분 뒤",NOW); assertEquals(NOW+90*m,h.when);
    h=TimePhrase.find("반 시간 뒤에 알려줘",NOW); assertEquals(NOW+30*m,h.when);
    h=TimePhrase.find("삼십분 뒤에 알려줘",NOW); assertEquals(NOW+30*m,h.when);
    h=TimePhrase.find("십오분 후에 알려줘",NOW); assertEquals(NOW+15*m,h.when);
    h=TimePhrase.find("5분 이따가 알려줘",NOW); assertEquals(NOW+5*m,h.when);
  }
  @Test public void afterDays(){
    TimePhrase.Hit h=TimePhrase.find("3일 뒤에 알려줘",NOW); assertNotNull(h); assertEquals(ms(2026,11,30,9,0),h.when); assertTrue(h.defaulted);
    h=TimePhrase.find("이틀 뒤 오후 2시에 알려줘",NOW); assertEquals(ms(2026,11,29,14,0),h.when);
    h=TimePhrase.find("일주일 뒤에 알려줘",NOW); assertEquals(ms(2026,12,4,9,0),h.when);
    h=TimePhrase.find("2주 뒤 3시",NOW); assertEquals(ms(2026,12,11,15,0),h.when);
  }
  @Test public void numericDate(){
    at("12월 25일 오후 6시에 알려줘",ms(2026,12,25,18,0));
    at("12월 3일에 알려줘",ms(2026,12,3,9,0));
    past("11월 3일 2시",ms(2026,11,3,14,0)); // 지난 날짜
  }
  // ---- 제안하면 안 되는 말 ----
  @Test public void noOffer(){
    none("오늘 날씨 좋네");
    none("밥 먹었어");
    none("3시간 걸렸어");
    none("2시간 동안 운전했어");
    none("어제 3시에 봤어");
    none("어제 3시에 회의했어");
    none("30분 전에 도착했어");
    none("");
    assertNull(TimePhrase.find(null,NOW));
    none("목욕탕 갔다 왔어");           // 목욕 ≠ 목요일
    none("어제저녁 얘기야");
    none("지난주 월요일 3시에 갔어");
  }
  @Test public void pastToday(){
    past("오늘 오후 2시에 알려줘",ms(2026,11,27,14,0)); // 15:00 기준 이미 지남
    at("오늘 오후 4시에 알려줘",ms(2026,11,27,16,0));
  }
  @Test public void tenseWithoutCueIsSkippedButCueWins(){
    none("내일 3시에 만났어");
    at("내일 3시에 만났던 거 알려줘",ms(2026,11,28,15,0));
  }
  @Test public void labels(){
    assertEquals("12월 3일(목) 오후 2시",TimePhrase.label(ms(2026,12,3,14,0),true));
    assertEquals("12월 3일(목) 오후 2시 30분",TimePhrase.label(ms(2026,12,3,14,30),true));
    assertEquals("11월 28일(토) 오전 12시",TimePhrase.label(ms(2026,11,28,0,0),true));
    assertEquals("12월 3일 (목) 오전 9:00",TimePhrase.confirmWhen(ms(2026,12,3,9,0)));
    assertEquals("12월 3일 (목) 오후 2:00",TimePhrase.confirmWhen(ms(2026,12,3,14,0)));
    assertEquals("12월 3일 (목) 오후 2:30",TimePhrase.confirmWhen(ms(2026,12,3,14,30)));
    assertEquals("11월 28일 (토) 오전 12:00",TimePhrase.confirmWhen(ms(2026,11,28,0,0)));
    assertTrue(TimePhrase.confirmWhen(NOW+10000L).endsWith("오후 3:00:10"));
  }
  @Test public void minutesParser(){
    assertEquals(30,TimePhrase.minutes("삼십")); assertEquals(15,TimePhrase.minutes("십오")); assertEquals(10,TimePhrase.minutes("십"));
    assertEquals(25,TimePhrase.minutes("이십오")); assertEquals(5,TimePhrase.minutes("오")); assertEquals(45,TimePhrase.minutes("45"));
  }
}

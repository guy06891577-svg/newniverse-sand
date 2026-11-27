package kr.newniverse.memo;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.*;

/** 일정 메모 vs 알림만. 지금은 2026-11-27(금) 15:00 (Asia/Seoul) */
public class ApptPhraseTest {
  static TimeZone old;
  @BeforeClass public static void tz(){ old=TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul")); }
  @AfterClass public static void untz(){ TimeZone.setDefault(old); }
  static long ms(int y,int mo,int d,int h,int mi){ Calendar c=Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul")); c.clear(); c.set(y,mo-1,d,h,mi,0); return c.getTimeInMillis(); }
  static final long NOW=ms(2026,11,27,15,0);

  static ApptPhrase.Hit appt(String text){
    ApptPhrase.Hit h=ApptPhrase.find(text,NOW);
    assertNotNull("‘"+text+"’ 는 달력 일정이어야 해요",h);
    return h;
  }
  static void onlyRemind(String text){
    assertNotNull("알림으로라도 읽어야 해요: "+text,TimePhrase.find(text,NOW));
    assertNull("‘"+text+"’ 는 달력에 넣으면 안 돼요",ApptPhrase.find(text,NOW));
  }

  @Test public void v4RequiredSentences(){
    ApptPhrase.Hit h=appt("다음주 목요일 2시 안동대학교 미팅");
    assertEquals(ms(2026,12,3,14,0),h.when.when);
    assertEquals("안동대학교 미팅",h.title);
    assertFalse(h.allDay);
    assertEquals("달력에 넣음 · 12월 3일 (목) 오후 2:00 안동대학교 미팅",h.line());

    onlyRemind("내일 10시에 나한테 전화하라고 말해 줘");
    assertEquals(ms(2026,11,28,10,0),TimePhrase.find("내일 10시에 나한테 전화하라고 말해 줘",NOW).when);

    h=appt("모레 저녁 7시 친구 만나");
    assertEquals(ms(2026,11,29,19,0),h.when.when);
    assertEquals("친구 만나",h.title);
    assertFalse(h.allDay);

    h=appt("금요일 오전 11시 병원 예약");
    assertEquals(ms(2026,12,4,11,0),h.when.when); // 오늘은 금요일 15:00, 오전 11시는 지나서 다음 주
    assertEquals("병원 예약",h.title);

    onlyRemind("30분 뒤 알려줘");
  }

  @Test public void placeFromSeo(){
    ApptPhrase.Hit h=appt("내일 2시에 안동대학교서 봐");
    assertEquals("안동대학교",h.location);
    assertEquals(ms(2026,11,28,14,0),h.when.when);
    assertTrue(h.title.contains("안동대학교"));
  }

  @Test public void dateOnlyEventIsAllDay(){
    ApptPhrase.Hit h=appt("모레 병원");
    assertTrue(h.allDay);
    assertEquals(ms(2026,11,29,9,0),h.when.when); // TimePhrase 기본 오전 9시, 달력은 하루 종일
    assertTrue(h.line().startsWith("달력에 넣음 · 11월 29일 (일)"));
    assertFalse(h.line().contains(":"));
  }

  @Test public void wakeAndAlarmStayReminderOnly(){
    onlyRemind("내일 아침 9시에 깨워 줘");
    onlyRemind("10초 뒤에 알람");
    assertNull(ApptPhrase.find("광개토대왕",NOW));
    assertNull(ApptPhrase.find("오늘 날씨 좋네",NOW));
  }

  @Test public void reminderCueWithMeetingIsAppointment(){
    ApptPhrase.Hit h=appt("내일 2시 미팅 알려 줘");
    assertEquals("미팅 알려 줘",h.title);
    assertEquals(ms(2026,11,28,14,0),h.when.when);
  }
}

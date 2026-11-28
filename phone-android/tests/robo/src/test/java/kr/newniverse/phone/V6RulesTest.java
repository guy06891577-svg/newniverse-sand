package kr.newniverse.phone;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.*;

/** v6 순수 규칙: 통화 메모를 물을 때 · 「며칠 전」 · 받아쓰기 고르기 · 일정 규칙 · 명함 글 읽기 */
public class V6RulesTest {
  static final TimeZone KST=TimeZone.getTimeZone("Asia/Seoul");
  static long at(int y,int mo,int d,int h,int mi){ Calendar c=Calendar.getInstance(KST); c.clear(); c.set(y,mo-1,d,h,mi,0); return c.getTimeInMillis(); }

  // ---------- 통화 메모 ----------
  @Test public void memoOfferedOnlyForLongOrSavedConnectedCalls(){
    assertTrue("15초 넘게",MemoRules.shouldOffer(true,15_000,false,"010-1234-5678",0));
    assertFalse("14초 · 모르는 번호",MemoRules.shouldOffer(true,14_999,false,"010-1234-5678",0));
    assertTrue("짧아도 저장된 사람",MemoRules.shouldOffer(true,3_000,true,"010-1234-5678",0));
    assertFalse("안 받은 전화(부재중·거절)",MemoRules.shouldOffer(false,0,true,"010-1234-5678",0));
    assertFalse("다른 통화가 아직 있음",MemoRules.shouldOffer(true,60_000,true,"010-1234-5678",1));
    assertFalse("번호 없음(발신자 표시 제한)",MemoRules.shouldOffer(true,60_000,true,"",0));
    assertFalse(MemoRules.shouldOffer(true,60_000,true,"-2",0));
  }
  @Test public void agoUsesCalendarDays(){
    long now=at(2026,11,28,9,0);
    assertEquals("오늘",MemoRules.ago(now,at(2026,11,28,0,5),KST));
    assertEquals("어제",MemoRules.ago(now,at(2026,11,27,23,59),KST));
    assertEquals("3일 전",MemoRules.ago(now,at(2026,11,25,12,0),KST));
    assertEquals("1년 넘게 전",MemoRules.ago(now,at(2025,11,1,12,0),KST));
    assertEquals("",MemoRules.ago(now,0,KST));
  }
  @Test public void snippetAndClean(){
    assertEquals("두부 2판 · 내일 배송",MemoRules.snippet("두부 2판\n  · 내일   배송",60));
    assertEquals("가나다…",MemoRules.snippet("가나다라마",3));
    StringBuilder b=new StringBuilder(); for(int i=0;i<1200;i++) b.append('가');
    assertEquals(MemoRules.MAX_TEXT,MemoRules.clean(b.toString()).length());
    assertEquals("",MemoRules.clean(null));
  }
  @Test public void voiceNeverUsesCloudWhileLocked(){
    assertEquals("device",MemoRules.voiceMode(34,true,true,true));
    assertEquals("locked",MemoRules.voiceMode(34,true,false,true));
    assertEquals("locked",MemoRules.voiceMode(30,true,true,true));   // 안드 12 이하는 기기 안 인식기 API가 없음
    assertEquals("cloud",MemoRules.voiceMode(34,false,false,true));
    assertEquals("none",MemoRules.voiceMode(34,false,false,false));
    // 기기 안 인식기에 한국어 팩이 없을 때: 잠금이 풀려 있을 때만 일반 인식기로
    assertTrue(MemoRules.cloudFallback(12,false,true)); assertTrue(MemoRules.cloudFallback(13,false,true));
    assertFalse("잠금 중엔 절대 안 함",MemoRules.cloudFallback(12,true,true));
    assertFalse(MemoRules.cloudFallback(12,false,false));
    assertFalse("말을 못 알아들은 건 다시 시도 대상 아님",MemoRules.cloudFallback(7,false,true));
  }

  // ---------- 일정 → 회의 모드 ----------
  @Test public void calendarRules(){
    long b=at(2026,11,28,14,0);
    assertTrue(CalRules.eligible(false,b,b+60*60000L,0));
    assertFalse("종일",CalRules.eligible(true,b,b+60*60000L,0));
    assertFalse("3시간 이상",CalRules.eligible(false,b,b+3*60*60000L,0));
    assertTrue("2시간 59분",CalRules.eligible(false,b,b+179*60000L,0));
    assertFalse("내가 거절한 일정",CalRules.eligible(false,b,b+30*60000L,CalRules.DECLINED));
    assertFalse("끝이 시작보다 앞",CalRules.eligible(false,b,b,0));
    assertTrue(CalRules.due(b-10*60000L,b)); assertTrue(CalRules.due(b-15*60000L,b));
    assertFalse("16분 전은 아직",CalRules.due(b-16*60000L,b)); assertFalse("시작 1분 안은 늦음",CalRules.due(b-30000L,b));
    assertEquals(b-10*60000L,CalRules.alarmAt(b-60*60000L,b));
    assertEquals("이미 10분 안이면 곧바로",b-5*60000L+5000L,CalRules.alarmAt(b-5*60000L,b));
    assertTrue("운전 중엔 안 띄움",CalRules.blockedByMode(ModeDefs.DRIVE)); assertTrue(CalRules.blockedByMode(ModeDefs.MEETING)); assertFalse(CalRules.blockedByMode(null));
    assertEquals("ask",CalRules.mode(null)); assertEquals("ask",CalRules.mode("weird")); assertEquals("off",CalRules.mode("off")); assertEquals("auto",CalRules.mode("auto"));
  }

  // ---------- 명함 ----------
  static String lines(Object... a){ JSONArray r=new JSONArray(); try{ for(int i=0;i<a.length;i+=4){ JSONObject o=new JSONObject(); o.put("t",a[i]); o.put("h",a[i+1]); o.put("y",a[i+2]); o.put("x",a[i+3]); r.put(o);} }catch(Exception e){} return r.toString(); }
  /** 에뮬레이터에서 ML Kit 한국어 OCR이 실제로 돌려준 줄 모양 (card1) — 순서가 섞여 와도 위→아래로 정렬 */
  @Test public void card1CompanyCard(){
    String j=lines("M 010-2345-6789",22,410,40, "(주)한빛상사",30,40,40, "김민수",64,170,40, "영업부 과장",26,250,40,
                   "T 02-555-1234",22,450,40, "E minsu.kim@hanbit.co kr",22,490,40, "서울특별시 강남구 테헤란로 123",20,530,40);
    CardParser.Card c=CardParser.parse(CardParser.linesFromJson(j));
    assertEquals("김민수",c.name); assertEquals("영업부 과장",c.title); assertEquals("(주)한빛상사",c.company);
    assertEquals("010-2345-6789",c.mobile); assertEquals("02-555-1234",c.tel);
    assertEquals("OCR이 마지막 점을 빈칸으로 읽어도 고침","minsu.kim@hanbit.co.kr",c.email);
    assertEquals("(주)한빛상사",c.lines.get(0));
  }
  @Test public void card2SmallShop(){
    String j=lines("행복분식",40,40,40, "대표 박영희",34,120,40, "H.P 010.9876.5432",22,300,40, "TEL 031)123-4567",22,340,40, "경기도 수원시 팔달구 행궁로 12",20,380,40);
    CardParser.Card c=CardParser.parse(CardParser.linesFromJson(j));
    assertEquals("박영희",c.name); assertEquals("대표",c.title); assertEquals("행복분식",c.company);
    assertEquals("010-9876-5432",c.mobile); assertEquals("031-123-4567",c.tel); assertEquals("",c.email);
  }
  @Test public void emailTailFixOnlyForRealTlds(){
    CardParser.Card a=CardParser.parse(Arrays.asList(new CardParser.Line("hong@abc.com",10)));
    assertEquals("hong@abc.com",a.email);
    CardParser.Card b=CardParser.parse(Arrays.asList(new CardParser.Line("이메일 hong@abc.co KR",10)));
    assertEquals("hong@abc.co.kr",b.email);
    CardParser.Card c=CardParser.parse(Arrays.asList(new CardParser.Line("hong@abc.com 홍길동",10)));
    assertEquals("hong@abc.com",c.email); assertEquals("홍길동",c.name);
  }
  @Test public void cardJsonRoundTrip() throws Exception {
    CardParser.Card c=CardParser.parse(CardParser.linesFromJson(lines("이순신",40,10,10,"010-1111-2222",20,50,10)));
    JSONObject o=CardParser.toJson(c);
    assertEquals("이순신",o.getString("name")); assertEquals("010-1111-2222",o.getString("mobile")); assertEquals(2,o.getJSONArray("lines").length());
    assertEquals(0,CardParser.linesFromJson("not json").size());
  }
}

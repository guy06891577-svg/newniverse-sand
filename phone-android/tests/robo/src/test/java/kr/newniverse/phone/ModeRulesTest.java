package kr.newniverse.phone;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;
import kr.newniverse.phone.ModeRules.Kind;
import kr.newniverse.phone.ModeRules.Why;

/** 자동 답장을 보내도 되는 번호인지 가르는 규칙 (순수 자바) */
public class ModeRulesTest {
  static Why d(String num){ return ModeRules.decide(true,false,false,false,num,false,new HashSet<>(),0,"문구"); }

  @Test public void keyNormalizesKoreanForms(){
    assertEquals("01012345678",ModeRules.key("010-1234-5678"));
    assertEquals("01012345678",ModeRules.key("+82 10-1234-5678"));
    assertEquals("01012345678",ModeRules.key("+821012345678"));
    assertEquals("01012345678",ModeRules.key("821012345678"));
    assertEquals("0212345678",ModeRules.key("(02) 1234-5678"));
    assertEquals("+14155550123",ModeRules.key("+1 (415) 555-0123"));
    assertEquals("",ModeRules.key(""));
    assertEquals("",ModeRules.key(null));
    assertEquals("",ModeRules.key("-2"));        // 번호 표시 제한 값
    assertEquals("",ModeRules.key("Private"));
    assertEquals("",ModeRules.key("KB국민카드")); // 글자 섞인 발신자
  }

  @Test public void mobileNumbers(){
    for(String n:new String[]{"010-1234-5678","01012345678","+82-10-9876-5432","010-123-4567","011-234-5678","016-123-4567","019-9999-0000"})
      assertEquals(n,Kind.MOBILE,ModeRules.kind(n));
  }

  @Test public void businessAndShortCodesAreNeverMobile(){
    assertEquals(Kind.BUSINESS,ModeRules.kind("1588-1234"));
    assertEquals(Kind.BUSINESS,ModeRules.kind("1544-0000"));
    assertEquals(Kind.BUSINESS,ModeRules.kind("1661-1234"));
    assertEquals(Kind.BUSINESS,ModeRules.kind("1833-1234"));
    assertEquals(Kind.SHORT,ModeRules.kind("114"));
    assertEquals(Kind.SHORT,ModeRules.kind("112"));
    assertEquals(Kind.SHORT,ModeRules.kind("119"));
    assertEquals(Kind.SHORT,ModeRules.kind("1577"));     // 4자리 짧은 번호
    assertEquals(Kind.TOLLFREE,ModeRules.kind("080-123-4567"));
    assertEquals(Kind.PREMIUM,ModeRules.kind("060-700-1234"));
    assertEquals(Kind.HIDDEN,ModeRules.kind(""));
    assertEquals(Kind.HIDDEN,ModeRules.kind("-1"));
    assertEquals(Kind.HIDDEN,ModeRules.kind("비공개"));
  }

  @Test public void landlineVoipIntl(){
    assertEquals(Kind.LANDLINE,ModeRules.kind("02-123-4567"));
    assertEquals(Kind.LANDLINE,ModeRules.kind("02-1234-5678"));
    assertEquals(Kind.LANDLINE,ModeRules.kind("031-123-4567"));
    assertEquals(Kind.LANDLINE,ModeRules.kind("+82-2-123-4567"));
    assertEquals(Kind.VOIP,ModeRules.kind("070-1234-5678"));
    assertEquals(Kind.VOIP,ModeRules.kind("0507-1234-5678")); // 안심번호
    assertEquals(Kind.INTL,ModeRules.kind("+1 415 555 0123"));
    assertEquals(Kind.INTL,ModeRules.kind("+86 138 0013 8000"));
  }

  @Test public void oddNumbersAreOther(){
    assertEquals(Kind.OTHER,ModeRules.kind("1234567"));     // 지역번호 없는 7자리
    assertEquals(Kind.OTHER,ModeRules.kind("0101234"));     // 너무 짧은 010
    assertEquals(Kind.OTHER,ModeRules.kind("0109999999999")); // 너무 긴
    assertEquals(Kind.HIDDEN,ModeRules.kind("+82"));   // 번호가 아님
  }

  @Test public void defaultSendsOnlyToMobile(){
    assertEquals(Why.SEND,d("010-1234-5678"));
    assertEquals(Why.SEND,d("+82 10 5555 6666"));
    assertEquals(Why.KIND,d("02-123-4567"));
    assertEquals(Why.KIND,d("070-1111-2222"));
    assertEquals(Why.KIND,d("+1 415 555 0123"));
    assertEquals(Why.KIND,d("1588-1234"));
    assertEquals(Why.KIND,d("1644-0000"));
    assertEquals(Why.KIND,d("114"));
    assertEquals(Why.KIND,d("080-111-2222"));
    assertEquals(Why.NO_NUMBER,d(""));
    assertEquals(Why.NO_NUMBER,d("-2"));
  }

  @Test public void otherSettingOpensLandlineVoipIntlButNeverBusiness(){
    java.util.function.Function<String,Why> f=n->ModeRules.decide(true,true,false,false,n,false,new HashSet<>(),0,"문구");
    assertEquals(Why.SEND,f.apply("02-123-4567"));
    assertEquals(Why.SEND,f.apply("031-123-4567"));
    assertEquals(Why.SEND,f.apply("070-1111-2222"));
    assertEquals(Why.SEND,f.apply("+1 415 555 0123"));
    // 설정을 켜도 절대 안 보내는 것들
    for(String n:new String[]{"1588-1234","1644-0000","1833-1234","114","119","1577","080-111-2222","060-700-1234","","-2","KB국민카드"})
      assertNotEquals(n,Why.SEND,f.apply(n));
  }

  @Test public void oncePerNumberPerSession(){
    Set<String> done=new HashSet<>();
    assertEquals(Why.SEND,ModeRules.decide(true,false,false,false,"010-1234-5678",false,done,0,"x"));
    done.add(ModeRules.key("010-1234-5678"));
    assertEquals(Why.DUP,ModeRules.decide(true,false,false,false,"010-1234-5678",false,done,1,"x"));
    assertEquals("같은 번호를 다른 모양으로 써도 한 번만",Why.DUP,ModeRules.decide(true,false,false,false,"+82 10 1234 5678",false,done,1,"x"));
    assertEquals(Why.DUP,ModeRules.decide(true,false,false,false,"01012345678",false,done,1,"x"));
    assertEquals(Why.SEND,ModeRules.decide(true,false,false,false,"010-9999-0000",false,done,1,"x"));
  }

  @Test public void switchesAndCap(){
    assertEquals(Why.OFF,ModeRules.decide(false,false,false,false,"010-1234-5678",false,new HashSet<>(),0,"x"));
    assertEquals(Why.ANSWERED,ModeRules.decide(true,false,false,false,"010-1234-5678",true,new HashSet<>(),0,"x"));
    assertEquals(Why.NO_TEXT,ModeRules.decide(true,false,false,false,"010-1234-5678",false,new HashSet<>(),0,"  "));
    assertEquals(Why.NO_TEXT,ModeRules.decide(true,false,false,false,"010-1234-5678",false,new HashSet<>(),0,null));
    assertEquals(Why.NOT_CONTACT,ModeRules.decide(true,false,true,false,"010-1234-5678",false,new HashSet<>(),0,"x"));
    assertEquals(Why.SEND,ModeRules.decide(true,false,true,true,"010-1234-5678",false,new HashSet<>(),0,"x"));
    assertEquals(Why.CAP,ModeRules.decide(true,false,false,false,"010-1234-5678",false,new HashSet<>(),ModeRules.MAX_REPLIES,"x"));
    assertEquals(Why.SEND,ModeRules.decide(true,false,false,false,"010-1234-5678",false,new HashSet<>(),ModeRules.MAX_REPLIES-1,"x"));
  }

  @Test public void kindOrderMatters_businessBeforeContactsOnly(){
    // 연락처에 있는 번호라도 기업 대표번호면 안 보냄
    assertEquals(Why.KIND,ModeRules.decide(true,true,false,true,"1588-1234",false,new HashSet<>(),0,"x"));
  }
}

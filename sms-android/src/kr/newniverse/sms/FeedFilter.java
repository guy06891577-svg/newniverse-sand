package kr.newniverse.sms;

import java.util.regex.Pattern;

/** v2: 런처로 넘길 문자를 고르는 규칙(순수 자바, 폰 안에서만). 택배·병원/예약·카드 결제·인증번호와 관련된 말이 들어 있는 문자만 넘기고, 나머지 사적인 문자는 넘기지 않음. */
final class FeedFilter {
  static final Pattern KW=Pattern.compile("택배|배송|배달|송장|운송장|병원|의원|치과|한의원|진료|예약|내원|검진|카드|승인|결제|체크|인증|본인확인|OTP|일회용|보안코드|verification|code",Pattern.CASE_INSENSITIVE);
  static final int MAX_DAYS=30, MAX_ROWS=300, MAX_LEN=1000;
  static boolean relevant(String body){ return body!=null&&body.trim().length()>=6&&KW.matcher(body).find(); }
  static String clip(String s){ if(s==null) return ""; s=s.trim(); return s.length()>MAX_LEN?s.substring(0,MAX_LEN):s; }
  static int days(String arg){ int d=7; try{ d=Integer.parseInt(String.valueOf(arg).trim()); }catch(Exception e){} return Math.max(1,Math.min(MAX_DAYS,d)); }
}

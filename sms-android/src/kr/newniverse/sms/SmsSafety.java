package kr.newniverse.sms;

import java.util.regex.Pattern;

/** v2: 달력에 자동으로 넣기 전 안전 확인(순수 자바). 광고 문자와 의심스러운 링크(스미싱)는 건너뜀 */
final class SmsSafety {
  static final Pattern AD=Pattern.compile("[\\(\\[【]\\s*광고\\s*[\\)\\]】]|광고\\)|무료\\s*수신\\s*거부|무료\\s*거부|수신\\s*거부|080[\\s\\-]?\\d{3,4}[\\s\\-]?\\d{4}|광고\\s*문자");
  static final Pattern URL=Pattern.compile("(?:https?://|www\\.)[^\\s)\\]】]+",Pattern.CASE_INSENSITIVE);
  static final Pattern SHORT=Pattern.compile("(?:bit\\.ly|me2\\.do|url\\.kr|vo\\.la|han\\.gl|t\\.ly|goo\\.gl|tinyurl\\.com|is\\.gd|buly\\.kr|c11\\.kr|zrr\\.kr|ow\\.ly|rb\\.gy|cutt\\.ly|shorturl\\.at|nl\\.go\\.kr|abit\\.ly|han\\.gl)(?:/|$|\\s)",Pattern.CASE_INSENSITIVE);
  static final Pattern IPURL=Pattern.compile("https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}",Pattern.CASE_INSENSITIVE);
  static final Pattern APK=Pattern.compile("\\.apk\\b|앱\\s*설치|설치\\s*하세요|원격\\s*제어|악성",Pattern.CASE_INSENSITIVE);
  static final Pattern URGENT=Pattern.compile("미납|과태료|체납|정지|해외\\s*결제|명의\\s*도용|검찰|수사|대출|당첨|상품권|긴급|즉시\\s*확인|본인\\s*확인\\s*필요|환급|지연\\s*수수료|주소\\s*불일치|주소\\s*확인|반송|재배송\\s*요청|결제\\s*실패|계정\\s*(?:정지|보호)");
  static boolean isAd(String b){ return b!=null&&AD.matcher(b).find(); }
  static boolean suspicious(String b){
    if(b==null) return false;
    if(IPURL.matcher(b).find()||APK.matcher(b).find()) return true;
    java.util.regex.Matcher m=URL.matcher(b); boolean has=false;
    while(m.find()){ has=true; if(SHORT.matcher(m.group()+" ").find()) return true; }
    return has&&URGENT.matcher(b).find();
  }
}

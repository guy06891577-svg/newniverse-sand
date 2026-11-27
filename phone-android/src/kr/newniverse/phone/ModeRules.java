package kr.newniverse.phone;

import java.util.Set;

/**
 * 상황 모드의 「자동 답장을 보내도 되는 번호인지」 규칙. 안드로이드 부품을 쓰지 않는 순수 자바(시험하기 쉽게).
 * 원칙: 확실히 사람 휴대전화로 보이는 번호에만 보내요. 기업 대표번호(15xx·16xx·18xx)·짧은 번호·080·060·표시 제한은 어떤 설정이어도 안 보내요.
 */
final class ModeRules {
  private ModeRules(){}

  enum Kind { MOBILE, LANDLINE, VOIP, INTL, BUSINESS, TOLLFREE, PREMIUM, SHORT, HIDDEN, OTHER }
  enum Why { SEND, OFF, KIND, ANSWERED, DUP, CAP, NOT_CONTACT, NO_TEXT, NO_NUMBER }

  /** 한 번 켜 둔 동안 보낼 수 있는 답장 수 상한 (실수로 문자가 쏟아지는 것을 막는 안전장치) */
  static final int MAX_REPLIES=40;

  /** 번호를 「010…」 같은 국내 숫자꼴로. 국제 번호는 「+」로 시작. 숫자가 없으면 "" */
  static String key(String raw){
    if(raw==null) return "";
    String s=raw.trim();
    if(s.isEmpty()) return "";
    for(int i=0;i<s.length();i++){ char c=s.charAt(i); if(Character.isLetter(c)) return ""; }   // 글자가 섞인 발신자(문자 서비스 이름 등)
    if(s.startsWith("-")) return "";   // 통화 기록의 「-1」「-2」는 비공개·공중전화 같은 표시 값이지 번호가 아님
    boolean plus=s.startsWith("+");
    StringBuilder d=new StringBuilder();
    for(int i=0;i<s.length();i++){ char c=s.charAt(i); if(c>='0'&&c<='9') d.append(c); }
    String x=d.toString();
    if(x.length()<3) return "";        // 1~2자리는 번호가 아님
    if(plus){
      if(x.startsWith("82")&&x.length()>=10&&x.length()<=13&&x.charAt(2)!='0') return "0"+x.substring(2);
      if(x.startsWith("820")&&x.length()>=11) return x.substring(2);   // +82 010… (0을 그대로 쓴 경우)
      return "+"+x;
    }
    if(x.startsWith("82")&&x.length()>=11&&x.length()<=13&&x.charAt(2)!='0') return "0"+x.substring(2);   // 국내 번호는 0으로 시작하므로 겹치지 않음
    return x;
  }

  static Kind kind(String raw){
    String k=key(raw);
    if(k.isEmpty()) return Kind.HIDDEN;
    if(k.startsWith("+")) return k.length()>=8?Kind.INTL:Kind.OTHER;
    int n=k.length();
    if(k.charAt(0)!='0'){
      if(n<=6) return Kind.SHORT;                                         // 112·114·119 · 1577 같은 짧은 번호
      if(k.matches("1[5-9]\\d{6}")) return Kind.BUSINESS;                  // 1588-xxxx · 1644-xxxx 같은 기업 대표번호
      return Kind.OTHER;
    }
    if(k.matches("01[016789]\\d{7,8}")) return Kind.MOBILE;
    if(k.matches("080\\d{7,8}")) return Kind.TOLLFREE;
    if(k.matches("060\\d{6,8}")) return Kind.PREMIUM;
    if(k.matches("070\\d{7,8}")||k.matches("050\\d{8,9}")) return Kind.VOIP;
    if(k.matches("0[2-6]\\d{7,9}")) return Kind.LANDLINE;
    if(n<=6) return Kind.SHORT;
    return Kind.OTHER;
  }

  /** 종류만 보고: 휴대전화는 항상, 일반전화·인터넷전화·해외 번호는 사용자가 허락했을 때만, 나머지는 절대 안 함 */
  static boolean kindAllowed(Kind k,boolean otherOk){
    switch(k){
      case MOBILE: return true;
      case LANDLINE: case VOIP: case INTL: return otherOk;
      default: return false;
    }
  }

  static String kindLabel(Kind k){
    switch(k){
      case MOBILE: return "휴대전화";
      case LANDLINE: return "일반 전화";
      case VOIP: return "인터넷 전화";
      case INTL: return "해외 번호";
      case BUSINESS: return "기업 대표번호";
      case TOLLFREE: return "수신자 부담 번호";
      case PREMIUM: return "정보 이용료 번호";
      case SHORT: return "짧은 번호";
      case HIDDEN: return "번호 표시 제한";
      default: return "알 수 없는 번호";
    }
  }

  /** 안 보내는 이유를 한 묶음으로 센 이름 (요약 카드에 보여 줌) */
  static String skipLabel(Why w,Kind k){
    switch(w){
      case KIND: return kindLabel(k);
      case DUP: return "이미 보낸 번호";
      case CAP: return "보낼 수 있는 수를 넘음";
      case NOT_CONTACT: return "연락처에 없는 번호";
      case ANSWERED: return "받은 전화";
      case NO_TEXT: return "답장 문구 없음";
      case OFF: return "자동 답장 꺼짐";
      default: return "번호를 몰라요";
    }
  }

  /**
   * 안 받은 전화 한 통에 답장을 보낼지.
   * @param replyOn 모드의 「자동 답장」 켜짐
   * @param otherOk 일반전화·인터넷전화·해외 번호에도 보내기
   * @param contactsOnly 연락처에 있는 사람에게만
   * @param isContact 이 번호가 연락처에 있는지
   * @param answered 사용자가 받았는지(받았으면 답장 안 함)
   * @param done 이번에 이미 답장한 번호들(key 기준)
   * @param sent 이번에 보낸 답장 수
   */
  static Why decide(boolean replyOn,boolean otherOk,boolean contactsOnly,boolean isContact,String raw,boolean answered,Set<String> done,int sent,String text){
    if(!replyOn) return Why.OFF;
    if(answered) return Why.ANSWERED;
    if(text==null||text.trim().isEmpty()) return Why.NO_TEXT;
    String k=key(raw);
    if(k.isEmpty()) return Why.NO_NUMBER;
    if(!kindAllowed(kind(raw),otherOk)) return Why.KIND;
    if(done!=null&&done.contains(k)) return Why.DUP;
    if(contactsOnly&&!isContact) return Why.NOT_CONTACT;
    if(sent>=MAX_REPLIES) return Why.CAP;
    return Why.SEND;
  }
}

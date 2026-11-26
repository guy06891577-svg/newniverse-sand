package kr.newniverse.sms;
public class FeedFilterTest{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  public static void main(String[] a){
    String[] yes={"[CJ대한통운] 고객님의 택배가 오늘 도착 예정입니다. 운송장 123456789012","[Web발신] 안동병원 진료예약 12/3 14:30 박OO님","[신한카드] 승인 홍*동 35,000원 일시불 11/26 12:30 스타벅스","[국민은행] 인증번호 [482913]를 입력해 주세요","Your verification code is 123456","[롯데택배] 배송 출발","[삼성카드] 해외결제 승인 USD 12.00","[한국] 본인확인 인증번호 77123"};
    String[] no={"엄마 오늘 저녁 뭐 먹어?","내일 7시에 보자 ㅎㅎ","ㅇㅋ","  ",null,"짧음","[광고] 겨울 세일 최대 70%"};
    for(String s:yes) t("yes:"+s,FeedFilter.relevant(s));
    for(String s:no) t("no:"+s,!FeedFilter.relevant(s));
    t("days default",FeedFilter.days(null)==7); t("days max",FeedFilter.days("999")==30); t("days min",FeedFilter.days("0")==1); t("days 14",FeedFilter.days("14")==14);
    t("clip",FeedFilter.clip(new String(new char[2000]).replace('\0','가')).length()==1000);
    System.out.println("FeedFilter java tests: "+ok+" passed, "+bad+" failed"); if(bad>0) System.exit(1);
  }}

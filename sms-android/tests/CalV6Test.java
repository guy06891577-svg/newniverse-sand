package kr.newniverse.sms;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** v6: 실제 폰(S25+ 문자 앱에서 복사 → LMS)에서 올 수 있는 글자 모양 변형 + 단계별 실패 이유 */
public class CalV6Test{
  static int ok=0,bad=0;
  static void t(String n,boolean c){ if(c) ok++; else { bad++; System.out.println("FAIL "+n);} }
  static long ms(int y,int mo,int d,int h,int mi){ return CalV5Test.ms(y,mo,d,h,mi); }
  static final long AT=CalV5Test.AT;
  static final String BASE=CalV5Test.LMS;
  static final String SP="\u00A0";

  /** 문자 하나를 읽어 기대한 일정이 나오는지 */
  static void expect(String name,String text){
    ApptParser.R r=ApptParser.parse(text,AT,"내 번호","01012345678");
    boolean good=r!=null&&r.when==ms(2026,11,27,16,0)&&r.hasTime&&r.location.equals("안동시 중앙로 1-2 솜씨쟁이")&&r.title.equals("길주중학교 · 원목 트레이, 원목 수납장 만들기")&&r.trusted&&r.ask;
    t(name,good);
    if(!good) System.out.println("   got: "+(r==null?"null → "+ApptParser.why(text,AT):r.when+" | "+r.location+" | "+r.title+" | trusted="+r.trusted+" ask="+r.ask));
    CalDecide.D d=CalDecide.decide(true,true,text,AT,"내 번호","01012345678");
    t(name+" → decide ask",d.act.equals("ask"));
    // 신청 기간·마감일이 일정이 되지 않음
    t(name+" not deadline",r!=null&&r.when!=ms(2026,11,24,10,0)&&ApptParser.dayOf(r.when)==ApptParser.dayOf(ms(2026,11,27,0,0)));
  }
  static MmsPdu.Pdu pdu(String subject,String... texts){
    MmsPdu.Pdu p=new MmsPdu.Pdu(); p.type=MmsPdu.RETRIEVE_CONF;
    if(subject!=null) p.subject=new MmsPdu.Enc(106,subject.getBytes(StandardCharsets.UTF_8));
    for(String x:texts){ MmsPdu.Part q=new MmsPdu.Part(); q.ct="text/plain"; q.charset=106; q.data=x.getBytes(StandardCharsets.UTF_8); p.parts.add(q); }
    return p;
  }
  static String viaMms(MmsPdu.Pdu p){ return MmsLogic.feedText(p.subjectStr(),p.text()); }

  public static void main(String[] a){
    expect("baseline",BASE);
    expect("CRLF",BASE.replace("\n","\r\n"));
    expect("CR only",BASE.replace("\n","\r"));
    expect("U+2028 line sep",BASE.replace("\n","\u2028"));
    expect("NBSP for every space",BASE.replace(" ",SP));
    expect("NBSP before bullet only",BASE.replace("\n ○","\n"+SP+"○").replace("\n○","\n"+SP+SP+"○"));
    expect("ideographic space",BASE.replace(" ","\u3000"));
    expect("narrow NBSP + thin space",BASE.replace(". ",".\u202F").replace(": ",":\u2009"));
    expect("tabs",BASE.replace(" ○","\t○"));
    expect("fullwidth colon ：",BASE.replace(":","：").replace("16：00","16:00").replace("1-2","1-2"));
    expect("fullwidth colon in time too",BASE.replace(":","："));
    expect("tilde ～",BASE.replace("~","～"));
    expect("tilde 〜",BASE.replace("~","〜"));
    expect("fullwidth digits",BASE.replace("2026. 11. 27.(금) 16:00","２０２６. １１. ２７.(금) １６:００"));
    expect("fullwidth dot and parens",BASE.replace("27.(금)","27．（금）"));
    expect("zero-width chars",BASE.replace("일시","일\u200B시").replace("2026.","2026\u200B.").replace("○","\u200B○"));
    expect("BOM at start",("\uFEFF"+BASE));
    expect("bullet ㅇ",BASE.replace("○","ㅇ"));
    expect("bullet ◦",BASE.replace("○","◦"));
    expect("bullet ⚬",BASE.replace("○","\u26AC"));
    expect("bullet ◯",BASE.replace("○","\u25EF"));
    expect("bullet ●",BASE.replace("○","●"));
    expect("bullet ▶",BASE.replace("○","▶"));
    expect("bullet dash",BASE.replace("○","-"));
    expect("no bullets",BASE.replace("○ ","").replace("○",""));
    expect("all newlines lost (one line)",BASE.replace("\n"," "));
    expect("all newlines lost, no spaces",BASE.replace("\n",""));
    expect("space before colon",BASE.replace("일시:","일시 :").replace("장소:","장소 :").replace("주제:","주제 :"));
    expect("spaced label 일 시",BASE.replace("일시:","일 시:").replace("장소:","장 소:"));
    expect("date with spaces around parens",BASE.replace("27.(금) 16:00","27. (금)  16:00"));
    expect("no dot before weekday",BASE.replace("27.(금)","27(금)"));
    expect("all together: CRLF+NBSP+：+～+ㅇ",BASE.replace("\n","\r\n").replace(" ",SP).replace(":","：").replace("~","～").replace("○","ㅇ"));

    // MMS 변형: 제목 비어 있음 / 제목=본문 / 제목=본문 앞부분 / 글 조각 여러 개
    expect("mms empty subject",viaMms(pdu("",BASE)));
    expect("mms null subject",viaMms(pdu(null,BASE)));
    expect("mms subject 제목없음",viaMms(pdu("제목없음",BASE)));
    expect("mms subject = whole body",viaMms(pdu(BASE,BASE)));
    expect("mms subject = body with CRLF",viaMms(pdu(BASE.replace("\n","\r\n"),BASE)));
    expect("mms subject = body first line",viaMms(pdu("[길주중학교]",BASE)));
    expect("mms subject = body prefix (40 chars)",viaMms(pdu(BASE.substring(0,40),BASE)));
    expect("mms subject only, body empty",viaMms(pdu(BASE)));
    expect("mms subject same text but flattened spaces",viaMms(pdu(BASE.replace("\n"," "),BASE)));
    String[] ln=BASE.split("\n");
    expect("mms parts split at line boundaries (join \\n)",viaMms(pdu("",ln[0]+"\n"+ln[1]+"\n"+ln[2],String.join("\n",Arrays.copyOfRange(ln,3,ln.length)))));
    int cut=BASE.indexOf("○ 장소");
    expect("mms parts split right before 장소",viaMms(pdu("",BASE.substring(0,cut),BASE.substring(cut))));
    int cut2=BASE.indexOf("참가를 희망")+8;
    expect("mms parts split mid sentence",viaMms(pdu("",BASE.substring(0,cut2),BASE.substring(cut2))));
    int cut3=BASE.indexOf("신청 기간")+3;
    expect("mms parts split inside 신청 기간 label",viaMms(pdu("",BASE.substring(0,cut3),BASE.substring(cut3))));
    expect("mms five parts at line boundaries",viaMms(pdu("",ln[0],ln[1]+"\n"+ln[2],ln[3]+"\n"+ln[4]+"\n"+ln[5],ln[6]+"\n"+ln[7],String.join("\n",Arrays.copyOfRange(ln,8,ln.length)))));
    MmsPdu.Pdu sm=pdu("",BASE); MmsPdu.Part smil=new MmsPdu.Part(); smil.ct="application/smil"; smil.data="<smil/>".getBytes(); sm.parts.add(0,smil);
    expect("mms with smil part first",viaMms(sm));

    // 느슨한 보조 규칙: '일시' 줄 + 날짜 + 시각
    ApptParser.R r=ApptParser.parse("[길주중학교]\n일시 2026. 11. 27.(금) 오후 4시\n장소: 솜씨쟁이",AT,"x","01000000000");
    t("fallback: 일시 without colon, 오후 4시",r!=null&&r.when==ms(2026,11,27,16,0)&&r.hasTime&&r.location.equals("솜씨쟁이"));
    r=ApptParser.parse("[길주중학교] 학부모 안내\n○ 일시: 11월 27일(금) 16:00\n○ 장소: 도서관",AT,"x","01000000000");
    t("fallback: M월 D일",r!=null&&r.when==ms(2026,11,27,16,0));
    r=ApptParser.parse("[길주중학교] 학부모 안내\n○ 일시: 11/27(금) 16:30",AT,"x","01000000000");
    t("fallback: M/D",r!=null&&r.when==ms(2026,11,27,16,30));
    r=ApptParser.parse("[길주중학교] 학부모 안내\n○ 일시: 2026. 11. 27.(금)\n○ 시간: 오후 4시 30분",AT,"x","01000000000");
    t("time on a separate 시간 line",r!=null&&r.when==ms(2026,11,27,16,30)&&r.hasTime);
    r=ApptParser.parse("[길주중학교] 학부모 안내\n○ 일시: 2026. 11. 27.(금)\n○ 장소: 강당",AT,"x","01000000000");
    t("no time → all-day event",r!=null&&!r.hasTime&&ApptParser.dayOf(r.when)==ApptParser.dayOf(ms(2026,11,27,0,0)));
    CalLogic.Out o=CalLogic.apply(new CalV5Test.Fake(),r,"01000000000",false); t("all-day entry",o.op.equals("add")&&o.e.allDay);
    r=ApptParser.parse("[길주중학교]\n행사 일시: 2026. 11. 27.(금) 16:00 ~ 18:00",AT,"x","01000000000"); t("행사 일시 + end",r!=null&&r.endMs==ms(2026,11,27,18,0));
    t("idempotent norm",ApptParser.norm(ApptParser.norm(BASE.replace(" ",SP).replace("\n","\r\n"))).equals(ApptParser.norm(BASE.replace(" ",SP).replace("\n","\r\n"))));
    t("norm: NBSP → space",ApptParser.norm("a"+SP+"b").equals("a b"));
    t("norm: CRLF → LF",ApptParser.norm("a\r\nb\rc").equals("a\nb\nc"));
    t("norm: ：～ → :~",ApptParser.norm("일시：1～2").equals("일시:1~2"));

    // 단계별 실패 이유
    t("why no-date",ApptParser.why("[길주중학교] 학부모 동아리 안내입니다. 많은 참여 바랍니다.",AT).equals("parse:no-date"));
    t("why deadline-only",ApptParser.why("[길주중학교] 학부모님께서는 11월 26일(목)까지 신청하여 주시기 바랍니다.",AT).equals("parse:deadline-only"));
    t("why deadline-only (신청 기간 line)",ApptParser.why("[길주중학교] 안내\n○ 신청 기간: 2026. 11. 24.(화) 10:00 ~ 11. 26.(목)",AT).equals("parse:deadline-only"));
    t("why no-datetime-line",ApptParser.why("[길주중학교] 12/3 오후 3시에 보자 안녕",AT).equals("parse:no-datetime-line"));
    t("why past",ApptParser.why("[길주중학교]\n○ 일시: 2026. 11. 1.(일) 10:00",AT).equals("parse:past"));
    CalDecide.D d=CalDecide.decide(true,true,"[길주중학교] 학부모님께서는 11월 26일(목)까지 신청하여 주시기 바랍니다.",AT,"내 번호","010");
    t("decide reason carries stage",d.reason.equals("noparse:parse:deadline-only"));
    t("label shows Korean + stage",CalDecide.stepsLabel("skip","noparse").equals("건너뜀(일정으로 읽지 못함)"));
    t("stages hold no body/number",!ApptParser.why(BASE+"01012345678",AT).matches(".*\\d{4}.*"));

    // 예전 문구는 그대로
    expect("v5 fixture still ok",CalV5Test.LMS);
    r=ApptParser.parse("[안동병원] 박준혁님 진료예약 확인\r\n일시:\u00A02026.12.03(목)\u00A014:30\r\n장소: 본관 3층",AT,null);
    t("hospital CRLF+NBSP",r!=null&&r.src.equals("hosp")&&r.when==ms(2026,12,3,14,30)&&!r.ask&&r.location.equals("본관 3층"));
    System.out.println("Cal v6 java tests: "+ok+" passed, "+bad+" failed"); if(bad>0) System.exit(1);
  }}

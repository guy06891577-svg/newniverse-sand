package kr.newniverse.phone;

import java.util.*;
import java.util.regex.*;

/** v6 명함 글 → 연락처 칸 (순수 자바). OCR이 준 줄(글·글자 높이)을 보고 이름·직함·가게/회사·휴대폰·전화·팩스·이메일을 골라요.
 *  틀릴 수 있어서 화면에서 꼭 확인·수정한 뒤 저장해요. */
final class CardParser {
  private CardParser(){}
  static final class Line { final String t; final float h; Line(String t,float h){ this.t=t==null?"":t.trim(); this.h=h; } }
  static final class Card {
    String name="", title="", company="", mobile="", tel="", fax="", email="";
    boolean companyGuess=false, nameGuess=false;
    final List<String> lines=new ArrayList<>();
    final List<String> mobiles=new ArrayList<>(), tels=new ArrayList<>();
  }

  static final String TITLES="대표이사|공동대표|대표원장|대표|부사장|사장|회장|전무|상무|이사|본부장|실장|부장|차장|과장|대리|주임|사원|팀장|파트장|원장|부원장|점장|지점장|소장|국장|센터장|매니저|사무장|간사|교수|박사|변호사|세무사|회계사|노무사|변리사|약사|기사|셰프|주방장|실장님|CEO|CTO|COO|CFO|Manager|Director|President";
  static final Pattern TITLE_ONLY=Pattern.compile("^(?:[가-힣A-Za-z]{1,10}(?:부|팀|과|실|본부|센터|사업부)\\s*)?(?:"+TITLES+")(?:\\s*/\\s*(?:"+TITLES+"))?$");
  static final Pattern TITLE_IN=Pattern.compile("(?:"+TITLES+")");
  static final Pattern COMPANY=Pattern.compile("(\\(주\\)|㈜|주식회사|유한회사|\\(유\\)|합자회사|상사|상회|식당|마트|카페|커피|공업|산업|건설|기업|그룹|컴퍼니|코리아|센터|약국|병원|의원|치과|한의원|부동산|공인중개|학원|미용실|헤어|정비|모터스|자동차|전자|통신|물산|유통|무역|농장|농원|축산|수산|제과|베이커리|떡집|정육|철물|인테리어|디자인|스튜디오|사진관|여행사|보험|은행|회계|세무|법무|특허|사무소|협회|조합|재단|연구소|공방|플라워|꽃집|세탁|청소|이삿짐|택배|푸드|치킨|피자|족발|반찬|김밥|분식|횟집|고기|갈비|국밥|Co\\.|Corp|Inc|Ltd|LLC|Company|Studio|Cafe)",Pattern.CASE_INSENSITIVE);
  static final Pattern ADDRESS=Pattern.compile("(특별시|광역시|특별자치|[가-힣]+도\\s|[가-힣]+시\\s|[가-힣]+구\\s|[가-힣]+군\\s|[가-힣]+(로|길)\\s*\\d|\\d+번길|\\d+층|\\d+호|우\\)|우편)");
  /** OCR이 마지막 점을 빈칸으로 읽은 경우(hanbit.co kr) */
  static final Pattern EMAIL_TAIL=Pattern.compile("^\\s+(kr|com|net|org)\\b",Pattern.CASE_INSENSITIVE);
  static final Pattern EMAIL=Pattern.compile("[A-Za-z0-9._%+\\-]+\\s?@\\s?[A-Za-z0-9\\-]+(?:\\s?\\.\\s?[A-Za-z0-9\\-]+)+");
  static final Pattern URL=Pattern.compile("(?i)(www\\.|https?://|\\.com\\b|\\.co\\.kr\\b|\\.kr\\b|\\.net\\b)");
  // 번호: +82 10 1234 5678 / 010-1234-5678 / 02)123-4567 / 1588-1234 / 031.123.4567
  static final Pattern PHONE=Pattern.compile("(?<![0-9])(?:\\+\\s?82[\\s.\\-)]*\\(?0?\\)?|\\(?0)\\d{1,2}\\)?[\\s.\\-)]*\\d{3,4}[\\s.\\-]*\\d{4}(?![0-9])|(?<![0-9])1[5-9]\\d{2}[\\s.\\-]*\\d{4}(?![0-9])");
  static final Pattern L_MOBILE=Pattern.compile("(?i)(mobile|mob|cell|h\\.?\\s?p|c\\.?\\s?p|휴대폰|휴대전화|핸드폰|휴대|모바일|\\bm\\b)");
  static final Pattern L_FAX=Pattern.compile("(?i)(fax|팩스|\\bf\\b)");
  static final Pattern L_TEL=Pattern.compile("(?i)(tel|phone|전화|대표번호|대표|사무실|office|direct|직통|\\bt\\b|\\bp\\b|\\bo\\b)");
  static final Pattern HANGUL_NAME=Pattern.compile("^[가-힣]{2,4}$");
  static final Pattern LATIN_NAME=Pattern.compile("^[A-Z][a-z]+(?:[ \\-][A-Z][a-z]+){1,2}$");

  static String digits(String s){ return s==null?"":s.replaceAll("[^0-9]",""); }
  /** 숫자 모양 다듬기: +82 10… → 010… */
  static String normNum(String raw){
    String d=digits(raw);
    if(raw.trim().startsWith("+")&&d.startsWith("82")){ d=d.substring(2); if(!d.startsWith("0")) d="0"+d; }
    else if(d.startsWith("820")&&d.length()>=11) d=d.substring(2);
    return d;
  }
  static String fmt(String d){
    int L=d.length();
    if(d.startsWith("02")&&L>=9&&L<=10) return d.substring(0,2)+"-"+d.substring(2,L-4)+"-"+d.substring(L-4);
    if(d.matches("1[5-9]\\d{6}")) return d.substring(0,4)+"-"+d.substring(4);
    if(d.startsWith("0")&&(L==10||L==11)) return d.substring(0,3)+"-"+d.substring(3,L-4)+"-"+d.substring(L-4);
    return d;
  }
  static boolean mobileDigits(String d){ return d.matches("01[016789]\\d{7,8}"); }
  static boolean validDigits(String d){ return mobileDigits(d)||d.matches("0[2-9]\\d{7,9}")||d.matches("1[5-9]\\d{6}"); }
  /** OCR이 숫자 자리에서 흔히 헷갈리는 글자 고치기 (번호처럼 보이는 줄에서만) */
  static String fixDigitsLike(String s){
    StringBuilder b=new StringBuilder(s);
    for(int i=0;i<b.length();i++){ char ch=b.charAt(i);
      boolean nearDigit=(i>0&&Character.isDigit(b.charAt(i-1)))||(i+1<b.length()&&Character.isDigit(b.charAt(i+1)));
      if(!nearDigit) continue;
      if(ch=='O'||ch=='o') b.setCharAt(i,'0'); else if(ch=='l'||ch=='I'||ch=='|') b.setCharAt(i,'1'); }
    return b.toString();
  }

  /** OCR 결과 [{t,h,y,x}] → 위에서 아래 순서의 줄 */
  static List<Line> linesFromJson(String json){
    List<Line> l=new ArrayList<>(); List<float[]> pos=new ArrayList<>();
    try{ org.json.JSONArray a=new org.json.JSONArray(json==null?"[]":json);
      for(int i=0;i<a.length()&&i<200;i++){ org.json.JSONObject o=a.optJSONObject(i); if(o==null) continue; l.add(new Line(o.optString("t"),(float)o.optDouble("h",0))); pos.add(new float[]{(float)o.optDouble("y",i),(float)o.optDouble("x",0),i}); }
    }catch(Exception e){}
    Integer[] ix=new Integer[l.size()]; for(int i=0;i<ix.length;i++) ix[i]=i;
    Arrays.sort(ix,(x,y)->{ float[] p=pos.get(x),q=pos.get(y); return Float.compare(p[0],q[0])!=0?Float.compare(p[0],q[0]):Float.compare(p[1],q[1]); });
    List<Line> out=new ArrayList<>(); for(Integer i:ix) out.add(l.get(i)); return out;
  }
  static org.json.JSONObject toJson(Card c){
    org.json.JSONObject o=new org.json.JSONObject();
    try{ o.put("name",c.name); o.put("title",c.title); o.put("company",c.company); o.put("mobile",c.mobile); o.put("tel",c.tel); o.put("fax",c.fax); o.put("email",c.email);
      o.put("companyGuess",c.companyGuess); o.put("nameGuess",c.nameGuess);
      o.put("lines",new org.json.JSONArray(c.lines)); o.put("mobiles",new org.json.JSONArray(c.mobiles)); o.put("tels",new org.json.JSONArray(c.tels)); }catch(Exception e){}
    return o;
  }

  static Card parse(List<Line> in){
    Card c=new Card();
    List<Line> rest=new ArrayList<>();
    for(Line l:in){ if(l.t.isEmpty()) continue; c.lines.add(l.t);
      String t=l.t;
      // 이메일
      Matcher em=EMAIL.matcher(t);
      boolean used=false;
      if(em.find()){ String e=em.group().replaceAll("\\s",""); int end=em.end(); Matcher tl=EMAIL_TAIL.matcher(t.substring(end)); if(tl.find()){ e=e+"."+tl.group(1).toLowerCase(java.util.Locale.ROOT); end+=tl.end(); } if(c.email.isEmpty()) c.email=e; t=(t.substring(0,em.start())+" "+t.substring(end)).trim(); used=true; }
      // 번호
      String tf=fixDigitsLike(t);
      Matcher pm=PHONE.matcher(tf); int prevEnd=0; boolean any=false;
      while(pm.find()){
        String d=normNum(pm.group()); if(!validDigits(d)){ prevEnd=pm.end(); continue; }
        String before=tf.substring(prevEnd,pm.start());
        String kind=label(before);
        if("fax".equals(kind)){ if(c.fax.isEmpty()) c.fax=fmt(d); }
        else if(mobileDigits(d)){ if(!c.mobiles.contains(fmt(d))) c.mobiles.add(fmt(d)); }
        else { if(!c.tels.contains(fmt(d))) c.tels.add(fmt(d)); }
        prevEnd=pm.end(); any=true;
      }
      if(any) continue;          // 번호 줄은 이름·회사 후보가 아님
      if(used&&t.replaceAll("[^가-힣A-Za-z]","").length()<2) continue;
      if(URL.matcher(t).find()) continue;
      rest.add(new Line(t,l.h));
    }
    if(!c.mobiles.isEmpty()) c.mobile=c.mobiles.get(0);
    if(!c.tels.isEmpty()) c.tel=c.tels.get(0);

    // 이름 · 직함
    Line best=null; String bestName="", bestTitle="";
    for(Line l:rest){
      String t=stripLabel(l.t,"(성명|이름|name)");
      if(ADDRESS.matcher(t+" ").find()&&t.length()>6) continue;
      if(COMPANY.matcher(TITLE_IN.matcher(t).replaceAll("")).find()) continue;
      String title="";
      Matcher tm=TITLE_IN.matcher(t);
      String noTitle=t;
      if(tm.find()){ title=t.substring(tm.start()).trim(); noTitle=(t.substring(0,tm.start())).trim();
        // 「영업부 과장 홍길동」처럼 직함이 앞에 오는 모양
        if(noTitle.isEmpty()||!candName(noTitle)){ String after=t.substring(tm.end()).trim(); if(candName(after)){ noTitle=after; title=t.substring(0,tm.end()).trim(); } }
      }
      String nm=joinSpacedHangul(noTitle);
      if(!candName(nm)) continue;
      if(best==null||l.h>best.h+0.5f){ best=l; bestName=nm; bestTitle=title; }
    }
    if(best==null){ // 한글 이름이 없으면 영어 이름
      for(Line l:rest){ String t=l.t.replaceAll("\\s+"," ").trim(); if(LATIN_NAME.matcher(t).matches()&&!COMPANY.matcher(t).find()){ if(best==null||l.h>best.h){ best=l; bestName=t; } } }
      if(best!=null) c.nameGuess=true;
    }
    c.name=bestName; c.title=bestTitle;
    if(c.title.isEmpty()){
      for(Line l:rest){ if(l==best) continue; String t=l.t.trim(); if(t.length()<=16&&TITLE_ONLY.matcher(t.replaceAll("\\s+"," ")).matches()){ c.title=t; break; } }
    }
    // 가게 · 회사
    Line co=null;
    for(Line l:rest){ if(l==best) continue; String t=stripLabel(l.t,"(상호|회사|업체|company)"); if(COMPANY.matcher(t).find()&&!(ADDRESS.matcher(t+" ").find()&&t.length()>14)){ if(co==null||l.h>co.h) co=l; } }
    if(co!=null) c.company=stripLabel(co.t,"(상호|회사|업체|company)");
    else {
      for(Line l:rest){ if(l==best) continue; String t=l.t.trim();
        if(t.equals(c.title)||TITLE_ONLY.matcher(t).matches()) continue;
        if(ADDRESS.matcher(t+" ").find()) continue;
        if(t.length()>20||t.replaceAll("[^가-힣A-Za-z]","").length()<2) continue;
        if(co==null||l.h>co.h) co=l; }
      if(co!=null){ c.company=co.t.trim(); c.companyGuess=true; }
    }
    return c;
  }
  static boolean candName(String s){ return HANGUL_NAME.matcher(s).matches(); }
  static String joinSpacedHangul(String s){
    String t=s.replaceAll("[·.,:]"," ").trim();
    String[] p=t.split("\\s+");
    boolean allOne=p.length>=2&&p.length<=4; for(String x:p) if(!x.matches("[가-힣]")) allOne=false;
    return allOne?String.join("",p):t.replaceAll("\\s+"," ");
  }
  static String stripLabel(String t,String lab){ return t.replaceFirst("(?i)^\\s*"+lab+"\\s*[:：.]?\\s*","").trim(); }
  static String label(String before){
    String b=before.length()>14?before.substring(before.length()-14):before;
    int fm=lastIdx(L_FAX,b), mm=lastIdx(L_MOBILE,b), tm=lastIdx(L_TEL,b);
    int max=Math.max(fm,Math.max(mm,tm)); if(max<0) return "";
    return max==fm?"fax":max==mm?"mobile":"tel";
  }
  private static int lastIdx(Pattern p,String s){ Matcher m=p.matcher(s); int i=-1; while(m.find()) i=m.start(); return i; }
}

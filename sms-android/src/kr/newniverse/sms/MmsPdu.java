package kr.newniverse.sms;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v4: 사진·장문(MMS/LMS) PDU 읽기·쓰기 (순수 자바, 안드로이드 없음).
 * OMA MMS Encapsulation 1.x + WAP WSP 인코딩 중 받기에 필요한 만큼만:
 *  - M-Notification.ind (0x82): 트랜잭션 ID, 내려받을 주소(Content-Location), 보낸 사람, 제목, 크기, 만료
 *  - M-Retrieve.conf (0x84): 보낸 사람·받는 사람·제목·날짜·Message-ID + multipart 본문(글·사진)
 *  - M-NotifyResp.ind (0x83) 만들기
 * 한국 LMS 글자: EUC-KR(38) · KS_C_5601(36) → CP949 로 읽음 · UTF-8(106) · UCS-2(1000) · UTF-16(1015) · US-ASCII(3) · ISO-8859-1(4).
 * 모르는 머리말은 WSP 일반 규칙으로 건너뛴다. 망가진 PDU 에서도 예외 대신 null/부분 결과.
 */
final class MmsPdu {
  // X-Mms-Message-Type
  static final int NOTIFICATION_IND=0x82, NOTIFYRESP_IND=0x83, RETRIEVE_CONF=0x84, ACKNOWLEDGE_IND=0x85, DELIVERY_IND=0x86;
  // 머리말 번호 (0x80 을 뺀 값)
  static final int H_BCC=0x01,H_CC=0x02,H_CONTENT_LOCATION=0x03,H_CONTENT_TYPE=0x04,H_DATE=0x05,H_DELIVERY_REPORT=0x06,H_EXPIRY=0x08,H_FROM=0x09,
    H_MESSAGE_CLASS=0x0A,H_MESSAGE_ID=0x0B,H_MESSAGE_TYPE=0x0C,H_VERSION=0x0D,H_MESSAGE_SIZE=0x0E,H_PRIORITY=0x0F,H_READ_REPORT=0x10,H_REPORT_ALLOWED=0x11,
    H_STATUS=0x15,H_SUBJECT=0x16,H_TO=0x17,H_TRANSACTION_ID=0x18,H_RETRIEVE_STATUS=0x19,H_RETRIEVE_TEXT=0x1A;
  static final int STATUS_RETRIEVED=0x81, STATUS_DEFERRED=0x83;
  static final int CS_UTF8=106, CS_EUCKR=38, CS_KSC5601=36, CS_ASCII=3, CS_LATIN1=4, CS_UCS2=1000, CS_UTF16=1015;

  static final String[] WK_TYPES={
    "*/*","text/*","text/html","text/plain","text/x-hdml","text/x-ttml","text/x-vCalendar","text/x-vCard",
    "text/vnd.wap.wml","text/vnd.wap.wmlscript","text/vnd.wap.wta-event","multipart/*","multipart/mixed","multipart/form-data","multipart/byterantes","multipart/alternative",
    "application/*","application/java-vm","application/x-www-form-urlencoded","application/x-hdmlc","application/vnd.wap.wmlc","application/vnd.wap.wmlscriptc","application/vnd.wap.wta-eventc","application/vnd.wap.uaprof",
    "application/vnd.wap.wtls-ca-certificate","application/vnd.wap.wtls-user-certificate","application/x-x509-ca-cert","application/x-x509-user-cert","image/*","image/gif","image/jpeg","image/tiff",
    "image/png","image/vnd.wap.wbmp","application/vnd.wap.multipart.*","application/vnd.wap.multipart.mixed","application/vnd.wap.multipart.form-data","application/vnd.wap.multipart.byteranges","application/vnd.wap.multipart.alternative","application/xml",
    "text/xml","application/vnd.wap.wbxml","application/x-x968-cross-cert","application/x-x968-ca-cert","application/x-x968-user-cert","text/vnd.wap.si","application/vnd.wap.sic","text/vnd.wap.sl",
    "application/vnd.wap.slc","text/vnd.wap.co","application/vnd.wap.coc","application/vnd.wap.multipart.related","application/vnd.wap.sia","text/vnd.wap.connectivity-xml","application/vnd.wap.connectivity-wbxml","application/pkcs7-mime",
    "application/vnd.wap.hashed-certificate","application/vnd.wap.signed-certificate","application/vnd.wap.cert-response","application/xhtml+xml","application/wml+xml","text/css","application/vnd.wap.mms-message"
  };

  static final class Part {
    String ct="application/octet-stream", name="", filename="", cid="", cl="";
    int charset=0; byte[] data=new byte[0];
    boolean isText(){ return "text/plain".equalsIgnoreCase(ct); }
    boolean isSmil(){ return "application/smil".equalsIgnoreCase(ct); }
    boolean isImage(){ return ct.toLowerCase().startsWith("image/"); }
    String text(){ return decode(data,charset); }
  }

  /** 글자셋이 붙은 문자열(Encoded-string-value): 원래 바이트 + MIBenum */
  static final class Enc { final int charset; final byte[] raw; Enc(int c,byte[] r){ charset=c; raw=r; } String str(){ return decode(raw,charset); } }

  static final class Pdu {
    int type=-1, version=0, priority=0, retrieveStatus=0, status=0;
    String transactionId="", contentLocation="", messageId="", messageClass="", contentType="";
    Enc from=null, subject=null;
    final List<Enc> to=new ArrayList<>(), cc=new ArrayList<>();
    long date=0, size=0, expiry=0; boolean expiryRelative=false;
    final List<Part> parts=new ArrayList<>();
    String fromStr(){ return from==null?"":cleanAddr(from.str()); }
    String subjectStr(){ return subject==null?"":subject.str(); }
    /** 글 부분을 순서대로 이어 붙임 (SMIL 제외) */
    String text(){ StringBuilder sb=new StringBuilder(); for(Part p:parts) if(p.isText()){ String t=p.text(); if(t.isEmpty()) continue; if(sb.length()>0) sb.append('\n'); sb.append(t); } return sb.toString(); }
    int imageCount(){ int n=0; for(Part p:parts) if(p.isImage()) n++; return n; }
  }

  /** "01012345678/TYPE=PLMN" → "01012345678" */
  static String cleanAddr(String a){
    if(a==null) return ""; String s=a.trim(); int i=s.toUpperCase().indexOf("/TYPE="); if(i>=0) s=s.substring(0,i);
    return s.trim();
  }

  static Charset charsetOf(int mib){
    String[] names;
    switch(mib){
      case CS_UTF8: return StandardCharsets.UTF_8;
      case CS_ASCII: return StandardCharsets.US_ASCII;
      case CS_LATIN1: return StandardCharsets.ISO_8859_1;
      case CS_EUCKR: case CS_KSC5601: names=new String[]{"x-windows-949","MS949","windows-949","cp949","EUC-KR"}; break;
      case CS_UCS2: names=new String[]{"UTF-16BE"}; break;
      case CS_UTF16: return StandardCharsets.UTF_16;
      case 2026: names=new String[]{"Big5"}; break;
      case 17: names=new String[]{"Shift_JIS"}; break;
      case 39: names=new String[]{"ISO-2022-KR"}; break;
      case 113: names=new String[]{"GBK"}; break;
      case 2025: names=new String[]{"GB2312"}; break;
      default: return null;
    }
    for(String n:names){ try{ if(Charset.isSupported(n)) return Charset.forName(n); }catch(Exception e){} }
    return null;
  }

  /** 글자셋을 모르면(0) UTF-8로 읽어 보고, 깨지면 EUC-KR(CP949)로 */
  static String decode(byte[] b,int mib){
    if(b==null||b.length==0) return "";
    int n=b.length; while(n>0&&b[n-1]==0) n--;
    Charset cs=charsetOf(mib);
    if(cs!=null) return stripBom(new String(b,0,n,cs));
    if(validUtf8(b,n)) return stripBom(new String(b,0,n,StandardCharsets.UTF_8));
    Charset kr=charsetOf(CS_EUCKR);
    return new String(b,0,n,kr!=null?kr:StandardCharsets.ISO_8859_1);
  }
  static String stripBom(String s){ return s.startsWith("\uFEFF")?s.substring(1):s; }
  static boolean validUtf8(byte[] b,int n){
    int i=0;
    while(i<n){
      int c=b[i]&0xff, k;
      if(c<0x80) k=0; else if((c>>5)==6) k=1; else if((c>>4)==14) k=2; else if((c>>3)==30) k=3; else return false;
      if(i+k>=n&&k>0) return false;
      for(int j=1;j<=k;j++) if(((b[i+j]&0xff)>>6)!=2) return false;
      i+=k+1;
    }
    return true;
  }

  // ---------------- 읽기 ----------------
  static final class R {
    final byte[] b; int p; final int end;
    R(byte[] b,int p,int end){ this.b=b; this.p=p; this.end=Math.min(end,b.length); }
    boolean more(){ return p<end; }
    int peek(){ if(p>=end) throw new IllegalStateException("eof"); return b[p]&0xff; }
    int u8(){ if(p>=end) throw new IllegalStateException("eof"); return b[p++]&0xff; }
    long uintvar(){ long v=0; for(int i=0;i<5;i++){ int c=u8(); v=(v<<7)|(c&0x7f); if((c&0x80)==0) return v; } throw new IllegalStateException("uintvar"); }
    /** Value-length: 0..30 짧은 길이, 31 + uintvar */
    int valueLength(){ int c=u8(); if(c<31) return c; if(c==31) return (int)uintvar(); throw new IllegalStateException("vlen"); }
    long longInt(){ int n=u8(); if(n>8) throw new IllegalStateException("long"); long v=0; for(int i=0;i<n;i++) v=(v<<8)|u8(); return v; }
    /** Integer-value: short-integer(0x80|v) 또는 long-integer */
    long intValue(){ int c=peek(); if((c&0x80)!=0){ p++; return c&0x7f; } return longInt(); }
    /** Text-string (따옴표 0x7F 건너뜀), NUL 까지 */
    byte[] textBytes(){ if(p<end&&(b[p]&0xff)==0x7f) p++; int s=p; while(p<end&&b[p]!=0) p++; byte[] o=Arrays.copyOfRange(b,s,p); if(p<end) p++; return o; }
    String text(){ return new String(textBytes(),StandardCharsets.ISO_8859_1); }
    String textUtf8(){ return decode(textBytes(),0); }
    /** Quoted-string (0x22 로 시작) 또는 text */
    String quoted(){ if(p<end&&(b[p]&0xff)==0x22) p++; return text(); }
    /** Encoded-string-value: Text-string | Value-length Char-set Text-string */
    Enc encString(){
      int c=peek();
      if(c<=31){ int len=valueLength(); int stop=Math.min(p+len,end); int cs=(int)intValue(); byte[] t=textBytes(); p=Math.max(p,stop); if(p>end) p=end; return new Enc(cs,t); }
      return new Enc(0,textBytes());
    }
    /** 모르는 값 건너뛰기 (WSP 일반 규칙) */
    void skipValue(){
      int c=peek();
      if(c<31){ p++; p+=c; }
      else if(c==31){ p++; int n=(int)uintvar(); p+=n; }
      else if(c<128){ textBytes(); }
      else p++;
      if(p>end) p=end;
    }
    byte[] bytes(int n){ if(n<0||p+n>end) throw new IllegalStateException("short"); byte[] o=Arrays.copyOfRange(b,p,p+n); p+=n; return o; }
  }

  /** Content-type 값 읽기 → 종류. charset·name·filename 은 into 에 채움 */
  static String contentType(R r,Part into){
    int c=r.peek();
    if(c>=0x80){ r.u8(); return wk(c&0x7f); }
    if(c>=32&&c<128){ return r.text().toLowerCase(); }
    int len=r.valueLength(); int stop=Math.min(r.p+len,r.end);
    String type;
    int d=r.peek();
    if(d>=0x80){ r.u8(); type=wk(d&0x7f); }
    else if(d<31){ long v=r.longInt(); type=wk((int)v); }
    else type=r.text().toLowerCase();
    while(r.p<stop){
      int t=r.peek();
      try{
        if(t>=0x80){
          r.u8(); int tok=t&0x7f;
          switch(tok){
            case 0x01: { int cs=(int)r.intValue(); if(into!=null) into.charset=cs; break; } // Charset (0x80 = 아무거나)
            case 0x05: case 0x17: { String n=r.textUtf8(); if(into!=null) into.name=n; break; } // Name
            case 0x06: case 0x18: { String n=r.textUtf8(); if(into!=null) into.filename=n; break; } // Filename
            default: r.skipValue(); // Type / Start / Start-info / Level ...
          }
        } else if(t>=32&&t<128){
          String name=r.text().toLowerCase();
          if(r.p>=stop) break;
          int v=r.peek();
          if(v>=0x80||v<31){ long iv=r.intValue(); if(name.equals("charset")&&into!=null) into.charset=(int)iv; }
          else { String sv=r.textUtf8(); if(into!=null){ if(name.equals("charset")) into.charset=mibOf(sv); else if(name.equals("name")) into.name=sv; else if(name.equals("filename")) into.filename=sv; } }
        } else r.skipValue();
      }catch(IllegalStateException e){ break; }
    }
    r.p=stop;
    return type;
  }
  static int mibOf(String n){
    String s=n==null?"":n.trim().toLowerCase();
    switch(s){ case "utf-8": case "utf8": return CS_UTF8; case "euc-kr": case "euckr": case "cp949": case "ms949": return CS_EUCKR; case "ks_c_5601-1987": return CS_KSC5601;
      case "us-ascii": return CS_ASCII; case "iso-8859-1": return CS_LATIN1; case "utf-16": return CS_UTF16; case "iso-10646-ucs-2": return CS_UCS2; default: return 0; }
  }
  static String wk(int v){ return v>=0&&v<WK_TYPES.length?WK_TYPES[v]:"application/octet-stream"; }

  /** 전체 PDU 읽기. 메시지 종류조차 못 읽으면 null */
  static Pdu parse(byte[] data){
    if(data==null||data.length<2) return null;
    Pdu d=new Pdu(); R r=new R(data,0,data.length);
    try{
      boolean ctSeen=false;
      while(r.more()&&!ctSeen){
        int h=r.u8();
        if(h<0x80){ // 텍스트 머리말: 이름\0 값\0
          r.p--; r.text(); if(r.more()) r.text(); continue; }
        int f=h&0x7f;
        switch(f){
          case H_MESSAGE_TYPE: d.type=r.u8(); break;
          case H_TRANSACTION_ID: d.transactionId=r.text(); break;
          case H_VERSION: d.version=(int)r.intValue(); break;
          case H_CONTENT_LOCATION: d.contentLocation=r.text(); break;
          case H_MESSAGE_ID: d.messageId=r.text(); break;
          case H_DATE: d.date=r.longInt(); break;
          case H_MESSAGE_SIZE: d.size=r.longInt(); break;
          case H_PRIORITY: d.priority=r.u8(); break;
          case H_RETRIEVE_STATUS: d.retrieveStatus=r.u8(); break;
          case H_STATUS: d.status=r.u8(); break;
          case H_SUBJECT: d.subject=r.encString(); break;
          case H_TO: d.to.add(r.encString()); break;
          case H_CC: d.cc.add(r.encString()); break;
          case H_FROM: {
            int len=r.valueLength(); int stop=r.p+len; int tok=r.u8();
            if(tok==0x80&&r.p<stop) d.from=r.encString();
            r.p=Math.min(stop,r.end); break; }
          case H_MESSAGE_CLASS: { int c=r.peek(); if(c>=0x80){ r.u8(); d.messageClass=c==0x80?"personal":c==0x81?"advertisement":c==0x82?"informational":"auto"; } else d.messageClass=r.text(); break; }
          case H_EXPIRY: {
            int len=r.valueLength(); int stop=r.p+len; int tok=r.u8();
            d.expiryRelative=tok==0x81; try{ d.expiry=r.p<stop?r.intValue():0; }catch(Exception e){}
            r.p=Math.min(stop,r.end); break; }
          case H_CONTENT_TYPE: { d.contentType=contentType(r,new Part()); ctSeen=true; break; }
          default: r.skipValue();
        }
      }
      if(ctSeen&&r.more()){
        String ct=d.contentType.toLowerCase();
        if(ct.contains("multipart")) parseMultipart(r,d.parts);
        else { Part p=new Part(); p.ct=ct; p.data=r.bytes(r.end-r.p); d.parts.add(p); }
      }
    }catch(RuntimeException e){ /* 부분 결과 */ }
    return d.type<0?null:d;
  }

  static void parseMultipart(R r,List<Part> out){
    long n=r.uintvar();
    for(long i=0;i<n&&r.more();i++){
      int hl=(int)r.uintvar(); int dl=(int)r.uintvar();
      int hs=r.p, he=hs+hl;
      if(he>r.end) throw new IllegalStateException("part");
      Part p=new Part();
      R h=new R(r.b,hs,he);
      try{
        p.ct=contentType(h,p);
        while(h.more()){
          int c=h.u8();
          if(c>=0x80){
            int f=c&0x7f;
            if(f==0x0E) p.cl=h.textUtf8();          // Content-Location
            else if(f==0x40) p.cid=strip(h.quoted()); // Content-ID
            else h.skipValue();                      // Content-Disposition 등
          } else if(c>=32){ h.p--; String name=h.text().toLowerCase(); String v=h.more()?h.textUtf8():"";
            if(name.equals("content-id")) p.cid=strip(v); else if(name.equals("content-location")) p.cl=v; }
          else { h.p--; h.skipValue(); }
        }
      }catch(IllegalStateException e){}
      r.p=he;
      p.data=r.bytes(Math.min(dl,r.end-r.p));
      out.add(p);
    }
  }
  static String strip(String s){ if(s==null) return ""; String t=s.trim(); if(t.startsWith("<")&&t.endsWith(">")) t=t.substring(1,t.length()-1); return t; }

  // ---------------- 쓰기 ----------------
  /** M-NotifyResp.ind: 받았어요(0x81) / 나중에(0x83) */
  static byte[] notifyResp(String transactionId,int status){
    ByteArrayOutputStream o=new ByteArrayOutputStream();
    o.write(0x80|H_MESSAGE_TYPE); o.write(NOTIFYRESP_IND);
    o.write(0x80|H_TRANSACTION_ID); writeText(o,transactionId);
    o.write(0x80|H_VERSION); o.write(0x92); // 1.2
    o.write(0x80|H_STATUS); o.write(status);
    o.write(0x80|H_REPORT_ALLOWED); o.write(0x81); // 읽음 보고 보내지 않음
    return o.toByteArray();
  }
  static void writeText(ByteArrayOutputStream o,String s){
    byte[] b=(s==null?"":s).getBytes(StandardCharsets.ISO_8859_1);
    if(b.length>0&&(b[0]&0xff)>=0x80) o.write(0x7f);
    o.write(b,0,b.length); o.write(0);
  }
}

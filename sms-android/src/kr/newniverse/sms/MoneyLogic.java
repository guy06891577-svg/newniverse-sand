package kr.newniverse.sms;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** v10: 입금 요약(오늘·어제·이번 주)·나갈 돈 목록·금액 말투 (순수 자바, 한국 시간 기준) */
final class MoneyLogic {
  static final ZoneId KST=DepositParser.KST;
  static final String NOTE="빠른 확인용이에요. 정확한 내역은 은행 앱에서 확인해 주세요.";

  static final class Item { String bank,who; long amt,at; boolean mobile; String key; }
  static final class Bucket { long sum; int n; List<Item> items=new ArrayList<>(); }
  static final class Sum { Bucket today=new Bucket(), yesterday=new Bucket(), week=new Bucket(); int suspect; long now; }

  static LocalDate day(long ms){ return Instant.ofEpochMilli(ms).atZone(KST).toLocalDate(); }
  static LocalDate weekStart(LocalDate d){ return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); }

  /** 같은 문자 두 통(2분 안·같은 은행·금액·이름) 하나로. items 는 최신순 */
  static List<Item> dedupe(List<Item> in){
    List<Item> s=new ArrayList<>(in); s.sort((a,b)->Long.compare(b.at,a.at)); List<Item> out=new ArrayList<>();
    for(Item x:s){ boolean dup=false; for(Item y:out) if(y.amt==x.amt&&y.bank.equals(x.bank)&&y.who.equals(x.who)&&Math.abs(y.at-x.at)<=120_000L){ dup=true; break; } if(!dup) out.add(x); }
    return out;
  }
  static Sum summarize(List<Item> in,long now,int suspect){
    Sum s=new Sum(); s.now=now; s.suspect=suspect;
    LocalDate t=day(now), y=t.minusDays(1), ws=weekStart(t);
    for(Item it:dedupe(in)){
      if(it.at>now+36L*3600_000L) continue;
      LocalDate d=day(it.at);
      if(d.equals(t)) add(s.today,it);
      if(d.equals(y)) add(s.yesterday,it);
      if(!d.isBefore(ws)&&!d.isAfter(t)) add(s.week,it);
    }
    return s;
  }
  static void add(Bucket b,Item it){ b.sum+=it.amt; b.n++; b.items.add(it); }

  // ---- 나갈 돈 ----
  static final class BillItem { String org,cat,kind,due; long amt; }
  static List<BillItem> upcoming(List<BillItem> in,long now,int maxDays){
    LocalDate t=day(now); List<BillItem> out=new ArrayList<>(); Set<String> seen=new HashSet<>();
    for(BillItem b:in){ LocalDate d; try{ d=LocalDate.parse(b.due); }catch(Exception e){ continue; }
      long diff=java.time.temporal.ChronoUnit.DAYS.between(t,d); if(diff<0||diff>maxDays) continue;
      String k=b.org+"|"+b.due+"|"+b.amt; if(!seen.add(k)) continue; out.add(b); }
    out.sort((a,b)->{ int c=a.due.compareTo(b.due); return c!=0?c:Long.compare(b.amt,a.amt); });
    return out;
  }
  static List<BillItem> tomorrow(List<BillItem> up,long now){ String tm=day(now).plusDays(1).toString(); List<BillItem> o=new ArrayList<>(); for(BillItem b:up) if(b.due.equals(tm)) o.add(b); return o; }
  /** 알림 시각: 출금 하루 전 오전 9시(한국). 이미 지났으면 -1 */
  static long alertAt(String due,long now){ try{ long t=LocalDate.parse(due).minusDays(1).atTime(9,0).atZone(KST).toInstant().toEpochMilli(); return t>now?t:-1; }catch(Exception e){ return -1; } }
  static String dueLabel(String due,long now){
    try{ LocalDate d=LocalDate.parse(due),t=day(now); long diff=java.time.temporal.ChronoUnit.DAYS.between(t,d);
      if(diff==0) return "오늘"; if(diff==1) return "내일"; if(diff==2) return "모레";
      return d.getMonthValue()+"월 "+d.getDayOfMonth()+"일("+"일월화수목금토".charAt(d.getDayOfWeek().getValue()%7)+")"; }catch(Exception e){ return due; } }
  static String kindLabel(String k){ return "auto".equals(k)?"자동이체 예정":"card".equals(k)?"카드 결제 예정":"납기일"; }

  // ---- 금액 말투 ----
  /** 350000 → 35만 원 · 1234567 → 123만 4,567원 · 9500 → 9,500원 · 100000000 → 1억 원 */
  static String won(long v){
    if(v<0) return "-"+won(-v);
    if(v<10_000) return String.format(Locale.ROOT,"%,d원",v);
    long eok=v/100_000_000L, man=(v%100_000_000L)/10_000L, rest=v%10_000L; StringBuilder sb=new StringBuilder();
    if(eok>0) sb.append(String.format(Locale.ROOT,"%,d억",eok));
    if(man>0){ if(sb.length()>0) sb.append(' '); sb.append(String.format(Locale.ROOT,"%,d만",man)); }
    if(rest>0){ sb.append(' ').append(String.format(Locale.ROOT,"%,d원",rest)); return sb.toString(); }
    return sb.append(" 원").toString();
  }
  static String full(long v){ return String.format(Locale.ROOT,"%,d원",v); }
  static String line(String when,Bucket b){ return b.n==0?when+" 입금 없음":when+" 입금 "+won(b.sum)+" · "+b.n+"건"; }
  /** 음성 ‘오늘 입금 얼마야’ 대답 */
  static String say(Bucket b,String when){
    if(b.n==0) return when+"은 아직 들어온 입금 문자가 없어요. 빠른 확인용이라 은행 앱도 한번 봐 주세요.";
    return when+" 입금은 "+won(b.sum)+", "+b.n+"건이에요. 빠른 확인용이라 정확한 내역은 은행 앱에서 봐 주세요.";
  }
  static String billLine(BillItem b,long now){ return dueLabel(b.due,now)+" 출금 예상 · "+b.org+" · "+won(b.amt); }
}

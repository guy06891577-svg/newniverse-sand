package kr.newniverse.sms;

import android.content.*;
import android.database.Cursor;
import android.provider.Telephony;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/**
 * v10: 받은 문자함에서 입금·출금 예정 문자를 그때그때 읽어 요약을 만든다 (따로 복사해 저장하지 않음, 폰 밖으로 안 나감).
 * 화면(입금 화면) · 런처(같은 서명 앱만, FeedProvider "money") · 음성 질문 ‘오늘 입금 얼마야’ 가 같은 JSON 을 씀.
 * 결과에는 은행·보낸 사람·금액·시각만 있고, 문자 본문과 계좌번호는 없다.
 */
final class MoneyFeed {
  static final String PREF="nv_money";
  static final int DEP_DAYS=9, BILL_DAYS=45, MAX_ROWS=3000;
  static boolean on(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean("on",true); }
  static void setOn(Context c,boolean v){ c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean("on",v).apply(); }

  static final class Data { MoneyLogic.Sum sum; List<MoneyLogic.BillItem> bills=new ArrayList<>(); int rows; String err=""; }

  static Data load(Context c,long now){
    Data d=new Data(); List<MoneyLogic.Item> deps=new ArrayList<>(); int suspect=0;
    long since=now-BILL_DAYS*86400_000L, depSince=now-DEP_DAYS*86400_000L;
    String sel=Telephony.Sms.DATE+">? AND (body LIKE ? OR body LIKE ? OR body LIKE ? OR body LIKE ?)";
    String[] args={String.valueOf(since),"%입금%","%보냈%","%출금%","%납%"};
    Cursor cur=null;
    try{
      String[] proj={Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE};
      try{ cur=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,proj,sel,args,Telephony.Sms.DATE+" DESC"); }
      catch(Exception e){ d.err=InboxLogic.errClass(e); cur=c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,proj,Telephony.Sms.DATE+">?",new String[]{String.valueOf(since)},Telephony.Sms.DATE+" DESC"); }
      List<MoneyLogic.BillItem> bs=new ArrayList<>();
      while(cur!=null&&cur.moveToNext()&&d.rows++<MAX_ROWS){
        String addr=cur.getString(0), body=cur.getString(1); long date=cur.getLong(2);
        if(body==null||date<=since) continue;
        if(date>=depSince){
          DepositParser.Res r=DepositParser.parse(addr,body,date);
          if(r.ok){ MoneyLogic.Item it=new MoneyLogic.Item(); it.bank=r.dep.bank; it.who=r.dep.who; it.amt=r.dep.amount; it.at=r.dep.at; it.mobile=r.dep.mobile; deps.add(it); continue; }
          if(r.suspect) suspect++;
        }
        BillParser.Res b=BillParser.parse(addr,body,date);
        if(b.ok){ MoneyLogic.BillItem bi=new MoneyLogic.BillItem(); bi.org=b.bill.org; bi.cat=b.bill.cat; bi.kind=b.bill.kind; bi.due=b.bill.due; bi.amt=b.bill.amt; bs.add(bi); }
      }
      d.bills=MoneyLogic.upcoming(bs,now,BILL_DAYS);
    }catch(Exception e){ d.err=InboxLogic.errClass(e); }
    finally{ if(cur!=null) try{ cur.close(); }catch(Exception e){} }
    // 오늘 입금 요약은 ‘입금 문자 시각’ 기준(문자를 받은 날과 다를 수 있음)이라 9일 안 문자만 모아도 이번 주를 다 덮음
    d.sum=MoneyLogic.summarize(deps,now,suspect);
    return d;
  }

  static JSONObject bucket(MoneyLogic.Bucket b,String when) throws Exception {
    JSONObject o=new JSONObject(); o.put("sum",b.sum); o.put("n",b.n); o.put("text",b.n==0?"없음":MoneyLogic.won(b.sum)); o.put("line",MoneyLogic.line(when,b)); o.put("say",MoneyLogic.say(b,when));
    JSONArray a=new JSONArray(); int k=0;
    for(MoneyLogic.Item it:b.items){ if(k++>=100) break; JSONObject x=new JSONObject(); x.put("at",it.at); x.put("bank",it.bank); x.put("who",it.who); x.put("amt",it.amt); x.put("t",MoneyLogic.full(it.amt)); a.put(x); }
    o.put("items",a); return o;
  }

  static JSONObject json(Context c,long now){
    JSONObject o=new JSONObject();
    try{
      boolean perm=Nv.has(c,android.Manifest.permission.READ_SMS);
      o.put("ver",1); o.put("ok",true); o.put("perm",perm); o.put("on",on(c)); o.put("asof",now); o.put("note",MoneyLogic.NOTE);
      if(!perm||!on(c)){ o.put("empty",true); return o; }
      Data d=load(c,now);
      o.put("today",bucket(d.sum.today,"오늘")); o.put("yesterday",bucket(d.sum.yesterday,"어제")); o.put("week",bucket(d.sum.week,"이번 주"));
      o.put("line",MoneyLogic.line("오늘",d.sum.today)); o.put("say",MoneyLogic.say(d.sum.today,"오늘")); o.put("suspect",d.sum.suspect); o.put("err",d.err);
      JSONArray bs=new JSONArray(), tm=new JSONArray(); List<MoneyLogic.BillItem> t=MoneyLogic.tomorrow(d.bills,now);
      for(MoneyLogic.BillItem b:d.bills){ bs.put(billJson(b,now)); }
      for(MoneyLogic.BillItem b:t) tm.put(billJson(b,now));
      o.put("bills",bs); o.put("tomorrow",tm);
      o.put("billLine",t.isEmpty()?"":MoneyLogic.billLine(t.get(0),now)+(t.size()>1?" 외 "+(t.size()-1)+"건":""));
      long out=0; for(MoneyLogic.BillItem b:d.bills) out+=b.amt; o.put("billSum",out);
    }catch(Exception e){ try{ o.put("ok",false); o.put("err",InboxLogic.errClass(e)); }catch(Exception x){} }
    return o;
  }
  static JSONObject billJson(MoneyLogic.BillItem b,long now) throws Exception {
    JSONObject x=new JSONObject(); x.put("org",b.org); x.put("cat",b.cat); x.put("kind",b.kind); x.put("kindLabel",MoneyLogic.kindLabel(b.kind)); x.put("due",b.due); x.put("when",MoneyLogic.dueLabel(b.due,now)); x.put("amt",b.amt); x.put("t",MoneyLogic.won(b.amt)); x.put("line",MoneyLogic.billLine(b,now)); return x;
  }
}

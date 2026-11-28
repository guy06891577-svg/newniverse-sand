package kr.newniverse.phone;

import android.content.*;
import org.json.*;
import java.util.*;

/** v6 통화 메모: 번호에 붙여 폰 안(앱 전용 저장소)에만 저장. 인터넷으로 나가지 않아요. */
final class MemoStore {
  private MemoStore(){}
  static final String PREF="nvp_memos";
  static final int MAX=3000;
  static volatile java.util.function.LongSupplier clock=null;
  static long now(){ java.util.function.LongSupplier c=clock; return c!=null?c.getAsLong():System.currentTimeMillis(); }

  private static SharedPreferences p(Context c){ return c.getSharedPreferences(PREF,0); }
  static synchronized JSONArray all(Context c){ try{ return new JSONArray(p(c).getString("list","[]")); }catch(Exception e){ return new JSONArray(); } }
  private static void put(Context c,JSONArray a){ p(c).edit().putString("list",a.toString()).apply(); }

  /** 새 메모. src = "voice" | "text". 빈 글이면 null */
  static synchronized JSONObject add(Context c,String num,String name,String text,String src,long durMs){
    String t=MemoRules.clean(text), k=ModeRules.key(num);
    if(t.isEmpty()||k.isEmpty()) return null;
    try{
      JSONArray a=all(c); long id=Math.max(now(),p(c).getLong("lastId",0)+1); p(c).edit().putLong("lastId",id).apply();
      JSONObject o=new JSONObject(); o.put("id",id); o.put("k",k); o.put("num",num==null?"":num); o.put("name",name==null?"":name);
      o.put("text",t); o.put("at",now()); o.put("dur",Math.max(0,durMs)); o.put("src","voice".equals(src)?"voice":"text");
      a.put(o);
      if(a.length()>MAX){ JSONArray n=new JSONArray(); for(int i=a.length()-MAX;i<a.length();i++) n.put(a.get(i)); a=n; }
      put(c,a); return o;
    }catch(Exception e){ return null; }
  }
  static synchronized boolean edit(Context c,long id,String text){
    String t=MemoRules.clean(text); if(t.isEmpty()) return false;
    JSONArray a=all(c);
    for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o!=null&&o.optLong("id")==id){ try{ o.put("text",t); o.put("edited",now()); }catch(Exception e){} put(c,a); return true; } }
    return false;
  }
  /** 지우고, 되돌리기용으로 지운 것을 돌려줌 */
  static synchronized JSONObject del(Context c,long id){
    JSONArray a=all(c), n=new JSONArray(); JSONObject gone=null;
    for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o==null) continue; if(gone==null&&o.optLong("id")==id) gone=o; else n.put(o); }
    if(gone!=null) put(c,n);
    return gone;
  }
  static synchronized boolean restore(Context c,JSONObject o){
    if(o==null||o.optLong("id")==0||o.optString("k").isEmpty()) return false;
    JSONArray a=all(c);
    for(int i=0;i<a.length();i++){ JSONObject x=a.optJSONObject(i); if(x!=null&&x.optLong("id")==o.optLong("id")) return false; }
    a.put(o); put(c,a); return true;
  }
  /** 이 번호의 메모 (최신 먼저) */
  static List<JSONObject> forNum(Context c,String num){
    String k=ModeRules.key(num); List<JSONObject> l=new ArrayList<>(); if(k.isEmpty()) return l;
    JSONArray a=all(c); for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o!=null&&k.equals(o.optString("k"))) l.add(o); }
    l.sort((x,y)->Long.compare(y.optLong("at"),x.optLong("at")));
    return l;
  }
  static JSONObject latest(Context c,String num){ List<JSONObject> l=forNum(c,num); return l.isEmpty()?null:l.get(0); }
  /** 메모가 있는 사람 목록: {k,num,name,n,last,text} 최근 메모 순 */
  static JSONArray people(Context c){
    Map<String,JSONObject> m=new LinkedHashMap<>(); JSONArray a=all(c);
    try{
      for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i); if(o==null) continue; String k=o.optString("k");
        JSONObject g=m.get(k); if(g==null){ g=new JSONObject(); g.put("k",k); g.put("n",0); g.put("last",0L); m.put(k,g); }
        g.put("n",g.optInt("n")+1);
        if(o.optLong("at")>=g.optLong("last")){ g.put("last",o.optLong("at")); g.put("num",o.optString("num")); g.put("text",o.optString("text")); if(!o.optString("name").isEmpty()) g.put("name",o.optString("name")); }
        else if(!g.has("name")&&!o.optString("name").isEmpty()) g.put("name",o.optString("name"));
      }
    }catch(Exception e){}
    List<JSONObject> l=new ArrayList<>(m.values()); l.sort((x,y)->Long.compare(y.optLong("last"),x.optLong("last")));
    JSONArray out=new JSONArray(); for(JSONObject o:l) out.put(o); return out;
  }
}

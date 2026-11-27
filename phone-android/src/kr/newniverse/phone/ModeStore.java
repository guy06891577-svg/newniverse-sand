package kr.newniverse.phone;

import android.content.*;
import org.json.*;

/** 상황 모드 설정·진행 상태·끝난 뒤 요약을 폰 안(SharedPreferences)에만 저장. 인터넷으로 나가지 않아요. */
final class ModeStore {
  private ModeStore(){}
  static final String PREF="nvp_modes";
  static final int LOG_MAX=120;

  static SharedPreferences p(Context c){ return c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }
  private static JSONObject obj(String s){ try{ return s==null||s.isEmpty()?null:new JSONObject(s); }catch(Exception e){ return null; } }

  // ---------- 설정 ----------
  static JSONObject defaults(String id){
    JSONObject o=new JSONObject();
    try{
      o.put("text",ModeDefs.defaultText(id)); o.put("allow",ModeDefs.ALLOW_NONE); o.put("repeat",false);
      o.put("reply",true); o.put("other",false); o.put("contactsOnly",false); o.put("texts",true); o.put("timer",0);
    }catch(Exception e){}
    return o;
  }
  /** 저장된 값을 기본값 위에 덮어서, 항상 빠진 칸 없이 */
  static JSONObject cfg(Context c,String id){
    JSONObject d=defaults(id), s=obj(p(c).getString("cfg_"+id,null));
    if(s==null) return d;
    return sanitize(id,s);
  }
  /** 화면에서 온 값을 검사해서 정리 */
  static JSONObject sanitize(String id,JSONObject in){
    JSONObject d=defaults(id);
    try{
      String t=ModeDefs.cleanText(in.optString("text",d.getString("text")));
      d.put("text",t.isEmpty()?(in.has("text")?"":ModeDefs.defaultText(id)):t);
      d.put("allow",ModeDefs.allow(in.optString("allow","none")));
      d.put("repeat",in.optBoolean("repeat",false)); d.put("reply",in.optBoolean("reply",true));
      d.put("other",in.optBoolean("other",false)); d.put("contactsOnly",in.optBoolean("contactsOnly",false));
      d.put("texts",in.optBoolean("texts",true));
      d.put("timer",ModeDefs.timers(id)?ModeDefs.timer(in.optInt("timer",0)):0);
    }catch(Exception e){}
    return d;
  }
  static JSONObject saveCfg(Context c,String id,JSONObject in){
    JSONObject s=sanitize(id,in);
    p(c).edit().putString("cfg_"+id,s.toString()).apply();
    return s;
  }

  // ---------- 진행 중 ----------
  static JSONObject active(Context c){ JSONObject a=obj(p(c).getString("active",null)); return a!=null&&ModeDefs.valid(a.optString("id"))?a:null; }
  static void putActive(Context c,JSONObject a){ p(c).edit().putString("active",a.toString()).apply(); }
  static void clearActive(Context c){ p(c).edit().remove("active").apply(); }

  // ---------- 끝난 뒤 요약(확인 누를 때까지 남김) ----------
  static JSONObject summary(Context c){ return obj(p(c).getString("summary",null)); }
  static void putSummary(Context c,JSONObject s){ p(c).edit().putString("summary",s.toString()).apply(); }
  static void clearSummary(Context c){ p(c).edit().remove("summary").apply(); }

  /** 보낸 답장 결과(문자 보내기 성공/실패)를 진행 중·요약 양쪽에 반영 */
  static synchronized void markSent(Context c,String key,boolean ok){
    JSONObject a=active(c);
    if(a!=null&&setStatus(a.optJSONArray("log"),key,ok)) putActive(c,a);
    JSONObject s=summary(c);
    if(s!=null&&setStatus(s.optJSONArray("log"),key,ok)) putSummary(c,s);
  }
  private static boolean setStatus(JSONArray log,String key,boolean ok){
    if(log==null) return false;
    boolean ch=false;
    for(int i=0;i<log.length();i++){ JSONObject e=log.optJSONObject(i);
      if(e!=null&&key.equals(e.optString("k"))){ String cur=e.optString("st"); if(ok&&"wait".equals(cur)){ try{ e.put("st","ok"); }catch(Exception x){} ch=true; } else if(!ok&&!"fail".equals(cur)){ try{ e.put("st","fail"); }catch(Exception x){} ch=true; } } }
    return ch;
  }
}

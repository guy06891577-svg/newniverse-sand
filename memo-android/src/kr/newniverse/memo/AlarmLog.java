package kr.newniverse.memo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** 최근에 맞춘 알림 10개(진단용). 설정 화면의 「알림 기록」에서 보여 줘요. */
final class AlarmLog {
  static final int MAX=10;
  static SharedPreferences sp(Context c){ return c.getSharedPreferences("memo",Context.MODE_PRIVATE); }
  static JSONArray list(Context c){
    try{ String s=sp(c).getString("alarmLog","[]"); return s==null||s.isEmpty()?new JSONArray():new JSONArray(s); }
    catch(Exception e){ return new JSONArray(); }
  }
  static void add(Context c,Note n,TimePhrase.Hit h,int code,String method,boolean notif,boolean exact){
    try{
      boolean ok=code==Reminders.OK_EXACT||code==Reminders.OK_INEXACT;
      long when=h!=null?h.when:n.remindAt;
      String text=n.text==null?"":n.text; if(text.length()>80) text=text.substring(0,80);
      JSONObject e=new JSONObject()
        .put("id",n.id).put("text",text).put("when",when).put("method",method==null?"":method)
        .put("code",code).put("ok",ok).put("notif",notif).put("exact",exact)
        .put("fired",false).put("at",System.currentTimeMillis());
      JSONArray cur=list(c), next=new JSONArray(); next.put(e);
      for(int i=0;i<cur.length()&&next.length()<MAX;i++) next.put(cur.get(i));
      sp(c).edit().putString("alarmLog",next.toString()).apply();
    }catch(Exception e){}
  }
  static void markFired(Context c,long id,boolean posted){
    try{
      JSONArray cur=list(c), next=new JSONArray();
      for(int i=0;i<cur.length();i++){
        JSONObject e=cur.getJSONObject(i);
        if(e.optLong("id")==id&&!e.optBoolean("fired")){
          e.put("fired",true).put("posted",posted).put("firedAt",System.currentTimeMillis());
        }
        next.put(e);
      }
      sp(c).edit().putString("alarmLog",next.toString()).apply();
    }catch(Exception e){}
  }
}

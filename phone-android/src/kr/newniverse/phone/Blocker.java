package kr.newniverse.phone;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.telephony.TelephonyManager;
import org.json.*;
import java.util.*;

/** v6 차단 목록: 안드로이드 공용 차단 목록(BlockedNumberContract). 기본 전화 앱(또는 기본 문자 앱)만 읽고 쓸 수 있어요.
 *  이 목록은 폰 전체가 같이 써서, 삼성 전화·메시지 앱의 차단 목록과도 같아요. */
final class Blocker {
  private Blocker(){}
  static final Set<String> EMERGENCY=new HashSet<>(Arrays.asList("112","119","122","110","111","113","117","118","125","127","128","129","182","911","999","1332"));

  static boolean can(Context c){
    if(!Nv.isDefault(c)) return false;
    try{ return BlockedNumberContract.canCurrentUserBlockNumbers(c); }catch(Exception e){ return false; }
  }
  static boolean emergency(Context c,String num){
    String d=num==null?"":num.replaceAll("[^0-9]","");
    if(EMERGENCY.contains(d)) return true;
    try{ TelephonyManager tm=c.getSystemService(TelephonyManager.class); if(tm!=null&&android.os.Build.VERSION.SDK_INT>=29) return tm.isEmergencyNumber(num); }catch(Exception e){}
    return false;
  }
  /** [{id,num}] 최근에 넣은 것 먼저 */
  static JSONArray list(Context c){
    JSONArray a=new JSONArray(); if(!can(c)) return a;
    try(Cursor q=c.getContentResolver().query(BlockedNumbers.CONTENT_URI,new String[]{BlockedNumbers.COLUMN_ID,BlockedNumbers.COLUMN_ORIGINAL_NUMBER},null,null,BlockedNumbers.COLUMN_ID+" DESC")){
      while(q!=null&&q.moveToNext()){ JSONObject o=new JSONObject(); o.put("id",q.getLong(0)); o.put("num",q.getString(1)==null?"":q.getString(1)); a.put(o); }
    }catch(Exception e){}
    return a;
  }
  static Set<String> keys(Context c){
    Set<String> s=new HashSet<>(); JSONArray a=list(c);
    for(int i=0;i<a.length();i++){ String k=ModeRules.key(a.optJSONObject(i).optString("num")); if(!k.isEmpty()) s.add(k); }
    return s;
  }
  static boolean isBlocked(Context c,String num){ String k=ModeRules.key(num); return !k.isEmpty()&&keys(c).contains(k); }

  /** 반환: ok · already · role(기본 전화 앱 아님) · bad(번호 아님) · emergency · fail */
  static String block(Context c,String num){
    String k=ModeRules.key(num);
    if(k.isEmpty()) return "bad";
    if(emergency(c,num)) return "emergency";
    if(!can(c)) return "role";
    if(keys(c).contains(k)) return "already";
    try{ ContentValues v=new ContentValues(); v.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER,num.trim());
      Uri u=c.getContentResolver().insert(BlockedNumbers.CONTENT_URI,v); return u!=null?"ok":"fail"; }
    catch(Exception e){ return "fail"; }
  }
  /** 이 번호(모양이 달라도 같은 번호)를 모두 풀기. 반환: 푼 줄 수, 권한 없으면 -1 */
  static int unblock(Context c,String num){
    String k=ModeRules.key(num); if(k.isEmpty()) return 0;
    if(!can(c)) return -1;
    int n=0; JSONArray a=list(c);
    for(int i=0;i<a.length();i++){ JSONObject o=a.optJSONObject(i);
      if(k.equals(ModeRules.key(o.optString("num")))){ try{ n+=c.getContentResolver().delete(ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI,o.optLong("id")),null,null); }catch(Exception e){} } }
    return n;
  }
}

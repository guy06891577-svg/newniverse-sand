package kr.newniverse.sms;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import org.json.*;
import java.util.*;

/** v2: 「뉴니버스」 전용 로컬 달력(ACCOUNT_TYPE_LOCAL)에만 씀. 기본 달력은 건드리지 않음. 목록은 앱 전용 비공개 저장소(nv_cal). */
final class CalStore implements CalLogic.Store {
  static final String ACC="뉴니버스", PREF="nv_cal";
  final Context c; String err="";
  CalStore(Context c){ this.c=c.getApplicationContext(); }

  static Uri asSync(Uri u){ return u.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true").appendQueryParameter(Calendars.ACCOUNT_NAME,ACC).appendQueryParameter(Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build(); }

  /** 달력 앱(제공자)이 있는지, 달력을 보여 줄 앱이 있는지 */
  static boolean hasProvider(Context c){ try{ return c.getPackageManager().resolveContentProvider(CalendarContract.AUTHORITY,0)!=null; }catch(Exception e){ return false; } }
  static boolean hasViewer(Context c){
    try{ Intent i=new Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build());
      if(i.resolveActivity(c.getPackageManager())!=null) return true;
      Intent j=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR);
      return j.resolveActivity(c.getPackageManager())!=null;
    }catch(Exception e){ return false; }
  }

  long calId(boolean create){
    Cursor q=null;
    try{ q=c.getContentResolver().query(Calendars.CONTENT_URI,new String[]{Calendars._ID},Calendars.ACCOUNT_NAME+"=? AND "+Calendars.ACCOUNT_TYPE+"=?",new String[]{ACC,CalendarContract.ACCOUNT_TYPE_LOCAL},null);
      if(q!=null&&q.moveToFirst()) return q.getLong(0);
    }catch(Exception e){} finally{ if(q!=null) try{ q.close(); }catch(Exception e){} }
    if(!create) return -1;
    try{ ContentValues v=new ContentValues();
      v.put(Calendars.ACCOUNT_NAME,ACC); v.put(Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL); v.put(Calendars.NAME,"newniverse");
      v.put(Calendars.CALENDAR_DISPLAY_NAME,"뉴니버스"); v.put(Calendars.CALENDAR_COLOR,0xFF8F6BFF); v.put(Calendars.CALENDAR_ACCESS_LEVEL,Calendars.CAL_ACCESS_OWNER);
      v.put(Calendars.OWNER_ACCOUNT,ACC); v.put(Calendars.SYNC_EVENTS,1); v.put(Calendars.VISIBLE,1); v.put(Calendars.CALENDAR_TIME_ZONE,"Asia/Seoul");
      Uri u=c.getContentResolver().insert(asSync(Calendars.CONTENT_URI),v); if(u==null) err="nocal"; return u==null?-1:ContentUris.parseId(u);
    }catch(Exception e){ err="cal:"+InboxLogic.errClass(e); return -1; }
  }
  static long utcMidnight(long localMs){ long d=ApptParser.dayOf(localMs); return d*24L*3600*1000; }

  @Override public long insert(CalLogic.Entry e){
    try{ long cid=calId(true); if(cid<=0){ if(err.isEmpty()) err="nocal"; return -1; }
      ContentValues v=new ContentValues(); v.put(Events.CALENDAR_ID,cid); v.put(Events.TITLE,e.title);
      v.put(Events.DESCRIPTION,"문자에서 넣었어요 · 뉴니버스 문자"+(e.sender.isEmpty()?"":" · 보낸 곳 "+e.sender));
      if(e.location!=null&&!e.location.isEmpty()) v.put(Events.EVENT_LOCATION,e.location);
      if(e.allDay){ long s=utcMidnight(e.start); v.put(Events.DTSTART,s); v.put(Events.DTEND,s+24L*3600*1000); v.put(Events.ALL_DAY,1); v.put(Events.EVENT_TIMEZONE,"UTC"); }
      else { v.put(Events.DTSTART,e.start); v.put(Events.DTEND,e.end>e.start?e.end:e.start+(e.location!=null&&!e.location.isEmpty()?60L:30L)*60*1000); v.put(Events.ALL_DAY,0); v.put(Events.EVENT_TIMEZONE,"Asia/Seoul"); v.put(Events.HAS_ALARM,1); }
      Uri u=c.getContentResolver().insert(Events.CONTENT_URI,v); if(u==null){ err="noevent"; return -1; } long id=ContentUris.parseId(u);
      if(!e.allDay){ try{ ContentValues r=new ContentValues(); r.put(Reminders.EVENT_ID,id); r.put(Reminders.MINUTES,60); r.put(Reminders.METHOD,Reminders.METHOD_ALERT); c.getContentResolver().insert(Reminders.CONTENT_URI,r); }catch(Exception x){} }
      return id;
    }catch(Exception x){ err="ev:"+InboxLogic.errClass(x); return -1; }
  }
  @Override public void update(CalLogic.Entry e){
    try{ ContentValues v=new ContentValues();
      if(e.allDay){ long s=utcMidnight(e.start); v.put(Events.DTSTART,s); v.put(Events.DTEND,s+24L*3600*1000); v.put(Events.ALL_DAY,1); v.put(Events.EVENT_TIMEZONE,"UTC"); }
      else { v.put(Events.DTSTART,e.start); v.put(Events.DTEND,e.start+30L*60*1000); v.put(Events.ALL_DAY,0); v.put(Events.EVENT_TIMEZONE,"Asia/Seoul"); }
      c.getContentResolver().update(ContentUris.withAppendedId(Events.CONTENT_URI,e.id),v,null,null);
    }catch(Exception x){}
  }
  @Override public void delete(CalLogic.Entry e){ try{ if(e.id>0) c.getContentResolver().delete(ContentUris.withAppendedId(Events.CONTENT_URI,e.id),null,null); }catch(Exception x){} }

  @Override public List<CalLogic.Entry> load(){
    List<CalLogic.Entry> l=new ArrayList<>();
    try{ JSONArray a=new JSONArray(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("ev","[]"));
      for(int i=0;i<a.length();i++){ JSONObject o=a.getJSONObject(i); CalLogic.Entry e=new CalLogic.Entry();
        e.id=o.optLong("id"); e.sender=o.optString("s"); e.org=o.optString("o"); e.title=o.optString("t"); e.location=o.optString("l"); e.end=o.optLong("en"); e.start=o.optLong("st"); e.allDay=o.optBoolean("ad"); e.undone=o.optBoolean("u"); l.add(e); }
    }catch(Exception x){}
    return l;
  }
  @Override public void save(List<CalLogic.Entry> l){
    try{ JSONArray a=new JSONArray(); for(CalLogic.Entry e:l){ JSONObject o=new JSONObject(); o.put("id",e.id); o.put("s",e.sender); o.put("o",e.org); o.put("t",e.title); o.put("l",e.location==null?"":e.location); o.put("en",e.end); o.put("st",e.start); o.put("ad",e.allDay); o.put("u",e.undone); a.put(o); }
      c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString("ev",a.toString()).apply();
    }catch(Exception x){}
  }
  /** 뉴니버스 달력과 안에 넣은 일정을 한꺼번에 지움 */
  static void deleteAll(Context c){
    try{ c.getContentResolver().delete(asSync(Calendars.CONTENT_URI),Calendars.ACCOUNT_NAME+"=? AND "+Calendars.ACCOUNT_TYPE+"=?",new String[]{ACC,CalendarContract.ACCOUNT_TYPE_LOCAL}); }catch(Exception x){}
    try{ c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove("ev").apply(); }catch(Exception x){}
  }
}

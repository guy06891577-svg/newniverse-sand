package kr.newniverse.memo;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;

/**
 * 일정 메모를 폰 달력에 넣어요. 문자 앱 AutoCal(CalStore)과 같은 「뉴니버스」 로컬 달력을 찾아 쓰고,
 * 없으면 같은 이름·계정으로 만들어 두 앱의 일정이 한 달력에 모이게 해요. (그 달력은 보이도록 표시해, 삼성·구글 달력 앱에서 볼 수 있어요.)
 */
final class MemoCal {
  static final String ACC="뉴니버스";
  static final String ACC_TYPE=CalendarContract.ACCOUNT_TYPE_LOCAL;
  static final long HOUR=3600000L;

  /** 시험이 바꿔 끼울 수 있는 뒷단 */
  interface Backend {
    long calId(boolean create);
    long insert(String title,String location,long start,long end,boolean allDay);
    boolean delete(long eventId);
    String err();
  }
  static Backend io;

  static boolean granted(Context c){
    return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED
        && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR)==PackageManager.PERMISSION_GRANTED;
  }

  static Backend io(Context c){ return io!=null?io:new Android(c); }

  static Uri asSync(Uri u){
    return u.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true")
      .appendQueryParameter(Calendars.ACCOUNT_NAME,ACC)
      .appendQueryParameter(Calendars.ACCOUNT_TYPE,ACC_TYPE).build();
  }
  static long utcMidnight(long localMs){
    long d=TimePhrase.dayOf(localMs);
    return d*24L*3600*1000;
  }

  static final class Android implements Backend {
    final Context c; String err="";
    Android(Context c){ this.c=c.getApplicationContext(); }
    @Override public String err(){ return err; }
    @Override public long calId(boolean create){
      err="";
      Cursor q=null;
      try{
        q=c.getContentResolver().query(Calendars.CONTENT_URI,new String[]{Calendars._ID},
          Calendars.ACCOUNT_NAME+"=? AND "+Calendars.ACCOUNT_TYPE+"=?",new String[]{ACC,ACC_TYPE},null);
        if(q!=null&&q.moveToFirst()) return q.getLong(0);
      }catch(Exception e){ err="cal:"+e.getClass().getSimpleName(); }
      finally{ if(q!=null) try{ q.close(); }catch(Exception e){} }
      if(!create) return -1;
      try{
        ContentValues v=new ContentValues();
        v.put(Calendars.ACCOUNT_NAME,ACC); v.put(Calendars.ACCOUNT_TYPE,ACC_TYPE); v.put(Calendars.NAME,"newniverse");
        v.put(Calendars.CALENDAR_DISPLAY_NAME,"뉴니버스"); v.put(Calendars.CALENDAR_COLOR,0xFF8F6BFF);
        v.put(Calendars.CALENDAR_ACCESS_LEVEL,Calendars.CAL_ACCESS_OWNER);
        v.put(Calendars.OWNER_ACCOUNT,ACC); v.put(Calendars.SYNC_EVENTS,1); v.put(Calendars.VISIBLE,1);
        v.put(Calendars.CALENDAR_TIME_ZONE,"Asia/Seoul");
        Uri u=c.getContentResolver().insert(asSync(Calendars.CONTENT_URI),v);
        if(u==null){ err="nocal"; return -1; }
        return ContentUris.parseId(u);
      }catch(Exception e){ err="cal:"+e.getClass().getSimpleName(); return -1; }
    }
    @Override public long insert(String title,String location,long start,long end,boolean allDay){
      err="";
      try{
        long cid=calId(true); if(cid<=0){ if(err.isEmpty()) err="nocal"; return -1; }
        ContentValues v=new ContentValues();
        v.put(Events.CALENDAR_ID,cid); v.put(Events.TITLE,title==null||title.isEmpty()?"일정":title);
        v.put(Events.DESCRIPTION,"메모에서 넣었어요 · 뉴니버스 빠른 메모");
        if(location!=null&&!location.isEmpty()) v.put(Events.EVENT_LOCATION,location);
        if(allDay){
          long s=utcMidnight(start); v.put(Events.DTSTART,s); v.put(Events.DTEND,s+24L*3600*1000);
          v.put(Events.ALL_DAY,1); v.put(Events.EVENT_TIMEZONE,"UTC");
        } else {
          long en=end>start?end:start+HOUR;
          v.put(Events.DTSTART,start); v.put(Events.DTEND,en);
          v.put(Events.ALL_DAY,0); v.put(Events.EVENT_TIMEZONE,"Asia/Seoul");
        }
        Uri u=c.getContentResolver().insert(Events.CONTENT_URI,v);
        if(u==null){ err="noevent"; return -1; }
        return ContentUris.parseId(u);
      }catch(Exception e){ err="ev:"+e.getClass().getSimpleName(); return -1; }
    }
    @Override public boolean delete(long eventId){
      if(eventId<=0) return false;
      try{ return c.getContentResolver().delete(ContentUris.withAppendedId(Events.CONTENT_URI,eventId),null,null)>0; }
      catch(Exception e){ err="del:"+e.getClass().getSimpleName(); return false; }
    }
  }

  static long insert(Context c,ApptPhrase.Hit h){
    Backend b=io(c);
    long end=h.allDay?0:h.when.when+HOUR;
    return b.insert(h.title,h.location,h.when.when,end,h.allDay);
  }
  static boolean delete(Context c,long eventId){ return io(c).delete(eventId); }
  static String err(Context c){ return io(c).err(); }
}

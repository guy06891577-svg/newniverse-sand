package kr.newniverse.phone;

import java.util.*;

/** 모드가 끝난 뒤 보여 줄 「놓친 연락」 묶기 (순수 자바): 같은 번호는 한 줄, 횟수와 마지막 시각 */
final class ModeSummary {
  private ModeSummary(){}
  static final class Row { final String num,name,text; final long at; Row(String num,String name,long at,String text){ this.num=num==null?"":num; this.name=name==null?"":name; this.at=at; this.text=text==null?"":text; } }
  static final class Grp { String key,num,name,text=""; int n; long last; }

  static List<Grp> group(List<Row> rows){
    Map<String,Grp> m=new LinkedHashMap<>();
    for(Row r:rows){
      String k=ModeRules.key(r.num); boolean hid=k.isEmpty(); if(hid) k="hidden";
      Grp g=m.get(k);
      if(g==null){ g=new Grp(); g.key=k; g.num=hid?"":r.num; g.name=""; m.put(k,g); }
      g.n++; if(r.at>=g.last){ g.last=r.at; g.text=r.text; }
      if(g.name.isEmpty()&&!r.name.isEmpty()) g.name=r.name;
    }
    List<Grp> out=new ArrayList<>(m.values());
    Collections.sort(out,(a,b)->Long.compare(b.last,a.last));
    return out;
  }
}

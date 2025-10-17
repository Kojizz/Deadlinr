package com.deadlinr.app.service;
import com.deadlinr.app.dto.PageAncestor;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service public class PageNavigationService {
 private final JdbcTemplate db;
 public PageNavigationService(JdbcTemplate db){this.db=db;}
 public List<PageAncestor> ancestors(User user,long id){
  var rows=db.query("""
   with recursive trail as (
    select p.id,p.parent_id,p.title,p.icon,0 as depth,array[p.id] as visited,false as cycle
    from pages p where p.id=? and p.owner_id=?
    union all
    select p.id,p.parent_id,p.title,p.icon,t.depth+1,t.visited||p.id,p.id=any(t.visited)
    from trail t join pages p on p.id=t.parent_id and p.owner_id=?
    where not t.cycle and t.depth<100
   ) select id,parent_id,title,icon,cycle from trail order by depth desc
   """,(r,n)->new Entry(new PageAncestor(r.getLong("id"),r.getObject("parent_id",Long.class),r.getString("title"),r.getString("icon")),r.getBoolean("cycle")),id,user.getId(),user.getId());
  if(rows.isEmpty())throw new ResourceNotFoundException("Page not found");
  if(rows.stream().anyMatch(Entry::cycle)||rows.getFirst().page().parentId()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Page hierarchy is cyclic, incomplete or deeper than 100 levels");
  return rows.stream().map(Entry::page).toList();
 }
 private record Entry(PageAncestor page,boolean cycle){}
}

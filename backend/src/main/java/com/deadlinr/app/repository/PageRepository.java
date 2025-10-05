package com.deadlinr.app.repository;

import com.deadlinr.app.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Long> {

    List<Page> findByOwnerIdAndArchivedAtIsNull(Long ownerId);

    List<Page> findByOwnerIdAndArchivedAtIsNotNull(Long ownerId);

    List<Page> findByOwnerIdAndFavoriteTrueAndArchivedAtIsNull(Long ownerId);

    List<Page> findByOwnerIdAndParentIdOrderByPositionAsc(Long ownerId, Long parentId);

    List<Page> findByOwnerIdAndParentIsNullOrderByPositionAsc(Long ownerId);

    interface TreeRow {
        Long getId(); String getTitle(); String getIcon(); Long getParentId(); Long getCourseId();
        boolean getFavorite(); double getPosition(); boolean getHasDueDate();
    }
    @Query(value = """
      select p.id,p.title,p.icon,p.parent_id as "parentId",p.course_id as "courseId",p.favorite,p.position,
      exists(select 1 from work_items w where w.page_id=p.id and w.archived_at is null and w.due_at is not null) as "hasDueDate"
      from pages p where p.owner_id=:ownerId and p.archived_at is null order by p.position,p.id
      """, nativeQuery=true)
    List<TreeRow> lightweightTree(@Param("ownerId") Long ownerId);
    @Query("select coalesce(max(p.position),0)+1 from Page p where p.owner.id=:ownerId and ((:parentId is null and p.parent is null) or p.parent.id=:parentId)")
    double nextPosition(@Param("ownerId") Long ownerId,@Param("parentId") Long parentId);

    interface SearchHit {
        Long getId();
        String getTitle();
        String getIcon();
        String getSnippet();
    }

    // Transaction-local, so pooled connections cannot leak search settings.
    @Query(value = "select set_config('pg_trgm.word_similarity_threshold', '0.35', true)", nativeQuery = true)
    String configureSearchSimilarity();

    @Query(value = """
            with input as (
                select lower(unaccent(:q)) as term,
                       websearch_to_tsquery('simple', unaccent(:q)) as exact_query,
                       deadlinr_prefix_query(:q) as prefix_query
            )
            select p.id as id, p.title as title, p.icon as icon,
                   ts_headline('simple', p.title || ' ' || coalesce(p.search_text, ''),
                               input.prefix_query,
                               'StartSel=«, StopSel=», MaxWords=28, MinWords=8, MaxFragments=2') as snippet
            from pages p cross join input
            where p.owner_id = :ownerId and p.archived_at is null
              and (p.search_vector @@ input.exact_query
                   or p.search_vector @@ input.prefix_query
                   or (input.term ~ '^[[:alnum:]]{3,}$' and p.search_title %> input.term))
            order by (ts_rank(p.search_vector, input.exact_query)
                      + ts_rank(p.search_vector, input.prefix_query)
                      + word_similarity(input.term, p.search_title)) desc,
                     p.title asc, p.id asc
            limit 50
            """, nativeQuery = true)
    List<SearchHit> search(@Param("ownerId") Long ownerId, @Param("q") String q);
}

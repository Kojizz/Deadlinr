package com.deadlinr.app.service;

import com.deadlinr.app.dto.PageContentRequest;
import com.deadlinr.app.dto.PageMoveRequest;
import com.deadlinr.app.dto.PageRequest;
import com.deadlinr.app.dto.PageResponse;
import com.deadlinr.app.dto.PageTreeNode;
import com.deadlinr.app.dto.PageUpdateRequest;
import com.deadlinr.app.dto.SearchResult;
import com.deadlinr.app.entity.Course;
import com.deadlinr.app.entity.Page;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.ForbiddenException;
import com.deadlinr.app.exception.ResourceNotFoundException;
import com.deadlinr.app.repository.CourseRepository;
import com.deadlinr.app.repository.PageRepository;
import com.deadlinr.app.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PageService {

    private final PageRepository pageRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final WorkspaceService workspace;
    private final jakarta.persistence.EntityManager entityManager;

    public PageService(PageRepository pageRepository,
                       CourseRepository courseRepository,
                       UserRepository userRepository, WorkspaceService workspace, jakarta.persistence.EntityManager entityManager) {
        this.workspace=workspace; this.entityManager=entityManager;
        this.pageRepository = pageRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PageTreeNode> getTree(User owner) {
        var rows=pageRepository.lightweightTree(owner.getId());
        Map<Long,List<PageRepository.TreeRow>> grouped=new HashMap<>();
        for(var p:rows)grouped.computeIfAbsent(p.getParentId(),k->new ArrayList<>()).add(p);
        var openTasks=new HashMap<Long,Long>();
        for(var row:entityManager.createNativeQuery("select w.page_id as pid,count(*) as c from work_items w where w.owner_id=:owner and w.archived_at is null and w.kind='TASK' and w.status<>'DONE' and w.page_id is not null group by w.page_id").setParameter("owner",owner.getId()).getResultList()){
            var r=(Object[])row;openTasks.put(((Number)r[0]).longValue(),((Number)r[1]).longValue());
        }
        return lightweightNodes(null,grouped,openTasks);
    }
    private List<PageTreeNode> lightweightNodes(Long parent,Map<Long,List<PageRepository.TreeRow>> grouped,Map<Long,Long> openTasks){
        return grouped.getOrDefault(parent,List.of()).stream().map(p->{
            var children=lightweightNodes(p.getId(),grouped,openTasks);
            return new PageTreeNode(p.getId(),p.getTitle(),p.getIcon(),p.getParentId(),p.getCourseId(),p.getFavorite(),p.getPosition(),"NOTE",children.size(),p.getHasDueDate(),children,openTasks.getOrDefault(p.getId(),0L).intValue());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<PageResponse> getFavorites(User owner) {
        return pageRepository.findByOwnerIdAndFavoriteTrueAndArchivedAtIsNull(owner.getId()).stream()
                .map(PageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PageResponse> getTrash(User owner) {
        return pageRepository.findByOwnerIdAndArchivedAtIsNotNull(owner.getId()).stream()
                .map(PageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse getPage(User owner, Long id) {
        return PageResponse.from(findOwnedPage(owner, id));
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(User owner, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String needle = query.trim();
        if (needle.length() > 100) {
            needle = needle.substring(0, 100);
        }
        pageRepository.configureSearchSimilarity();
        return pageRepository.search(owner.getId(), needle).stream()
                .map(hit -> new SearchResult(hit.getId(), hit.getTitle(), hit.getIcon(),
                        SearchResult.TYPE_PAGE, hit.getSnippet()))
                .toList();
    }

    @Transactional
    public PageResponse createPage(User owner, PageRequest request) {
        Course course = null;
        if (request.courseId() != null) {
            course = courseRepository.findByIdAndUserId(request.courseId(), owner.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found: " + request.courseId()));
        }

        Page parent = null;
        if (request.parentId() != null) {
            parent = findOwnedPage(owner, request.parentId());
            if(parent.getArchivedAt()!=null)throw new IllegalArgumentException("Restore the parent before adding children");
        }

        Page page = Page.builder()
                .owner(userRepository.getReferenceById(owner.getId()))
                .course(course)
                .parent(parent)
                .title(request.title())
                .icon(request.icon())
                .coverStyle(request.coverStyle())
                .content(request.content())
                .searchText(extractSearchText(request.content()))
                .favorite(false)
                .position(nextPosition(owner, request.parentId()))
                .build();

        pageRepository.save(page);
        return PageResponse.from(page);
    }

    @Transactional
    public PageResponse updatePage(User owner, Long id, PageUpdateRequest request) {
        Page page = findOwnedPage(owner, id);
        if (request.title() != null) {
            page.setTitle(request.title());
        }
        if (request.icon() != null) {
            page.setIcon(request.icon());
        }
        if (request.coverStyle() != null) {
            page.setCoverStyle(request.coverStyle());
        }
        if (request.favorite() != null) {
            page.setFavorite(request.favorite());
        }
        return PageResponse.from(page);
    }

    @Transactional
    public PageResponse movePage(User owner, Long id, PageMoveRequest request) {
        Page page = findOwnedPage(owner, id);

        Page newParent = null;
        if (request.parentId() != null) {
            if (request.parentId().equals(page.getId())) {
                throw new IllegalArgumentException("A page cannot be its own parent");
            }
            newParent = findOwnedPage(owner, request.parentId());
            if(newParent.getArchivedAt()!=null)throw new IllegalArgumentException("Cannot move into Trash");
            if (isDescendant(page, newParent)) {
                throw new IllegalArgumentException("Cannot move a page under its own descendant");
            }
        }

        page.setParent(newParent);
        page.setPosition(request.position());
        return PageResponse.from(page);
    }

    @Transactional
    public PageResponse updateContent(User owner, Long id, PageContentRequest request) {
        Page page = findOwnedPage(owner, id);
        page.setContent(request.content()); 
        page.setSearchText(extractSearchText(request.content()));
        return PageResponse.from(page);
    }

    @Transactional
    public void archive(User owner, Long id) {
        workspace.archive(owner,id);
        entityManager.clear();
    }

    @Transactional
    public PageResponse restore(User owner, Long id) {
        workspace.restore(owner,id);
        entityManager.clear();
        return getPage(owner,id);
    }

    private Page findOwnedPage(User owner, Long id) {
        Page page = pageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Page not found: " + id));
        if (!page.getOwner().getId().equals(owner.getId())) {
            throw new ForbiddenException("You do not have access to this page");
        }
        return page;
    }

    private double nextPosition(User owner, Long parentId) {
        return pageRepository.nextPosition(owner.getId(),parentId);
    }

    private boolean isDescendant(Page ancestor, Page node) {
        Page cursor = node.getParent();
        while (cursor != null) {
            if (cursor.getId().equals(ancestor.getId())) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private List<PageTreeNode> buildTree(List<Page> pages) {
        Set<Long> ids = new HashSet<>();
        pages.forEach(p -> ids.add(p.getId()));

        Map<Long, List<Page>> childrenByParent = new HashMap<>();
        for (Page p : pages) {
            Long parentId = (p.getParent() != null && ids.contains(p.getParent().getId()))
                    ? p.getParent().getId()
                    : null;
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(p);
        }

        List<Page> roots = childrenByParent.getOrDefault(null, new ArrayList<>());
        roots.sort(Comparator.comparingDouble(Page::getPosition));
        return roots.stream()
                .map(p -> toTreeNode(p, childrenByParent))
                .toList();
    }

    private PageTreeNode toTreeNode(Page page, Map<Long, List<Page>> childrenByParent) {
        List<Page> children = childrenByParent.getOrDefault(page.getId(), new ArrayList<>());
        children.sort(Comparator.comparingDouble(Page::getPosition));
        List<PageTreeNode> childNodes = children.stream()
                .map(c -> toTreeNode(c, childrenByParent))
                .toList();

        return new PageTreeNode(
                page.getId(),
                page.getTitle(),
                page.getIcon(),
                page.getParent() != null ? page.getParent().getId() : null,
                page.getCourse() != null ? page.getCourse().getId() : null,
                page.isFavorite(),
                page.getPosition(),
                childNodes
        );
    }

    private String extractSearchText(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        collectText(content, sb);
        return sb.toString().trim();
    }

    private void collectText(JsonNode node, StringBuilder sb) {
        if (node.isArray()) {
            node.forEach(child -> collectText(child, sb));
        } else if (node.isObject()) {
            JsonNode text = node.get("text");
            if (text != null && text.isTextual()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(text.asText());
            }
            node.fields().forEachRemaining(entry -> collectText(entry.getValue(), sb));
        }
    }
}

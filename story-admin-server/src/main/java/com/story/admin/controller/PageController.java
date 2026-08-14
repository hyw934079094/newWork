package com.story.admin.controller;

import com.story.admin.domain.StoryPage;
import com.story.admin.dto.PageCreateRequest;
import com.story.admin.dto.PageReorderRequest;
import com.story.admin.dto.PageUpdateRequest;
import com.story.admin.service.PageService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PageController {

  private final PageService pageService;

  public PageController(PageService pageService) {
    this.pageService = pageService;
  }

  @GetMapping("/api/arcs/{arcId}/pages")
  public List<StoryPage> list(@PathVariable Long arcId) {
    return pageService.listByArc(arcId);
  }

  @PostMapping("/api/arcs/{arcId}/pages")
  public StoryPage create(@PathVariable Long arcId, @RequestBody PageCreateRequest body) {
    return pageService.create(arcId, body);
  }

  @PutMapping("/api/arcs/{arcId}/pages/reorder")
  public Map<String, String> reorder(
      @PathVariable Long arcId, @RequestBody PageReorderRequest body) {
    pageService.reorder(arcId, body == null ? null : body.orderedIds());
    return Map.of("status", "ok");
  }

  @GetMapping("/api/pages/{id}")
  public StoryPage get(@PathVariable Long id) {
    return pageService.get(id);
  }

  @PutMapping("/api/pages/{id}")
  public StoryPage update(@PathVariable Long id, @RequestBody PageUpdateRequest body) {
    return pageService.update(id, body);
  }

  @DeleteMapping("/api/pages/{id}")
  public Map<String, String> delete(@PathVariable Long id) {
    pageService.delete(id);
    return Map.of("status", "ok");
  }
}

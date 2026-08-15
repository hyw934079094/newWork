package com.story.admin.controller;

import com.story.admin.domain.StoryArc;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.ArcQuery;
import com.story.admin.dto.ArcReadingStreamResponse;
import com.story.admin.dto.ArcUpdateRequest;
import com.story.admin.service.ArcService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArcController {

  private final ArcService arcService;

  public ArcController(ArcService arcService) {
    this.arcService = arcService;
  }

  @GetMapping("/api/series/{seriesId}/arcs")
  public List<StoryArc> list(
      @PathVariable Long seriesId, @RequestParam(required = false) String q) {
    return arcService.listBySeries(seriesId, new ArcQuery(q));
  }

  @PostMapping("/api/series/{seriesId}/arcs")
  public StoryArc create(@PathVariable Long seriesId, @RequestBody ArcCreateRequest body) {
    return arcService.create(seriesId, body);
  }

  @GetMapping("/api/arcs/{id}")
  public StoryArc get(@PathVariable Long id) {
    return arcService.get(id);
  }

  @GetMapping("/api/arcs/{id}/reading-stream")
  public ArcReadingStreamResponse readingStream(@PathVariable Long id) {
    return arcService.readingStream(id);
  }

  @PutMapping("/api/arcs/{id}")
  public StoryArc update(@PathVariable Long id, @RequestBody ArcUpdateRequest body) {
    return arcService.update(id, body);
  }

  @DeleteMapping("/api/arcs/{id}")
  public Map<String, String> delete(@PathVariable Long id) {
    arcService.delete(id);
    return Map.of("status", "ok");
  }
}

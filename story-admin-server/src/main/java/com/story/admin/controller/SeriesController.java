package com.story.admin.controller;

import com.story.admin.domain.SeriesStatus;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.dto.SeriesQuery;
import com.story.admin.dto.SeriesUpdateRequest;
import com.story.admin.service.SeriesService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/series")
public class SeriesController {

  private final SeriesService seriesService;

  public SeriesController(SeriesService seriesService) {
    this.seriesService = seriesService;
  }

  @GetMapping
  public List<StorySeries> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) SeriesStatus status) {
    return seriesService.list(new SeriesQuery(q, status));
  }

  @GetMapping("/{id}")
  public StorySeries get(@PathVariable Long id) {
    return seriesService.get(id);
  }

  @PostMapping
  public StorySeries create(@RequestBody SeriesCreateRequest body) {
    return seriesService.create(body);
  }

  @PutMapping("/{id}")
  public StorySeries update(@PathVariable Long id, @RequestBody SeriesUpdateRequest body) {
    return seriesService.update(id, body);
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable Long id) {
    seriesService.delete(id);
    return Map.of("status", "ok");
  }
}

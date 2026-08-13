package com.story.admin.controller;

import com.story.admin.dto.ComboDetailResponse;
import com.story.admin.dto.ComboUpsertRequest;
import com.story.admin.service.ComboService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/combos")
public class ComboController {

  private final ComboService comboService;

  public ComboController(ComboService comboService) {
    this.comboService = comboService;
  }

  @GetMapping
  public List<ComboDetailResponse> list() {
    return comboService.list();
  }

  @PostMapping
  public ComboDetailResponse create(@RequestBody ComboUpsertRequest body) {
    return comboService.create(body);
  }

  @GetMapping("/{id}")
  public ComboDetailResponse get(@PathVariable Long id) {
    return comboService.get(id);
  }

  @PutMapping("/{id}")
  public ComboDetailResponse update(@PathVariable Long id, @RequestBody ComboUpsertRequest body) {
    return comboService.update(id, body);
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable Long id) {
    comboService.delete(id);
    return Map.of("status", "ok");
  }
}

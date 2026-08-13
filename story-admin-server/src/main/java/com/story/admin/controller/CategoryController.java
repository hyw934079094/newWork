package com.story.admin.controller;

import com.story.admin.domain.AssetCategory;
import com.story.admin.dto.CategoryCreateRequest;
import com.story.admin.dto.CategoryUpdateRequest;
import com.story.admin.service.CategoryService;
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
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @GetMapping
  public List<AssetCategory> list() {
    return categoryService.list();
  }

  @PostMapping
  public AssetCategory create(@RequestBody CategoryCreateRequest body) {
    return categoryService.create(body);
  }

  @PutMapping("/{id}")
  public AssetCategory update(@PathVariable Long id, @RequestBody CategoryUpdateRequest body) {
    return categoryService.update(id, body);
  }

  @DeleteMapping("/{id}")
  public Map<String, String> delete(@PathVariable Long id) {
    categoryService.delete(id);
    return Map.of("status", "ok");
  }
}

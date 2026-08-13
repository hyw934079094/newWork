package com.story.admin.service;

import com.story.admin.domain.AssetCategory;
import com.story.admin.dto.CategoryCreateRequest;
import com.story.admin.dto.CategoryUpdateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {

  private final AssetCategoryRepository repo;
  private final AssetRepository assetRepository;

  public CategoryService(AssetCategoryRepository repo, AssetRepository assetRepository) {
    this.repo = repo;
    this.assetRepository = assetRepository;
  }

  public List<AssetCategory> list() {
    return repo.findAllByOrderBySortOrderAscIdAsc();
  }

  public AssetCategory get(Long id) {
    return repo.findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found: " + id));
  }

  @Transactional
  public AssetCategory create(CategoryCreateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category name is required");
    }
    if (req.code() == null || req.code().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category code is required");
    }
    String code = req.code().trim().toLowerCase(Locale.ROOT);
    if (repo.existsByCode(code)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "category code already exists: " + code);
    }
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(req.name().trim());
    category.setSortOrder(
        req.sortOrder() != null ? req.sortOrder() : repo.findMaxSortOrder().orElse(-1) + 1);
    category.setSystemPreset(false);
    return repo.save(category);
  }

  @Transactional
  public AssetCategory update(Long id, CategoryUpdateRequest req) {
    if (req == null || req.name() == null || req.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category name is required");
    }
    AssetCategory category = get(id);
    category.setName(req.name().trim());
    if (req.sortOrder() != null) {
      category.setSortOrder(req.sortOrder());
    }
    return repo.save(category);
  }

  @Transactional
  public void delete(Long id) {
    AssetCategory category = get(id);
    if (category.isSystemPreset()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preset category cannot be deleted");
    }
    if (assetRepository.existsByCategoryId(id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "category still has assets");
    }
    repo.delete(category);
  }
}

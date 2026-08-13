package com.story.admin.service;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetStatus;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.service.StorageService.StoredFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssetService {

  private final AssetRepository assetRepository;
  private final AssetCategoryRepository categoryRepository;
  private final StorageService storageService;

  public AssetService(
      AssetRepository assetRepository,
      AssetCategoryRepository categoryRepository,
      StorageService storageService) {
    this.assetRepository = assetRepository;
    this.categoryRepository = categoryRepository;
    this.storageService = storageService;
  }

  @Transactional
  public List<Asset> upload(Long categoryId, MultipartFile[] files) {
    if (categoryId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId is required");
    }
    if (!categoryRepository.existsById(categoryId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found: " + categoryId);
    }
    if (files == null || files.length == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "files are required");
    }
    int sort = assetRepository.findMaxSortOrderByCategoryId(categoryId).orElse(-1);
    List<Asset> saved = new ArrayList<>();
    List<String> storedPaths = new ArrayList<>();
    try {
      for (MultipartFile file : files) {
        StoredFile stored = storageService.store(file);
        storedPaths.add(stored.relativePath());
        sort += 1;
        Asset asset = new Asset();
        asset.setDisplayName(displayNameFrom(file.getOriginalFilename()));
        asset.setCategoryId(categoryId);
        asset.setSortOrder(sort);
        asset.setStatus(AssetStatus.NORMAL);
        asset.setOriginalFilename(originalName(file.getOriginalFilename()));
        asset.setStoragePath(stored.relativePath());
        asset.setContentType(stored.contentType());
        asset.setWidth(stored.width());
        asset.setHeight(stored.height());
        asset.setSizeBytes(stored.size());
        asset.setChecksum(stored.checksum());
        saved.add(assetRepository.save(asset));
      }
      return saved;
    } catch (RuntimeException ex) {
      storedPaths.forEach(storageService::deleteQuietly);
      throw ex;
    }
  }

  public List<Asset> list(Long categoryId, String status, String q) {
    AssetStatus parsed = parseStatus(status);
    String query = q == null ? "" : q.trim();
    return assetRepository.search(categoryId, parsed, query);
  }

  public Asset get(Long id) {
    return assetRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found: " + id));
  }

  @Transactional
  public Asset update(Long id, AssetUpdateRequest req) {
    if (req == null || req.displayName() == null || req.displayName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
    }
    Asset asset = get(id);
    asset.setDisplayName(req.displayName().trim());
    asset.setDescription(blankToNull(req.description()));
    asset.setChapterRefPlaceholder(blankToNull(req.chapterRefPlaceholder()));
    return assetRepository.save(asset);
  }

  public Path resolveContent(Long id) {
    Asset asset = get(id);
    return storageService.resolveAbsolute(asset.getStoragePath());
  }

  private static AssetStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return AssetStatus.NORMAL;
    }
    try {
      return AssetStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status: " + status);
    }
  }

  private static String displayNameFrom(String originalFilename) {
    String name = originalName(originalFilename);
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      return name.substring(0, dot);
    }
    return name;
  }

  private static String originalName(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      return "untitled";
    }
    String replaced = originalFilename.replace("\\", "/");
    int slash = replaced.lastIndexOf('/');
    return slash >= 0 ? replaced.substring(slash + 1) : replaced;
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}

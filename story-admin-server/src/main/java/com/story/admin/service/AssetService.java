package com.story.admin.service;

import com.story.admin.domain.AiReferenceItem;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.AssetTag;
import com.story.admin.domain.AssetTagRel;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.AssetTagRelRepository;
import com.story.admin.repository.AssetTagRepository;
import com.story.admin.repository.CharacterProfileRepository;
import com.story.admin.service.StorageService.StoredFile;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  private final AssetTagRepository tagRepository;
  private final AssetTagRelRepository tagRelRepository;
  private final AssetCharacterRelRepository characterRelRepository;
  private final CharacterProfileRepository characterProfileRepository;
  private final AiReferenceItemRepository aiReferenceItemRepository;

  public AssetService(
      AssetRepository assetRepository,
      AssetCategoryRepository categoryRepository,
      StorageService storageService,
      AssetTagRepository tagRepository,
      AssetTagRelRepository tagRelRepository,
      AssetCharacterRelRepository characterRelRepository,
      CharacterProfileRepository characterProfileRepository,
      AiReferenceItemRepository aiReferenceItemRepository) {
    this.assetRepository = assetRepository;
    this.categoryRepository = categoryRepository;
    this.storageService = storageService;
    this.tagRepository = tagRepository;
    this.tagRelRepository = tagRelRepository;
    this.characterRelRepository = characterRelRepository;
    this.characterProfileRepository = characterProfileRepository;
    this.aiReferenceItemRepository = aiReferenceItemRepository;
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
        saved.add(hydrate(assetRepository.save(asset)));
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
    return assetRepository.search(categoryId, parsed, query).stream()
        .map(this::hydrate)
        .toList();
  }

  public Asset get(Long id) {
    return hydrate(
        assetRepository
            .findById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found: " + id)));
  }

  @Transactional
  public Asset update(Long id, AssetUpdateRequest req) {
    if (req == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
    }
    Asset asset = getRaw(id);
    if (req.displayName() != null) {
      if (req.displayName().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
      }
      asset.setDisplayName(req.displayName().trim());
      asset.setDescription(blankToNull(req.description()));
      asset.setChapterRefPlaceholder(blankToNull(req.chapterRefPlaceholder()));
    } else if (req.tagNames() == null && req.characterIds() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
    }
    assetRepository.save(asset);
    if (req.tagNames() != null) {
      replaceTags(id, req.tagNames());
    }
    if (req.characterIds() != null) {
      replaceCharacters(id, req.characterIds());
    }
    return get(id);
  }

  public Path resolveContent(Long id) {
    Asset asset = getRaw(id);
    return storageService.resolveAbsolute(asset.getStoragePath());
  }

  @Transactional
  public Asset recycle(Long id) {
    Asset asset = getRaw(id);
    asset.setStatus(AssetStatus.DELETED);
    asset.setDeletedAt(LocalDateTime.now());
    return hydrate(assetRepository.save(asset));
  }

  @Transactional
  public Asset restore(Long id) {
    Asset asset = getRaw(id);
    asset.setStatus(AssetStatus.NORMAL);
    asset.setDeletedAt(null);
    return hydrate(assetRepository.save(asset));
  }

  @Transactional
  public void hardDelete(Long id) {
    Asset asset = getRaw(id);
    List<Long> characterIds = characterRelRepository.findCharacterIdsByAssetId(id);
    List<AiReferenceItem> aiRefs = aiReferenceItemRepository.findByAssetId(id);
    if (!characterIds.isEmpty() || !aiRefs.isEmpty()) {
      throw new ConflictException(buildReferenceSummary(characterIds, aiRefs));
    }
    tagRelRepository.deleteByAssetId(id);
    characterRelRepository.deleteByAssetId(id);
    String storagePath = asset.getStoragePath();
    assetRepository.delete(asset);
    storageService.deleteQuietly(storagePath);
  }

  @Transactional
  public void reorder(Long categoryId, List<Long> orderedIds) {
    if (categoryId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId is required");
    }
    if (orderedIds == null || orderedIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderedIds is required");
    }
    if (orderedIds.stream().anyMatch(Objects::isNull) || new HashSet<>(orderedIds).size() != orderedIds.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderedIds must be unique and non-null");
    }
    if (!categoryRepository.existsById(categoryId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found: " + categoryId);
    }
    List<Asset> current =
        assetRepository.findAllByCategoryIdAndStatusOrderBySortOrderAsc(categoryId, AssetStatus.NORMAL);
    Set<Long> currentIds = current.stream().map(Asset::getId).collect(Collectors.toSet());
    if (currentIds.size() != orderedIds.size() || !currentIds.containsAll(orderedIds)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "orderedIds must match all assets in the category");
    }
    Map<Long, Asset> byId = current.stream().collect(Collectors.toMap(Asset::getId, Function.identity()));
    for (int i = 0; i < orderedIds.size(); i++) {
      Asset asset = byId.get(orderedIds.get(i));
      asset.setSortOrder(i);
      assetRepository.save(asset);
    }
  }

  @Transactional
  public Asset move(Long id, Long targetCategoryId, int targetIndex) {
    if (targetCategoryId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetCategoryId is required");
    }
    Asset asset = getRaw(id);
    if (!categoryRepository.existsById(targetCategoryId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found: " + targetCategoryId);
    }
    Long sourceCategoryId = asset.getCategoryId();
    boolean sameCategory = sourceCategoryId.equals(targetCategoryId);
    List<Asset> sourceList =
        new ArrayList<>(
            assetRepository.findAllByCategoryIdAndStatusOrderBySortOrderAsc(
                sourceCategoryId, AssetStatus.NORMAL));
    List<Asset> targetList =
        sameCategory
            ? sourceList
            : new ArrayList<>(
                assetRepository.findAllByCategoryIdAndStatusOrderBySortOrderAsc(
                    targetCategoryId, AssetStatus.NORMAL));
    sourceList.removeIf(item -> item.getId().equals(id));
    if (!sameCategory) {
      targetList.removeIf(item -> item.getId().equals(id));
      asset.setCategoryId(targetCategoryId);
    }
    int insertAt = Math.max(0, Math.min(targetIndex, targetList.size()));
    targetList.add(insertAt, asset);
    if (!sameCategory) {
      resequence(sourceList);
    }
    resequence(targetList);
    return hydrate(asset);
  }

  private void replaceTags(Long assetId, List<String> tagNames) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String raw : tagNames) {
      if (raw == null) {
        continue;
      }
      String name = raw.trim();
      if (!name.isEmpty()) {
        normalized.add(name);
      }
    }
    tagRelRepository.deleteByAssetId(assetId);
    tagRelRepository.flush();
    for (String name : normalized) {
      AssetTag tag =
          tagRepository
              .findByName(name)
              .orElseGet(
                  () -> {
                    AssetTag created = new AssetTag();
                    created.setName(name);
                    return tagRepository.save(created);
                  });
      tagRelRepository.save(new AssetTagRel(assetId, tag.getId()));
    }
  }

  private void replaceCharacters(Long assetId, List<Long> characterIds) {
    LinkedHashSet<Long> unique = new LinkedHashSet<>();
    for (Long characterId : characterIds) {
      if (characterId != null) {
        unique.add(characterId);
      }
    }
    for (Long characterId : unique) {
      if (!characterProfileRepository.existsById(characterId)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "character not found: " + characterId);
      }
    }
    characterRelRepository.deleteByAssetId(assetId);
    characterRelRepository.flush();
    for (Long characterId : unique) {
      characterRelRepository.save(new AssetCharacterRel(assetId, characterId));
    }
  }

  private Asset getRaw(Long id) {
    return assetRepository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "asset not found: " + id));
  }

  private Asset hydrate(Asset asset) {
    if (asset == null || asset.getId() == null) {
      return asset;
    }
    asset.setTagNames(tagRelRepository.findTagNamesByAssetId(asset.getId()));
    asset.setCharacterIds(characterRelRepository.findCharacterIdsByAssetId(asset.getId()));
    return asset;
  }

  private void resequence(List<Asset> assets) {
    for (int i = 0; i < assets.size(); i++) {
      Asset item = assets.get(i);
      item.setSortOrder(i);
      assetRepository.save(item);
    }
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

  private String buildReferenceSummary(List<Long> characterIds, List<AiReferenceItem> aiRefs) {
    StringBuilder sb = new StringBuilder("无法彻底删除：仍存在引用。");
    if (!characterIds.isEmpty()) {
      Map<Long, CharacterProfile> byId =
          characterProfileRepository.findAllById(characterIds).stream()
              .collect(Collectors.toMap(CharacterProfile::getId, Function.identity()));
      String names =
          characterIds.stream()
              .map(
                  cid -> {
                    CharacterProfile profile = byId.get(cid);
                    String name = profile == null ? "?" : profile.getName();
                    return name + "(id=" + cid + ")";
                  })
              .collect(Collectors.joining(", "));
      sb.append(" 人物关联: [").append(names).append("].");
    }
    if (!aiRefs.isEmpty()) {
      String items =
          aiRefs.stream()
              .map(
                  item ->
                      "item#"
                          + item.getId()
                          + "/session="
                          + item.getSessionId()
                          + (item.getPurpose() == null || item.getPurpose().isBlank()
                              ? ""
                              : "/purpose=" + item.getPurpose()))
              .collect(Collectors.joining(", "));
      sb.append(" AI参考项(").append(aiRefs.size()).append("): [").append(items).append("].");
    }
    return sb.toString();
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}

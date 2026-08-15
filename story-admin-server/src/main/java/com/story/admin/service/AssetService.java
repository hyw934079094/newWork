package com.story.admin.service;

import com.story.admin.domain.AiReferenceItem;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetArcRel;
import com.story.admin.domain.AssetArcRelId;
import com.story.admin.domain.AssetCharacterRel;
import com.story.admin.domain.AssetCharacterRelId;
import com.story.admin.domain.AssetLinkType;
import com.story.admin.domain.AssetSeriesRel;
import com.story.admin.domain.AssetSeriesRelId;
import com.story.admin.domain.AssetStatus;
import com.story.admin.domain.AssetTag;
import com.story.admin.domain.AssetTagRel;
import com.story.admin.domain.AssetUnlinkedOrder;
import com.story.admin.domain.CharacterProfile;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StoryPage;
import com.story.admin.domain.StorySeries;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.exception.ConflictException;
import com.story.admin.repository.AiReferenceItemRepository;
import com.story.admin.repository.AssetArcRelRepository;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetCharacterRelRepository;
import com.story.admin.repository.AssetComboMemberRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.AssetSeriesRelRepository;
import com.story.admin.repository.AssetTagRelRepository;
import com.story.admin.repository.AssetTagRepository;
import com.story.admin.repository.AssetUnlinkedOrderRepository;
import com.story.admin.repository.CharacterProfileRepository;
import com.story.admin.repository.IdentityAssetRelRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.StoryArcRepository;
import com.story.admin.repository.StoryPageRepository;
import com.story.admin.repository.StorySeriesRepository;
import com.story.admin.service.StorageService.StoredFile;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
  private final AssetComboMemberRepository comboMemberRepository;
  private final IdentityAssetRelRepository identityAssetRelRepository;
  private final StorySeriesRepository storySeriesRepository;
  private final StoryArcRepository storyArcRepository;
  private final PageAssetRefRepository pageAssetRefRepository;
  private final StoryPageRepository storyPageRepository;
  private final AssetSeriesRelRepository assetSeriesRelRepository;
  private final AssetArcRelRepository assetArcRelRepository;
  private final AssetUnlinkedOrderRepository unlinkedOrderRepository;

  public AssetService(
      AssetRepository assetRepository,
      AssetCategoryRepository categoryRepository,
      StorageService storageService,
      AssetTagRepository tagRepository,
      AssetTagRelRepository tagRelRepository,
      AssetCharacterRelRepository characterRelRepository,
      CharacterProfileRepository characterProfileRepository,
      AiReferenceItemRepository aiReferenceItemRepository,
      AssetComboMemberRepository comboMemberRepository,
      IdentityAssetRelRepository identityAssetRelRepository,
      StorySeriesRepository storySeriesRepository,
      StoryArcRepository storyArcRepository,
      PageAssetRefRepository pageAssetRefRepository,
      StoryPageRepository storyPageRepository,
      AssetSeriesRelRepository assetSeriesRelRepository,
      AssetArcRelRepository assetArcRelRepository,
      AssetUnlinkedOrderRepository unlinkedOrderRepository) {
    this.assetRepository = assetRepository;
    this.categoryRepository = categoryRepository;
    this.storageService = storageService;
    this.tagRepository = tagRepository;
    this.tagRelRepository = tagRelRepository;
    this.characterRelRepository = characterRelRepository;
    this.characterProfileRepository = characterProfileRepository;
    this.aiReferenceItemRepository = aiReferenceItemRepository;
    this.comboMemberRepository = comboMemberRepository;
    this.identityAssetRelRepository = identityAssetRelRepository;
    this.storySeriesRepository = storySeriesRepository;
    this.storyArcRepository = storyArcRepository;
    this.pageAssetRefRepository = pageAssetRefRepository;
    this.storyPageRepository = storyPageRepository;
    this.assetSeriesRelRepository = assetSeriesRelRepository;
    this.assetArcRelRepository = assetArcRelRepository;
    this.unlinkedOrderRepository = unlinkedOrderRepository;
  }

  @Transactional
  public List<Asset> upload(Long categoryId, MultipartFile[] files) {
    return upload(categoryId, files, null, null, null, null);
  }

  @Transactional
  public List<Asset> upload(
      Long categoryId,
      MultipartFile[] files,
      AssetLinkType linkType,
      List<Long> seriesIds,
      List<Long> arcIds,
      List<Long> characterIds) {
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
        Asset persisted = hydrate(assetRepository.save(asset));
        if (shouldApplyLinks(linkType, seriesIds, arcIds, characterIds)) {
          applyLinks(
              persisted.getId(),
              resolveUploadLinkType(linkType, seriesIds, arcIds, characterIds),
              seriesIds,
              arcIds,
              characterIds);
          persisted = hydrate(getRaw(persisted.getId()));
        }
        saved.add(persisted);
      }
      return saved;
    } catch (RuntimeException ex) {
      storedPaths.forEach(storageService::deleteQuietly);
      throw ex;
    }
  }

  public List<Asset> list(
      Long categoryId, String status, String q, String characterFilter, Long characterId) {
    return list(categoryId, status, q, characterFilter, characterId, null, null, null);
  }

  public List<Asset> list(
      Long categoryId,
      String status,
      String q,
      String characterFilter,
      Long characterId,
      String linkType,
      Long seriesId,
      Long arcId) {
    AssetStatus parsed = parseStatus(status);
    String query = q == null ? "" : q.trim();
    String filter = normalizeCharacterFilter(characterFilter, characterId);
    String normalizedLinkType = normalizeLinkType(linkType);
    List<Asset> assets =
        assetRepository
            .search(
                categoryId, parsed, query, filter, characterId, normalizedLinkType, seriesId, arcId)
            .stream()
            .map(this::hydrate)
            .toList();
    return applyScopeOrder(
        assets, categoryId, filter, characterId, normalizedLinkType, seriesId, arcId);
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
    } else if (req.tagNames() == null && req.characterIds() == null && req.linkType() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName is required");
    }
    assetRepository.save(asset);
    if (req.tagNames() != null) {
      replaceTags(id, req.tagNames());
    }
    if (req.linkType() != null) {
      applyLinks(id, req.linkType(), req.seriesIds(), req.arcIds(), req.characterIds());
    } else if (req.characterIds() != null) {
      replaceCharacters(id, req.characterIds());
    }
    return get(id);
  }

  public Path resolveContent(Long id) {
    Asset asset = getRaw(id);
    return storageService.resolveAbsolute(asset.getStoragePath());
  }

  @Transactional
  public Asset replaceContent(Long id, MultipartFile file) {
    Asset asset = getRaw(id);
    if (asset.getStatus() != AssetStatus.NORMAL) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "only NORMAL assets can be replaced");
    }
    StoredFile stored = storageService.overwrite(asset.getStoragePath(), file);
    asset.setOriginalFilename(originalName(file.getOriginalFilename()));
    asset.setContentType(stored.contentType());
    asset.setWidth(stored.width());
    asset.setHeight(stored.height());
    asset.setSizeBytes(stored.size());
    asset.setChecksum(stored.checksum());
    return hydrate(assetRepository.save(asset));
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
    List<String> seriesLinkNames = assetSeriesRelRepository.findSeriesNamesByAssetId(id);
    List<String> arcLinkTitles = assetArcRelRepository.findArcTitlesByAssetId(id);
    List<AiReferenceItem> aiRefs = aiReferenceItemRepository.findByAssetId(id);
    List<String> comboNames = comboMemberRepository.findComboNamesByAssetId(id);
    List<String> identityNames = identityAssetRelRepository.findIdentityNamesByAssetId(id);
    List<StorySeries> seriesCovers = storySeriesRepository.findByCoverAssetId(id);
    List<StoryArc> arcCovers = storyArcRepository.findByCoverAssetId(id);
    List<Long> pageIds = pageAssetRefRepository.findPageIdsByAssetId(id);
    List<StoryPage> pageRefs =
        pageIds.isEmpty() ? List.of() : storyPageRepository.findAllById(pageIds);
    if (!characterIds.isEmpty()
        || !seriesLinkNames.isEmpty()
        || !arcLinkTitles.isEmpty()
        || !aiRefs.isEmpty()
        || !comboNames.isEmpty()
        || !identityNames.isEmpty()
        || !seriesCovers.isEmpty()
        || !arcCovers.isEmpty()
        || !pageIds.isEmpty()) {
      throw new ConflictException(
          buildReferenceSummary(
              characterIds,
              seriesLinkNames,
              arcLinkTitles,
              aiRefs,
              comboNames,
              identityNames,
              seriesCovers,
              arcCovers,
              pageRefs));
    }
    tagRelRepository.deleteByAssetId(id);
    characterRelRepository.deleteByAssetId(id);
    assetSeriesRelRepository.deleteByAssetId(id);
    assetArcRelRepository.deleteByAssetId(id);
    unlinkedOrderRepository.deleteByAssetId(id);
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
  public void reorderByScope(Long categoryId, String scope, Long scopeId, List<Long> orderedIds) {
    if (categoryId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId is required");
    }
    if (orderedIds == null || orderedIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderedIds is required");
    }
    if (orderedIds.stream().anyMatch(Objects::isNull)
        || new HashSet<>(orderedIds).size() != orderedIds.size()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderedIds must be unique and non-null");
    }
    if (!categoryRepository.existsById(categoryId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found: " + categoryId);
    }
    String normalizedScope = normalizeReorderScope(scope);
    requireScopeIdWhenNeeded(normalizedScope, scopeId);
    ensureScopeEntityExists(normalizedScope, scopeId);

    Set<Long> expectedIds = expectedIdsForScope(categoryId, normalizedScope, scopeId);
    if (expectedIds.size() != orderedIds.size() || !expectedIds.containsAll(orderedIds)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "orderedIds must match all assets in the scope");
    }

    for (int i = 0; i < orderedIds.size(); i++) {
      writeScopeSortOrder(categoryId, normalizedScope, scopeId, orderedIds.get(i), i);
    }
  }

  private static String normalizeReorderScope(String scope) {
    if (scope == null || scope.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope is required");
    }
    String normalized = scope.trim().toUpperCase(Locale.ROOT);
    if (!Set.of("CHARACTER", "SERIES", "ARC", "UNLINKED").contains(normalized)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope: " + scope);
    }
    return normalized;
  }

  private static void requireScopeIdWhenNeeded(String scope, Long scopeId) {
    if (!"UNLINKED".equals(scope) && scopeId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scopeId is required for " + scope);
    }
  }

  private void ensureScopeEntityExists(String scope, Long scopeId) {
    switch (scope) {
      case "CHARACTER" -> {
        if (!characterProfileRepository.existsById(scopeId)) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "character not found: " + scopeId);
        }
      }
      case "SERIES" -> {
        if (!storySeriesRepository.existsById(scopeId)) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "series not found: " + scopeId);
        }
      }
      case "ARC" -> {
        if (!storyArcRepository.existsById(scopeId)) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "arc not found: " + scopeId);
        }
      }
      default -> {
        // UNLINKED: no entity
      }
    }
  }

  private Set<Long> expectedIdsForScope(Long categoryId, String scope, Long scopeId) {
    List<Asset> assets =
        switch (scope) {
          case "CHARACTER" -> list(categoryId, "NORMAL", "", null, scopeId);
          case "SERIES" -> list(categoryId, "NORMAL", "", null, null, "SERIES", scopeId, null);
          case "ARC" -> list(categoryId, "NORMAL", "", null, null, "ARC", null, scopeId);
          case "UNLINKED" -> list(categoryId, "NORMAL", "", "unlinked", null);
          default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope: " + scope);
        };
    return assets.stream().map(Asset::getId).collect(Collectors.toSet());
  }

  private void writeScopeSortOrder(
      Long categoryId, String scope, Long scopeId, Long assetId, int sortOrder) {
    switch (scope) {
      case "CHARACTER" -> {
        AssetCharacterRel rel =
            characterRelRepository
                .findById(new AssetCharacterRelId(assetId, scopeId))
                .orElseThrow(
                    () ->
                        new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "character rel missing for asset: " + assetId));
        rel.setSortOrder(sortOrder);
        characterRelRepository.save(rel);
      }
      case "SERIES" -> {
        AssetSeriesRel rel =
            assetSeriesRelRepository
                .findById(new AssetSeriesRelId(assetId, scopeId))
                .orElseThrow(
                    () ->
                        new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "series rel missing for asset: " + assetId));
        rel.setSortOrder(sortOrder);
        assetSeriesRelRepository.save(rel);
      }
      case "ARC" -> {
        AssetArcRel rel =
            assetArcRelRepository
                .findById(new AssetArcRelId(assetId, scopeId))
                .orElseThrow(
                    () ->
                        new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "arc rel missing for asset: " + assetId));
        rel.setSortOrder(sortOrder);
        assetArcRelRepository.save(rel);
      }
      case "UNLINKED" -> {
        AssetUnlinkedOrder order =
            unlinkedOrderRepository
                .findByCategoryIdAndAssetId(categoryId, assetId)
                .orElseGet(() -> new AssetUnlinkedOrder(categoryId, assetId, sortOrder));
        order.setSortOrder(sortOrder);
        unlinkedOrderRepository.save(order);
      }
      default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid scope: " + scope);
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
    LinkedHashSet<Long> unique = uniqueIds(characterIds);
    for (Long characterId : unique) {
      if (!characterProfileRepository.existsById(characterId)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "character not found: " + characterId);
      }
    }
    Asset asset = getRaw(assetId);
    characterRelRepository.deleteByAssetId(assetId);
    characterRelRepository.flush();
    for (Long characterId : unique) {
      int next =
          characterRelRepository
              .findMaxSortOrderByCharacterIdAndCategoryId(characterId, asset.getCategoryId())
              .orElse(-1)
              + 1;
      characterRelRepository.save(new AssetCharacterRel(assetId, characterId, next));
    }
    if (unique.isEmpty()) {
      ensureUnlinkedOrder(asset);
    } else {
      unlinkedOrderRepository.deleteByCategoryIdAndAssetId(asset.getCategoryId(), assetId);
    }
  }

  private void applyLinks(
      Long assetId,
      AssetLinkType linkType,
      List<Long> seriesIds,
      List<Long> arcIds,
      List<Long> characterIds) {
    if (linkType == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "linkType is required");
    }
    switch (linkType) {
      case SERIES -> {
        LinkedHashSet<Long> unique = uniqueIds(seriesIds);
        if (unique.isEmpty()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "seriesIds is required");
        }
        for (Long seriesId : unique) {
          if (!storySeriesRepository.existsById(seriesId)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "series not found: " + seriesId);
          }
        }
        clearArcLinks(assetId);
        clearCharacterLinks(assetId);
        writeSeriesLinks(assetId, unique);
      }
      case ARC -> {
        LinkedHashSet<Long> unique = uniqueIds(arcIds);
        if (unique.isEmpty()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arcIds is required");
        }
        for (Long arcId : unique) {
          if (!storyArcRepository.existsById(arcId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "arc not found: " + arcId);
          }
        }
        clearSeriesLinks(assetId);
        clearCharacterLinks(assetId);
        writeArcLinks(assetId, unique);
      }
      case CHARACTER -> {
        LinkedHashSet<Long> unique = uniqueIds(characterIds);
        if (unique.isEmpty()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "characterIds is required");
        }
        clearSeriesLinks(assetId);
        clearArcLinks(assetId);
        replaceCharacters(assetId, List.copyOf(unique));
      }
      case NONE -> {
        clearSeriesLinks(assetId);
        clearArcLinks(assetId);
        clearCharacterLinks(assetId);
      }
    }
  }

  private void writeSeriesLinks(Long assetId, LinkedHashSet<Long> seriesIds) {
    Asset asset = getRaw(assetId);
    assetSeriesRelRepository.deleteByAssetId(assetId);
    assetSeriesRelRepository.flush();
    for (Long seriesId : seriesIds) {
      int next =
          assetSeriesRelRepository
              .findMaxSortOrderBySeriesIdAndCategoryId(seriesId, asset.getCategoryId())
              .orElse(-1)
              + 1;
      assetSeriesRelRepository.save(new AssetSeriesRel(assetId, seriesId, next));
    }
  }

  private void writeArcLinks(Long assetId, LinkedHashSet<Long> arcIds) {
    Asset asset = getRaw(assetId);
    assetArcRelRepository.deleteByAssetId(assetId);
    assetArcRelRepository.flush();
    for (Long arcId : arcIds) {
      int next =
          assetArcRelRepository
              .findMaxSortOrderByArcIdAndCategoryId(arcId, asset.getCategoryId())
              .orElse(-1)
              + 1;
      assetArcRelRepository.save(new AssetArcRel(assetId, arcId, next));
    }
  }

  private void clearSeriesLinks(Long assetId) {
    assetSeriesRelRepository.deleteByAssetId(assetId);
    assetSeriesRelRepository.flush();
  }

  private void clearArcLinks(Long assetId) {
    assetArcRelRepository.deleteByAssetId(assetId);
    assetArcRelRepository.flush();
  }

  private void clearCharacterLinks(Long assetId) {
    characterRelRepository.deleteByAssetId(assetId);
    characterRelRepository.flush();
    if (characterRelRepository.findCharacterIdsByAssetId(assetId).isEmpty()) {
      ensureUnlinkedOrder(getRaw(assetId));
    }
  }

  private void ensureUnlinkedOrder(Asset asset) {
    Long categoryId = asset.getCategoryId();
    Long assetId = asset.getId();
    var existing = unlinkedOrderRepository.findByCategoryIdAndAssetId(categoryId, assetId);
    if (existing.isPresent()) {
      return;
    }
    int next = unlinkedOrderRepository.findMaxSortOrderByCategoryId(categoryId).orElse(-1) + 1;
    unlinkedOrderRepository.save(new AssetUnlinkedOrder(categoryId, assetId, next));
  }

  private List<Asset> applyScopeOrder(
      List<Asset> assets,
      Long categoryId,
      String characterFilter,
      Long characterId,
      String linkType,
      Long seriesId,
      Long arcId) {
    if (characterId != null) {
      return sortByMap(assets, loadCharacterOrders(characterId, assets));
    }
    if ("unlinked".equals(characterFilter) && !hasConcreteSeriesOrArcFilter(linkType, seriesId, arcId)) {
      return sortByMap(assets, loadUnlinkedOrders(categoryId, assets));
    }
    if (seriesId != null && "SERIES".equalsIgnoreCase(linkType)) {
      return sortByMap(assets, loadSeriesOrders(seriesId, assets));
    }
    if (arcId != null && "ARC".equalsIgnoreCase(linkType)) {
      return sortByMap(assets, loadArcOrders(arcId, assets));
    }
    return assets;
  }

  private static boolean hasConcreteSeriesOrArcFilter(String linkType, Long seriesId, Long arcId) {
    if (seriesId != null && "SERIES".equalsIgnoreCase(linkType)) {
      return true;
    }
    if (arcId != null && "ARC".equalsIgnoreCase(linkType)) {
      return true;
    }
    return false;
  }

  private List<Asset> sortByMap(List<Asset> assets, Map<Long, Integer> scopeOrder) {
    return assets.stream()
        .sorted(
            Comparator.comparingInt(
                    (Asset a) -> scopeOrder.getOrDefault(a.getId(), a.getSortOrder()))
                .thenComparingLong(Asset::getId))
        .toList();
  }

  private Map<Long, Integer> loadCharacterOrders(Long characterId, List<Asset> assets) {
    Set<Long> ids = assets.stream().map(Asset::getId).collect(Collectors.toSet());
    Map<Long, Integer> map = new HashMap<>();
    for (AssetCharacterRel rel : characterRelRepository.findByCharacterId(characterId)) {
      if (ids.contains(rel.getAssetId())) {
        map.put(rel.getAssetId(), rel.getSortOrder());
      }
    }
    return map;
  }

  private Map<Long, Integer> loadUnlinkedOrders(Long categoryId, List<Asset> assets) {
    if (categoryId == null) {
      return Map.of();
    }
    Set<Long> ids = assets.stream().map(Asset::getId).collect(Collectors.toSet());
    Map<Long, Integer> map = new HashMap<>();
    for (AssetUnlinkedOrder order :
        unlinkedOrderRepository.findByCategoryIdOrderBySortOrderAscAssetIdAsc(categoryId)) {
      if (ids.contains(order.getAssetId())) {
        map.put(order.getAssetId(), order.getSortOrder());
      }
    }
    return map;
  }

  private Map<Long, Integer> loadSeriesOrders(Long seriesId, List<Asset> assets) {
    Map<Long, Integer> map = new HashMap<>();
    for (Asset asset : assets) {
      assetSeriesRelRepository
          .findById(new AssetSeriesRelId(asset.getId(), seriesId))
          .ifPresent(rel -> map.put(asset.getId(), rel.getSortOrder()));
    }
    return map;
  }

  private Map<Long, Integer> loadArcOrders(Long arcId, List<Asset> assets) {
    Map<Long, Integer> map = new HashMap<>();
    for (Asset asset : assets) {
      assetArcRelRepository
          .findById(new AssetArcRelId(asset.getId(), arcId))
          .ifPresent(rel -> map.put(asset.getId(), rel.getSortOrder()));
    }
    return map;
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
    List<Long> characterIds = characterRelRepository.findCharacterIdsByAssetId(asset.getId());
    List<Long> seriesIds = assetSeriesRelRepository.findSeriesIdsByAssetId(asset.getId());
    List<Long> arcIds = assetArcRelRepository.findArcIdsByAssetId(asset.getId());
    asset.setCharacterIds(characterIds);
    asset.setSeriesIds(seriesIds);
    asset.setArcIds(arcIds);
    asset.setLinkType(deriveLinkType(characterIds, seriesIds, arcIds));
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

  /** characterId wins; otherwise unlinked|all (unrecognized → all). */
  private static String normalizeCharacterFilter(String characterFilter, Long characterId) {
    if (characterId != null) {
      return "all";
    }
    if (characterFilter != null && "unlinked".equalsIgnoreCase(characterFilter.trim())) {
      return "unlinked";
    }
    return "all";
  }

  private static String normalizeLinkType(String linkType) {
    if (linkType == null || linkType.isBlank()) {
      return "";
    }
    String normalized = linkType.trim().toUpperCase(Locale.ROOT);
    try {
      AssetLinkType.valueOf(normalized);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid linkType: " + linkType);
    }
    return normalized;
  }

  private static boolean shouldApplyLinks(
      AssetLinkType linkType, List<Long> seriesIds, List<Long> arcIds, List<Long> characterIds) {
    if (linkType != null && linkType != AssetLinkType.NONE) {
      return true;
    }
    return hasAnyIds(seriesIds) || hasAnyIds(arcIds) || hasAnyIds(characterIds);
  }

  private static AssetLinkType resolveUploadLinkType(
      AssetLinkType linkType, List<Long> seriesIds, List<Long> arcIds, List<Long> characterIds) {
    if (linkType != null && linkType != AssetLinkType.NONE) {
      return linkType;
    }
    if (hasAnyIds(characterIds)) {
      return AssetLinkType.CHARACTER;
    }
    if (hasAnyIds(arcIds)) {
      return AssetLinkType.ARC;
    }
    return AssetLinkType.SERIES;
  }

  private static AssetLinkType deriveLinkType(
      List<Long> characterIds, List<Long> seriesIds, List<Long> arcIds) {
    if (characterIds != null && !characterIds.isEmpty()) {
      return AssetLinkType.CHARACTER;
    }
    if (arcIds != null && !arcIds.isEmpty()) {
      return AssetLinkType.ARC;
    }
    if (seriesIds != null && !seriesIds.isEmpty()) {
      return AssetLinkType.SERIES;
    }
    return AssetLinkType.NONE;
  }

  private static boolean hasAnyIds(List<Long> ids) {
    return ids != null && ids.stream().anyMatch(Objects::nonNull);
  }

  private static LinkedHashSet<Long> uniqueIds(List<Long> ids) {
    LinkedHashSet<Long> unique = new LinkedHashSet<>();
    if (ids == null) {
      return unique;
    }
    for (Long id : ids) {
      if (id != null) {
        unique.add(id);
      }
    }
    return unique;
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

  private String buildReferenceSummary(
      List<Long> characterIds,
      List<String> seriesLinkNames,
      List<String> arcLinkTitles,
      List<AiReferenceItem> aiRefs,
      List<String> comboNames,
      List<String> identityNames,
      List<StorySeries> seriesCovers,
      List<StoryArc> arcCovers,
      List<StoryPage> pageRefs) {
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
    if (!seriesLinkNames.isEmpty()) {
      sb.append(" 系列关联: [").append(String.join(", ", seriesLinkNames)).append("].");
    }
    if (!arcLinkTitles.isEmpty()) {
      sb.append(" 篇章关联: [").append(String.join(", ", arcLinkTitles)).append("].");
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
    if (!comboNames.isEmpty()) {
      sb.append(" 组合引用: [").append(String.join(", ", comboNames)).append("].");
    }
    if (!identityNames.isEmpty()) {
      sb.append(" 本体引用: [").append(String.join(", ", identityNames)).append("].");
    }
    if (!seriesCovers.isEmpty()) {
      String items =
          seriesCovers.stream()
              .map(s -> s.getName() + "(" + s.getCode() + ")")
              .collect(Collectors.joining(", "));
      sb.append(" 系列封面: [").append(items).append("].");
    }
    if (!arcCovers.isEmpty()) {
      String items =
          arcCovers.stream()
              .map(a -> a.getTitle() + "(" + a.getCode() + ")")
              .collect(Collectors.joining(", "));
      sb.append(" 篇章封面: [").append(items).append("].");
    }
    if (!pageRefs.isEmpty()) {
      String items =
          pageRefs.stream()
              .map(p -> p.getTitle() + "(id=" + p.getId() + ")")
              .collect(Collectors.joining(", "));
      sb.append(" 页面画面组: [").append(items).append("].");
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

package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_reorder_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetReorderTest {

  @Autowired AssetService assetService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void reorderUpdatesSortOrder() {
    Long categoryId = persistCategory("reorder-cat", "排序分类").getId();
    Long id1 = persistAsset(categoryId, "a", 0).getId();
    Long id2 = persistAsset(categoryId, "b", 1).getId();
    Long id3 = persistAsset(categoryId, "c", 2).getId();

    assetService.reorder(categoryId, List.of(id3, id1, id2));

    assertThat(
            assetRepository.findAllByCategoryIdAndStatusOrderBySortOrderAsc(categoryId, NORMAL).stream()
                .map(Asset::getId))
        .containsExactly(id3, id1, id2);
  }

  @Test
  void moveUpdatesCategoryAndSortOrderWithoutCopyingFile() {
    Long sourceCategoryId = persistCategory("move-src", "源分类").getId();
    Long targetCategoryId = persistCategory("move-tgt", "目标分类").getId();
    Long sourceFirst = persistAsset(sourceCategoryId, "src-a", 0).getId();
    Long sourceSecond = persistAsset(sourceCategoryId, "src-b", 1).getId();
    Long targetExisting = persistAsset(targetCategoryId, "tgt-a", 0).getId();
    String originalPath =
        assetRepository.findById(sourceFirst).orElseThrow().getStoragePath();

    Asset moved = assetService.move(sourceFirst, targetCategoryId, 0);

    assertThat(moved.getCategoryId()).isEqualTo(targetCategoryId);
    assertThat(moved.getStoragePath()).isEqualTo(originalPath);
    assertThat(
            assetRepository
                .findAllByCategoryIdAndStatusOrderBySortOrderAsc(targetCategoryId, NORMAL)
                .stream()
                .map(Asset::getId))
        .containsExactly(sourceFirst, targetExisting);
    assertThat(
            assetRepository
                .findAllByCategoryIdAndStatusOrderBySortOrderAsc(sourceCategoryId, NORMAL)
                .stream()
                .map(Asset::getId))
        .containsExactly(sourceSecond);
  }

  private AssetCategory persistCategory(String code, String name) {
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    return categoryRepository.save(category);
  }

  private Asset persistAsset(Long categoryId, String name, int sortOrder) {
    Asset asset = new Asset();
    asset.setDisplayName(name);
    asset.setCategoryId(categoryId);
    asset.setSortOrder(sortOrder);
    asset.setStatus(NORMAL);
    asset.setStoragePath("assets/test/" + name + ".png");
    return assetRepository.save(asset);
  }
}

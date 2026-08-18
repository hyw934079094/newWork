package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.dto.AssetPageResponse;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_list_page_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetListPaginationTest {

  @Autowired AssetService assetService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void listPageDefaultsSliceAndTotal() {
    Long categoryId = persistCategory("page-cat").getId();
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      ids.add(persistAsset(categoryId, "a-" + i, i).getId());
    }

    AssetPageResponse page0 =
        assetService.listPage(categoryId, "NORMAL", null, null, null, null, null, null, 0, 2);
    assertThat(page0.total()).isEqualTo(5);
    assertThat(page0.page()).isEqualTo(0);
    assertThat(page0.size()).isEqualTo(2);
    assertThat(page0.items()).hasSize(2);
    assertThat(page0.items()).extracting(Asset::getId).containsExactly(ids.get(0), ids.get(1));

    AssetPageResponse page1 =
        assetService.listPage(categoryId, "NORMAL", null, null, null, null, null, null, 1, 2);
    assertThat(page1.total()).isEqualTo(5);
    assertThat(page1.items()).extracting(Asset::getId).containsExactly(ids.get(2), ids.get(3));

    AssetPageResponse empty =
        assetService.listPage(categoryId, "NORMAL", null, null, null, null, null, null, 10, 2);
    assertThat(empty.total()).isEqualTo(5);
    assertThat(empty.items()).isEmpty();
  }

  @Test
  void listPageRejectsInvalidPageOrSize() {
    assertThatThrownBy(
            () ->
                assetService.listPage(null, "NORMAL", null, null, null, null, null, null, -1, 48))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    assertThatThrownBy(
            () -> assetService.listPage(null, "NORMAL", null, null, null, null, null, null, 0, 0))
        .isInstanceOf(ResponseStatusException.class);

    assertThatThrownBy(
            () ->
                assetService.listPage(null, "NORMAL", null, null, null, null, null, null, 0, 5001))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void listPageHydratesTagAndLinkFields() {
    Long categoryId = persistCategory("hydrate-cat").getId();
    Asset asset = persistAsset(categoryId, "tagged", 0);
    assetService.update(
        asset.getId(),
        AssetUpdateRequest.builder()
            .displayName("tagged")
            .description(null)
            .chapterRefPlaceholder(null)
            .tagNames(List.of("alpha"))
            .build());

    AssetPageResponse page =
        assetService.listPage(categoryId, "NORMAL", null, null, null, null, null, null, 0, 48);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).getTagNames()).contains("alpha");
  }

  private AssetCategory persistCategory(String code) {
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(code);
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

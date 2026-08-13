package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.AiReferenceItem;
import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.dto.AiReferenceItemRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_ai_ref_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AiReferenceServiceTest {

  @Autowired AiReferenceService aiReferenceService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void replaceItemsKeepsOrder() {
    Long a1 = persistAsset("ai-ref-a1").getId();
    Long a2 = persistAsset("ai-ref-a2").getId();

    aiReferenceService.replaceCurrentItems(
        List.of(
            new AiReferenceItemRequest(a1, "外貌", null, null),
            new AiReferenceItemRequest(a2, "服装", null, null)));

    assertThat(aiReferenceService.getCurrent().getItems())
        .extracting(AiReferenceItem::getAssetId)
        .containsExactly(a1, a2);
  }

  @Test
  void replaceItemsRejectsUnknownAssetId() {
    assertThatThrownBy(
            () ->
                aiReferenceService.replaceCurrentItems(
                    List.of(new AiReferenceItemRequest(9_999_999L, "外貌", null, null))))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void replaceItemsRejectsNullAssetId() {
    assertThatThrownBy(
            () ->
                aiReferenceService.replaceCurrentItems(
                    List.of(new AiReferenceItemRequest(null, "外貌", null, null))))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST));
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("ai-" + name);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    category = categoryRepository.save(category);

    Asset asset = new Asset();
    asset.setDisplayName(name);
    asset.setCategoryId(category.getId());
    asset.setSortOrder(0);
    asset.setStatus(NORMAL);
    asset.setStoragePath("assets/test/" + name + ".png");
    return assetRepository.save(asset);
  }
}

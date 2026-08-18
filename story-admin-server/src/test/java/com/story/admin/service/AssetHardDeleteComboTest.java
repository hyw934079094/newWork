package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.dto.ComboMemberRequest;
import com.story.admin.dto.ComboUpsertRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetComboMemberRepository;
import com.story.admin.repository.AssetRepository;
import java.math.BigDecimal;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_hard_delete_combo_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetHardDeleteComboTest {

  @Autowired AssetService assetService;
  @Autowired ComboService comboService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired AssetComboMemberRepository comboMemberRepository;

  @Test
  void hardDeleteCascadesWhenUsedInCombo() {
    Long assetId = persistAsset("combo-ref-asset").getId();
    var combo =
        comboService.create(
            new ComboUpsertRequest(
                "表情组合甲",
                "1",
                new BigDecimal("1.0"),
                true,
                null,
                List.of(new ComboMemberRequest(assetId, 1)),
                List.of()));

    assetService.hardDelete(assetId);

    assertThat(assetRepository.findById(assetId)).isEmpty();
    assertThat(comboMemberRepository.findByAssetId(assetId)).isEmpty();
    assertThat(comboMemberRepository.findByComboIdOrderBySortOrderAscMemberNoAsc(combo.id()))
        .isEmpty();
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("hdc-" + name);
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
    asset.setContentType("image/png");
    return assetRepository.save(asset);
  }
}

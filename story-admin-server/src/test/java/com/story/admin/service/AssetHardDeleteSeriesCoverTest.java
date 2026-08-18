package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.SeriesStatus;
import com.story.admin.domain.StorySeries;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.StorySeriesRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_hard_delete_series_cover_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetHardDeleteSeriesCoverTest {

  @Autowired AssetService assetService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired StorySeriesRepository storySeriesRepository;

  @Test
  void hardDeleteCascadesWhenAssetIsSeriesCover() {
    Long assetId = persistAsset("series-cover").getId();
    StorySeries series = new StorySeries();
    series.setCode("S000099");
    series.setName("暗夜物语");
    series.setStatus(SeriesStatus.DRAFT);
    series.setCoverAssetId(assetId);
    series.setSortOrder(0);
    series = storySeriesRepository.save(series);

    assetService.hardDelete(assetId);

    assertThat(assetRepository.findById(assetId)).isEmpty();
    assertThat(storySeriesRepository.findById(series.getId()).orElseThrow().getCoverAssetId())
        .isNull();
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("hdsc-" + name);
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

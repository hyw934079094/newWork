package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StoryPage;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.PageCreateRequest;
import com.story.admin.dto.PageUpdateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import com.story.admin.repository.PageAssetRefRepository;
import com.story.admin.repository.StoryPageRepository;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_hard_delete_page_beat_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetHardDeletePageBeatTest {

  @Autowired AssetService assetService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;
  @Autowired ArcService arcService;
  @Autowired SeriesService seriesService;
  @Autowired PageService pageService;
  @Autowired StoryPageRepository storyPageRepository;
  @Autowired PageAssetRefRepository pageAssetRefRepository;

  @Test
  void hardDeleteCascadesWhenAssetIsBeatCover() {
    Long assetId = persistAsset("beat-cover").getId();
    Long seriesId =
        seriesService.create(new SeriesCreateRequest("测试系列", null, null, null, null)).getId();
    StoryArc arc = arcService.create(seriesId, new ArcCreateRequest("开篇", null, null, null));
    StoryPage page = pageService.create(arc.getId(), new PageCreateRequest("第一页"));
    String json =
        "[{\"type\":\"BEAT\",\"coverAssetId\":"
            + assetId
            + ",\"children\":[{\"type\":\"BODY\",\"text\":\"hi\"}]}]";
    pageService.update(page.getId(), new PageUpdateRequest("第一页", json));

    assetService.hardDelete(assetId);

    assertThat(assetRepository.findById(assetId)).isEmpty();
    assertThat(pageAssetRefRepository.findByAssetId(assetId)).isEmpty();
    StoryPage afterDelete = storyPageRepository.findById(page.getId()).orElseThrow();
    assertThat(afterDelete.getContentJson()).contains("\"type\":\"COVER\"");
    assertThat(afterDelete.getContentJson()).doesNotContain("\"assetId\":" + assetId);
    assertThat(afterDelete.getContentJson()).contains("\"coverAssetId\":null");
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("hdpb-" + name);
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

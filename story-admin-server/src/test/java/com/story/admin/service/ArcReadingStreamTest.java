package com.story.admin.service;

import static com.story.admin.domain.AssetStatus.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.StoryArc;
import com.story.admin.domain.StoryPage;
import com.story.admin.dto.ArcCreateRequest;
import com.story.admin.dto.ArcReadingStreamResponse;
import com.story.admin.dto.PageCreateRequest;
import com.story.admin.dto.PageUpdateRequest;
import com.story.admin.dto.SeriesCreateRequest;
import com.story.admin.repository.AssetCategoryRepository;
import com.story.admin.repository.AssetRepository;
import java.util.List;
import java.util.Map;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_arc_reading_stream_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class ArcReadingStreamTest {

  @Autowired ArcService arcService;
  @Autowired SeriesService seriesService;
  @Autowired PageService pageService;
  @Autowired AssetRepository assetRepository;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void readingStreamOrdersPagesAndBeatImageBeforeText() {
    Long seriesId =
        seriesService.create(new SeriesCreateRequest("阅读流系列", null, null, null, null)).getId();
    StoryArc arc = arcService.create(seriesId, new ArcCreateRequest("第一卷", null, null, null));
    Asset beatCover = persistAsset("beat-cover-stream");

    StoryPage page1 = pageService.create(arc.getId(), new PageCreateRequest("P1"));
    String page1Json =
        "[{\"type\":\"TITLE\",\"text\":\"T1\"},{\"type\":\"BEAT\",\"coverAssetId\":"
            + beatCover.getId()
            + ",\"children\":[{\"type\":\"BODY\",\"text\":\"B1\"},{\"type\":\"DIALOGUE\",\"text\":\"D1\"}]}]";
    pageService.update(page1.getId(), new PageUpdateRequest("P1", page1Json));

    StoryPage page2 = pageService.create(arc.getId(), new PageCreateRequest("P2"));
    pageService.update(page2.getId(), new PageUpdateRequest("P2", "[]"));

    ArcReadingStreamResponse stream = arcService.readingStream(arc.getId());

    assertThat(stream.arcId()).isEqualTo(arc.getId());
    assertThat(stream.arcTitle()).isEqualTo("第一卷");
    assertThat(stream.pageCount()).isEqualTo(2);

    List<String> types = stream.segments().stream().map(s -> (String) s.get("type")).toList();
    assertThat(types)
        .containsExactly(
            "ARC_TITLE",
            "PAGE_TITLE",
            "TITLE",
            "IMAGE",
            "BODY",
            "DIALOGUE",
            "PAGE_TITLE");

    Map<String, Object> image =
        stream.segments().stream()
            .filter(s -> "IMAGE".equals(s.get("type")))
            .findFirst()
            .orElseThrow();
    assertThat(image.get("role")).isEqualTo("BEAT_COVER");
    assertThat(image.get("assetId")).isEqualTo(beatCover.getId());
    assertThat(image.get("contentPath"))
        .isEqualTo("/api/assets/" + beatCover.getId() + "/content");
    assertThat(image.get("pageId")).isEqualTo(page1.getId());

    List<Map<String, Object>> pageTitles =
        stream.segments().stream().filter(s -> "PAGE_TITLE".equals(s.get("type"))).toList();
    assertThat(pageTitles.get(0).get("text")).isEqualTo("P1");
    assertThat(pageTitles.get(0).get("pageId")).isEqualTo(page1.getId());
    assertThat(pageTitles.get(1).get("text")).isEqualTo("P2");
    assertThat(pageTitles.get(1).get("pageId")).isEqualTo(page2.getId());
  }

  @Test
  void readingStreamNotFound() {
    assertThatThrownBy(() -> arcService.readingStream(-1L))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  private Asset persistAsset(String name) {
    AssetCategory category = new AssetCategory();
    category.setCode("stream-" + name);
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

package com.story.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.AssetStatus;
import com.story.admin.repository.AssetCategoryRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetServiceTest {

  @Autowired AssetService assetService;
  @Autowired StorageService storageService;
  @Autowired ConfigService configService;
  @Autowired CategoryService categoryService;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void uploadPersistsAssetAndFile(@TempDir Path dir) throws Exception {
    configService.upsert("storage.root", dir.toAbsolutePath().toString(), "test");
    AssetCategory category = persistCategory("portrait", "人物立绘", 1, true);

    MockMultipartFile file =
        new MockMultipartFile("files", "dot.png", "image/png", oneByOnePng());

    List<Asset> uploaded = assetService.upload(category.getId(), new MockMultipartFile[] {file});

    assertThat(uploaded).hasSize(1);
    Asset asset = uploaded.get(0);
    assertThat(asset.getStatus()).isEqualTo(AssetStatus.NORMAL);
    assertThat(asset.getDisplayName()).isEqualTo("dot");
    assertThat(asset.getStoragePath()).matches("assets/\\d{4}/\\d{2}/[0-9a-fA-F-]+\\.png");
    Path absolute = storageService.resolveAbsolute(asset.getStoragePath());
    assertThat(Files.exists(absolute)).isTrue();
    assertThat(absolute.startsWith(dir.toAbsolutePath().normalize())).isTrue();
  }

  @Test
  void deletePresetCategoryRejected() {
    AssetCategory preset = persistCategory("expression", "人物表情", 1, true);
    assertThatThrownBy(() -> categoryService.delete(preset.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("400");
  }

  private AssetCategory persistCategory(
      String code, String name, int sortOrder, boolean systemPreset) {
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(name);
    category.setSortOrder(sortOrder);
    category.setSystemPreset(systemPreset);
    return categoryRepository.save(category);
  }

  private static byte[] oneByOnePng() throws Exception {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}

package com.story.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetCategory;
import com.story.admin.domain.AssetStatus;
import com.story.admin.repository.AssetCategoryRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_asset_replace_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AssetReplaceContentTest {

  @Autowired AssetService assetService;
  @Autowired StorageService storageService;
  @Autowired ConfigService configService;
  @Autowired AssetCategoryRepository categoryRepository;

  @Test
  void replaceContentRejectsNonNormal(@TempDir Path dir) throws Exception {
    configService.upsert("storage.root", dir.toAbsolutePath().toString(), "test");
    AssetCategory category = persistCategory("replace-reject", "替换拒绝");
    MockMultipartFile original =
        new MockMultipartFile("files", "keep.png", "image/png", solidPng(1, 1, Color.BLACK));
    Asset uploaded =
        assetService.upload(category.getId(), new MockMultipartFile[] {original}).get(0);
    assetService.recycle(uploaded.getId());

    MockMultipartFile replacement =
        new MockMultipartFile("file", "new.png", "image/png", solidPng(2, 2, Color.RED));

    assertThatThrownBy(() -> assetService.replaceContent(uploaded.getId(), replacement))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException rse = (ResponseStatusException) ex;
              assertThat(rse.getStatusCode().is4xxClientError()).isTrue();
            });

    Asset after = assetService.get(uploaded.getId());
    assertThat(after.getStatus()).isEqualTo(AssetStatus.DELETED);
    assertThat(after.getChecksum()).isEqualTo(uploaded.getChecksum());
  }

  @Test
  void replaceContentOverwritesFileKeepsDisplayName(@TempDir Path dir) throws Exception {
    configService.upsert("storage.root", dir.toAbsolutePath().toString(), "test");
    AssetCategory category = persistCategory("replace-ok", "替换成功");
    byte[] beforeBytes = solidPng(1, 1, Color.BLACK);
    MockMultipartFile original =
        new MockMultipartFile("files", "portrait.png", "image/png", beforeBytes);
    Asset uploaded =
        assetService.upload(category.getId(), new MockMultipartFile[] {original}).get(0);
    String storagePath = uploaded.getStoragePath();
    String displayName = uploaded.getDisplayName();
    String beforeChecksum = uploaded.getChecksum();
    Path absolute = storageService.resolveAbsolute(storagePath);
    assertThat(Files.readAllBytes(absolute)).isEqualTo(beforeBytes);

    byte[] afterBytes = solidPng(3, 4, Color.BLUE);
    MockMultipartFile replacement =
        new MockMultipartFile("file", "updated.png", "image/png", afterBytes);

    Asset replaced = assetService.replaceContent(uploaded.getId(), replacement);

    assertThat(replaced.getId()).isEqualTo(uploaded.getId());
    assertThat(replaced.getDisplayName()).isEqualTo(displayName);
    assertThat(replaced.getStoragePath()).isEqualTo(storagePath);
    assertThat(replaced.getOriginalFilename()).isEqualTo("updated.png");
    assertThat(replaced.getChecksum()).isNotEqualTo(beforeChecksum);
    assertThat(replaced.getWidth()).isEqualTo(3);
    assertThat(replaced.getHeight()).isEqualTo(4);
    assertThat(replaced.getSizeBytes()).isEqualTo((long) afterBytes.length);
    assertThat(Files.readAllBytes(absolute)).isEqualTo(afterBytes);
    assertThat(replaced.getCategoryId()).isEqualTo(uploaded.getCategoryId());
    assertThat(replaced.getSortOrder()).isEqualTo(uploaded.getSortOrder());
    assertThat(replaced.getStatus()).isEqualTo(AssetStatus.NORMAL);
  }

  private AssetCategory persistCategory(String code, String name) {
    AssetCategory category = new AssetCategory();
    category.setCode(code);
    category.setName(name);
    category.setSortOrder(1);
    category.setSystemPreset(false);
    return categoryRepository.save(category);
  }

  private static byte[] solidPng(int width, int height, Color color) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        image.setRGB(x, y, color.getRGB());
      }
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}

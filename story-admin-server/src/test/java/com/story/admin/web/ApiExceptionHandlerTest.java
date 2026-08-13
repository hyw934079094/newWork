package com.story.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.story.admin.config.UploadProperties;
import com.story.admin.exception.ConflictException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

class ApiExceptionHandlerTest {

  private ApiExceptionHandler handler;

  @BeforeEach
  void setUp() {
    UploadProperties uploadProperties = new UploadProperties();
    uploadProperties.setMaxFileSizeMb(20);
    uploadProperties.setAllowedExtensions("jpg,jpeg,png,webp,gif");
    handler = new ApiExceptionHandler(uploadProperties);
  }

  @Test
  void maxUploadSizeExceededReturns400WithChineseMessage() {
    MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(20L * 1024 * 1024);

    ResponseEntity<Map<String, Object>> response = handler.handleMaxUploadSize(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("status", 400);
    assertThat(response.getBody().get("message").toString()).contains("20MB");
    assertThat(response.getBody().get("message").toString()).contains("上传文件超过大小限制");
  }

  @Test
  void multipartExceptionReturns400WithFormatAndSizeHint() {
    MultipartException ex = new MultipartException("Failed to parse multipart request");

    ResponseEntity<Map<String, Object>> response = handler.handleMultipart(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("status", 400);
    assertThat(response.getBody().get("message").toString()).contains("20MB");
    assertThat(response.getBody().get("message").toString()).contains("上传失败");
  }

  @Test
  void conflictExceptionReturns409() {
    ConflictException ex = new ConflictException("无法删除人物：仍存在素材关联");

    ResponseEntity<Map<String, Object>> response = handler.handleConflict(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("status", 409);
    assertThat(response.getBody()).containsEntry("error", "Conflict");
    assertThat(response.getBody().get("message").toString()).contains("无法删除人物");
  }
}

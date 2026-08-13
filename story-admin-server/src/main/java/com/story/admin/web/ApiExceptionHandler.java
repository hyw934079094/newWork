package com.story.admin.web;

import com.story.admin.config.UploadProperties;
import com.story.admin.exception.ConflictException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

  private final UploadProperties uploadProperties;

  public ApiExceptionHandler(UploadProperties uploadProperties) {
    this.uploadProperties = uploadProperties;
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    return badRequest(uploadSizeMessage());
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<Map<String, Object>> handleMultipart(MultipartException ex) {
    if (ex instanceof MaxUploadSizeExceededException) {
      return handleMaxUploadSize((MaxUploadSizeExceededException) ex);
    }
    return badRequest(multipartMessage());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", ex.getReason() == null ? "" : ex.getReason()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
    return badRequest(ex.getMessage() == null ? "" : ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Map.of(
                "status", 409,
                "error", "Conflict",
                "message", ex.getMessage() == null ? "" : ex.getMessage()));
  }

  private String uploadSizeMessage() {
    return "上传文件超过大小限制（单个文件最大 "
        + uploadProperties.getMaxFileSizeMb()
        + "MB）";
  }

  private String multipartMessage() {
    return "上传失败：文件格式或大小不符合要求（支持 "
        + String.join("/", uploadProperties.allowedExtensionList())
        + "，单个文件最大 "
        + uploadProperties.getMaxFileSizeMb()
        + "MB）";
  }

  private static ResponseEntity<Map<String, Object>> badRequest(String message) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", message));
  }
}

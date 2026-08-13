package com.story.admin.service;

import com.story.admin.config.StorageProperties;
import com.story.admin.config.UploadProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StorageService {

  private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
  private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
  private static final Map<String, String> CONTENT_TYPES =
      Map.of(
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg",
          "png", "image/png",
          "webp", "image/webp",
          "gif", "image/gif");

  private final ConfigService configService;
  private final StorageProperties storageProperties;
  private final UploadProperties uploadProperties;

  public StorageService(
      ConfigService configService,
      StorageProperties storageProperties,
      UploadProperties uploadProperties) {
    this.configService = configService;
    this.storageProperties = storageProperties;
    this.uploadProperties = uploadProperties;
  }

  public StoredFile store(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    }
    String original = sanitizeFilename(file.getOriginalFilename());
    String ext = extensionOf(original);
    Set<String> allowed = uploadProperties.allowedExtensionSet();
    if (!allowed.contains(ext)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "unsupported file type: " + ext + "; allowed: " + allowed);
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read upload");
    }
    if (bytes.length == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is empty");
    }
    if (bytes.length > uploadProperties.maxFileSizeBytes()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "file exceeds max size of " + uploadProperties.getMaxFileSizeMb() + "MB");
    }
    LocalDate today = LocalDate.now();
    String relativePath =
        "assets/" + today.format(YEAR) + "/" + today.format(MONTH) + "/" + UUID.randomUUID() + "." + ext;
    Path absolute = resolveAbsolute(relativePath);
    try {
      Files.createDirectories(absolute.getParent());
      Files.write(absolute, bytes);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store file");
    }
    Integer width = null;
    Integer height = null;
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image != null) {
        width = image.getWidth();
        height = image.getHeight();
      }
    } catch (IOException ignored) {
      // keep dimensions null when the decoder cannot read the image
    }
    String contentType = file.getContentType();
    if (contentType == null || contentType.isBlank() || contentType.equals("application/octet-stream")) {
      contentType = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }
    return new StoredFile(relativePath, contentType, bytes.length, sha256(bytes), width, height);
  }

  public Path resolveAbsolute(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "storage path is required");
    }
    Path root = storageRoot();
    Path resolved = root.resolve(relativePath).toAbsolutePath().normalize();
    if (!resolved.startsWith(root)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid storage path");
    }
    return resolved;
  }

  public void deleteQuietly(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return;
    }
    try {
      Files.deleteIfExists(resolveAbsolute(relativePath));
    } catch (Exception ignored) {
      // best-effort cleanup
    }
  }

  private Path storageRoot() {
    String configured = configService.get("storage.root", storageProperties.getRoot());
    if (configured == null || configured.isBlank()) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "storage.root is not configured");
    }
    return Path.of(configured).toAbsolutePath().normalize();
  }

  private static String sanitizeFilename(String originalFilename) {
    if (originalFilename == null || originalFilename.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "original filename is required");
    }
    String name = Path.of(originalFilename.replace("\\", "/")).getFileName().toString();
    if (name.isBlank() || !name.contains(".")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file extension is required");
    }
    return name;
  }

  private static String extensionOf(String filename) {
    return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  private static String sha256(byte[] bytes) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  public record StoredFile(
      String relativePath, String contentType, long size, String checksum, Integer width, Integer height) {}
}

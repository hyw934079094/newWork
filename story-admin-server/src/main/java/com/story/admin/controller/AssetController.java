package com.story.admin.controller;

import com.story.admin.domain.Asset;
import com.story.admin.domain.AssetLinkType;
import com.story.admin.dto.AssetMoveRequest;
import com.story.admin.dto.AssetReorderByScopeRequest;
import com.story.admin.dto.AssetReorderRequest;
import com.story.admin.dto.AssetUpdateRequest;
import com.story.admin.service.AssetService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/assets")
public class AssetController {

  private final AssetService assetService;

  public AssetController(AssetService assetService) {
    this.assetService = assetService;
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public List<Asset> upload(
      @RequestParam Long categoryId,
      @RequestParam("files") MultipartFile[] files,
      @RequestParam(required = false) AssetLinkType linkType,
      @RequestParam(required = false) List<Long> seriesIds,
      @RequestParam(required = false) List<Long> arcIds,
      @RequestParam(required = false) List<Long> characterIds) {
    return assetService.upload(categoryId, files, linkType, seriesIds, arcIds, characterIds);
  }

  @PutMapping("/reorder")
  public void reorder(@RequestBody AssetReorderRequest body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
    }
    assetService.reorder(body.categoryId(), body.orderedIds());
  }

  @PutMapping("/reorder-by-scope")
  public void reorderByScope(@RequestBody AssetReorderByScopeRequest body) {
    if (body == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body is required");
    }
    assetService.reorderByScope(body.categoryId(), body.scope(), body.scopeId(), body.orderedIds());
  }

  @GetMapping
  public List<Asset> list(
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false, defaultValue = "NORMAL") String status,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String characterFilter,
      @RequestParam(required = false) Long characterId,
      @RequestParam(required = false) String linkType,
      @RequestParam(required = false) Long seriesId,
      @RequestParam(required = false) Long arcId) {
    return assetService.list(
        categoryId, status, q, characterFilter, characterId, linkType, seriesId, arcId);
  }

  @GetMapping("/{id}")
  public Asset get(@PathVariable Long id) {
    return assetService.get(id);
  }

  @PutMapping("/{id}")
  public Asset update(@PathVariable Long id, @RequestBody AssetUpdateRequest body) {
    return assetService.update(id, body);
  }

  @PutMapping("/{id}/move")
  public Asset move(@PathVariable Long id, @RequestBody AssetMoveRequest body) {
    if (body == null || body.targetIndex() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetIndex is required");
    }
    return assetService.move(id, body.targetCategoryId(), body.targetIndex());
  }

  @PostMapping("/{id}/recycle")
  public Asset recycle(@PathVariable Long id) {
    return assetService.recycle(id);
  }

  @PostMapping("/{id}/restore")
  public Asset restore(@PathVariable Long id) {
    return assetService.restore(id);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void hardDelete(@PathVariable Long id) {
    assetService.hardDelete(id);
  }

  @PostMapping(value = "/{id}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Asset replaceContent(
      @PathVariable Long id, @RequestParam("file") MultipartFile file) {
    return assetService.replaceContent(id, file);
  }

  @GetMapping("/{id}/content")
  public ResponseEntity<Resource> content(@PathVariable Long id) {
    Asset asset = assetService.get(id);
    Path path = assetService.resolveContent(id);
    if (!Files.exists(path)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "asset file not found");
    }
    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
    if (asset.getContentType() != null && !asset.getContentType().isBlank()) {
      try {
        mediaType = MediaType.parseMediaType(asset.getContentType());
      } catch (Exception ignored) {
        mediaType = MediaType.APPLICATION_OCTET_STREAM;
      }
    }
    String filename = asset.getOriginalFilename() == null ? "file" : asset.getOriginalFilename();
    ContentDisposition disposition = ContentDisposition.inline().filename(filename).build();
    long size = 0;
    try {
      size = Files.size(path);
    } catch (IOException ignored) {
      // optional content-length
    }
    return ResponseEntity.ok()
        .contentType(mediaType)
        .contentLength(size)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(new FileSystemResource(path));
  }
}

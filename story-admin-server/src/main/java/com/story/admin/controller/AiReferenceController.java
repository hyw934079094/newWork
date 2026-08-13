package com.story.admin.controller;

import com.story.admin.domain.AiReferenceSession;
import com.story.admin.dto.AiReferenceItemRequest;
import com.story.admin.service.AiReferenceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-reference")
public class AiReferenceController {

  private final AiReferenceService aiReferenceService;

  public AiReferenceController(AiReferenceService aiReferenceService) {
    this.aiReferenceService = aiReferenceService;
  }

  @GetMapping("/current")
  public AiReferenceSession getCurrent() {
    return aiReferenceService.getCurrent();
  }

  @PutMapping("/current/items")
  public AiReferenceSession replaceCurrentItems(@RequestBody List<AiReferenceItemRequest> body) {
    return aiReferenceService.replaceCurrentItems(body);
  }
}

package com.story.admin.controller;

import com.story.admin.dto.AuthUserResponse;
import com.story.admin.dto.ChangePasswordRequest;
import com.story.admin.dto.LoginRequest;
import com.story.admin.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public AuthUserResponse login(
      @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return authService.login(request, httpRequest, httpResponse);
  }

  @PostMapping("/logout")
  public Map<String, String> logout(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    authService.logout(httpRequest, httpResponse);
    return Map.of("status", "ok");
  }

  @GetMapping("/me")
  public AuthUserResponse me() {
    return authService.me();
  }

  @PutMapping("/password")
  public Map<String, String> changePassword(@RequestBody ChangePasswordRequest request) {
    authService.changePassword(request);
    return Map.of("status", "ok");
  }
}

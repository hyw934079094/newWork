package com.story.admin.service;

import com.story.admin.domain.AdminUser;
import com.story.admin.dto.AuthUserResponse;
import com.story.admin.dto.ChangePasswordRequest;
import com.story.admin.dto.LoginRequest;
import com.story.admin.repository.AdminUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final AdminUserRepository adminUserRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthService(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      AdminUserRepository adminUserRepository,
      PasswordEncoder passwordEncoder) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.adminUserRepository = adminUserRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public AuthUserResponse login(
      LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    if (request == null
        || request.username() == null
        || request.username().isBlank()
        || request.password() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password required");
    }
    try {
      Authentication authentication =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(
                  request.username().trim(), request.password()));
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, httpRequest, httpResponse);
      httpRequest.getSession(true);
      return toResponse(authentication.getName());
    } catch (BadCredentialsException ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid username or password");
    }
  }

  public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    SecurityContextHolder.clearContext();
    HttpSession session = httpRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    securityContextRepository.saveContext(
        SecurityContextHolder.createEmptyContext(), httpRequest, httpResponse);
  }

  public AuthUserResponse me() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || "anonymousUser".equals(authentication.getPrincipal())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated");
    }
    return toResponse(authentication.getName());
  }

  @Transactional
  public void changePassword(ChangePasswordRequest request) {
    if (request == null
        || request.currentPassword() == null
        || request.newPassword() == null
        || request.newPassword().isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "currentPassword and newPassword required");
    }
    if (request.newPassword().length() < 4) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "newPassword too short");
    }
    AuthUserResponse me = me();
    AdminUser user =
        adminUserRepository
            .findByUsername(me.username())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated"));
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "current password incorrect");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    adminUserRepository.save(user);
  }

  private AuthUserResponse toResponse(String username) {
    AdminUser user =
        adminUserRepository
            .findByUsername(username)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "not authenticated"));
    String display =
        user.getDisplayName() == null || user.getDisplayName().isBlank()
            ? user.getUsername()
            : user.getDisplayName();
    return new AuthUserResponse(user.getUsername(), display);
  }
}

package com.story.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.story.admin.dto.AuthUserResponse;
import com.story.admin.dto.ChangePasswordRequest;
import com.story.admin.dto.LoginRequest;
import com.story.admin.security.AdminUserSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
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
      "spring.datasource.url=jdbc:h2:mem:story_admin_auth_svc_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "story.storage.root=../storage"
    })
class AuthServiceTest {

  @Autowired AuthService authService;

  @Test
  void loginSucceedsWithSeedAdmin() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    AuthUserResponse user =
        authService.login(
            new LoginRequest(AdminUserSeeder.DEFAULT_USERNAME, AdminUserSeeder.DEFAULT_PASSWORD),
            req,
            res);
    assertThat(user.username()).isEqualTo("admin");
    assertThat(user.displayName()).isNotBlank();
    assertThat(req.getSession(false)).isNotNull();
  }

  @Test
  void loginRejectsBadPassword() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    assertThatThrownBy(
            () ->
                authService.login(
                    new LoginRequest(AdminUserSeeder.DEFAULT_USERNAME, "wrong"), req, res))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void changePasswordThenLoginWithNewPassword() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    authService.login(
        new LoginRequest(AdminUserSeeder.DEFAULT_USERNAME, AdminUserSeeder.DEFAULT_PASSWORD),
        req,
        res);

    authService.changePassword(new ChangePasswordRequest("admin", "admin2"));

    SecurityContextHolder.clearContext();
    MockHttpServletRequest req2 = new MockHttpServletRequest();
    MockHttpServletResponse res2 = new MockHttpServletResponse();
    AuthUserResponse again =
        authService.login(new LoginRequest("admin", "admin2"), req2, res2);
    assertThat(again.username()).isEqualTo("admin");

    // restore default for other tests sharing DB if any
    authService.changePassword(new ChangePasswordRequest("admin2", "admin"));
  }
}

package com.story.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class ComboSchemaSmoke {

  @Autowired DataSource dataSource;

  @Test
  void flywayV3CreatesAssetComboTables() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(tableExists(connection, "asset_combo")).isTrue();
      assertThat(tableExists(connection, "asset_combo_member")).isTrue();
      assertThat(tableExists(connection, "asset_combo_step_hold")).isTrue();

      List<String> versions = new ArrayList<>();
      try (ResultSet rs =
          connection
              .createStatement()
              .executeQuery(
                  "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank")) {
        while (rs.next()) {
          versions.add(rs.getString(1));
        }
      }
      assertThat(versions).contains("3");
    }
  }

  private static boolean tableExists(Connection connection, String tableName) throws Exception {
    try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
      return rs.next();
    }
  }
}

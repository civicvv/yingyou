package com.yingyou;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 要求本地已存在 yingyou 库，且后端已至少执行过一次 Flyway 迁移（当前基线 V1–V13）。
 * 通过 DB_URL / DB_USER / DB_PASSWORD 覆盖连接信息；未设置 DB_PASSWORD 时跳过。
 */
class FlywayMigrationSmokeTest {

    @Test
    void allMigrationsAreAppliedSuccessfully() throws SQLException {
        String password = System.getenv("DB_PASSWORD");
        assumeTrue(password != null && !password.isBlank(),
                "设置 DB_PASSWORD 后才会执行 Flyway 迁移冒烟测试");

        String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/yingyou");
        String user = System.getenv().getOrDefault("DB_USER", "yingyou");

        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "select version, success from flyway_schema_history order by installed_rank")) {

            int count = 0;
            while (result.next()) {
                assertTrue(result.getBoolean("success"),
                        "迁移 " + result.getString("version") + " 未成功");
                count++;
            }
            assertTrue(count >= 8, "期望至少包含 V1–V8 八个迁移，实际 " + count);
        }
    }
}

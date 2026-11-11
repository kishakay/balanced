package com.balanced.articles;

import org.junit.jupiter.api.Test;

/**
 * Placeholder unit test. Full Spring context + PostgreSQL smoke coverage
 * is verified manually in Stage 1 (docker compose + spring-boot:run).
 */
class ArticleServiceApplicationTests {

    @Test
    void applicationClassIsLoadable() {
        // Ensures the main class remains on the compile/test classpath.
        org.junit.jupiter.api.Assertions.assertNotNull(ArticleServiceApplication.class);
    }
}

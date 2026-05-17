package kr.devslab.apilog;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap class for @SpringBootTest in this module.
 *
 * The library itself is not a Spring Boot application, so it ships no @SpringBootApplication.
 * Tests need one for the application context lookup — this empty class satisfies that
 * requirement without leaking app-scaffolding into main sources.
 *
 * Sits at the root of the kr.devslab.apilog package so every test under
 * src/test/java/kr/devslab/apilog/** can find it during the upward package scan.
 */
@SpringBootApplication
public class TestApp {
}

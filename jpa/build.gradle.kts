// :jpa — JPA (Hibernate) backend for the api-log starter.
//
// Published as `kr.devslab:api-log-jpa`. Depends transitively on `:core` so
// consumers add a single coordinate and get the full Servlet + JPA stack
// (event listener, HTTP utilities, writer, schema initializer, Flyway hook).

plugins {
    `java-library`
    jacoco
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("com.vanniktech.maven.publish")
}

base {
    archivesName.set("api-log-jpa")
}

afterEvaluate {
    tasks.named<AbstractArchiveTask>("mavenPlainJavadocJar").configure {
        archiveBaseName.set("api-log-jpa")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-parameters",
        "-Xlint:all,-classfile,-processing,-serial",
        "-Werror"
    ))
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("Xdoclint:none", true)
        addBooleanOption("html5", true)
        locale = "en_US"
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.6")
    }
}

dependencies {
    // Core carries the event types, SPI, listener, HTTP utilities, and the
    // V1.0 schema script (under classpath:db/api-log/). Pulled in as `api` so
    // consumers see one coordinate.
    api(project(":core"))

    // JPA + JDBC — the whole point of this artifact.
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // PostgreSQL JDBC driver — runtime only; this starter is PostgreSQL-specific
    // (JSONB columns + Hibernate's @JdbcTypeCode(JSON) mapping rely on it).
    runtimeOnly("org.postgresql:postgresql")

    // Lombok — compile + annotation-processor only.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Flyway is OPTIONAL — consumers who pick `api.log.schema.management=flyway`
    // bring their own flyway-core + flyway-database-postgresql. The Flyway
    // customizer in this module is gated by @ConditionalOnClass(FluentConfiguration.class)
    // so absence is silent.
    compileOnly("org.flywaydb:flyway-core:11.13.1")

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    // Lets the ConfigurationTest assert that ReactiveApiClientAutoConfiguration
    // also activates when WebClient is on the classpath — same shape as a real
    // mixed Servlet+WebFlux consumer.
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.h2database:h2")

    // Testcontainers — real PostgreSQL backs the integration tests because
    // Hibernate's JSONB mapping (@JdbcTypeCode(JSON)) is PostgreSQL-specific.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // Flyway runtime for the FlywayConfigurationCustomizer integration test
    // (provides flyway-core + the PostgreSQL dialect plugin).
    testImplementation("org.flywaydb:flyway-core:11.13.1")
    testRuntimeOnly("org.flywaydb:flyway-database-postgresql:11.13.1")

    // MockWebServer drives the end-to-end HTTP integration tests
    // (real HTTP through RestApiClientUtil → assert api_log rows via Testcontainers).
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    systemProperty("file.encoding", "UTF-8")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

mavenPublishing {
    coordinates(
        providers.gradleProperty("GROUP").get(),
        "api-log-jpa",
        providers.gradleProperty("VERSION").get()
    )

    pom {
        name.set("API Log Spring Boot Starter - JPA")
        description.set("JPA + Hibernate backend for api-log. PostgreSQL JSONB columns mapped via @JdbcTypeCode(JSON). Pair with api-log-core.")

        developers {
            developer {
                id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                url.set(providers.gradleProperty("POM_DEVELOPER_URL"))
                email.set(providers.gradleProperty("POM_DEVELOPER_EMAIL"))
                organization.set(providers.gradleProperty("POM_ORGANIZATION_NAME"))
                organizationUrl.set(providers.gradleProperty("POM_ORGANIZATION_URL"))
            }
        }

        organization {
            name.set(providers.gradleProperty("POM_ORGANIZATION_NAME"))
            url.set(providers.gradleProperty("POM_ORGANIZATION_URL"))
        }

        issueManagement {
            system.set(providers.gradleProperty("POM_ISSUE_SYSTEM"))
            url.set(providers.gradleProperty("POM_ISSUE_URL"))
        }
    }
}

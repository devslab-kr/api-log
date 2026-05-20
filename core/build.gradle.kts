// :core — backend-agnostic foundation for the api-log starter.
//
// Published as `kr.devslab:api-log-core`. Holds:
//   - The event objects (ApiCallInitiatedEvent / SuccessEvent / ErrorEvent)
//   - The `ApiLogWriter` SPI that backend modules implement
//   - The async event listener that drives writers off the event bus
//   - The HTTP client utilities (RestApiClientUtil for sync, ReactiveApiClientUtil for reactive)
//   - The shared Jackson customizer (Blackbird), retry config, and properties
//
// Backend modules (`:jpa`, `:r2dbc`, `:mybatis`) depend on this and each
// register exactly one `ApiLogWriter` bean — consumers pick by adding the
// backend artifact they want.

plugins {
    `java-library`
    jacoco
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("com.vanniktech.maven.publish")
}

base {
    // On-disk jar filename. Vanniktech overrides the *publish* coordinates
    // separately via `mavenPublishing.coordinates(...)`; this controls only
    // the local `build/libs/*.jar` name so GitHub Release assets are readable.
    archivesName.set("api-log-core")
}

// Vanniktech's javadoc jar task hardcodes its archive base name to
// `<subproject>-maven-javadoc` and only sets it inside its plugin's own
// `afterEvaluate`. Without this override the GitHub Release ends up with a
// confusing `core-maven-javadoc-X.Y.Z-javadoc.jar` next to the properly named
// main jar. Configuring inside `afterEvaluate` makes us the last writer.
afterEvaluate {
    tasks.named<AbstractArchiveTask>("mavenPlainJavadocJar").configure {
        archiveBaseName.set("api-log-core")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -parameters: keep AOP-readable param names. -Xlint enabled but the noisy
    // categories (classfile/processing/serial) are excluded so -Werror stays
    // usable for real code issues without tripping on annotation-processor noise.
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
    // Pulled in transitively for every consumer of any api-log-* artifact:
    // spring-context + spring-boot give us @EventListener, @EnableAsync,
    // ApplicationEventPublisher, @ConditionalOnProperty, etc.
    api("org.springframework.boot:spring-boot-starter")

    // The listener's @Retryable wraps each persistence attempt — without
    // spring-retry on the classpath consumers can't use the retry semantics.
    api("org.springframework.retry:spring-retry")
    // @Retryable needs Spring AOP at runtime to weave the proxy.
    api("org.springframework.boot:spring-boot-starter-aop")

    // Events / payloads use Jackson directly (JsonNode in payloads, error JSON).
    api("com.fasterxml.jackson.core:jackson-databind")

    // Blackbird = ~30-50% Jackson serialization speedup. The Jackson2ObjectMapperBuilderCustomizer
    // we register installs it into Spring Boot's default ObjectMapper.
    api("com.fasterxml.jackson.module:jackson-module-blackbird")

    // Lombok — compile + annotation-processor only.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Auto-configuration metadata processor.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // HTTP client API surfaces — `compileOnly` because consumers may have
    // either, both, or neither. The corresponding @AutoConfigurations gate
    // themselves with @ConditionalOnClass so absence is silent.
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework:spring-webflux")
    compileOnly("io.projectreactor.netty:reactor-netty-http")

    // Silences cosmetic "cannot find javax.annotation.Nonnull" warnings from
    // resolving Spring's @Nullable. Not exposed to consumers.
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.assertj:assertj-core")

    // MockWebServer drives the HTTP-client utils against a real socket.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    // Explicit launcher pin. JUnit Jupiter 5.11+ requires junit-platform-launcher
    // >= 1.11; Gradle 8.10 still bundles 1.10.x. Without this declaration the
    // BOM's 1.11 doesn't make it onto the test runtime classpath.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true   // temporary: surface application logs in CI for the v0.6.0 integration-test debugging
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
        "api-log-core",
        providers.gradleProperty("VERSION").get()
    )

    pom {
        name.set("API Log Spring Boot Starter - Core")
        description.set("Backend-agnostic core for api-log: events, SPI, async listener, HTTP client utilities. Pair with api-log-jpa / api-log-r2dbc / api-log-mybatis.")

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

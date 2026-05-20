// :r2dbc — Reactive (R2DBC) backend for the api-log starter.
//
// Published as `kr.devslab:api-log-r2dbc`. Depends transitively on `:core`.
// Use this artifact (instead of api-log-jpa) when your application is built
// on Spring WebFlux + R2DBC and you want the audit log to participate in the
// same reactive pipeline rather than block on JDBC.

plugins {
    `java-library`
    jacoco
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("com.vanniktech.maven.publish")
}

base {
    archivesName.set("api-log-r2dbc")
}

afterEvaluate {
    tasks.named<AbstractArchiveTask>("mavenPlainJavadocJar").configure {
        archiveBaseName.set("api-log-r2dbc")
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
    api(project(":core"))

    // R2DBC stack — the writer talks directly to DatabaseClient (no Spring Data
    // repository) so consumers don't get a transitive dependency on Spring Data
    // R2DBC unless they want it. spring-r2dbc gives DatabaseClient + the
    // connection-factory-based ScriptDatabaseInitializer used by our schema bean.
    api("org.springframework:spring-r2dbc")

    // PostgreSQL R2DBC driver — runtime only (the starter is PostgreSQL-specific
    // because of the JSONB column type).
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    // Reactor — explicit (rather than transitively via webflux) so this module
    // works in non-webflux setups too.
    api("io.projectreactor:reactor-core")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.assertj:assertj-core")

    // Testcontainers — real PostgreSQL via R2DBC for the integration tests.
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:r2dbc")
    // PostgreSQL JDBC driver is used by Testcontainers' Postgres module to run
    // init scripts; not used by the runtime R2DBC path.
    testImplementation("org.postgresql:postgresql")

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
        "api-log-r2dbc",
        providers.gradleProperty("VERSION").get()
    )

    pom {
        name.set("API Log Spring Boot Starter - R2DBC")
        description.set("Reactive (R2DBC) backend for api-log. Native PostgreSQL JSONB inserts via DatabaseClient — no JDBC dependency. Pair with api-log-core.")

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

// :mybatis — MyBatis backend for the api-log starter.
//
// Published as `kr.devslab:api-log-mybatis`. Depends transitively on `:core`.
// Use this artifact when your application is already on MyBatis and you don't
// want to drag in JPA / Hibernate just for the audit log.

plugins {
    `java-library`
    jacoco
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("com.vanniktech.maven.publish")
}

base {
    archivesName.set("api-log-mybatis")
}

afterEvaluate {
    tasks.named<AbstractArchiveTask>("mavenPlainJavadocJar").configure {
        archiveBaseName.set("api-log-mybatis")
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

    // MyBatis Spring Boot Starter — drives @Mapper scanning + SqlSessionFactory
    // + automatic transaction management. Spring Boot 3.x line uses 3.0.x.
    api("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4")

    // JDBC connection pool comes from the consumer's spring-boot-starter-jdbc /
    // -data-jpa; we don't force a particular pool here. spring-jdbc is needed
    // for DataSource + the JDBC schema initializer below.
    api("org.springframework:spring-jdbc")

    // PostgreSQL JDBC driver — runtime only.
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.assertj:assertj-core")

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
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
        "api-log-mybatis",
        providers.gradleProperty("VERSION").get()
    )

    pom {
        name.set("API Log Spring Boot Starter - MyBatis")
        description.set("MyBatis backend for api-log. Native PostgreSQL JSONB inserts via mapper with explicit cast. Pair with api-log-core.")

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

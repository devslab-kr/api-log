// Multi-module orchestration. The root project is not published — each
// publishable artifact lives under its own subproject (see settings.gradle.kts)
// and applies the publishing plugin itself.
//
// Plugin versions are declared here with `apply false` so subprojects can
// apply them without repeating version numbers, and so the version drift
// between modules stays at zero.

plugins {
    id("org.springframework.boot") version "3.5.6" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION").get()
}

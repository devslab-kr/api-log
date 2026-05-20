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

// Subproject publications all rely on the Spring Boot BOM via the dependency-management
// plugin, so individual `api(...)` / `implementation(...)` declarations have no
// explicit version. When Gradle generates each module's `.module` metadata it
// validates that every dep carries a coordinate-level version and aborts the
// publication if any are missing -- which is exactly the BOM case.
//
// `versionMapping { allVariants { fromResolutionResult() } }` tells Gradle to
// freeze the version Gradle actually resolved (3.5.6 from the Spring Boot BOM)
// into the published metadata, so the resulting POM and .module files carry
// concrete versions Maven consumers can use without our BOM. Applied via
// `subprojects` so the four backend modules all pick it up uniformly.
subprojects {
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {
            publications.withType<org.gradle.api.publish.maven.MavenPublication>().configureEach {
                versionMapping {
                    allVariants {
                        fromResolutionResult()
                    }
                }
            }
        }
    }
}

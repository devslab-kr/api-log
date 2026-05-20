pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "api-log"

// Subprojects. Each publishable artifact lives under its own subproject.
// The on-disk directory name is short (`core`, `jpa`, `r2dbc`, `mybatis`);
// the Maven artifact ID is pinned via `mavenPublishing.coordinates(...)` in
// each module's build file.
//
//   core     → kr.devslab:api-log-core        (events, SPI, HTTP utils, listener)
//   jpa      → kr.devslab:api-log-jpa         (JPA writer + entity + Flyway hook)
//   r2dbc    → kr.devslab:api-log-r2dbc       (reactive R2DBC writer)
//   mybatis  → kr.devslab:api-log-mybatis     (MyBatis mapper writer)
include("core")
include("jpa")
include("r2dbc")
include("mybatis")

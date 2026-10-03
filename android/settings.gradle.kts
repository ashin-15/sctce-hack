pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sakshi"

include(":app")
include(":core:model")
include(":core:integrity")
include(":core:temporal")
include(":core:crypto")
include(":core:database")
include(":core:vault")
include(":acquisition:importer")
include(":processing:text")
include(":export:bundle")
include(":processing:analysis")
include(":processing:ocr")
include(":processing:llm")
include(":processing:stt")
include(":export:report")
include(":tools:policy")
include(":acquisition:notifications")
include(":acquisition:accessibility")
include(":acquisition:projection")

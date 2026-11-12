plugins {
    // Lets Gradle download a matching JDK when the machine doesn't have Java 21.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "omni-client-java"

plugins {
    `java-library`
}

group = "io.github.pod32g"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // The library itself has no dependencies; JUnit is only used by the tests.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    // Compile and test with JDK 21, whichever JDK runs Gradle. The jar runs on Java 21 and newer.
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.compileJava {
    options.compilerArgs.add("-Werror") // the library must compile without warnings
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    // Print a one-line total at the end, so a refactoring that loses a test is easy to spot.
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun beforeTest(testDescriptor: TestDescriptor) {}
        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
            if (suite.parent == null) {
                println(
                    "${result.testCount} tests: ${result.successfulTestCount} passed, " +
                        "${result.failedTestCount} failed, ${result.skippedTestCount} skipped"
                )
            }
        }
    })
}

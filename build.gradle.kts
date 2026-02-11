import java.nio.file.Files
import java.nio.file.Paths

plugins {
    kotlin("jvm") version "2.2.0"
    antlr
    application
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.diffplug.spotless") version "6.25.0"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.compileJava {
    options.release.set(21)
}

group = "io.github.slang"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    antlr("org.antlr:antlr4:4.5")
    implementation("com.fifesoft:rsyntaxtextarea:3.3.3")
    implementation("io.arrow-kt:arrow-core:2.2.0")
    implementation("com.formdev:flatlaf:3.5.4")
    testImplementation("com.approvaltests:approvaltests:23.0.0")
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.0")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("org.reflections:reflections:0.10.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-listener", "-long-messages")
}

sourceSets {
    main {
        java.srcDir("build/generated-src/antlr/main")
    }
}

tasks.named("compileKotlin") {
    dependsOn("generateGrammarSource")
}

tasks.named("compileTestKotlin") {
    dependsOn("generateTestGrammarSource")
}

tasks.register("showGui", JavaExec::class) {
    group = "application"
    description = "Runs the AST Visualizer GUI"
    mainClass.set("slast.visualizer.AstVisualizerKt")
    classpath = sourceSets.main.get().runtimeClasspath
}

application {
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// Task to approve snapshot tests using the helper script.
// Dry-run by default; pass -PapproveSnapshotsCommit=true to actually git-mv and commit.
tasks.register<Exec>("approveSnapshots") {
    group = "verification"
    description = "Run scripts/approve_snapshots.sh (dry-run). Use -PtestPath=/path/to/test to commit approvals."

    val testPath = project.findProperty("testPath") as String?
    val scriptFile = file("scripts/approve_snapshots.sh")
    if (!scriptFile.exists()) {
        throw GradleException("Snapshot approval script not found: ${scriptFile.absolutePath}")
    }

    if (testPath == null) {
        throw GradleException("Test path not found}")
    }

    if (Files.notExists(Paths.get(testPath))) {
        throw GradleException("Directory does not exist: $testPath")
    }
    commandLine = listOf("bash", scriptFile.absolutePath, testPath)
    isIgnoreExitValue = false
}

ktlint {
    version.set("1.7.1")
    android.set(false) // use official Kotlin style
    outputToConsole.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/build/generated-src/**")
        exclude("**/generated/**")
    }
}

tasks.runKtlintCheckOverMainSourceSet {
    enabled = true
    dependsOn(tasks.generateGrammarSource)
}

tasks.runKtlintFormatOverMainSourceSet {
    enabled = true
    dependsOn(tasks.generateGrammarSource)
}

tasks.runKtlintCheckOverTestSourceSet {
    enabled = true
    dependsOn(tasks.generateTestGrammarSource)
}

tasks.runKtlintFormatOverTestSourceSet {
    enabled = true
    dependsOn(tasks.generateTestGrammarSource)
}

spotless {
    kotlin {
        ktlint
        target("**/*.kt")
        targetExclude("build/**", "**/generated/**")
        trimTrailingWhitespace()
        endWithNewline()
        indentWithSpaces()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint()
    }
}

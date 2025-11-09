plugins {
    kotlin("jvm") version "2.1.0"
    antlr
    application
}

group = "org.example"
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

tasks.named("compileKotlin") {
    dependsOn("generateGrammarSource")
}

tasks.named("compileTestKotlin") {
    dependsOn("generateTestGrammarSource")
}


kotlin {
    jvmToolchain(21)
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

// Task to approve snapshot tests using the helper script.
// Dry-run by default; pass -PapproveSnapshotsCommit=true to actually git-mv and commit.
tasks.register<Exec>("approveSnapshots") {
    group = "verification"
    description = "Run scripts/approve_snapshots.sh (dry-run). Use -PapproveSnapshotsCommit=true to commit approvals."

    val commit = (project.findProperty("approveSnapshotsCommit") as String?).toBoolean()
    val scriptFile = file("scripts/approve_snapshots.sh")
    if (!scriptFile.exists()) {
        throw GradleException("Snapshot approval script not found: ${scriptFile.absolutePath}")
    }

    // Use bash for portability and to support script features.
    commandLine = listOf("bash", scriptFile.absolutePath) + if (commit) listOf("--commit") else emptyList()

    // Stream output to console so user sees what will happen.
    isIgnoreExitValue = false
}

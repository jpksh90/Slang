plugins {
    kotlin("jvm") version "1.9.23"
    antlr
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
     antlr("org.antlr:antlr4:4.5") // use ANTLR version 4
    implementation("com.fifesoft:rsyntaxtextarea:3.3.3")
    implementation("com.formdev:flatlaf:3.5.4")
    testImplementation("com.approvaltests:approvaltests:23.0.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages")
}

tasks.named("compileKotlin") {
    dependsOn("generateGrammarSource")
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
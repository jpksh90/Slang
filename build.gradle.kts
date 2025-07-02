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
    implementation("com.formdev:flatlaf:3.5.4")
    testImplementation("com.approvaltests:approvaltests:23.0.0")

    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-util:9.6") // Optional, for utilities

    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.0")
    implementation("org.yaml:snakeyaml:2.0")

    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.0")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("org.reflections:reflections:0.10.2")

    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-util:9.6") // Optional, for utilities

    implementation("org.javassist:javassist:3.29.2-GA")


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
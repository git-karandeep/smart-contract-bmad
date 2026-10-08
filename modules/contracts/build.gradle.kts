plugins {
    id("java")
}

group = "ai.generalcontracts"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":modules:chain-adapter"))
    implementation(project(":modules:persistence-mongo"))
    implementation(libs.web3j.core)
    implementation(libs.spring.boot.starter.kafka)
    implementation(libs.micrometer.registry.prometheus)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    testImplementation(libs.spring.kafka.test)
}

springBoot {
    mainClass.set("ai.generalcontracts.startup.GeneralContractApplication")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = true
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration", "api")
    }
}

tasks.register<Test>("apiTest") {
    description = "Runs API validation and controller tests."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("api")
    }
    shouldRunAfter(tasks.test)
}

tasks.register<Test>("integrationTest") {
    description = "Runs messaging round-trip tests against an embedded Kafka broker."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    shouldRunAfter(tasks.test)
}

tasks.register<org.gradle.api.tasks.Exec>("e2eTest") {
    description = "Runs the Docker Compose end-to-end smoke test."
    group = "verification"
    workingDir = rootProject.projectDir
    commandLine("bash", "tests/e2e-smoke.sh")
}

plugins {
    id("java")
}

group = "ai.generalcontracts"
version = "0.0.1-SNAPSHOT"

dependencies {
    implementation(project(":modules:common-domain"))
    implementation(project(":modules:domain-service"))
    implementation(project(":modules:process-definitions"))
    implementation(project(":modules:process-workers"))
    implementation(project(":modules:chain-adapter"))
    implementation(project(":modules:persistence-mongo"))
    implementation(libs.web3j.core)
    implementation(libs.spring.boot.starter.kafka)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation(libs.micrometer.registry.prometheus)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.grpc.netty)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.protobuf.java)

    constraints {
        implementation("org.bouncycastle:bcprov-jdk18on:1.85") {
            because("Fixes CVE-2025-14813, CVE-2026-8763 and CVE-2026-13506 in transitive web3j dependency")
        }
    }

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

plugins {
    id("java")
}

group = "ai.generalcontracts"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":modules:common-domain"))
    implementation(project(":modules:chain-adapter"))
    implementation(project(":modules:chain-listener"))
    implementation(project(":modules:domain-service"))
    implementation(project(":modules:persistence-mongo"))
    testImplementation(project(":modules:process-definitions"))
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

val camundaProcessTestSourceSet = sourceSets.create("camundaProcessTest") {
    java.srcDir("src/camundaProcessTest/java")
}

dependencies.add("camundaProcessTestImplementation", "io.camunda:camunda-process-test-java:8.9.0")

configurations[camundaProcessTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[camundaProcessTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

val camundaProcessTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs Camunda process tests; requires Docker."
    dependsOn(tasks.named("classes"))
    dependsOn(tasks.named(camundaProcessTestSourceSet.classesTaskName))
    dependsOn(":modules:process-definitions:processResources")
    testClassesDirs = camundaProcessTestSourceSet.output.classesDirs
    classpath = camundaProcessTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}
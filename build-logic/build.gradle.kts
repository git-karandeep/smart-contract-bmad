plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("org.springframework.boot:org.springframework.boot.gradle.plugin:4.1.0")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}

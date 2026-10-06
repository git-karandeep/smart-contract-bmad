plugins {
    id("java")
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ai.generalcontracts"
version = "0.0.1-SNAPSHOT"

dependencies {
}

tasks.test {
    useJUnitPlatform()
}
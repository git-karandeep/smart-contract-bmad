plugins {
    id("java")
}

group = "ai.generalcontracts"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.camunda:zeebe-bpmn-model:8.9.0")
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

val generatedResourceRoot = layout.buildDirectory.dir("generated/resources/main")
val generatedProcessDirectory = generatedResourceRoot.map { it.dir("generated-processes") }
val transferLifecycleSource = layout.projectDirectory.file(
    "src/main/resources/processes/transfer-lifecycle.bpmn"
)

sourceSets.named("main") {
    resources.srcDir(generatedResourceRoot)
}

val generateBpmnWorkflows by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Validates and generates the transfer lifecycle BPMN process set."
    dependsOn(tasks.named("compileJava"))
    classpath = files(sourceSets.main.get().output.classesDirs, configurations.runtimeClasspath)
    mainClass.set("ai.generalcontracts.startup.workflow.BpmnWorkflowGenerator")
    args(transferLifecycleSource.asFile.absolutePath, generatedProcessDirectory.get().asFile.absolutePath)
    inputs.file(transferLifecycleSource)
    inputs.files(sourceSets.main.get().output.classesDirs)
    inputs.files(configurations.runtimeClasspath)
    outputs.dir(generatedProcessDirectory)
}

tasks.named("processResources") {
    dependsOn(generateBpmnWorkflows)
}

tasks.test {
    useJUnitPlatform()
}
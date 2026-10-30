plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.0.3"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
    testImplementation("de.bwaldvogel:mongo-java-server:1.47.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

application {
    applicationName = "taskflow-backend"
    mainClass.set("com.example.taskflow.ApplicationKt")
}

tasks.test {
    useJUnit()
}

tasks.shadowJar {
    archiveFileName.set("taskflow-backend-all.jar")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.example.taskflow.ApplicationKt"
    }
}

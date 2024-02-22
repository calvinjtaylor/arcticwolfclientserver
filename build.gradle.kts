import java.io.File
plugins {
    id("java")
}

group = "org.caltaylor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.11")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("ch.qos.logback:logback-core:1.4.14")
    implementation("org.json:json:20231013");
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1");

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.wiremock:wiremock:3.3.1");
}

tasks.test {
    useJUnitPlatform()
}



fun main() {
    // Start the JsonServer class
    startJsonServer()

    // Run the DirWatcher class
    runDirWatcher()
}

fun startJsonServer() {
    val jsonServerProcess = ProcessBuilder("\$JAVA_HOME/bin/java", "build/libs/articwolfscanner-1.0-SNAPSHOT.jar", "org/caltaylor/server/JsonServer")
            .directory(File("path/to/your/jsonserver/directory"))
            .start()

    // Optionally, you can wait for the process to finish
    jsonServerProcess.waitFor()

    // Optionally, you can check the exit value of the process
    if (jsonServerProcess.exitValue() == 0) {
        println("JsonServer started successfully.")
    } else {
        println("Failed to start JsonServer.")
    }
}

fun runDirWatcher() {
    val dirWatcherProcess = ProcessBuilder("java", "-cp", "path/to/your/dirwatcher.jar", "org.caltaylor.client.DirWatcher")
            .directory(File("path/to/your/dirwatcher/directory"))
            .start()

    // Optionally, you can wait for the process to finish
    dirWatcherProcess.waitFor()

    // Optionally, you can check the exit value of the process
    if (dirWatcherProcess.exitValue() == 0) {
        println("DirWatcher finished successfully.")
    } else {
        println("DirWatcher failed.")
    }
}
plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
tasks.register<JavaExec>("verify") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.openhoyi.protocol.ProtocolChecksKt")
}
tasks.named("test") { dependsOn("verify") }
tasks.check { dependsOn("verify") }

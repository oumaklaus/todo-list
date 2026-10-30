tasks.register("azurePackage") {
    dependsOn(":backend:shadowJar")
}

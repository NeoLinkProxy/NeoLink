rootProject.name = "NeoLink"

includeBuild("../APIs/NeoLinkAPI") {
    dependencySubstitution {
        substitute(module("top.ceroxe.api:neolinkapi")).using(project(":"))
    }
}

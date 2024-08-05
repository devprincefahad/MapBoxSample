pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mapbox Maven repository
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            // Do not change the username below. It should always be "mapbox" (not your username).
            credentials {
                username = "mapbox"
                password = "sk.eyJ1IjoiZXVsZXItZGV2IiwiYSI6ImNsZGE5ZzIxczAxNXEzcXB1NWpkNzdsbHQifQ.Ow4Jyry12-FMuO5-cWKKlA"
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}


rootProject.name = "MapBoxSample"
include(":app")
 
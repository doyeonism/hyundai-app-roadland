import java.util.Properties
plugins { id("com.android.application") version "9.5.0" }
val config = Properties().apply { rootProject.file("mapbox.local.properties").inputStream().use { load(it) } }
android {
 namespace = "com.doyeonism.mappreview"
 compileSdk = 37
 defaultConfig {
  applicationId = "com.doyeonism.mappreview"
  minSdk = 28
  targetSdk = 34
  versionCode = 1
  versionName = "1.0"
  buildConfigField("String", "MAPBOX_TOKEN", "\"${config.getProperty("token")}\"")
 }
 buildFeatures { buildConfig = true }
}

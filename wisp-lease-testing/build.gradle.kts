dependencies {
  api(project(":wisp-lease"))
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}

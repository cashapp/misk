Releasing
=========

 1. Change the version in `gradle.properties` to a non-SNAPSHOT verson.
 2. Update the `CHANGELOG.md` for the impending release.
 3. `git commit -am "Prepare for release X.Y.Z."` (where X.Y.Z is the new version)
 4. `./gradlew clean uploadArchives`.
 5. Visit [Sonatype Nexus](https://oss.sonatype.org/) and promote the artifact.
 6. `git tag -a X.Y.X -m "Version X.Y.Z"` (where X.Y.Z is the new version)
 7. Update the `gradle.properties` to the next SNAPSHOT version.
 8. `git commit -am "Prepare next development version."`
 9. `git push && git push --tags`

If step 5 fails, drop the Sonatype repo, fix the problem, commit, and start again at step 4.


Prerequisites
-------------

In `~/.gradle/gradle.properties`, set the following:

 * `SONATYPE_NEXUS_USERNAME` - Sonatype username for releasing to `com.squareup`.
 * `SONATYPE_NEXUS_PASSWORD` - Sonatype password for releasing to `com.squareup`.
 * `signing.keyId` - The public key ID (The last 8 symbols of the keyId. You can use `gpg -K` to get it).
 * `signing.password` - Passphrase for private key
 * `signing.secretKeyRingFile` - Secret key ring file containing your private key. May need to export this with `gpg --keyring secring.gpg --export-secret-keys > ~/.gnupg/secring.gpg`
 
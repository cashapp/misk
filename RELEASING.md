Releasing
=========

1. Checkout a new branch. Update `CHANGELOG.md`.

2. Set versions:

    ```
    export RELEASE_VERSION=A.B.C
    export NEXT_VERSION=A.B.D-SNAPSHOT
    ```

3. Update documentation and Gradle properties with `RELEASE_VERSION`

    ```
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      gradle.properties
    sed -i "" \
      "s/\"com.squareup.misk:\([^\:]*\):[^\"]*\"/\"com.squareup.misk:\1:$RELEASE_VERSION\"/g" \
      `find . -maxdepth 2 -name "README.md"`
    ```

4. Tag the release and push to GitHub. Submit and merge PR.

    ```
    git commit -am "Prepare for release $RELEASE_VERSION."
    git tag -a misk-$RELEASE_VERSION -m "Version $RELEASE_VERSION"
    git push && git push --tags
    ``` 

5. Trigger the [`Publish a release` action](https://github.com/cashapp/misk/actions/workflows/Release.yml) manually. Wait until it completes, then visit [Sonatype Nexus][sonatype_nexus] to promote (close then release in `Staging Repositories`) the artifact. Or drop it if there is a problem!

    ![Sonatype Release](/img/sonatype-release.gif)

6. In a new branch, prepare for the next release and push to GitHub. Submit and merge PR.

    ```
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/g" \
      gradle.properties
    git commit -am "Prepare next development version."
    git push
    ```

7. Draft a new [release](https://docs.github.com/en/github/administering-a-repository/managing-releases-in-a-repository) of `A.B.C` to trigger the "Publish the mkdocs to gh-pages" action.

## Snapshots

For a non-semvar snapshot release, use the following simpler steps.

1. Trigger the [`Publish a release` action](https://github.com/cashapp/misk/actions/workflows/Release.yml) manually. Wait until it completes, then visit [Sonatype Nexus][sonatype_nexus] to promote (close then release in `Staging Repositories`) the artifact. Or drop it if there is a problem!

    ![Sonatype Release](/img/sonatype-release.gif)
    
2. Update your usages to the new snapshot version, it will have a format like `0.25.0-20221109.1931-857c333`. Get your snapshot version in the `Set gradle publish version` section of the [`Publish a release` action](https://github.com/cashapp/misk/actions/workflows/Release.yml) logs.

## Troubleshooting

If the github action fails, drop the artifacts from Sonatype and re run the job. You might need to delete the plugin off the JetBrains plugin portal first if the ubuntu job which publishes it already succeeded.

For full Sonatype instructions, [see their docs](https://central.sonatype.org/publish/release/#releasing-deployment-from-ossrh-to-the-central-repository-introduction).

[sonatype_nexus]: https://s01.oss.sonatype.org/#welcome

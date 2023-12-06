# Releasing

Our release process is designed to be hands-off and automatic. This document aims to provide an understanding of the automated 
workflow we use to release new versions of Misk repository, along with insights into how to troubleshoot potential publishing issues. 

# Prerequisites

Before proceeding with the release process, ensure the following:
- You have read [CONTRIBUTING.md](CONTRIBUTING.md)
- `build.gradle.kts` is properly configured with the correct publishing configurations and publishing plugin is applied
- If you are changing the publication details, it is recommended that you have the necessary permissions 
and credentials to publish artifacts to Sonatype Nexus Repository (OSSRH)

# Automated Publishing Workflow

This repository is set up with an automated workflow using GitHub Actions.
- The workflow is triggered as soon as a pull request is merged into the `master` branch.  
- A `version` number is dynamically generated and assigned to the release
- The artifacts are published to Sonatype Nexus to a staging repository
- Sonatype performs various checks and validations on the artifacts
- Once verified, these artifacts are promoted and eventually synchronized to Maven Central
- Public builds can be found [here](https://mvnrepository.com/artifact/com.squareup.misk/misk)

# Troubleshooting

While our release process is designed to be seamless, occasionally, issues may arise that require attention. 
Here are some tips to help diagnose:

1. Review the logs and look for error messages or warnings related to the publishing process
2. If the failure seems to be related to network issues or timeouts, you can manually re-run the job 
in the Actions tab.
3. Verify the publication configurations are correct with the appropriate Sonatype host and pom settings
4. If the issue persists, you can contact the maintainers or reach out to Sonatype 
[support](https://issues.sonatype.org/secure/Dashboard.jspa]) for assistance.

# misk-aws-api

Generic AWS helper types that are not tied to a specific AWS SDK version (v1 or v2).

This module provides lightweight data types and utilities for working with AWS environments
(regions, account IDs) without pulling in any AWS SDK dependencies. It is safe to depend on
from both SDK v1 and SDK v2 codebases.

## Contents

- `AwsRegion` — data class representing an AWS region
- `AwsAccountId` — data class representing an AWS account ID
- `AwsEnvironment` — reads region and account information from environment variables

## Usage

Depend on `misk-aws-api` instead of `misk-aws` when you only need these core types and want
to avoid transitive AWS SDK v1 dependencies:

```kotlin
dependencies {
  implementation("com.squareup.misk:misk-aws-api")
}
```

If you need Guice bindings for `AwsRegion` and `AwsAccountId`, use `AwsEnvironmentModule` from
`misk-aws` which depends on this module transitively.

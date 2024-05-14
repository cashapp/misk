# Migrating Misk Metics

## Misk Metrics V1 to V2

To migrate from Misk Metrics v1 to v2, please apply the following openrewrite recipe:

[//]: # (TODO: small guide on how to use open rewrite or good doc links)

[//]: # (TODO: put the recipe somewhere so it's ready to use)

```yaml
type: specs.openrewrite.org/v1beta/recipe
name: misk.metrics.Migrator
displayName: Migrate Misk Metrics V1
description: Migrate from Misk Metrics v1 to v2
recipeList:
    - org.openrewrite.java.ChangeMethodName:
        methodPattern: misk.metrics.Metrics histogram(..)
        newMethodName: legacyHistogram
        matchOverrides: true
    - org.openrewrite.java.ChangeType:
        oldFullyQualifiedTypeName: misk.metrics.Metrics
        newFullyQualifiedTypeName: misk.metrics.v2.Metrics
```

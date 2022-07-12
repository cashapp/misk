
TODO - fix/rewrite

<img src="https://github.com/cashapp/misk/raw/master/misk.png" width="300">

See the [project website][misk] for documentation and APIs.

# Releases

Our [change log][changelog] has release history. API is subject to change. 

```kotlin
implementation("app.cash.wisp:wisp:0.0.1-SNAPSHOT")
```

Snapshot builds are [available][snap].


# Modules

[changelog]: http://cashapp.github.io/misk/changelog/
[misk]: https://cashapp.github.io/misk/
[snap]: https://oss.sonatype.org/content/repositories/snapshots/

## What are the wisp* modules?

The wisp* modules contain no Dependency Injection based code (i.e. no Guice, etc).  These modules 
should never refer to misk* modules, although misk* modules can (and should) use wisp* modules.

Also, modules that are wisp*-testing will only be used in test scope in other wisp modules, never 
in the api/implementation scope. 

If you are refactoring code from misk into the wisp modules, you must not break any external Misk dependencies
or apis.  It is ok to deprecate items in misk to encourage eventual migration to wisp directly if desired. If
your refactoring does not fit one of the existing wisp modules, create a new module.  For now, it is preferred
to have many small modules rather than larger conglomerate modules requiring many different dependencies.

It should be considered that wisp will be volatile for sometime with the potential for a lot of changes, additions, etc.
Misk apps should use wisp modules directly with caution as breaking changes might be required.

# misk-tailwind-gradle-plugin

The misk-tailwind-gradle-plugin handles usage of the `tailwind` CLI used to generate a production, minified CSS package for a Misk service where UI is defined with [Tailwind CSS v3](https://tailwindcss.com/docs/) and [kotlinx.html](https://github.com/Kotlin/kotlinx.html) (or other HTML templates shipped in the JAR).

The `tailwind` CLI includes powerful tree-shaking but requires full local access to the source UI files.

The Misk admin dashboard makes it possible for UI code to live in multiple repos and be stitched together at runtime.

The misk-tailwind-gradle-plugin dumps all code in the classpath to the `build/tmp` directory to make use of the `tailwind` CLI possible.

The resulting production CSS is given a unique name with the service name and SHA to prevent name collision and served at `/static/service-sha.min.css`.

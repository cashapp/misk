# misk-tailwind

The misk-tailwind module provides a type safe interface for frontend UI with [Tailwind CSS v3](https://tailwindcss.com/docs/) and [kotlinx.html](https://github.com/Kotlin/kotlinx.html).

For dynamic parts of the provided UI, it uses Hotwire ([Turbo](https://turbo.hotwired.dev/), [Stimulus](https://stimulus.hotwired.dev/)) which offers very lightweight dynamic UI in-browser functionality.


## Config

To support Misk UI, the `misk-tailwind/config` directory includes a full Tailwind NPM project which looks for Tailwind CSS classes within Misk code, and then produces a minified, tree-shaken CSS file to be included in deployment builds.

For services using Misk, they will likely need to mimic this setup so that they can get generated CSS for the classes that your UI uses (which may be a superset of the Misk classes).

Notably, in local development environments, the Tailwind Play CDN is used for easy iteration without running the config task. 

## Maintainers
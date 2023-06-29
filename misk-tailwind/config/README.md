# Tailwind CSS

Use the Tailwind CLI in this folder to generate tree-shaken CSS for Misk. 

Make a copy of this folder to your service repo to generate tree-shaken CSS for your application.

## Getting Started

Install the Tailwind CLI.

```bash
cd misk-tailwind/config
npm install
```

## Generating Output CSS

Use the run script to re-generate output.css which will be output into the correct location in `src/main/resources/web/static/cache/tailwind.css`.

You should re-run after adding any new UI so that any new styling classes are included in the output. 

By default, output is minified and "tree-shaking" is applied to remove any unused classes.

```bash
./start.sh
```

## Development

In local development, we use the [Tailwind Play CDN](https://tailwindcss.com/docs/installation/play-cdn) so any Tailwind classes can be added without re-running the above script.

The script must be re-run before shipping to real environments so all necessary CSS classes are present in the production output minified CSS.

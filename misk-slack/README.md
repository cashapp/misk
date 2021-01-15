This module provides a Slack webhook client to use for misk applications.

Installation:
1. Install the `SlackModule`
1. create a secret for your app that contains the webhook path
2. provide a config in your app like so:
```
  slack:
    webhook_path: "filesystem:/etc/secrets/service/slack-webhook-url"
    default_channel: "#misk" # optional
```

Usage:
1. `@Inject` the `SlackClient`
2. Call `SlackClient.postMessage()`

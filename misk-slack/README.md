This module provides two APIs that can be used to interact with Slack:  
Use the webhook client for posting one-off messages to channels.  
Use the Slack Web API when enabling user interactivity or calling any of the API
methods (https://api.slack.com/methods).

## Slack Webhook Client

### Installation

1. Install the `SlackModule`
2. Create a secret for your app that contains the webhook path
3. Provide a config in your app like so:

```
  slack:
    webhook_path: "filesystem:/etc/secrets/service/slack-api-webhook-url"
    default_channel: "#misk" # optional
```

### Usage

1. `@Inject` the `SlackClient`
2. Call `SlackClient.postMessage()`


## Slack Web API

1. Install the `RealSlackClientModule`
2. Upload the bearer token and signing secret provided by the Slack app.
   These are used by Slack to authenticate the request.

```
slack:
  bearer_token: "filesystem:/etc/secrets/service/slack_bearer_token.txt"
  signing_secret: "filesystem:/etc/secrets/service/slack_signing_secret.txt"
```

3. `@Inject` the `RealSlackClient`

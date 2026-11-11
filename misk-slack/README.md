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
    webhook_path: "filesystem:/etc/secrets/service/slack-webhook-url"
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

### Testing

Add the test fixtures to your test dependencies:

```kotlin
testImplementation(testFixtures("com.squareup.misk:misk-slack:<version>"))
```

Install `misk.slack.webapi.FakeSlackClientModule` in your test module in place of
`RealSlackClientModule`. Inject `SlackClient` into application code and
`FakeSlackClient` into tests. The module resets the fake between Misk tests.

Each method has its own invocation history, response queue, and exception queue:

| Method | Invocations | Responses | Exceptions |
| --- | --- | --- | --- |
| `postMessage` | `messages` | `responses` | `errors` |
| `postConfirmation` | `postConfirmationCalls` | `postConfirmationResponses` | `postConfirmationErrors` |
| `getUserByEmail` | `getUserByEmailCalls` | `getUserByEmailResponses` | `getUserByEmailErrors` |
| `getUserById` | `getUserByIdCalls` | `getUserByIdResponses` | `getUserByIdErrors` |

Confirmation calls record both the URL and request. Lookup calls record the email or
user ID. Queue response objects or exceptions to test failures and retries. Each queue
is consumed in insertion order. Exceptions take precedence over responses and are thrown
after recording the call, without consuming a response.

Once both queues for a method are empty, messages and confirmations succeed with no
timestamp. User lookups return a test user: email lookups use ID `123`, and ID lookups
echo the requested ID. All histories and queues are cleared when the fake is reset.

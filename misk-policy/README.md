# misk-policy

The misk-policy module provides a type safe interface to policy engine side cars.
The only supported policy engine is [Open Policy Agent (OPA)](https://www.openpolicyagent.org/docs/latest/) today.

## Assumptions

Setup of OPA is not included.  
The policy modules assumes that the policy engine is accessible via HTTP(s).

## Setup

Install the OPA misk module to make it accessible for guice.

```kotlin
install(OpaModule(serviceBuilder.config.opa))
```

Ensure that your configuration sets up the required baseURL.
Optionally, you may also use unix domain sockets to communicate with OPA.

```yaml
opa:
  baseUrl: "http://localhost:8181/"
  unixSocket: "\u0000authz.sock"
```

For unit testing and local development, see the `misk-policy-testing` module.

## Usage

`misk-policy` only supports policy queries to the `/v1/data` API of OPA today.
To make such a query, inject the `OpaPolicyEngine` interface and utilize one of the evaluate APIs.
This abstractions core tenet was type safety while interacting with a free form JSON interface.
To facilitate this, types which represent the shape of input and output documents must be defined.
In kotlin, this will take the shape of data classes.
Here is an example:

```kotlin
data class BasicResponse(val test: String) : OpaResponse
data class BasicRequest(val someValue: Int) : OpaRequest
```

This defines an input document, which has a single integer value, called someValue in the matching policy.
The output is a String, labelled test.

A matching policy might use these values like this (abc.rego):

```rego
package abc

default val test = "some value"

test = returnVal {
  input.someValue == 1
  returnVal := "something else"
}

test = returnVal {
  input.someValue > 9000
  returnVal := "That's impossible!"
}
```

Finally to actually perform a query from misk, all we need is to use the extension functions to the OpaPolicyEngine class, requiring some inferrable type hints:

```kotlin
val evaluate: BasicResponse = opaPolicyEngine.evaluate("abc", BasicRequest(1))
```

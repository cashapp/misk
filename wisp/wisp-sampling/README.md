# wisp-sampling

Utilities for sampling and rate limiting actions.

## Usage

A `Sampler` can be used to test if an action is allowed based on a particular policy.

### Rate Limiting

The rate limiting sampler allows a given number of samples per second:

```kotlin
val sampler = Sampler.rateLimiting(1L);

if (sampler.sample()) {
    performAction()
}
```

### Percentage

The percentage sampler allows the given percentage of samples:

```kotlin
val sampler = Sampler.percentage(50);

if (sampler.sample()) {
    performAction()
}
```

### Always

The always sampler allows all samples:

```kotlin
val sampler = Sampler.always();

if (sampler.sample()) {
    performAction()
}
```

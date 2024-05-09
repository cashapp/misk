Developer Guide
===============

## Action
Actions are Misk's unit for an endpoint. Misk lets you make HTTP actions, and gRPC actions via
[Wire](https://github.com/square/wire).

Learn more about [Misk actions](actions.md)

## Interceptors
Misk uses interceptors (middleware) to observe and potentially transform HTTP messages. The pattern
is borrowed from [OkHttp Interceptors].

Learn more about [Misk interceptors](interceptors.md)

## Clients
Misk provides configurable HTTP clients built on top of [OkHttp](https://github.com/square/okhttp)
and [Retrofit](https://github.com/square/retrofit), and gRPC clients built on top of
[Wire](https://github.com/square/wire).

Learn more about how to create, configure and test HTTP clients or gRPC clients within a Misk
application [here](clients.md).

## Tests
Misk provides a few ways to streamline testing with JUnit.

Learn more about [Misk tests](testing.md)

## Modules
Misk provides dozens of modules to facilitate the development and deployment of applications, and
the integration with clouds and various common technologies.

Learn more about [Misk modules](modules.md)

## Wisp
Wisp is a collection of kotlin modules providing various features and utilities, including config,
logging, feature flags and more. It is basically extracted Misk functionality without Dependency
Injection (i.e., no Guice).

Learn more about [Wisp](wisp/index.md).


[OkHttp Interceptors]: https://square.github.io/okhttp/features/interceptors/

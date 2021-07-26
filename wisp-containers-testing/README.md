# wisp-containers-testing

Create, start and stop containers for use in tests.

## Usage

The following example creates the "alpine" container, starts and stops it.

```kotlin
val container = Container {
  withImage("alpine")
  withName("alpine")
}

val composer = Composer("alpine", container)
composer.start()
assertTrue(composer.running.get())
composer.stop()
assertFalse(composer.running.get())
```

The following example composes Kafka and Zookeeper containers for testing. Kafka is exposed to the
jUnit test via 127.0.0.1:9102. In this example, Zookeeper is not exposed to the test.

```kotlin
val zkContainer = Container {
  withImage("confluentinc/cp-zookeeper")
  withName("zookeeper")
  withEnv("ZOOKEEPER_CLIENT_PORT=2181")
}
val kafka = Container {
  withImage("confluentinc/cp-kafka")
  withName("kafka")
  withExposedPorts(ExposedPort.tcp(port))
  withPortBindings(
    Ports().apply {
      bind(ExposedPort.tcp(9102), Ports.Binding.bindPort(9102))
    }
  )
  withEnv(
    "KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181",
    "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9102"
  )
}
val composer = Composer("e-kafka", zkContainer, kafka)
composer.start()
```
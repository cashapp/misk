# misk-audit-client

This module provides an interface and fake client for sending audit events to a data warehouse.

For an example real implementation, see [samples/exemplar](../samples/exemplar) for the `ExemplarAuditClient`.

If you don't have a remote service you want to use for audit events, you can use the `NoOpAuditClient` in real environments which does not send any events to a remote service.

For a fake client to use in tests, use the `FakeAuditClient` accessible via Gradle import `testImplementation(testFixtures(libs.miskAuditClient))`.

# Shutdown Management
In any application it is important to shut down gracefully, to avoid dropping already accepted work or 
creating inconsistent state. Misk handles this through use of a special `ReadyService`
and its [Service Manager Module](service-management.md)

## Orchestrating Graceful Shutdown
Misk ensures a graceful shutdown by dividing services into those that ingest or create work (e.g. SQS, Cron, Jetty), 
and those that are needed to process work (e.g. JDBC, Redis). To ensure the work created by an incoming API request, 
SQS subscription, cron job, or other work producing service is handled correctly even during shutdown, these services need

1. To stop generating new work
2. For the services they depend on to process their work to remain running until they have processed all existing work

### Ensuring needed services remain running
Because Misk cannot know ahead of time which services an application might or might not need, we cannot create 
hard dependencies from these work producing services to the various services needed for work processing. Instead,
we configure the work producing services to depend on - and services needed for work processing to be enhanced by -
the `ReadyService`, a special service that does no work but exists only to orchestrate a graceful shutdown.

By having work producing services depend on the `ReadyService` and work processing services enhanced by it, Misk will 
guarantee that services startup as follows:

1. Work processing services (e.g. Redis)
2. The `ReadyService`
3. Work generating services (e.g. Jetty)

At shutdown time, we walk the dependency graph in reverse, shutting down services as follows:

1. Work generating services (e.g. Jetty)
2. The `ReadyService`
3. Work processing services (e.g. Redis)

This ensures services that are needed for work processing remain up until all ingested work has been processed.

## Notes
* The mechanism for enhancing one service with another 
is [described in the service management doc](service-management.md#enhancements) 

Metrics
=======

Misk's metrics interface is a partial abstraction over Prometheus metrics. Any code that depends on
this module will probably also need to depend on misk-prometheus as that currently is the only
implementation.

There are two roles in the Metrics API:

 * Metrics producers: this is application code that emits metrics when certain events happen. Code
   in this role should depend on this misk-metrics module.
   
 * Metrics backends: this is infrastructure code that implements the metrics APIs used by metrics
   producers. Our current only backend is Prometheus. Backends can be in-memory only (for testing 
   and development) or they can integrate with a metrics service.
   


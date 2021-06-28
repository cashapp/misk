Misk-Warmup
===========

When any JVM service starts up, its earliest calls may suffer extreme latency. Multiple factors
contribute to this:

 * **Cold caches.** Instances Guava's `Cache` (or any other in-memory cache) start empty. The first
   calls need to do whatever expensive work the cache exists to accelerate.

 * **Empty connection pools.** JDBC and HTTPS connections to external services may be
   lazily-established on first use. These calls must wait to establish a connection, which typically
   requires both TCP and TLS handshakes.

 * **Empty thread pools.** The first use of an unbounded `ExecutorService` must allocate threads.

 * **Cold JVM Just-in-time (JIT) Compiler.** The JVM combines an interpreter with a just-in-time
   compiler. It optimizes frequently-used code paths (‘hot spots’) by compiling them into native
   code. The program is slower until its the JVM has discovered and optimized its hot spots. When
   the compiler is running, it competes with application code for CPU and memory. 

To mitigate this, a common practice is 'warming up' a process before serving interactive traffic.
Warm up a process by performing production-like work: this work should cause caches to be seeded,
pools to be filled, and hot spots to be compiled.



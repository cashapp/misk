# Misk Coroutines Rules

Coroutines are cooperative concurrency. If you don't cooperate (ie. suspend regularly), things will not be as efficient 
and may potentially deadlock.  

Misk allows for per request threading for which coroutines can be used to model concurrent operations. If the coroutines suspend,
then multiple coroutines can be run in parallel on the same thread. If coroutines need to block, they will run serially unless
a `Dispatcher` is provided that allows for running on multiple threads. Both models are supported.

Mixing thread based concurrency primitives, to synchronize coroutines, can result in deadlocks if threads are blocked waiting
Example
```class DangerTest {
  @Test
  fun threads() {
    val latch = CountDownLatch(3)
    thread { latch.countDown() }
    thread { latch.countDown() }
    thread { latch.countDown() }
    latch.await()
  }

  @Test
  fun coroutineDeadlock(){
    runBlocking {
      val latch = CountDownLatch(3)
      async { latch.countDown() }
      async { latch.countDown() }
      async { latch.countDown() }
      latch.await()
    }
  }
}```


When an action is declared with the `suspend` modifier, it will be called with a `Dispatcher` that has a single backing
thread (`runBlocking`). This thread is part of the Jetty Thread Pool and allocated to this specific request, therefore
it is safe to make blocking calls on. This will also take care of request scoped features that are thread local, 
such as ActionScoped values, MDC, tracing, etc.

When that function returns, the request and response body streams will be flushed and closed immediately.
The framework will release these resources for you.

Follow structured concurrency best practices, including:
 - All coroutines should be in child scopes for the incoming request scope.
 - Don't use `GlobalScope` or create a new `CoroutineScope()` object.

Misk is gradually adding experimental support for Kotlin coroutines. 
Functionality may be partially implemented or buggy in the short term as support is added to core libraries 
and implementations. Please report any issues encountered.





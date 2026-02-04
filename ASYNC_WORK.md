# AsyncSwitch Refactor - Misk Changes

This document contains all the information needed to complete the AsyncSwitch refactor for the misk repository. The goal is to enable dynamic runtime control of async tasks (SQS handlers, Cron, DynamoDB clustering) via feature flags without requiring pod restarts.

## Background

The cash-server repository has already been updated:
- `SkimAsyncSwitch.isEnabled()` now checks both env var AND feature flag
- Kafka consumers, Temporal workers, and Outbox have runtime checks in their loops
- Feature flag naming convention: `<appName>-disabled-<key>`

The misk changes are the final piece to complete the refactor.

## Goals

1. **Remove startup gating**: Services should always register; control happens at runtime
2. **Add runtime checks**: Check `asyncSwitch.isEnabled()` in processing loops for dynamic control
3. **Consistent pattern**: Use the same pattern as cash-server (log only on state changes, sleep when disabled)

---

## Phase 1: Remove Startup Gating

Remove all `conditionalOn<AsyncSwitch>(...)` calls from service module bindings. This ensures services are always registered in the Guice graph, allowing them to be dynamically enabled/disabled at runtime.

### Files to Modify

#### 1. `misk-cron/src/main/kotlin/misk/cron/CronModule.kt`

**Current code (lines 50-55):**
```kotlin
install(
  ServiceModule<RepeatedTaskQueue, ForMiskCron>().conditionalOn<AsyncSwitch>("cron").dependsOn<ReadyService>()
)
install(
  ServiceModule<CronTask>().conditionalOn<AsyncSwitch>("cron").dependsOn(dependencies).dependsOn<ReadyService>()
)
```

**Change to:**
```kotlin
install(
  ServiceModule<RepeatedTaskQueue, ForMiskCron>().dependsOn<ReadyService>()
)
install(
  ServiceModule<CronTask>().dependsOn(dependencies).dependsOn<ReadyService>()
)
```

**Note:** Remove `.conditionalOn<AsyncSwitch>("cron")` from both lines. CronTask already has a runtime check in its `scheduleWithBackoff` callback.

---

#### 2. `misk-aws/src/main/kotlin/misk/jobqueue/sqs/AwsSqsJobQueueModule.kt`

**Current code (lines 84-86):**
```kotlin
install(
  ServiceModule<RepeatedTaskQueue, ForSqsHandling>().conditionalOn<AsyncSwitch>("sqs").dependsOn<ReadyService>()
)
```

**Change to:**
```kotlin
install(
  ServiceModule<RepeatedTaskQueue, ForSqsHandling>().dependsOn<ReadyService>()
)
```

---

#### 3. `misk-aws/src/main/kotlin/misk/jobqueue/sqs/AwsSqsJobHandlerModule.kt`

**Current code (lines 35-40):**
```kotlin
install(DefaultAsyncSwitchModule())
install(
  ServiceModule<AwsSqsJobHandlerSubscriptionService>()
    .conditionalOn<AsyncSwitch>("sqs")
    .dependsOn(dependsOn)
    .dependsOn<ReadyService>()
)
```

**Change to:**
```kotlin
install(DefaultAsyncSwitchModule())
install(
  ServiceModule<AwsSqsJobHandlerSubscriptionService>()
    .dependsOn(dependsOn)
    .dependsOn<ReadyService>()
)
```

---

#### 4. `misk-aws/src/main/kotlin/misk/jobqueue/sqs/AwsSqsBatchJobHandlerModule.kt`

**Current code (lines 34-40):**
```kotlin
install(DefaultAsyncSwitchModule())
install(
  ServiceModule<AwsSqsJobHandlerSubscriptionService>()
    .conditionalOn<AsyncSwitch>("sqs")
    .dependsOn(dependsOn)
    .dependsOn<ReadyService>()
)
```

**Change to:**
```kotlin
install(DefaultAsyncSwitchModule())
install(
  ServiceModule<AwsSqsJobHandlerSubscriptionService>()
    .dependsOn(dependsOn)
    .dependsOn<ReadyService>()
)
```

---

#### 5. `misk-aws2-sqs/src/main/kotlin/misk/aws2/sqs/jobqueue/SqsJobHandlerModule.kt`

**Current code (lines 18-19):**
```kotlin
install(DefaultAsyncSwitchModule())
install(ServiceModule<SubscriptionService>().conditionalOn<AsyncSwitch>("sqs").dependsOn<ReadyService>())
```

**Change to:**
```kotlin
install(DefaultAsyncSwitchModule())
install(ServiceModule<SubscriptionService>().dependsOn<ReadyService>())
```

---

#### 6. `misk-aws2-sqs/src/main/kotlin/misk/aws2/sqs/jobqueue/SqsJobQueueModule.kt`

**Current code (lines 29-30):**
```kotlin
install(DefaultAsyncSwitchModule())
install(ServiceModule<SqsJobConsumer>().conditionalOn<AsyncSwitch>("sqs").dependsOn<ReadyService>())
```

**Change to:**
```kotlin
install(DefaultAsyncSwitchModule())
install(ServiceModule<SqsJobConsumer>().dependsOn<ReadyService>())
```

---

#### 7. `misk-clustering-dynamodb/src/main/kotlin/misk/clustering/dynamo/DynamoClusterModule.kt`

**Current code (lines 27-33):**
```kotlin
install(DefaultAsyncSwitchModule())
install(
  ServiceModule<DynamoClusterWatcherTask>()
    .conditionalOn<AsyncSwitch>("clustering")
    .dependsOn<ClusterService>()
    .enhancedBy<ReadyService>()
)
```

**Change to:**
```kotlin
install(DefaultAsyncSwitchModule())
install(
  ServiceModule<DynamoClusterWatcherTask>()
    .dependsOn<ClusterService>()
    .enhancedBy<ReadyService>()
)
```

---

## Phase 2: Add Runtime Checks

Add `asyncSwitch.isEnabled()` checks inside the processing/polling loops. This is where the actual dynamic control happens.

### Required Pattern

Follow this exact pattern for consistency with cash-server:

```kotlin
private var wasDisabled = false

// In the processing loop:
while (isRunning) {  // or equivalent loop condition
  // 1. Check shutdown/isRunning FIRST (usually already the loop condition)
  
  // 2. Check async switch SECOND
  if (!asyncSwitch.isEnabled("key")) {
    if (!wasDisabled) {
      logger.info { "Async <key> tasks disabled. Pausing." }
      wasDisabled = true
    }
    sleep(IDLE_WAIT_MS)  // Use existing backoff/delay if available
    continue
  }
  if (wasDisabled) {
    logger.info { "Async <key> tasks re-enabled. Resuming." }
    wasDisabled = false
  }
  
  // 3. Normal processing
  doWork()
}
```

**Key rules:**
1. Check shutdown state FIRST (before async switch)
2. Log only on state CHANGES (not every iteration)
3. Sleep and continue when disabled (don't exit the loop)
4. Use existing sleep/backoff mechanisms if available

---

### Files to Modify

#### 1. `misk-cron/src/main/kotlin/misk/cron/CronTask.kt` — VERIFY ONLY

**Current implementation already has runtime check (lines 27-29):**
```kotlin
asyncSwitch.isDisabled("cron") -> {
  logger.info { "Async tasks are disabled on this node. Skipping." }
  Status.OK
}
```

**Issue:** It logs on EVERY check when disabled, causing log spam. However, since this runs on a 60-second interval, this is acceptable. No changes needed, but optionally could be improved to only log on state changes.

**Action:** VERIFY this works correctly. No changes required.

---

#### 2. `misk-aws/src/main/kotlin/misk/jobqueue/sqs/SqsJobConsumer.kt`

The polling happens inside `QueueReceiver.run()` which is scheduled via `RepeatedTaskQueue`. The check needs to be added at the start of the `run()` method.

**File:** `misk-aws/src/main/kotlin/misk/jobqueue/sqs/SqsJobConsumer.kt`

**Step 1: Inject AsyncSwitch into SqsJobConsumer**

Add to the constructor:
```kotlin
private val asyncSwitch: AsyncSwitch,
```

Don't forget to add the import:
```kotlin
import misk.inject.AsyncSwitch
```

**Step 2: Add state tracking field**

Add to the class body (near line 62):
```kotlin
private var sqsAsyncSwitchWasDisabled = false
```

**Step 3: Modify `QueueReceiver.run()` method (around line 120)**

**Current:**
```kotlin
fun run(): Status {
  if (!shouldKeepRunning.get()) {
    log.info { "shutting down receiver for ${queue.queueName}" }
    return Status.NO_RESCHEDULE
  }
  val size = sqsConsumerAllocator.computeSqsConsumersForPod(queue.name, receiverPolicy)
  // ...
}
```

**Change to:**
```kotlin
fun run(): Status {
  if (!shouldKeepRunning.get()) {
    log.info { "shutting down receiver for ${queue.queueName}" }
    return Status.NO_RESCHEDULE
  }
  
  // Runtime async switch check
  if (!asyncSwitch.isEnabled("sqs")) {
    if (!sqsAsyncSwitchWasDisabled) {
      log.info { "Async sqs tasks disabled. SQS consumer paused for ${queue.queueName}." }
      sqsAsyncSwitchWasDisabled = true
    }
    return Status.NO_WORK  // Will cause backoff via RepeatedTaskQueue
  }
  if (sqsAsyncSwitchWasDisabled) {
    log.info { "Async sqs tasks re-enabled. SQS consumer resuming for ${queue.queueName}." }
    sqsAsyncSwitchWasDisabled = false
  }
  
  val size = sqsConsumerAllocator.computeSqsConsumersForPod(queue.name, receiverPolicy)
  // ...
}
```

**Note:** Since `QueueReceiver` is an inner class, it can access the outer class's `asyncSwitch` and `sqsAsyncSwitchWasDisabled` fields directly.

---

#### 3. `misk-aws2-sqs/src/main/kotlin/misk/aws2/sqs/jobqueue/SqsJobConsumer.kt`

This uses Kotlin coroutines. The `Subscriber.poll()` and `Subscriber.run()` methods are suspending functions.

**Approach:** The check needs to be added to the `Subscriber` class, which is created in `SqsJobConsumer.subscribe()`. Pass `AsyncSwitch` through to `Subscriber`.

**Step 1: Inject AsyncSwitch into SqsJobConsumer**

**File:** `misk-aws2-sqs/src/main/kotlin/misk/aws2/sqs/jobqueue/SqsJobConsumer.kt`

Add to constructor:
```kotlin
private val asyncSwitch: AsyncSwitch,
```

Add import:
```kotlin
import misk.inject.AsyncSwitch
```

**Step 2: Pass AsyncSwitch to Subscriber**

In the `subscribe()` method (around line 67), add `asyncSwitch` to the Subscriber constructor:

```kotlin
val subscriber =
  Subscriber(
    queueName = queueName,
    queueConfig = queueConfig,
    deadLetterQueueName = deadLetterQueueName,
    handler = handler,
    channel = Channel(queueConfig.channel_capacity),
    client = sqsClientFactory.get(queueConfig.region!!),
    sqsQueueResolver = sqsQueueResolver,
    sqsMetrics = sqsMetrics,
    moshi = moshi,
    clock = clock,
    tracer = tracer,
    visibilityTimeoutCalculator = visibilityTimeoutCalculator,
    asyncSwitch = asyncSwitch,  // ADD THIS
  )
```

**Step 3: Update Subscriber class**

**File:** `misk-aws2-sqs/src/main/kotlin/misk/aws2/sqs/jobqueue/Subscriber.kt`

Add to constructor:
```kotlin
val asyncSwitch: AsyncSwitch,
```

Add import:
```kotlin
import misk.inject.AsyncSwitch
import kotlinx.coroutines.delay
```

**Step 4: Add runtime check to `messageFlow()` method (around line 172)**

**Current:**
```kotlin
private fun messageFlow(queueName: QueueName) = flow {
  val queueUrl = sqsQueueResolver.getQueueUrl(queueName)
  while (true) {
    val startTime = clock.millis()
    val response = fetchMessages(queueUrl).await()
    // ...
  }
}
```

**Change to:**
```kotlin
private fun messageFlow(queueName: QueueName) = flow {
  val queueUrl = sqsQueueResolver.getQueueUrl(queueName)
  var wasDisabled = false
  while (true) {
    // Runtime async switch check
    if (!asyncSwitch.isEnabled("sqs")) {
      if (!wasDisabled) {
        logger.info { "Async sqs tasks disabled. SQS polling paused for ${queueName.value}." }
        wasDisabled = true
      }
      delay(2000)  // 2 second delay when disabled
      continue
    }
    if (wasDisabled) {
      logger.info { "Async sqs tasks re-enabled. SQS polling resuming for ${queueName.value}." }
      wasDisabled = false
    }
    
    val startTime = clock.millis()
    val response = fetchMessages(queueUrl).await()
    // ...
  }
}
```

---

#### 4. `misk-aws2-sqs/src/main/kotlin/misk/aws2/sqs/jobqueue/SubscriptionService.kt`

This service just subscribes handlers at startup. The actual polling is in `SqsJobConsumer` and `Subscriber`. No changes needed here since the runtime check is added to the polling loop in `Subscriber`.

**Action:** No changes needed.

---

#### 5. `misk-aws/src/main/kotlin/misk/jobqueue/sqs/AwsSqsJobHandlerSubscriptionService.kt`

Similar to above, this just subscribes handlers at startup. The actual polling is in `SqsJobConsumer`. 

**Action:** No changes needed.

---

#### 6. `misk-clustering-dynamodb/src/main/kotlin/misk/clustering/dynamo/DynamoClusterWatcherTask.kt`

**Step 1: Inject AsyncSwitch**

Add to constructor:
```kotlin
private val asyncSwitch: AsyncSwitch,
```

Add import:
```kotlin
import misk.inject.AsyncSwitch
```

**Step 2: Add state tracking field**

Add to class body:
```kotlin
private var clusteringAsyncSwitchWasDisabled = false
```

**Step 3: Modify `run()` method (around line 49)**

**Current:**
```kotlin
internal fun run(): Status {
  if (state() >= Service.State.STOPPING) {
    return Status.NO_RESCHEDULE
  }

  // If we're not active, we don't want to mark ourselves as part of the active cluster.
  if (clusterWeightProvider.get() > 0) {
    updateOurselfInDynamo()
  }
  recordCurrentDynamoCluster()
  return Status.OK
}
```

**Change to:**
```kotlin
internal fun run(): Status {
  if (state() >= Service.State.STOPPING) {
    return Status.NO_RESCHEDULE
  }

  // Runtime async switch check
  if (!asyncSwitch.isEnabled("clustering")) {
    if (!clusteringAsyncSwitchWasDisabled) {
      logger.info { "Async clustering tasks disabled. DynamoDB cluster watcher paused." }
      clusteringAsyncSwitchWasDisabled = true
    }
    return Status.NO_WORK  // Will cause backoff via RepeatedTaskQueue
  }
  if (clusteringAsyncSwitchWasDisabled) {
    logger.info { "Async clustering tasks re-enabled. DynamoDB cluster watcher resuming." }
    clusteringAsyncSwitchWasDisabled = false
  }

  // If we're not active, we don't want to mark ourselves as part of the active cluster.
  if (clusterWeightProvider.get() > 0) {
    updateOurselfInDynamo()
  }
  recordCurrentDynamoCluster()
  return Status.OK
}
```

**Step 4: Add logger**

Add to companion object (create one if needed):
```kotlin
companion object {
  internal val TABLE_SCHEMA = TableSchema.fromClass(DyClusterMember::class.java)
  private val logger = misk.logging.getLogger<DynamoClusterWatcherTask>()
}
```

Add import:
```kotlin
import misk.logging.getLogger
```

---

## Phase 3: Testing

### Unit Tests to Add/Update

#### 1. CronTask tests
Verify existing runtime check works. No new tests needed unless you want to validate the pattern.

#### 2. SqsJobConsumer tests (misk-aws)

Create or update test to verify:
- When `asyncSwitch.isEnabled("sqs")` returns false, `run()` returns `Status.NO_WORK` without calling SQS
- When switch toggles from disabled to enabled, processing resumes

**Test approach:**
```kotlin
@Test
fun `sqs consumer pauses when async switch is disabled`() {
  // Given
  val asyncSwitch = FakeAsyncSwitch()
  asyncSwitch.setEnabled("sqs", false)
  
  // When
  val result = queueReceiver.run()
  
  // Then
  assertThat(result).isEqualTo(Status.NO_WORK)
  // Verify no SQS calls were made
}

@Test
fun `sqs consumer resumes when async switch is re-enabled`() {
  // Given
  val asyncSwitch = FakeAsyncSwitch()
  asyncSwitch.setEnabled("sqs", false)
  queueReceiver.run()  // Logs "disabled"
  
  asyncSwitch.setEnabled("sqs", true)
  
  // When
  val result = queueReceiver.run()
  
  // Then
  assertThat(result).isEqualTo(Status.OK)  // Normal processing
  // Verify log shows "re-enabled"
}
```

#### 3. SqsJobConsumer tests (misk-aws2-sqs)

Similar tests but for the coroutine-based implementation. May need to use `runTest` from kotlinx-coroutines-test.

#### 4. DynamoClusterWatcherTask tests

```kotlin
@Test
fun `cluster watcher pauses when async switch is disabled`() {
  // Given
  asyncSwitch.setEnabled("clustering", false)
  
  // When
  val result = watcherTask.run()
  
  // Then
  assertThat(result).isEqualTo(Status.NO_WORK)
  // Verify no DynamoDB calls were made
}
```

---

## Checklist

### Phase 1: Remove Startup Gating
- [ ] `CronModule.kt` — remove `.conditionalOn<AsyncSwitch>("cron")` from lines 51, 54
- [ ] `AwsSqsJobQueueModule.kt` — remove `.conditionalOn<AsyncSwitch>("sqs")` from line 85
- [ ] `AwsSqsJobHandlerModule.kt` — remove `.conditionalOn<AsyncSwitch>("sqs")` from line 37
- [ ] `AwsSqsBatchJobHandlerModule.kt` — remove `.conditionalOn<AsyncSwitch>("sqs")` from line 37
- [ ] `SqsJobHandlerModule.kt` (aws2) — remove `.conditionalOn<AsyncSwitch>("sqs")` from line 19
- [ ] `SqsJobQueueModule.kt` (aws2) — remove `.conditionalOn<AsyncSwitch>("sqs")` from line 30
- [ ] `DynamoClusterModule.kt` — remove `.conditionalOn<AsyncSwitch>("clustering")` from line 30

### Phase 2: Add Runtime Checks
- [ ] Verify `CronTask.kt` already has runtime check (no changes needed)
- [ ] `SqsJobConsumer.kt` (misk-aws) — inject AsyncSwitch, add check in `QueueReceiver.run()`
- [ ] `SqsJobConsumer.kt` (misk-aws2-sqs) — inject AsyncSwitch, pass to Subscriber
- [ ] `Subscriber.kt` (misk-aws2-sqs) — add check in `messageFlow()` loop
- [ ] `DynamoClusterWatcherTask.kt` — inject AsyncSwitch, add check in `run()`

### Phase 3: Testing
- [ ] Add/update tests for `SqsJobConsumer` (misk-aws)
- [ ] Add/update tests for `SqsJobConsumer` and `Subscriber` (misk-aws2-sqs)
- [ ] Add/update tests for `DynamoClusterWatcherTask`
- [ ] Run all existing tests to verify no regressions

### Final Steps
- [ ] Build all affected modules: `./gradlew misk-cron:build misk-aws:build misk-aws2-sqs:build misk-clustering-dynamodb:build`
- [ ] Run tests: `./gradlew misk-cron:test misk-aws:test misk-aws2-sqs:test misk-clustering-dynamodb:test`
- [ ] Create PR with clear description referencing the AsyncSwitch refactor plan

---

## Risks and Mitigations

### Risk: Busy-looping when disabled
**Mitigation:** Always return `Status.NO_WORK` (for `RepeatedTaskQueue`) or use `delay()` (for coroutines). This triggers built-in backoff.

### Risk: Log spam when disabled for extended periods
**Mitigation:** Use `wasDisabled` state variable to log only on state changes.

### Risk: Slow shutdown due to long SQS polling
**Mitigation:** Check shutdown state FIRST before async switch. The existing `shouldKeepRunning` check handles this.

### Risk: Breaking existing functionality
**Mitigation:** The default behavior is "enabled" (feature flags return false = not disabled). Services will work exactly as before unless explicitly disabled via flag.

---

## Reference: Feature Flag Naming Convention

Feature flags follow the pattern: `<appName>-disabled-<key>`

| Key | What it controls |
|-----|------------------|
| `sqs` | SQS handlers |
| `cron` | Cron tasks |
| `clustering` | DynamoDB clustering |

The `appName` is the service name as configured in Misk (the `@AppName` binding).

Example: To disable SQS for `cash-postmaster`, create flag `cash-postmaster-disabled-sqs` and set to `true`.

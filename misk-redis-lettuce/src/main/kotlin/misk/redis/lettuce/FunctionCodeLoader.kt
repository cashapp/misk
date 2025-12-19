package misk.redis.lettuce

import java.util.concurrent.CompletionStage
import misk.redis.lettuce.cluster.ClusterFunctionCodeLoader
import misk.redis.lettuce.standalone.StandaloneFunctionCodeLoader

/**
 * Interface for loading Redis Functions from a file path specified in Redis configuration.
 *
 * Redis Functions are server-side Lua scripts that provide several advantages over traditional EVAL scripts:
 * - Better performance through automatic caching and compilation of functions
 * - Improved security with proper isolation and memory management
 * - Library organization capabilities for better code reuse and maintenance
 * - Built-in versioning and dependency management
 * - Type safety through function signatures
 * - Atomic execution guarantees
 *
 * This loader is responsible for:
 * 1. Reading function code from the file specified in [RedisReplicationGroupConfig.functionCodeFilePath] and/or
 *    [RedisClusterGroupConfig.functionCodeFilePath]
 * 2. Loading the functions into Redis using the FUNCTION LOAD command
 * 3. Managing function registration and updates
 *
 * Functions loaded through this interface are persistent across Redis restarts and are automatically replicated to
 * replica nodes, ensuring consistency across the Redis deployment.
 *
 * Two implementations are available:
 * - [StandaloneFunctionCodeLoader] for Redis standalone/replication deployments
 * - [ClusterFunctionCodeLoader] for Redis cluster deployments
 *
 * @see [StandaloneFunctionCodeLoader]
 * @see [ClusterFunctionCodeLoader]
 * @see <a href="https://redis.io/docs/latest/develop/interact/programmability/functions-intro/">Redis Functions
 *   Documentation</a>
 * @see <a href="https://redis.io/commands/function-load/">FUNCTION LOAD Command</a>
 * @see <a href="https://redis.io/docs/management/scaling/">Redis Scaling Documentation</a>
 */
interface FunctionCodeLoader {
  fun load(): CompletionStage<Void>
}

package misk.redis

import okio.Closeable
import redis.clients.jedis.ClusterPipeline
import redis.clients.jedis.Pipeline
import redis.clients.jedis.Response
import redis.clients.jedis.args.FlushMode
import redis.clients.jedis.args.FunctionRestorePolicy
import redis.clients.jedis.commands.PipelineBinaryCommands
import redis.clients.jedis.commands.PipelineCommands

internal sealed class JedisPipeline : Closeable, PipelineCommands, PipelineBinaryCommands {
  class ClusterJedisPipeline(private val pipeline: ClusterPipeline) : JedisPipeline(),
    Closeable by pipeline, PipelineCommands by pipeline, PipelineBinaryCommands by pipeline {

    // The following methods must be overridden because the class inherits multiple implementations.

    override fun functionDump(): Response<ByteArray> {
      return pipeline.functionDump()
    }

    override fun functionKill(): Response<String> {
      return pipeline.functionKill()
    }

    override fun functionFlush(): Response<String> {
      return pipeline.functionFlush()
    }

    override fun functionFlush(mode: FlushMode?): Response<String> {
      return pipeline.functionFlush(mode)
    }

    override fun functionRestore(serializedValue: ByteArray?): Response<String> {
      return pipeline.functionRestore(serializedValue)
    }

    override fun functionRestore(
      serializedValue: ByteArray?,
      policy: FunctionRestorePolicy?
    ): Response<String> {
      return pipeline.functionRestore(serializedValue, policy)
    }
  }

  class PooledPipeline(private val pipeline: Pipeline) : JedisPipeline(),
    Closeable by pipeline, PipelineCommands by pipeline, PipelineBinaryCommands by pipeline {

    // The following methods must be overridden because the class inherits multiple implementations.

    override fun functionDump(): Response<ByteArray> {
      return pipeline.functionDump()
    }

    override fun functionKill(): Response<String> {
      return pipeline.functionKill()
    }

    override fun functionFlush(): Response<String> {
      return pipeline.functionFlush()
    }

    override fun functionFlush(mode: FlushMode?): Response<String> {
      return pipeline.functionFlush(mode)
    }

    override fun functionRestore(serializedValue: ByteArray?): Response<String> {
      return pipeline.functionRestore(serializedValue)
    }

    override fun functionRestore(
      serializedValue: ByteArray?,
      policy: FunctionRestorePolicy?
    ): Response<String> {
      return pipeline.functionRestore(serializedValue, policy)
    }
  }
}

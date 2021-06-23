package misk.policy.opa

import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Binds
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.async.ResultCallbackTemplate
import com.github.dockerjava.core.command.WaitContainerResultCallback
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import okio.Buffer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create

interface OpaTestConfig {
  val policyDir: Property<String>
}

class OpaTestPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val config: OpaTestConfig = project.extensions.create("opa")

    val opaTestWithDocker = project.tasks.create("opaTestWithDocker") {
      group = "OPA"
      description = "Evaluate OPA tests via docker OPA instance"

      doLast {
        val dockerClient = DockerClientBuilder.getInstance()
          .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
          .build()
        val policyDir = project.projectDir.absolutePath + "/" + config.policyDir.get()
        val containerId = dockerClient.createContainerCmd("openpolicyagent/opa")
          .withCmd(listOf("test", "/repo", "-b", "--explain", "fails"))
          .withHostConfig(
            HostConfig.newHostConfig()
              .withAutoRemove(true)
              .withBinds(Binds(Bind(policyDir, Volume("/repo"))))
          )
          .withName("opa_tester")
          .withTty(true)
          .exec().id

        dockerClient.startContainerCmd(containerId)
          .withContainerId(containerId)
          .exec()

        val buffer = Buffer()
        dockerClient.logContainerCmd(containerId)
          .withSince(0)
          .withStdErr(true)
          .withStdOut(true)
          .withFollowStream(true)
          .exec(Callback(buffer))
          .awaitStarted()
          .awaitCompletion()

        val awaitStatusCode = dockerClient.waitContainerCmd(containerId)
          .withContainerId(containerId)
          .exec(WaitContainerResultCallback())
          .awaitStatusCode()

        val logs = buffer.readUtf8()
        if (awaitStatusCode != 0) {
          throw IllegalStateException(logs)
        }
        println(logs)
      }
    }

    project.tasks.getByName("check").dependsOn(opaTestWithDocker)
  }

  class Callback(val buffer: Buffer) : ResultCallbackTemplate<Callback, Frame>() {
    override fun onNext(item: Frame) {
      buffer.write(item.payload)
    }
  }
}

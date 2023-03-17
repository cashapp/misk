/** @jsx jsx */
import { Button, Callout, Drawer, H1 } from "@blueprintjs/core"
import { jsx } from "@emotion/core"
import { CodePreContainer } from "@misk/core"
import { useState } from "react"
import { cssFloatLeft, cssTableScroll, cssHeader } from "../components"

export const TabHeader = () => {
  const [isOpenInstructions, setIsOpenInstructions] = useState(false)

  return (
    <span css={cssHeader}>
      <span css={cssFloatLeft}>
        <H1>Config</H1>
      </span>
      <Button
        active={isOpenInstructions}
        onClick={() => setIsOpenInstructions(!isOpenInstructions)}
      >
        {"Documentation"}
      </Button>
      <Drawer
        isOpen={isOpenInstructions}
        onClose={() => setIsOpenInstructions(false)}
        size={Drawer.SIZE_LARGE}
        title={"Documentation"}
      >
        <div css={cssTableScroll}>
          <Callout
            title={
              "1. Only see JVM info? Want to see runtime config or raw YAML? Change your ConfigTabMode."
            }
          >
            <p>{"ConfigTabMode.SAFE: Only JVM config"}</p>
            <p>
              {
                "ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG: Show JVM and runtime config with secrets redacted"
              }
            </p>
            <p>
              {
                "ConfigTabMode.UNSAFE_LEAK_MISK_SECRETS: Show JVM, redacted runtime config, and raw unredacted YAML files"
              }
            </p>

            <CodePreContainer>
              {`// ExemplarService.kt

fun main(args: Array<String>) {
  val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  val config = MiskConfig.load<ExemplarConfig>("exemplar", deployment)
  MiskApplication(
    ConfigModule.create("exemplar", config),
    ...
    AdminDashboardModule(
      isDevelopment = !deployment.isReal,
-     configTabMode = ConfigMetadataAction.ConfigTabMode.SAFE
+     configTabMode = ConfigMetadataAction.ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG
    )
  ).run(args)
}
`}
            </CodePreContainer>
          </Callout>
          <Callout
            title={
              "2. Seeing sensitive information that should be redacted? Use misk.config.Secret or add @Redact."
            }
          >
            <CodePreContainer>
              {`// ExemplarConfig.kt

+ import misk.config.Redact
+ import misk.config.Secret

  data class ExemplarConfig(
// Redact using a Secret with sensitive data stored in a resource (classpath, filesystem...)
-   val apiKey: String,
+   val apiKey: Secret<String>,
    val web: WebConfig,
    val prometheus: PrometheusConfig,
    // Redact a single field
+   @Redact
    val password: String,
    val keys: KeysConfig,
  ) : Config

// Redact entire class
+ @Redact
  data class KeysConfig(
    val key1: String,
    val key2: String,
  )
`}
            </CodePreContainer>
          </Callout>
        </div>
      </Drawer>
    </span>
  )
}

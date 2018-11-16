import { color, Environment } from "@misk/common"

export const defaultEnvironment = Environment.PRODUCTION
export const defaultEnvironmentIndicatorsVisible = [
  Environment.DEVELOPMENT,
  Environment.STAGING,
  Environment.TESTING
]

export const environmentToColor = (environment: Environment) => {
  switch (environment) {
    case Environment.DEVELOPMENT:
      return color.blue
    case Environment.TESTING:
      return color.indigo
    case Environment.STAGING:
      return color.green
    case Environment.PRODUCTION:
      return color.red
  }
}

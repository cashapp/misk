import { color, Environment } from "@misk/common"

export const defaultEnvironment = Environment.PRODUCTION
export const defaultEnvironmentIndicatorsVisible = [
  Environment.DEVELOPMENT,
  Environment.STAGING,
  Environment.TESTING
]

export const environmentColorMap = {
  default: color.cadet,
  [`${Environment.DEVELOPMENT}`]: color.blue,
  [`${Environment.TESTING}`]: color.indigo,
  [`${Environment.STAGING}`]: color.green,
  [`${Environment.PRODUCTION}`]: color.red
}

export const environmentToColor = (environment: Environment) => {
  try {
    return environmentColorMap[environment]
  } catch (e) {
    return environmentColorMap.default
  }
}

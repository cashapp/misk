import * as frintConfig from 'frint-config'
import miskExternals from './externals'

export default {
  externals: miskExternals.concat(frintConfig.externals),
  miskExternals,
  frintExternals: frintConfig.frintExternals,
  lodashExternals: frintConfig.lodashExternals,
  thirdPartyExternals: frintConfig.thirdPartiesExternals,
  rxjsExternals: frintConfig.rxjsExternals,
}
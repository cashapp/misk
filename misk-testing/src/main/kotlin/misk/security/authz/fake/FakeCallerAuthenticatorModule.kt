package misk.security.authz.fake

import misk.inject.KAbstractModule
import misk.security.authz.MiskCallerAuthenticator

class FakeCallerAuthenticatorModule : KAbstractModule() {
  override fun configure() {
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
  }
}
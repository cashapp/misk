package misk.tokens

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealTokenGenerator @Inject constructor() : TokenGenerator by wisp.token.RealTokenGenerator()

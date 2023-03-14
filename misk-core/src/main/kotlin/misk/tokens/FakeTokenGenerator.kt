package misk.tokens

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeTokenGenerator @Inject constructor() : TokenGenerator by wisp.token.FakeTokenGenerator()

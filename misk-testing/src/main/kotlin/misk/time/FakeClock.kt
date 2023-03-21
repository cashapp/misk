package misk.time

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeClock @Inject constructor() : wisp.time.FakeClock()

package misk.time

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class FakeClock @Inject constructor() : wisp.time.FakeClock()

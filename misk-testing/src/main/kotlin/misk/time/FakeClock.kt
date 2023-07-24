package misk.time

import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class FakeClock @Inject constructor() : wisp.time.FakeClock()

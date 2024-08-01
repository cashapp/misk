package misk.inject

import misk.annotation.ExperimentalMiskApi

/**
 * This class should be extended by test modules used in tests, for misk to reuse the guice injector across tests for significantly faster test suite performance.
 */
@ExperimentalMiskApi
abstract class ReusableTestModule: KInstallOnceModule()

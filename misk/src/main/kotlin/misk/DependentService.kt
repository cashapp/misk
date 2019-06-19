package misk

import com.google.common.util.concurrent.Service
import com.google.inject.Key

/**
 * Directs the order that services will be started up. A service won't be started until the services
 * producing its consumed keys have been started.
 *
 * For example, this blocks startup of the `TyrannosaurusService` until the `BrontosaurusService`
 * has finished starting.
 *
 *     class BrontosaurusService : DependentService {
 *       fun consumedKeys() = setOf()
 *       fun producedKeys() = setOf(Meat::class.toKey())
 *       ...
 *     }
 *
 *     class TyrannosaurusService : DependentService {
 *       fun consumedKeys() = setOf(Meat::class.toKey())
 *       fun producedKeys() = setOf()
 *       ...
 *     }
 *
 * It is an error for the same key to be produced by multiple services. But multiple consumers can
 * consume the same key.
 *
 *
 *
 * ### Deprecated by [ServiceModule].
 *
 * The above example may be similarly implemented in a [misk.inject.KAbstractModule]. e.g.
 *
 *     interface Meat : Service
 *     class BrontosaurusService : Meat { ... }
 *     class TyrannosaurusService : Service { ... }
 *
 *     class DinosaurModule : KAbstractModule() {
 *       override fun configure() {
 *          bind<Meat>().to<BrontosaurusService>()
 *          install(ServiceModule<Meat>())
 *          install(ServiceModule<TyranosaurusService>()
 *              .dependsOn<Meat>())
 *       }
 *     }
 *
 * It is an error to install the same service twice. Multiple service may depend on the same service
 * however.
 */
@Deprecated(
    "ServiceModule allows you to declare dependencies on installation site. Do not implement this."
)
interface DependentService : Service {
  val consumedKeys: Set<Key<*>>
  val producedKeys: Set<Key<*>>
}
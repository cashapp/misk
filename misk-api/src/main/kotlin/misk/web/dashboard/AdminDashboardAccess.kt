package misk.web.dashboard

/**
 * Bind to set access for the Misk Admin Dashboard.
 *
 * ```kotlin
 * // Give engineers access to the admin dashboard for Exemplar service
 * multibind<AccessAnnotationEntry>().toInstance(
 *   AccessAnnotationEntry<AdminDashboardAccess>(
 *   capabilities = listOf("admin_console"))
 * )
 * ```
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class AdminDashboardAccess

package misk.web

/**
 * This represents an HTTP cookie that enables servers to maintain and manage stateful sessions
 *
 **/
data class Cookie @JvmOverloads constructor(
  /**
   * The name of the cookie
   */
  val name: String,
  /**
   * The value of the cookie
   */
  val value: String,
  /**
   * The domain for which the cookie is valid
   */
  val domain: String? = null,
  /**
   * The path on the server for which the cookie is valid
   */
  val path: String? = null,
  /**
   * The maximum duration in seconds for which the cookie remains valid
   *
   * A positive value indicates that the cookie will expire after that many seconds have passed.
   * Note that the value is the maximum age when the cookie will expire, not the cookie's current age.
   *
   * A negative value means that the cookie is not stored persistently and will be deleted when the Web browser exits.
   * A zero value causes the cookie to be deleted.
   */
  val maxAge: Int = -1,
  /**
   * Indicates if the cookie should only be transmitted over a secure protocol (HTTPS or SSL)
   */
  val secure: Boolean = false,
  /**
   * Indicates if cookie is HTTP only meaning it cannot be accessed by client-side scripting code
   */
  val httpOnly: Boolean = false
)

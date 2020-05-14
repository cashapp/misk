package misk.crypto

import java.security.GeneralSecurityException

class InvalidEncryptionContextException : GeneralSecurityException {
  constructor(msg: String) : super(msg)
  constructor(msg: String, throwable: Throwable) : super(msg, throwable)
}
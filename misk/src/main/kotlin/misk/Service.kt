package misk

import com.google.common.util.concurrent.Service

fun Service.name(): String = when (this) {
    is CoordinatedService -> service.javaClass.simpleName
    else -> javaClass.simpleName
}
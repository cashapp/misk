package misk.events

import com.google.common.util.concurrent.Service

/** Marker interface for services that are [Producer]s. **/
@Deprecated("This API is no longer supported and replaced by the new event system's client library")
interface ProducerService : Service

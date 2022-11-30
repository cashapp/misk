package misk.web.exceptions

import misk.web.Response
import misk.web.ResponseBody
import org.slf4j.event.Level

/** Maps an exception to a [Response] */
interface ExceptionMapper<in T : Throwable> {
  /** @return true if the [ExceptionMapper] can handle the given exception */
  fun canHandle(th: Throwable): Boolean {
    // Here to avoid breaking changes while I clean up downstream dependencies
    error("This is unused!")
  }

  /** @return the [Response] corresponding to the exception. */
  // TODO(mmihic): Allow control of marshalling based on content type. Ideally we could
  // return a Response<*> and then content negotiation would control how that * gets mapped.
  // Right now however the marshalling interceptor works off the static return type of the
  // action method, so it blows up when we return a different type from here. This is a general
  // issue; interceptors should be able to return responses that are of a different structure
  // then the action method return value
  fun toResponse(th: T): Response<ResponseBody>

  // gRPC has a different response mechanism where it leaves the body empty, sends HTTP 200, and
  // sets two _headers_, :grpc-status and :grpc-message. ExceptionMappers can _optionally_ override
  // toGrpcResponse to have more control over the status and message returned to gRPC clients.
  // If not overridden, a default mapping will be used that converts the HTTP status to a gRPC
  // status and puts the ResponseBody text in :grpc-message.
  fun toGrpcResponse(th: T): GrpcErrorResponse? = null

  /**
   * @return the level at which the given exception should be logged. defaults to ERROR but can
   * be overridden by the mapper for the given exception
   */
  fun loggingLevel(th: T): Level = Level.ERROR
}

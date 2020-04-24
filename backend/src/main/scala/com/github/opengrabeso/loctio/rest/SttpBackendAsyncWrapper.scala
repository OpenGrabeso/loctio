package com.github.opengrabeso.loctio.rest

import com.softwaremill.sttp._

import scala.concurrent.{ExecutionContext, Future}

/* Udash requires a backend of type SttpBackend[Future, Nothing]. The simplest backend working with GAE is HttpURLConnectionBackend,
which implements SttpBackend[Id, Nothing]
* */
class SttpBackendAsyncWrapper(syncBackend: SttpBackend[Id, Nothing])(implicit executionContext: ExecutionContext) extends SttpBackend[Future, Nothing] {
  override def send[T](request: Request[T, Nothing]) = {
    Future.successful(syncBackend.send(request))
  }
  override def close() = {
    syncBackend.close()
  }
  // following compiles, but I have no idea if it works, it does not seem to be called in our use case
  override def responseMonad: MonadError[Future] = {
    syncBackend.responseMonad.map(()){x => new FutureMonad()}
  }
}

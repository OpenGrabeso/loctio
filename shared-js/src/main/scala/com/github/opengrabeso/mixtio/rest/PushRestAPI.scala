package com.github.opengrabeso.mixtio
package rest

import io.udash.rest._

import scala.concurrent.Future

trait PushRestAPI {

  /**
    * offer files to the server, server will respond which files need to be sent
    * for each file send file id + file digest
    * server will return list of ids with the digest not matched
    */
  def offerFiles(files: Seq[(String, String)]): Future[Seq[String]]

  // upload a single file
  @PUT
  def uploadFile(@Query id: String, content: Array[Byte], digest: String): Future[Unit]

  /**
    check which files are still pending (offered but not uploaded) or done
    Note: file is reported as "done" only once
  */
  @GET
  def expected: Future[(Seq[String], Seq[String], Option[String])]

  @PUT
  def reportError(toString: String): Future[Unit]
}

object PushRestAPI extends RestApiCompanion[EnhancedRestImplicits,PushRestAPI](EnhancedRestImplicits)

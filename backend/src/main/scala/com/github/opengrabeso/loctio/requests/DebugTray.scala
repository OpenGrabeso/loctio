package com.github.opengrabeso.loctio
package requests

import com.github.opengrabeso.loctio.common.FileStore

import scala.reflect.ClassTag

object DebugTray extends DefineRequest("/debug-tray") {
  def html(request: Request, resp: Response) = {
    val token = request.queryParams("token")
    object dummyStorage extends common.FileStore {
      case class FileItem(name: String)

      def itemModified(fileItem: dummyStorage.FileItem) = None
      def listAllItems() = Seq.empty
      def deleteItem(item: dummyStorage.FileItem) = ()
      def store(name: FileStore.FullName, obj: AnyRef, metadata: (String, String)*) = ()
      def load[T: ClassTag](name: FileStore.FullName) = None
      def metadata(name: FileStore.FullName) = Seq.empty
      def updateMetadata(item: FileStore.FullName, metadata: Seq[(String, String)]) = false
      def itemName(item: dummyStorage.FileItem) = item.name
    }
    val html =  rest.RestAPIServer.user(token).trayNotificationsHTMLImpl(dummyStorage)._1


    xml.Unparsed(
      // modify the head / body so that the web page debugging is more useful
      html
        .replace("</head>",
          s"""
             <title>$appName - HTML Debugging</title>
             <link href="static/debug.css" rel="stylesheet"/>
             </head>
             """
        )
    )
  }

}

package com.github.opengrabeso.mixtio
package requests

import common.model._

trait ActivityStorage {

  def storeLocation(userId: String, location: String) = {
    Storage.store(Storage.getFullName("location", "current", userId), location)
  }

}

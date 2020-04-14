package com.github.opengrabeso.loctio
package requests

import common.model._

trait ActivityStorage {

  def storeLocation(userId: String, location: String) = {
    Storage.store(Storage.getFullName("location", "current", userId), location)
  }

}

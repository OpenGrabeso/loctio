package com.github.opengrabeso.mixtio
package rest

class UserRestAPIServer(val userAuth: Main.GitHubAuthResult) extends UserRestAPI with RestAPIUtils with requests.ActivityStorage {
  def name = syncResponse {
    userAuth.login
  }

}

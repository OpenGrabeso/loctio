Loctio
======

Presence / location
-------------------

The application tracks presence / location for the users connecting to it based on their IP address.

Users are identified by providing GitHub access token. For this purpose a token with no scopes is enough. If you
want to use notifications (see below), scopes `notifications` and `repo` should be present on the token. 

Tray utility can be downloaded from [releases](https://github.com/OpenGrabeso/loctio/releases).

Notifications
-------------

The utility also listens for GitHub notifications and displays them.

Developer notes
---------------

The application project is created in InteliJ IDEA, the project is deployed as Google App Engine.

Developer server
----------------

To run developer server use entry point DevServer, set environment variable like:

`GOOGLE_APPLICATION_CREDENTIALS=some_path/loctio-xxxx.json`


Setup notes
-----------

Before running the server you need to install at least one admin. 

Admins are listed in the /admins folder of the Google Cloud Storage bucket, there names are used as filenames, the file
content is ignored.

Admin can then add individual users.


Loctio
======

The application tracks presence / location for the users connecting to it based on their IP address.

Users are identified by providing GitHub access token (a token with no scopes at all is enough).

Developer notes
---------------

The application project is created in InteliJ IDEA, the project is deployed as Google App Engine.


Setup notes
-----------

Before running the server you need to install at least one admin. 

Admins are listed in the /admins folder of the Google Cloud Storage bucket, there names are used as filenames, the file
content is ignored.

Admin can then add individual users.


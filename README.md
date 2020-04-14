Loctio
======

The application tracks presence / location for the users connecting to it based on their IP address.

Users are identified by providing GitHub access token (a token with no scopes at all is enough).

Developer notes
---------------

The application project is created in InteliJ IDEA, the project is deployed as Google App Engine.
If you want to deploy your own build, you need to provide:
 - a list of GitHub users which can access the server

Put them in a file `resources/secret.txt`, list users on the first line, separated with commas, like:

    User1,User2,User3

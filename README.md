Stravimat
=========

The application allows spliting Strava activities and editing lap information in them. One particular use
is to simplify handling of multisport activities like triathlon or duathlon. The application should eventually
become an easy to use Strava uploader, especially for Suunto Quest users who need to merge heartrate and GPS data.


Developer notes
---------------

The application project is created in InteliJ IDEA, the project is deployed as Google App Engine.
If you want to deploy your own build, you need to provide:
 - your own Client ID and Client Secret from your own application API registration at https://www.strava.com/settings/api.
 - your own MapBox access token
 - your own DarkSky.net (Forecast.io) secret key 

Put them in a file `resources/secret.txt`, with an ID and the secret each on its own line, like:

    12356
    47875454gae8974bcd798654
    pk.eyJ1Ijoib3.......

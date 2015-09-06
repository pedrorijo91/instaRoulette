# insta-roulette[![Codacy Badge](https://api.codacy.com/project/badge/323d424fcc7f491d9cc129403129ab8e)](https://www.codacy.com)

Play webapp that challenges you into make random likes in your friends photos, using [Instagram API](https://instagram.com/developer/endpoints/). The app will choose an old photo, to make you seem a stalker.

Based on [Like Creeper](http://likecreeper.com/)

## How to run
Simply use the command `sbt run` as any other play app

### Requirements
In the `conf/application.conf` file you need to insert the app `client ID` and `client secret`.
You can get those creating a new instagram app in [Instagram developer](https://instagram.com/developer/clients/register/).

You may also define other minor configurations:
* Datetime format
* Minimum weeks for selected photos (for instance, if defined as `10`, only photos with `10`or more weeks will be choosen to a random like)
* Number of photos to be retrieved in each endpoint invocation (bigger number means less calls, but slower)

## Problems due to Instagram API new restrictions
Due to the fact that Instagram API does not allows new apps to get access to relationships/likes scopes [unless the target users are business](https://help.instagram.com/contact/185819881608116), this app will not work.

### How to overcome
If you want to try the roulette for your own account just get an access token and replace it in `app/services/Instagram#getAccessToken`.

You can get a token using [this url](https://apigee.com/console/instagram), providing authorization, making any endpoint call, and inspecting
the outgoing request, looking for the access token

## TODO

* make a prettier interface (I would spend time on that if the app could be deployed - hoping Instagram will reopen its API)
* take care of connection timeouts - currently throwing an error
* validate json responses

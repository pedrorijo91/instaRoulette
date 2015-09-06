package services

import controllers.routes
import models.Media
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.{WS, WSResponse}
import play.api.mvc.{AnyContent, Request}
import play.api.{Logger, Play}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

/**
 * Created by pedrorijo on 22/08/15.
 */
object Instagram {

  private[this] val clientId: String = Play.current.configuration.getString("instagram.client.id").getOrElse("")
  private[this] val clientSecret: String = Play.current.configuration.getString("instagram.client.secret").getOrElse("")
  private[this] val redirectURL: play.api.mvc.Call = routes.Application.instaCallback()
  private[this] val InstagramMaxElemsByPage: Int = 33

  def getAuthUrl(request: Request[AnyContent]): String = {
    val path = (if (request.host.contains(":9000")) "http://" else "https://") + request.host + redirectURL

    val scopes = "likes+relationships"
    //TODO state

    "https://api.instagram.com/oauth/authorize/?client_id=" + clientId + "&redirect_uri=" + path + "&response_type=code" + "&scope=" + scopes
  }

  def getAccessToken(request: Request[_], code: String): Option[String] = {
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global

    val path = (if (request.host.contains(":9000")) "http://" else "https://") + request.host + redirectURL

    //TODO 2 seconds
    val postParams: Map[String, Seq[String]] = Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret),
      "grant_type" -> Seq("authorization_code"), "redirect_uri" -> Seq(path), "code" -> Seq(code))
    val result: WSResponse = Await.result(WS.url("https://api.instagram.com/oauth/access_token").post(postParams), 2 seconds)

    Logger.info("getAccessToken result: " + result.json.toString())

    val accessToken: JsLookupResult = result.json \ "access_token"
    accessToken.toOption match {
      case None => {
        Logger.error("Not possible to parse access token. Instead: " + result.json)
        None
      }
      case Some(token) => {
        Logger.info("AccessToken parsed: " + token)
        Some(token.toString)
      }
    }

    /*
    FIXME
    Due to the fact that Instagram API does not allows new apps to get access to relationships/likes scopes
    unless the target users are business, this app will not work.
    If you want to check stats for your own account just get an access token and replace use the below line

    You can get a token using the following url, providing authorization, making an endpoint call, and inspecting
    the outgoing request, looking for the access token:

    https://apigee.com/console/instagram
     */

    //Some("VALID ACCESS TOKEN") // Replace the token here and uncomment this line  
  }

  def getRandomMediaFromRandomFriend(token: String, followingList: Seq[(String, String)] = Nil): (String, Media, DateTime) = {
    val followingIds: Seq[(String, String)] = if (followingList.isEmpty) fetchAllFollowing(token) else followingList
    Logger.info("IDS: " + followingIds.length)

    val friendIndex: Int = Random.nextInt(followingIds.length)
    val (friendId, friendUsername): (String, String) = followingIds(friendIndex)
    Logger.info("Friend: " + friendIndex + " - " + friendId + " @ " + friendUsername)

    val currentDateTime: DateTime = new DateTime()
    val minimumWeeks: Int = Play.current.configuration.getInt("instagram.media.timestamp.max.weeks").getOrElse(1)
    val moreRecentDateTime: DateTime = currentDateTime.minusWeeks(minimumWeeks)
    val moreRecentMillis: Long = moreRecentDateTime.getMillis / 1000
    Logger.info(currentDateTime + " vs. " + moreRecentDateTime + " => " + moreRecentMillis)

    val mediaIds: Seq[Media] = fetchAllMedia(token, friendId, moreRecentMillis)
    Logger.info("MEDIA: " + mediaIds.length)

    val filteredMedia: Seq[Media] = mediaIds.filter(m => !m.liked)
    Logger.info("MEDIA filtered: " + filteredMedia.length)

    if (filteredMedia.length == 0) {
      Logger.error("No media for: " + friendUsername + " (" + friendId + ")")
      getRandomMediaFromRandomFriend(token, followingIds)
    }
    else {

      val mediaIndex: Int = Random.nextInt(filteredMedia.length)
      val media: Media = filteredMedia(mediaIndex)

      assert(!media.liked)

      val datetime: DateTime = new DateTime(media.timestamp * 1000)
      val pattern: String = Play.current.configuration.getString("datetime.print.pattern").getOrElse("dd/MMMM/yyyy HH:mm")
      val prettyDateTime: String = datetime.toString(DateTimeFormat.forPattern(pattern))

      Logger.info("Media: " + mediaIndex + " - " + media + " in " + prettyDateTime)

      (friendUsername, media, datetime)
    }
  }

  private[this] def fetchAllFollowing(token: String): Seq[(String, String)] = {
    // (friendId, friendUsername)

    val url: String = "https://api.instagram.com/v1/users/self/follows?access_token=" + token

    fetchPageFollowing(url)
  }

  private[this] def fetchPageFollowing(url: String): Seq[(String, String)] = {
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global

    //TODO 2 seconds
    //TODO check result
    val result: WSResponse = Await.result(WS.url(url).get(), 2 seconds)

    val pagination: JsLookupResult = result.json \ "pagination" \ "next_url"

    val ids: Seq[(String, String)] = (result.json \ "data").as[JsArray].value.map(j => ((j \ "id").getOrElse(JsString("")).toString().filterNot(_ == '"'), (j \
      "username").getOrElse(JsString("")).toString().filterNot(_ == '"')))

    pagination.toOption match {
      case None => {
        Logger.info("No more pagination for followings")
        ids
      }
      case Some(nextUrl) => {
        val next: String = nextUrl.toString().filterNot(_ == '"')
        Logger.info("nextURL for followings: " + next)
        ids ++ fetchPageFollowing(next)
      }
    }
  }

  private[this] def fetchAllMedia(token: String, friend: String, moreRecentMillis: Long = 0): Seq[Media] = {
    // (mediaId, mediaUrl, mediaSource, mediaTImestamp)

    Logger.info("Media for: " + friend)

    val pageCount: Int = Play.current.configuration.getInt("instagram.query.media.count").getOrElse(InstagramMaxElemsByPage)

    val photosUrl: String = "https://api.instagram.com/v1/users/" + friend + "/media/recent?access_token=" + token + "&count=" + pageCount +
      "&max_timestamp=" + moreRecentMillis

    fetchPageMedia(photosUrl)
  }

  private[this] def fetchPageMedia(url: String): Seq[Media] = {

    // TODO 2 seconds
    //TODO check result
    val result: WSResponse = Await.result(WS.url(url).get(), 2 seconds)

    val pagination: JsLookupResult = result.json \ "pagination" \ "next_url"

    val media: Seq[Media] = (result.json \ "data").validate[Seq[Media]] match {
      case JsSuccess(value, _) => {
        Logger.info("Parsed media: " + value.length)
        value
      }
      case JsError(errors) => {
        Logger.error("Cannot parse Media from data: " + JsError.toJson(errors))
        Nil
      }
    }

    pagination.toOption match {
      case None => {
        Logger.info("No more pagination for media")
        media
      }
      case Some(nextUrl) => {
        val next: String = nextUrl.toString().filterNot(_ == '"')
        Logger.info("nextURL for media: " + next)
        media ++ fetchPageMedia(next)
      }
    }
  }


}

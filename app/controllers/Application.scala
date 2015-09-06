package controllers

import models.Media
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api._
import play.api.mvc._
import services.Instagram

class Application extends Controller {

  def index: Action[AnyContent] = Action {
    Ok(views.html.index())
  }

  def insta: Action[AnyContent] = Action { request =>

    val url = Instagram.getAuthUrl(request)
    Logger.info("Redirecting to: " + url)

    Redirect(url)
  }

  def instaCallback(code: String = "", error_reason: String = "", error: String = "", error_description: String = ""): Action[AnyContent] = Action { request =>

    Logger.info("Received code: " + code)
    Logger.info("Received error_reason: " + error_reason + "; error: " + error + "; error_description: " + error_description)

    if (code.isEmpty) {
      val errorMessage: String = "Instagram server has not sent a valid code. Instead: " + error + ": " + error_reason + " - " + error_description
      Logger.error(errorMessage)
      Ok(views.html.error(errorMessage))
    }
    else {
      val hit: Option[String] = Instagram.getAccessToken(request, code)

      hit match {
        case None => {
          val errorMessage: String = "No access token"
          Logger.error(errorMessage)
          Ok(views.html.error(errorMessage))
        }
        case Some(token) => {
          val (friendUsername, media, datetime): (String, Media, DateTime) =
            Instagram.getRandomMediaFromRandomFriend(token)
          val dateFormat: String = Play.current.configuration.getString("datetime.print.pattern").getOrElse("dd/MMMM/yyyy HH:mm")
          val prettyDateTime: String = datetime.toString(DateTimeFormat.forPattern(dateFormat))
          val postLink: String = "https://api.instagram.com/v1/media/" + media.id + "/likes?access_token=" + token

          Ok(views.html.insta(friendUsername, media, prettyDateTime, postLink))
        }
      }
    }
  }
}

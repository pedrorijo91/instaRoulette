package models

import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

/**
 * Created by pedrorijo on 28/08/15.
 */
case class Media(val id: String, val link: String, val photoUrl: String, val created: String, val caption: Option[String], val liked: Boolean) {
  def timestamp: Long = created.filterNot(_ == '"').toLong

  def captionText: String = {
    caption match {
      case None => ""
      case Some(text) => text
    }
  }
}

object Media {
  implicit val jsonReads: Reads[Media] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "link").read[String] and
      (JsPath \ "images" \ "standard_resolution" \ "url").read[String] and
      (JsPath \ "created_time").read[String] and
      (JsPath \ "caption" \ "text").readNullable[String] and
      (JsPath \ "user_has_liked").read[Boolean]
    )(Media.apply _)
}
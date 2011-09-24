package no.muda

import unfiltered.request._
import unfiltered.response._

import org.scalaquery.session._
import org.scalaquery.session.Database.threadLocalSession
import org.scalaquery.ql._
import org.scalaquery.ql.TypeMapper._
import org.scalaquery.ql.extended.H2Driver.Implicit._
import org.scalaquery.session.Database
import org.scalaquery.ql.extended.ExtendedTable

import java.util.Date
import java.net.URL

object Server {
  val logger = Logger(Server.getClass)
  def main(args: Array[String]) {
    val http = unfiltered.jetty.Http.anylocal // this will not be necessary in 0.4.0
    http.context("/assets") { _.resources(new URL(getClass().getResource("/www/css"), ".")) }
      .filter(new RSVP).run({ svr =>
        unfiltered.util.Browser.open(http.url)
      }, { svr =>
        logger.info("shutting down server")
      })
  }
}

case class Logger(logClass:Class[_]) {
  def log(message:String)   { println( logClass.getName + ": " + message ) }
  def debug(message:String) { log(message) }
  def info(message:String)  { log(message) }
}
case class Attendee(name:String, createdAt:String = new Date().toString)
case class Event(title: String, date: String, location: String, host: String, description: Option[String]) {
  val id = title.toLowerCase.replaceAll("[^\\w]+", "-")
}

/** unfiltered plan */
class RSVP extends unfiltered.filter.Plan {
  import QParams._

  val Events = new ExtendedTable[(String, String, String, String, String, Option[String])]("EVENTS") {
    def id = column[String]("ID", O.PrimaryKey)
    def title = column[String]("TITLE")
    def location = column[String]("LOCATION")
    def date = column[String]("DATE")
    def host = column[String]("HOST")
    def description = column[Option[String]]("DESCRIPTION")
    def * = id ~ title ~ date ~ location ~ host ~ description
    def asEvent = title ~ date ~ location ~ host ~ description <> (Event.apply _, Event.unapply _)
  }

  val Attendees = new ExtendedTable[(String, String, String)]("ATTENDEES") {
    def eventId = column[String]("EVENT_ID")
    def name = column[String]("NAME")
    def createdAt = column[String]("CREATED_AT")
    def * = eventId ~ name ~ createdAt
  }

  val DB = Database.forURL("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

  DB.withSession {
    (Events.ddl ++ Attendees.ddl).create
    Events.insertAll(
      ("scalaonaboat",  "ScalaOnABoat",     "2011-fjdskfjdsk", "Boat",     "Arktekk", None),
      ("into-clojure",  "into {} 'Clojure", "2011-fjdskfjdsk", "Sentrum",  "sociallyfunctional", None),
      ("javazoen",      "JavaZoen",         "2012-fdjkfjds",   "Spectrum", "javaBin", None)
    )
    Attendees.insert("scalaonaboat", "olecr", "2012-sdfdg")
  }

  val logger = Logger(classOf[RSVP])

  def intent = {
    case GET(Path(p @ "/event")) =>
      logger.debug("GET %s" format p)
      Ok ~> layout(<h2>New Event!?</h2> ++ createEventForm(Map.empty))

    case GET(Path(Seg("event" :: slug :: Nil))) =>
      logger.debug("GET /event/" + slug )
      DB.withSession {
        val m: List[Event] = for {
          e <- Events.map(_.asEvent).list if e.id == slug
        } yield e

        m.headOption match {
          case Some(event) => Ok ~> layout(<h2>Showing event!?</h2> ++ showEvent(event))
          case None => NotFound ~> layout(<xml:group>
            <h2>Event not Found!</h2> <p>Is the URL correct?</p>
          </xml:group>)
        }
      }

    case GET(Path(Seg("event" :: slug :: "attend" :: attendeeName :: Nil))) =>
      DB.withSession {
        val m: List[Event] = for {
          e <- Events.map(_.asEvent).list if e.id == slug
        } yield e

        m.headOption match {
          case Some(event) =>
            Attendees.insert(event.id, attendeeName, new Date().toString)
            Ok
          case None => NotFound
        }
      }

    case POST(Path(p @ "/event") & Params(params)) =>
      logger.debug("POST %s" format p)
      def viewWithParams(head: xml.NodeSeq) = layout(head ++ createEventForm(params))
      val expected = for {
        title <- lookup("eventTitle") is
          trimmed is
          nonempty("event title must be set") is
          required("missing event title param")

        date <- lookup("eventDate") is
          pred(validDate, { _ + " is not a valid date of format YYYY-MM-DD hh:mm"}) is
          required("missing event date")

        location <- lookup("eventLocation") is
          trimmed is
          nonempty("event location must be set") is
          required("missing event location")

        host <- lookup("eventHost") is
          trimmed is
          nonempty("event host must be set") is
          required("missing event host")

        description <- lookup("eventDescription")

      } yield {
        val event = Event(title.get, date.get, location.get, host.get, description)
        DB.withSession {
          Events.insert(event.id, event.title, event.date, event.location, event.host, event.description)
        }

        Redirect("/event/" + event.id)
      }
      expected(params) orFail { fails =>
        viewWithParams(<ul class="error"> { fails.map { f => <li>{f.error}</li> } } </ul>)
      }

    case GET(Path(p)) =>
      Ok ~> layout(<h2>Proud to present…</h2> ++ showEventList)
  }

  def validDate(s: String) = s matches """\d{4}-\d{2}-\d{2} \d{2}:\d{2}"""

  def showEventList = {
    <ul>{
      DB.withSession {
        for {
          e <- Events.map(_.asEvent).list
        } yield {
            <li>
              <a href={"/event/" + e.id}>
                {e.title}
              </a>
            </li>
        }
      }
    }
    </ul>
  }

  def showEvent(event:Event) = {
    <div class="event">
      <h2>{event.title}</h2>
      <span class="date">{event.date}</span>
      <span class="location">{event.location}</span>
      <span class="host">{event.host}</span>
      <p>{event.description.getOrElse("No description yet…")}</p>
      <ul class="attendees">
        {/*event.attendees.map { a => <li>{a.name} @ {a.createdAt}</li> }*/}
      </ul>
      <input type="submit" value="Attend" id="attendDialog" data-seg={event.id}/>
      <script type="application/javascript">
        /* <![CDATA[ */
        $('#attendDialog').click(function() {
          var username = prompt('Twitter username?');
          $.get('/event/' + $(this).attr('data-seg') + '/attend/' + username, function() {
            top.location = top.location;
          });
        });
      /* ]]> */
      </script>
    </div>
    <p>Event goes here </p>
  }

  def createEventForm(params: Map[String, Seq[String]]) = {
    def p(k: String) = params.get(k).flatMap { _.headOption } getOrElse("")
    <form method="POST">
      <div>Event title
          <input type="text" name="eventTitle" value={p("eventTitle")}/>
      </div>
      <div>Description
        <textarea name="eventDescription">
          {p("eventDescription")}
        </textarea>
      </div>
      <div>Date
          <input type="text" name="eventDate" value={p("eventDate")}/>
      </div>
      <div>Location
          <input type="text" name="eventLocation" value={p("eventLocation")}/>
      </div>
      <div>Host
          <input type="text" name="eventHost" value={p("eventHost")}/>
      </div>
        <input type="submit"/>
    </form>
  }

  def layout(body: scala.xml.NodeSeq) = {
    Html(
     <html>
      <head>
        <title>RSVP!</title>
        <link rel="stylesheet" type="text/css" href="/assets/css/app.css"/>
        <script type="application/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.2/jquery.min.js"/>
      </head>
      <body>
       <header><h1>RSVP!</h1></header>
       <div id="container">
         { body }
         <a href="/event" class="medium">Create new event</a>
       </div>
       <footer>Copyleft (c) 2011</footer>
     </body>
    </html>
   )
  }
}

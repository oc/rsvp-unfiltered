package no.muda

import unfiltered.request._
import unfiltered.response._

case class Logger(logClass:Class[_]) {
  def log(message:String)   { println( logClass.getName + ": " + message ) }
  def debug(message:String) { log(message) }
  def info(message:String)  { log(message) }
}

case class Event(title: String, date: String, location: String, host: String, description: Option[String])

/** unfiltered plan */
class RSVP extends unfiltered.filter.Plan {
  import QParams._

  var events = List[Event](
    Event("ScalaOnABoat", "2011-fjdskfjdsk", "Boat", "Arktekk", None),
    Event("JavaZoen", "2012-fdjkfjds", "Spectrum", "javaBin", None)
  )

  val logger = Logger(classOf[RSVP])

  def intent = {
    case GET(Path(p @ "/create")) =>
      logger.debug("GET %s" format p)
      Ok ~> layout(<h1>New Event!?</h1> ++ createEventForm(Map.empty))
    case GET(Path(p)) =>
      Ok ~> layout(<h1>RSVP!?</h1> ++ showEventList)
    case POST(Path(p) & Params(params)) =>
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
        events ::= Event(title.get, date.get, location.get, host.get, description)
        viewWithParams(<div class="notice">Yup. { title.get } is a title and { date.get } is a date. </div>)
      }
      expected(params) orFail { fails =>
        viewWithParams(<ul class="error"> { fails.map { f => <li>{f.error}</li> } } </ul>)
      }
  }

  def validDate(s: String) = s matches """\d{4}-\d{2}-\d{2} \d{2}:\d{2}"""
  def palindrome(s: String) = s.toLowerCase.reverse == s.toLowerCase

  def showEventList = {
    <ul>
    </ul>
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
      </head>
      <body>
       <div id="container">
       { body }
       </div>
     </body>
    </html>
   )
  }
}

/** embedded server */
object Server {
  val logger = Logger(Server.getClass)
  def main(args: Array[String]) {
    val http = unfiltered.jetty.Http.anylocal // this will not be necessary in 0.4.0
    http.context("/assets") { _.resources(new java.net.URL(getClass().getResource("/www/css"), ".")) }
      .filter(new RSVP).run({ svr =>
        unfiltered.util.Browser.open(http.url)
      }, { svr =>
        logger.info("shutting down server")
      })
  }
}

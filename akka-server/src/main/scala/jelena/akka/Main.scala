package jelena.akka

import java.time.LocalDate
import scala.util._
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{Accept, RawHeader}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, MediaRanges}
import akka.stream.alpakka.csv.scaladsl.{CsvParsing, CsvToMap}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import spray.json.{DefaultJsonProtocol, JsValue, JsonWriter}

import scala.concurrent.Future

object Main
  extends App
    with DefaultJsonProtocol {

  implicit val actorSystem: ActorSystem[Nothing] = ActorSystem[Nothing](Behaviors.empty, "political-speeches")

  import actorSystem.executionContext

  def httpRequest(uri: String) = HttpRequest(uri = uri)
    .withHeaders(Accept(MediaRanges.`text/*`))

  case class PoliticalSpeech(Redner: String, Thema: String, Datum: String, Wörter: String)

  case class PoliticalSpeechParsed(Redner: String, Thema: String, Datum: LocalDate, Wörter: Int)

  case class Result(mostSpeeches: Option[String], mostSecurity: String, leastWordy: String)

  implicit val resultFormat = jsonFormat3 {
    Result
  }

  object MyJsonProtocol extends DefaultJsonProtocol {
    implicit val politicalSpeechFormat = jsonFormat4 {
      PoliticalSpeech
    }
  }
  def extractEntityData(response: HttpResponse): Source[ByteString, _] =
    response match {
      case HttpResponse(OK, _, entity, _) => entity.dataBytes
      case notOkResponse =>
        Source.failed(new RuntimeException(s"illegal response $notOkResponse"))
    }

  def convertToParsed(ps: PoliticalSpeech): PoliticalSpeechParsed =
    PoliticalSpeechParsed(ps.Redner, ps.Thema, LocalDate.parse(ps.Datum), ps.Wörter.toInt)

  def toJson(map: Map[String, String])(
    implicit jsWriter: JsonWriter[Map[String, String]]): JsValue = jsWriter.write(map)
  import MyJsonProtocol.politicalSpeechFormat
  def trimMap(m: Map[String, String]) = m map {case (key, value) => (key.trim(), value.trim())}
  def future(uri: String) : Future[Seq[PoliticalSpeech]] =
    Source
      .single(httpRequest(uri)) //: HttpRequest
      .mapAsync(1)(Http()(actorSystem.toClassic).singleRequest(_)) //: HttpResponse
      .flatMapConcat(extractEntityData) //: ByteString
      .via(CsvParsing.lineScanner()) //: List[ByteString]
      .via(CsvToMap.toMapAsStrings().map(trimMap)) //: Map[String, String]
      .map(toJson) //: JsValue
      .map(_.convertTo[PoliticalSpeech])
      .runWith(Sink.seq)

  def mostSpeeches2023(data: Seq[PoliticalSpeechParsed]) =
    data.filter(_.Datum.isAfter(LocalDate.parse("2022-12-31"))).sortBy(_.Datum).headOption.map(_.Redner)
  def mostSpeechesSecurity(data: Seq[PoliticalSpeechParsed]) =
    data.filter(_.Thema == "Innere Sicherheit").groupBy(_.Redner).map{case (k, v) => (k, v.size)}.max._1
  def leastWordy(data: Seq[PoliticalSpeechParsed]) =
    data.groupBy(_.Redner).map{case (k, v) => (k, v.map(_.Wörter).sum)}.max._1

  def calculateResult(data: Seq[PoliticalSpeechParsed]): Result =
    Result(mostSpeeches2023(data), mostSpeechesSecurity(data), leastWordy(data))

  val route = path("evaluation" / "heartbeat") {
    get {
      complete("Success")
    }
  } ~ path("evaluation") { //here it can be done via parameters("url".repeated)
    get {
      parameters("url".repeated) { urls => //("https://fid-recruiting.s3-eu-west-1.amazonaws.com/politics.csv")
        val futures = Future.sequence(urls.map(future))
        onComplete(futures) {
          case scala.util.Success(res) => complete{for (r <- res) yield calculateResult(r.map(convertToParsed)).toJson}
          case Failure(ex) => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
  val server = Http().newServerAt("localhost", 9090).bind(route)
  server.map { _ =>
    println("Successfully started on localhost:9090 ")
  } recover { case ex =>
    println("Failed to start the server due to: " + ex.getMessage)
  }

}

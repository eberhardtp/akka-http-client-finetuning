package test

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorAttributes, ActorMaterializer, Supervision}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Success, Try}

object Main extends App {
  val timeout = 300.seconds

  //  val uri = uri
  val uri = "http://ems-webpush-performance-mock.herokuapp.com/api/v1/token-keys?pushtoken=sdfcsd&app_code=ASD-123"


  //  startServer()
  runTests()

  def startServer() = {
    implicit val system = ActorSystem("server")
    implicit val dispatcher = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val routes: Route = {
      val sleep = akka.pattern.after(300 milliseconds, system.scheduler)(Future(Unit))
      onComplete(sleep) {
        case _ => complete(StatusCodes.OK)
      }
    }

    val adminApiBindingFuture: Future[ServerBinding] = Http()
      .bindAndHandle(routes, "0.0.0.0", 9000)
      .map(binding => {
        println("Server started!")
        binding
      })
    Await.result(adminApiBindingFuture, timeout)
  }

  def runTests() = {
    implicit val system = ActorSystem("test")
    implicit val dispatcher = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val batch = 10000
    val sum = 30 * batch
    val source = Source.repeat(5).take(sum).via(new TimeReporter[Int](batch, t => println(s"$batch element in $t ms - approx ${if (t > 0) 1000.0 / t * batch else "INF"}rps}")))
    //    val source2 = Source.repeat[HttpRequest](HttpRequest(uri = uri)).take(sum).via(new TimeReporter[HttpRequest](batch, t => println(s"$batch element in $t ms - approx ${if (t > 0) 1000.0 / t * batch else "INF"}rps}")))

    println("========= Initial delay")
    Await.result(Future(Thread.sleep(5000)), timeout)
    /*
        println("========= OnlySource")
        val g1: Future[Done] = source.runWith(Sink.ignore)

        Await.result(g1, timeout)

        println("========= 2 Http")
        val flow1 = Flow[Int].mapAsyncUnordered(2)(x => Http().singleRequest(HttpRequest(uri = uri)))
        val g2 = source.via(flow1).runWith(Sink.ignore)

        Await.result(g2, timeout)

        println("========= 8 Http")

        val flow2 = Flow[Int].mapAsyncUnordered(8)(x => Http().singleRequest(HttpRequest(uri = uri)))
        val g3 = source.via(flow2).runWith(Sink.ignore)

        Await.result(g3, timeout)

        println("========= 8 Cached Http")
    */
    val http = Http()
    /*  val flow3 = Flow[Int].mapAsyncUnordered(8)(x => http.singleRequest(HttpRequest(uri = uri)))
        val g4 = source.via(flow3).runWith(Sink.ignore)

        Await.result(g4, timeout)

        println("========= 8 Cached Http Consumed")

        val flow4 = Flow[Int].mapAsyncUnordered(8)(x => http.singleRequest(HttpRequest(uri = uri)).map(resp => resp.discardEntityBytes(materializer)))
        val g5 = source.via(flow4).runWith(Sink.ignore)

        Await.result(g5, timeout)
    */
    println("========= 8 Cached HttpPool Consumed")

    val flow6_1 = Flow[Int].mapAsyncUnordered(8)(x => Future.successful((HttpRequest(uri = uri), x)))
    val flow6_2 = http.cachedHostConnectionPool[Int](host = "ems-webpush-performance-mock.herokuapp.com")
    val flow6_3 = Flow[(Try[HttpResponse], Int)].mapAsyncUnordered(8) {
      case (Success(response), x) => Future{response.discardEntityBytes(materializer); x}
      case (_, x) => Future.successful(x);
    }
    val decider: Supervision.Decider = {
      case _ => Supervision.Resume
    }
    val g6 = source.via(flow6_1).via(flow6_2).via(flow6_3)
      .via(new TimeReporter[Int](batch, t => println(s"before the sink: $batch element in $t ms - approx ${if (t > 0) 1000.0 / t * batch else "INF"}rps}")))
      .withAttributes(ActorAttributes.supervisionStrategy(decider))
      .runWith(Sink.ignore)


    Await.result(g6, timeout)


    println("========= FINISHED")
  }
}

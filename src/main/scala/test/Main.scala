package test

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Main extends App {
  val timeout = 300.seconds


  startServer()
  runTests()

  def startServer() = {
    implicit val system = ActorSystem("server")
    implicit val dispatcher = system.dispatcher
    implicit val materializer = ActorMaterializer()

    val routes: Route = {
      onComplete(Future(Thread.sleep(200))) {
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

    val batch = 100
    val sum = 30 * batch
    val source = Source.repeat(5).take(sum).via(new TimeReporter[Int](batch, t => println(s"$batch element in $t ms - approx ${if (t > 0) 1000.0 / t * batch else "INF"}rps}")))

    println("========= Initial delay")
    Await.result(Future(Thread.sleep(5000)), timeout)

    println("========= OnlySource")
    val g1: Future[Done] = source.runWith(Sink.ignore)

    Await.result(g1, timeout)

    println("========= 10 Http")
    val flow1 = Flow[Int].mapAsyncUnordered(10)(x => Http().singleRequest(HttpRequest(uri = "http://0.0.0.0:9000/")))
    val g2 = source.via(flow1).runWith(Sink.ignore)

    Await.result(g2, timeout)

    println("========= 800 Http")

    val flow2 = Flow[Int].mapAsyncUnordered(800)(x => Http().singleRequest(HttpRequest(uri = "http://0.0.0.0:9000/")))
    val g3 = source.via(flow2).runWith(Sink.ignore)

    Await.result(g3, timeout)

    println("========= 800 Cached Http")

    val http = Http()
    val flow3 = Flow[Int].mapAsyncUnordered(800)(x => http.singleRequest(HttpRequest(uri = "http://0.0.0.0:9000/")))
    val g4 = source.via(flow3).runWith(Sink.ignore)

    Await.result(g4, timeout)

    println("========= 800 Cached Http Consumed")

    val flow4 = Flow[Int].mapAsyncUnordered(800)(x => http.singleRequest(HttpRequest(uri = "http://0.0.0.0:9000/")).map(resp => resp.discardEntityBytes(materializer)))
    val g5 = source.via(flow4).runWith(Sink.ignore)

    Await.result(g5, timeout)


    println("========= FINISHED")
  }
}

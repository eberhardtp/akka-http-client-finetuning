package test

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}

class TimeReporter[A](batchSize: Int, f: Long ⇒ Unit) extends GraphStage[FlowShape[A, A]] {

  val in = Inlet[A]("test.TimeReporter.in")
  val out = Outlet[A]("test.TimeReporter.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      var counter = 0
      var startTime: Long = 0

      override def preStart(): Unit = {
        counter = batchSize
        startTime = System.currentTimeMillis
      }

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          push(out, grab(in))
          counter -= 1
          if(counter < 0) {
            val now = System.currentTimeMillis
            f(now-startTime)
            counter = batchSize
            startTime = now
          }
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
}
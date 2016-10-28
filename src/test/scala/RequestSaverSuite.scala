import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestKit
import akka.util.Timeout
import akka.pattern.ask
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration.{Deadline, DurationInt}
import language.postfixOps

/**
  * Created by qbormat on 2016-10-27.
  */
class RequestSaverSuite extends TestKit(ActorSystem("RequestSaverSuite")) with WordSpecLike with Matchers {

  // Swap these to try different variants
  //val requestSaver = RequestSaverRouter.apply(system)
  val requestSaver = RequestSaverNoRouter.apply(system)

  val durationCollector = system.actorOf(DurationCollector.props(), "DurationCollector")

  "Save a request via the RequestSaver" in {
    import  scala.language.reflectiveCalls

    //Start a couple of threads that generate load for specified amount of time (deadline)
    val es = Executors.newCachedThreadPool()
    //The time a thread shour run to load requests
    val deadline = 1.minutes.fromNow
    for( a <- 1 to 2) {
      es.execute(new RequestSenderThread("Thread-" + a, deadline, requestSaver, durationCollector))
    }
    es.shutdown()
    es.awaitTermination(1, TimeUnit.MINUTES)

    //Check results
    implicit val timeout = Timeout(5 seconds)
    val future = durationCollector ? DurationCollector.ReportExecutionTimes
    val result = Await.result(future, timeout.duration).asInstanceOf[String]
    println(result)
  }
}

/**
  * A thread to execute load. It will sleep 1ms between requests sent
  *
  * @param name
  * @param deadline
  * @param requestSaver
  * @param durationCollector
  */
class RequestSenderThread(name:String, deadline:Deadline, requestSaver:RequestSaver, durationCollector:ActorRef) extends Runnable {
  import  scala.language.reflectiveCalls
  import scala.concurrent.ExecutionContext.Implicits.global

  def run(): Unit = {
    var counter = 0
    while(deadline.hasTimeLeft()) {
      counter = counter + 1
      val f = requestSaver.save("Dummy message " + name + " " + counter)
      f.onComplete{x => durationCollector ! x.get}
      Thread.sleep(1)
    }
  }
}

import java.time.{Duration, Instant}
import java.util.concurrent.Executors

import GateKeeperMessages.{Granted, Halt, Permission2Enter}
import RequestSaverMessages.Request
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.BalancingPool
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Random

/**
  * Created by qbormat on 2016-10-28.
  */

object RequestSaverNoRouter {
  def apply(as: ActorSystem) = new RequestSaverNoRouterImpl(as)
  def getRandomInt():Int = Random.nextInt(Int.MaxValue)
}


class RequestSaverNoRouterImpl(as: ActorSystem) extends RequestSaver {
  import RequestSaverMessages._
  private val executorService = Executors.newCachedThreadPool()

  private val requestPersister:ActorRef = as.actorOf(RequestPersister.props()(ExecutionContext.fromExecutorService(executorService)), "RequestPersister")
  private val gateKeeper: ActorRef = as.actorOf(GateKeeper.props)
  private val throttler: ActorRef = as.actorOf(Throttler.props(gateKeeper, requestPersister))

  def save(msg: String): Future[Duration] = {
    import akka.pattern.ask
    import scala.concurrent.duration.DurationInt

    implicit val timeout = Timeout(5.seconds)
    val future = gateKeeper ? Permission2Enter
    val enterResult = Await.result(future, timeout.duration)

    val promise = Promise[Duration]()
    enterResult match {
      case Halt => promise.failure(new Exception("Queue full")).future
      case Granted =>
        as.actorOf(RequestSaverNoRouterWorker.props(throttler, Request(msg, promise, Instant.now())), "RequestSaverNoRouterWorker" + "-" + RequestSaverNoRouter.getRandomInt() + "-" + System.currentTimeMillis)
        promise.future
    }
  }
}

object RequestSaverNoRouterWorker {
  def props(throttler:ActorRef, req: Request) = Props(new RequestSaverNoRouterWorker(throttler, req))
}

class RequestSaverNoRouterWorker(throttler:ActorRef, req: Request) extends Actor {
  import RequestSaverMessages._

  throttler ! req

  def receive = {
    case resp: Response =>
      resp.promise.success(Duration.between(resp.startTime, Instant.now()))
      context.stop(self)
  }
}
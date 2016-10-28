import java.time.{Duration, Instant}
import java.util.concurrent.Executors

import RequestSaverMessages.Request
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.BalancingPool

import scala.concurrent.{ExecutionContext, Future, Promise}
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

  def save(msg: String): Future[Duration] = {
    val promise = Promise[Duration]()
    if (GateKeeper.gateOpen()) {
      as.actorOf(RequestSaverNoRouterWorker.props(requestPersister, Request(msg, promise, Instant.now())), "RequestSaverNoRouterWorker" + "-" + RequestSaverNoRouter.getRandomInt() + "-" + System.currentTimeMillis)
    }
    promise.future
  }
}

object RequestSaverNoRouterWorker {
  def props(requestPersister:ActorRef, req: Request) = Props(new RequestSaverNoRouterWorker(requestPersister, req))
}

class RequestSaverNoRouterWorker(requestPersister:ActorRef, req: Request) extends Actor {
  import RequestSaverMessages._

  requestPersister ! req

  def receive = {
    case resp: Response =>
      resp.promise.success(Duration.between(resp.startTime, Instant.now()))
      context.stop(self)
  }
}
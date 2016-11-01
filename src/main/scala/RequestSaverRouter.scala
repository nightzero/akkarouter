import java.time.{Duration, Instant}
import java.util.concurrent.Executors

import GateKeeperMessages.{Granted, Halt, Permission2Enter}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.BalancingPool
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}


/**
  * Created by qbormat on 2016-10-27.
  */

trait RequestSaver {
  def save(msg: String): Future[Duration]
}

object RequestSaverRouter {
  def apply(as: ActorSystem) = new RequestSaverRouterImpl(as)
}


class RequestSaverRouterImpl(as: ActorSystem) extends RequestSaver {
  import RequestSaverMessages._
  private val executorService = Executors.newCachedThreadPool()

  private val requestPersister:ActorRef = as.actorOf(RequestPersister.props()(ExecutionContext.fromExecutorService(executorService)), "RequestPersister")
  private val gateKeeper: ActorRef = as.actorOf(GateKeeper.props)
  private val throttler: ActorRef = as.actorOf(Throttler.props(gateKeeper, requestPersister))
  private val requestSaverRouter: ActorRef = as.actorOf(BalancingPool(1000).props(RequestSaverWorker.props(throttler)), "RequestSaverRouter")


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
        requestSaverRouter ! new Request(msg, promise, Instant.now())
        promise.future
    }
  }
}

object RequestSaverWorker {
  def props(throttler:ActorRef) = Props(new RequestSaverWorker(throttler))
}

class RequestSaverWorker(throttler:ActorRef) extends Actor {
  import RequestSaverMessages._
  def receive = {
    case req: Request => throttler ! req
    case resp: Response => resp.promise.success(Duration.between(resp.startTime, Instant.now()))
  }
}



object RequestPersister {
  def props()(implicit executor: ExecutionContext) = Props(new RequestPersister()(executor))
}

class RequestPersister()(implicit executor: ExecutionContext) extends Actor {
  import RequestSaverMessages._

  def receive = {
    case msg: Request =>
      val responseAddress = sender
      val f = DummyBackend.save(msg)
      f.onComplete{
        case Success(resp) =>  responseAddress ! resp
        case Failure(t) => println("An error has occured: " + t.getMessage)
      }
  }
}

object DummyBackend {
  import RequestSaverMessages._
  private val executorService = Executors.newCachedThreadPool()
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executorService)

  def save(req: Request):Future[Response] = {
    Future {
      scala.concurrent.blocking {
        Thread.sleep(15L)
      }
      Response(req.msg, req.promise, req.startTime)
    }
  }
}


object RequestSaverMessages {
  case class Request(msg: String, promise: Promise[Duration], startTime:Instant)
  case class Response(msg: String, promise: Promise[Duration], startTime:Instant)
}

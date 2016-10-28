import java.time.{Duration, Instant}
import java.util.concurrent.Executors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.routing.BalancingPool

import scala.concurrent.{ExecutionContext, Future, Promise}
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
  private val requestSaverRouter: ActorRef = as.actorOf(BalancingPool(1000).props(RequestSaverWorker.props(requestPersister)), "RequestSaverRouter")

  def save(msg: String): Future[Duration] = {
    val promise = Promise[Duration]()
    if (GateKeeper.gateOpen()) {
      requestSaverRouter ! new Request(msg, promise, Instant.now())
    }
    promise.future
  }
}

object RequestSaverWorker {
  def props(requestPersister:ActorRef) = Props(new RequestSaverWorker(requestPersister))
}

class RequestSaverWorker(requestPersister:ActorRef) extends Actor {
  import RequestSaverMessages._
  def receive = {
    case req: Request => requestPersister ! req
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

object GateKeeper {
  def gateOpen(): Boolean = true
}


/*
trait GateKeeperMessages {
  case object Permission2Enter
  case object Permission2EnterGranted
}
object GateKeeper {
  def props() = Props()
}
class GateKeeper extends Actor with GateKeeperMessages{
  def receive = {
    case Permission2Enter => sender ! Permission2EnterGranted
  }
}*/

import GateKeeperMessages.{AreYouOk, CloseGate, OpenGate}
import RequestSaverMessages.Response
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive

/**
  * Created by qbormat on 2016-10-29.
  */

object Throttler {
  def props(gateKeeper:ActorRef, persister:ActorRef): Props = Props(new Throttler(gateKeeper, persister))
  val closeSize = 1000
  val reopenSize = 100
  val maxConcurrent = 10

  case class ThrottlerQueueItem(item: Any, sender: ActorRef)
}

class Throttler(gateKeeper:ActorRef, persister:ActorRef) extends Actor {
  import Throttler._
  private var throttlerQueue = Vector[ThrottlerQueueItem]()
  private var ongoingWorkers = 0

  override def receive: Receive = LoggingReceive {
    case a: Any =>
      throttlerQueue = throttlerQueue ++ Vector(ThrottlerQueueItem(a, sender))
      executeNextIfAllowed()

      if(throttlerQueue.size >= closeSize) {
        gateKeeper ! CloseGate
      }

    case AreYouOk =>
      if(throttlerQueue.size < reopenSize) {
        sender ! OpenGate
      }
  }

  private def executeNextIfAllowed() {
    if(ongoingWorkers < maxConcurrent) {
      //pick next request in queue to execute
      throttlerQueue.headOption.foreach { transaction =>
        ongoingWorkers += 1
        context.actorOf(ThrottlerWorker.props(persister, self, transaction.sender, transaction.item))
        throttlerQueue = throttlerQueue.tail
      }
    }
  }
}

object ThrottlerWorker {
  def props(persister:ActorRef, throttler: ActorRef, originalSender: ActorRef, message: Any): Props = Props(new ThrottlerWorker(persister, throttler, originalSender, message))
}

class ThrottlerWorker(persister:ActorRef, throttler: ActorRef, originalSender: ActorRef, message: Any) extends Actor {
  persister ! message

  override def receive: Receive = LoggingReceive {
    case resp:Response => originalSender ! resp
  }
}

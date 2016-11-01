import GateKeeperMessages._
import akka.actor.{Actor, ActorRef, Props}
import akka.event.LoggingReceive

/**
  * Created by qbormat on 2016-11-01.
  */

object GateKeeper {
  def props: Props = Props(new GateKeeper())
}
class GateKeeper extends Actor {
  private[this] var isOpen = true
  private[this] var closeRequestSenders:Set[ActorRef] = Set.empty

  case object CheckState
  import scala.concurrent.duration.DurationInt
  import scala.concurrent.ExecutionContext.Implicits.global

  context.system.scheduler.schedule(5.seconds, 2.seconds, self, CheckState)

  override def receive: Receive = LoggingReceive {
    case CloseGate =>
      isOpen = false
      closeRequestSenders = closeRequestSenders + sender
    case OpenGate =>
      isOpen = true
      closeRequestSenders = closeRequestSenders - sender
    case CheckState =>
      closeRequestSenders.foreach { _ ! AreYouOk }
    case Permission2Enter =>
      if(isOpen){
        sender ! Granted
      }
      else {
        sender ! Halt
      }
  }

  //This is not threadsafe! Should ask Permission2Enter instead!
  def gateIsOpen(): Boolean = isOpen
}

object GateKeeperMessages {
  case object CloseGate
  case object OpenGate
  case object AreYouOk
  case object Permission2Enter
  case object Granted
  case object Halt
}
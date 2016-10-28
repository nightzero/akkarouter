import java.time.Duration
import java.util.LongSummaryStatistics
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props}
import akka.event.LoggingReceive

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/**
  * Created by qbormat on 2016-10-28.
  */

object DurationCollector {
  def props(): Props = Props(new DurationCollector())

  val timerInterval = FiniteDuration(10, TimeUnit.SECONDS)

  case object ReportExecutionTimes
  case object TimerTick
}
class DurationCollector extends Actor {
  import DurationCollector._
  import context._

  system.scheduler.schedule(timerInterval, timerInterval, self, TimerTick)

  private[this] val execTimes = mutable.Queue[Long]()

  override def receive: Receive = LoggingReceive {
    case d:Duration => collectPersistExecTime(d)
    case ReportExecutionTimes => sender ! getPersistExecTime()
    case TimerTick => println(getPersistExecTime())
  }

  private def collectPersistExecTime(duration:Duration): Unit = {
    execTimes += duration.toMillis
    // Only keep execution time for the last 1000 requests.
    //if(execTimes.size > 1000) {execTimes.dequeue()}
  }

  private def getPersistExecTime(): String = {
    val stats:LongSummaryStatistics = new LongSummaryStatistics()
    execTimes.foreach(stats.accept(_))
    val sb = new StringBuilder("Persist throughput (millis): \n")
    sb.append("Max: " + stats.getMax + "\n")
    sb.append("Min: " + stats.getMin + "\n")
    sb.append("Average: " + stats.getAverage + "\n")
    sb.toString()
  }
}

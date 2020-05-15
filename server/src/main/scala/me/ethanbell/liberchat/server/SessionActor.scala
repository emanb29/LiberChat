package me.ethanbell.liberchat.server

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.stream.scaladsl.SourceQueueWithComplete
import me.ethanbell.liberchat.{CommandMessage, Response}

object SessionActor {
  sealed trait Command
  case object NoOp                                extends Command
  case object Shutdown                            extends Command
  case class HandleIRCMessage(cm: CommandMessage) extends Command

  def apply(responseQueue: SourceQueueWithComplete[Response]): Behavior[SessionActor.Command] =
    Behaviors.setup { ctx =>
      ctx.log.debug("Creating a new SessionActor")
      SessionActor(ctx, responseQueue)
    }
}
case class SessionActor(
  ctx: ActorContext[SessionActor.Command],
  responseQueue: SourceQueueWithComplete[Response]
) extends AbstractBehavior[SessionActor.Command](ctx) {

  override def onMessage(msg: SessionActor.Command): Behavior[SessionActor.Command] = msg match {
    case SessionActor.Shutdown =>
      ctx.log.debug(s"Shutting down $this")
      Behaviors.stopped
    case SessionActor.HandleIRCMessage(cm) =>
      ctx.log.debug(s"Recevied IRC command message $cm")
      responseQueue.offer(
        Response.ERR_UNKNOWNCOMMAND(s"${cm.command.name} (but actually did know it)")
      ) // TODO
      this
    case SessionActor.NoOp => this
  }
}

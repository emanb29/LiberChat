package me.ethanbell.liberchat.server

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import me.ethanbell.liberchat.{CommandMessage, Response}

object SessionActor {
  sealed trait Command
  case object Shutdown                                                         extends Command
  case class HandleIRCMessage(cm: CommandMessage, replyTo: ActorRef[Response]) extends Command

  def apply(): Behavior[SessionActor.Command] = Behaviors.setup(ctx => SessionActor(ctx))
}
case class SessionActor(ctx: ActorContext[SessionActor.Command])
    extends AbstractBehavior[SessionActor.Command](ctx) {

  override def onMessage(msg: SessionActor.Command): Behavior[SessionActor.Command] = msg match {
    case SessionActor.Shutdown =>
      println(s"Shutting down $this")
      Behaviors.stopped
    case SessionActor.HandleIRCMessage(cm, replyTo) =>
      println(s"Recevied IRC command message $cm and reply address $replyTo")
      this
  }
}

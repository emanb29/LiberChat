package me.ethanbell.liberchat.server

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import me.ethanbell.liberchat.AkkaUtil.Replyable
import me.ethanbell.liberchat.{CommandMessage, Response}

object IRCCommandActor {
  case class ReplyableCommandMsg(override val replyTo: ActorRef[Response], command: CommandMessage)
      extends Replyable {
    override type T = Response
  }

  def apply(): Behavior[ReplyableCommandMsg] = Behaviors.receive {
    (ctx, command: ReplyableCommandMsg) =>
      command.replyTo.tell(???) // TODO talk to actor system
      Behaviors.same
  }
}

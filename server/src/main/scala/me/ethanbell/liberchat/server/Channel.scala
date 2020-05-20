package me.ethanbell.liberchat.server

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import me.ethanbell.liberchat.IRCString

import scala.collection.mutable

object Channel {
  sealed trait Command
  final case class Join(client: ActorRef[SessionActor]) extends Command

  def apply(name: IRCString): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Initializing user registry")
    Channel(ctx, name)
  }
}
final case class Channel(ctx: ActorContext[Channel.Command], name: IRCString)
    extends AbstractBehavior[Channel.Command](ctx) {
  val users: mutable.Set[ActorRef[SessionActor.Command]] = mutable.Set.empty
  override def onMessage(msg: Channel.Command): Behavior[Channel.Command] = msg match {
    case Channel.Join(client) =>
      users.foreach(u => u.tell(???)) // TODO
      this
  }
}

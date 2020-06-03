package me.ethanbell.liberchat.server

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import me.ethanbell.liberchat.AkkaUtil.ActorCompanion
import me.ethanbell.liberchat.IRCString

import scala.collection.mutable

object Channel extends ActorCompanion {
  sealed trait Command

  /**
   * Instruct the channel to start sending messages to a user
   * @param newUserPrefix the prefix of the user joining, to send to all current users
   * @param who the user joining
   */
  final case class Join(newUserPrefix: Client.Prefix, who: ActorRef[Client.Command]) extends Command

  def apply(name: IRCString): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Initializing user registry")
    Channel(ctx, name)
  }
}
final case class Channel(ctx: ActorContext[Channel.Command], name: IRCString)
    extends AbstractBehavior[Channel.Command](ctx) {
  val users: mutable.Set[ActorRef[Client.Command]] = mutable.Set.empty
  override def onMessage(msg: Channel.Command): Behavior[Channel.Command] = msg match {
    case Channel.Join(prefix, who) =>
      (users += who).foreach(u => u.tell(Client.NotifyJoin(prefix, this.name)))
      this
  }
}

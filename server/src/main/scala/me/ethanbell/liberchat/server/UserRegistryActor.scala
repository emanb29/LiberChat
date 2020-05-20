package me.ethanbell.liberchat.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import me.ethanbell.liberchat.IRCString
import me.ethanbell.liberchat.server.SessionActor.ReserveNickCallback

import scala.collection.mutable

object UserRegistryActor {
  final val ACTOR_NAME = "user-registry"
  sealed trait Command
  final case class ReserveNick(nick: IRCString, who: ActorRef[SessionActor.Command]) extends Command
  final case class FreeNick(nick: IRCString)                                         extends Command

  def apply(): Behavior[UserRegistryActor.Command] = Behaviors.setup { ctx =>
    ctx.log.info("Initializing user registry")
    UserRegistryActor(ctx)
  }
}
final case class UserRegistryActor(ctx: ActorContext[UserRegistryActor.Command])
    extends AbstractBehavior[UserRegistryActor.Command](ctx) {

  val clients: mutable.Map[IRCString, ActorRef[SessionActor.Command]] = mutable.Map.empty

  override def onMessage(msg: UserRegistryActor.Command): Behavior[UserRegistryActor.Command] = {
    msg match {
      case UserRegistryActor.ReserveNick(nick, who) if clients.contains(nick) =>
        who.tell(SessionActor.ReserveNickCallback(nick, success = false))
      case UserRegistryActor.ReserveNick(nick, who) if !clients.contains(nick) =>
        ctx.log.debug(s"$nick reserved for $who")
        clients += (nick -> who)
        who.tell(ReserveNickCallback(nick, success = true))
      case UserRegistryActor.FreeNick(nick) =>
        clients.remove(nick)
    }
    this
  }
}

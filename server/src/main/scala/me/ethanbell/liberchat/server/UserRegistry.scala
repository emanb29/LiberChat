package me.ethanbell.liberchat.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import me.ethanbell.liberchat.AkkaUtil.RegistryCompanion
import me.ethanbell.liberchat.Response.ERR_NOSUCHNICK
import me.ethanbell.liberchat.{IRCString, ResponseMessage}
import me.ethanbell.liberchat.server.Client.ReserveNickCallback

import scala.collection.mutable

object UserRegistry extends RegistryCompanion {
  final val ACTOR_NAME = "user-registry"
  sealed trait Command

  /**
   * Attempt to reserve a nickname, issuing a reserve nick callback
   * @param nick
   * @param who
   */
  final case class ReserveNick(nick: IRCString, who: ActorRef[Client.Command]) extends Command

  /**
   * Mark a nickname as free for claiming
   * @param nick
   */
  final case class FreeNick(nick: IRCString) extends Command

  /**
   * Attempt to send a private message to a user
   * @param from the prefix of the sender of the message
   * @param toNick the nickname of the recipient of the message
   * @param fromRef the actor who requested the message be sent, in case the recipient could not be found
   * @param message the message body
   */
  final case class SendMessage(
    from: Client.Prefix,
    toNick: IRCString,
    fromRef: ActorRef[Client.Command],
    message: String
  ) extends Command

  def apply(): Behavior[UserRegistry.Command] = Behaviors.setup { ctx =>
    ctx.log.info("Initializing user registry")
    UserRegistry(ctx)
  }
}
final case class UserRegistry(ctx: ActorContext[UserRegistry.Command])
    extends AbstractBehavior[UserRegistry.Command](ctx) {

  val clients: mutable.Map[IRCString, ActorRef[Client.Command]] = mutable.Map.empty

  override def onMessage(msg: UserRegistry.Command): Behavior[UserRegistry.Command] = {
    msg match {
      case UserRegistry.ReserveNick(nick, who) if clients.contains(nick) =>
        who.tell(Client.ReserveNickCallback(nick, success = false))
      case UserRegistry.ReserveNick(nick, who) if !clients.contains(nick) =>
        ctx.log.debug(s"$nick reserved for $who")
        clients += (nick -> who)
        who.tell(ReserveNickCallback(nick, success = true))
      case UserRegistry.FreeNick(nick) =>
        clients.remove(nick)
      case UserRegistry.SendMessage(from, toNick, _, msg) if (clients.contains(toNick)) =>
        clients(toNick).tell(Client.NotifyMessage(from, toNick, msg))
      case UserRegistry.SendMessage(_, toNick, fromRef, _) if !clients.contains(toNick) =>
        fromRef.tell(Client.Passthru(ResponseMessage(None, ERR_NOSUCHNICK(toNick))))
    }
    this
  }
}

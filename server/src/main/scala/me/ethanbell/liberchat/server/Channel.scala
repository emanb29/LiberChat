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
   * @param channelToJoin the name of the channel the user is trying to join. This is needed to recreate the
   *                      channel the join message falls to dead letters due to a race condition between the
   *                      last user leaving and an additional user joining
   */
  final case class Join(
    newUserPrefix: Client.Prefix,
    who: ActorRef[Client.Command],
    channelToJoin: IRCString
  ) extends Command
  final case class Part(leavingPrefix: Client.Prefix, reason: Option[String]) extends Command
  final case class SendMessage(sourcePrefix: Client.Prefix, msg: String)      extends Command

  def apply(registry: ActorRef[ChannelRegistry.Command], name: IRCString): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.log.info("Initializing user registry")
      Channel(ctx, registry, name)
    }

}
final case class Channel(
  ctx: ActorContext[Channel.Command],
  registry: ActorRef[ChannelRegistry.Command],
  name: IRCString
) extends AbstractBehavior[Channel.Command](ctx) {
  val users: mutable.Map[IRCString, ActorRef[Client.Command]] = mutable.Map.empty
  override def onMessage(msg: Channel.Command): Behavior[Channel.Command] = msg match {
    case Channel.Join(prefix, who, _) =>
      (users += (prefix.nick -> who)).foreach {
        case (_, userRef) => userRef.tell(Client.NotifyJoin(prefix, this.name, ctx.self))
      }
      this
    case Channel.Part(prefix, reason) =>
      users.foreach {
        case (_, userRef) => userRef.tell(Client.NotifyPart(prefix, this.name, reason))
      }
      users -= prefix.nick
      if (users.isEmpty) {
        registry.tell(ChannelRegistry.RemoveChannel(this.name))
        Behaviors.stopped
      } else this
    case Channel.SendMessage(sourcePrefix, msg) =>
      users
        .collect {
          case (nick, ref) if nick != sourcePrefix.nick => ref
        }
        .foreach { client =>
          client.tell(Client.NotifyMessage(sourcePrefix, this.name, msg))
        }
      this
  }
}

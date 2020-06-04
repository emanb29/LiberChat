package me.ethanbell.liberchat.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import me.ethanbell.liberchat.AkkaUtil.RegistryCompanion
import me.ethanbell.liberchat.IRCString

import scala.collection.mutable

object ChannelRegistry extends RegistryCompanion {
  final val ACTOR_NAME = "channel-registry"
  sealed trait Command

  final case class JoinOrCreate(
    channelName: IRCString,
    userPrefix: Client.Prefix,
    user: ActorRef[Client.Command]
  ) extends Command

  case class GetChannelList(channels: Seq[IRCString], replyTo: ActorRef[Client.Command])
      extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Initializing user registry")
    ChannelRegistry(ctx)
  }
}

final case class ChannelRegistry(ctx: ActorContext[ChannelRegistry.Command])
    extends AbstractBehavior[ChannelRegistry.Command](ctx) {

  val channels: mutable.Map[IRCString, ActorRef[Channel.Command]] = mutable.Map.empty

  override def onMessage(msg: ChannelRegistry.Command): Behavior[ChannelRegistry.Command] =
    msg match {
      case ChannelRegistry.JoinOrCreate(name, prefix, user) =>
        if (!channels.contains(name)) {
          val newChan =
            ctx.spawn(Channel(name), s"chan-{${name.toLower.str.filter(_.isLetterOrDigit)}")
          channels += (name -> newChan)
        }
        channels(name).tell(Channel.Join(prefix, user))
        this
      case ChannelRegistry.GetChannelList(selectedChannels, replyTo) =>
        val channelsToSend =
          if (selectedChannels.isEmpty) channels.keys
          else selectedChannels.toSet & channels.keySet
        val channelInfo = channelsToSend.map((_, -1, "This server does not support topics.")).toList
        replyTo.tell(Client.SendChannelList(channelInfo))
        this
    }

}

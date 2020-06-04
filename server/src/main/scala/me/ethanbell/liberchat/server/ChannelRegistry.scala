package me.ethanbell.liberchat.server

import akka.actor.DeadLetter
import akka.actor.typed.eventstream.EventStream
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

  final case class GetChannelList(channels: Seq[IRCString], replyTo: ActorRef[Client.Command])
      extends Command

  final case class RemoveChannel(channelName: IRCString)    extends Command
  final case class HandleDeadLetter(deadLetter: DeadLetter) extends Command

  final case class ListNames(channels: Seq[IRCString], replyTo: ActorRef[Client.Command])
      extends Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    val deadLetterMapper = ctx.messageAdapter[DeadLetter](HandleDeadLetter.apply)
    ctx.system.eventStream.tell(EventStream.Subscribe(deadLetterMapper))
    ctx.log.info("Initializing channel registry")
    ChannelRegistry(ctx)
  }
}

final case class ChannelRegistry(ctx: ActorContext[ChannelRegistry.Command])
    extends AbstractBehavior[ChannelRegistry.Command](ctx) {

  val channels: mutable.Map[IRCString, ActorRef[Channel.Command]] = mutable.Map.empty

  override def onMessage(msg: ChannelRegistry.Command): Behavior[ChannelRegistry.Command] =
    msg match {
      case ChannelRegistry.HandleDeadLetter(deadLetter)
          if deadLetter.message.isInstanceOf[Channel.Join] =>
        val joinMsg = deadLetter.message.asInstanceOf[Channel.Join]
        ctx.log.info(
          s"User tried to join ${joinMsg.channelToJoin} but JOIN message encountered deadletters. Trying again."
        )
        ctx.self.tell(
          ChannelRegistry.JoinOrCreate(joinMsg.channelToJoin, joinMsg.newUserPrefix, joinMsg.who)
        )
        this
      case ChannelRegistry.HandleDeadLetter(_) => Behaviors.unhandled
      case ChannelRegistry.JoinOrCreate(name, prefix, user) =>
        if (!channels.contains(name)) {
          val newChan =
            ctx.spawn(
              Channel(ctx.self, name),
              s"chan-${name.toLower.str.filter(_.isLetterOrDigit)}"
            )
          channels += (name -> newChan)
        }
        channels(name).tell(Channel.Join(prefix, user, name))
        this
      case ChannelRegistry.RemoveChannel(name) =>
        ctx.log.info(s"Last user left $name so removing that channel")
        channels -= name
        this
      case ChannelRegistry.GetChannelList(selectedChannels, replyTo) =>
        val channelsToSend =
          if (selectedChannels.isEmpty) channels.keys
          else selectedChannels.toSet & channels.keySet
        val channelInfo = channelsToSend.map((_, -1, "This server does not support topics.")).toList
        replyTo.tell(Client.SendChannelList(channelInfo))
        this
      case ChannelRegistry.ListNames(selectedChannels, replyTo) =>
        val channelsToSend =
          if (selectedChannels.isEmpty) channels.keys
          else selectedChannels.toSet & channels.keySet
        channelsToSend.map(channels.apply).foreach { chanRef =>
          chanRef.tell(Channel.GetNames(replyTo))
        }
        this
    }

}

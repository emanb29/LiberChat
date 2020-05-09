package me.ethanbell.liberchat.server

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}

import scala.collection.mutable

object ChannelDirectory {
  sealed trait Command
  final case class ListChannels(replyTo: ActorRef[List[Channel]])               extends Command
  final case class AddChannel(chan: Channel, replyTo: ActorRef[Channel])        extends Command
  final case class GetChannel(name: String, replyTo: ActorRef[Option[Channel]]) extends Command
}

/**
 * The channeldirectory actor manages the list of all channels on this server, and creates them
 * @todo channel removal is not yet supported
 * @param context
 */
case class ChannelDirectory(override val context: ActorContext[ChannelDirectory.Command])
    extends AbstractBehavior[ChannelDirectory.Command](context) {
  import me.ethanbell.liberchat.server.ChannelDirectory._
  val rooms: mutable.Map[String, Channel] = mutable.Map.empty
  override def onMessage(msg: ChannelDirectory.Command): Behavior[ChannelDirectory.Command] = {
    msg match {
      case ListChannels(replyTo) => replyTo.tell(rooms.values.toList)
      case AddChannel(chan, replyTo) =>
        rooms.addOne(chan.name, chan)
        replyTo.tell(chan)
      case GetChannel(name, replyTo) => replyTo.tell(rooms.get(name))
    }
    this
  }
}

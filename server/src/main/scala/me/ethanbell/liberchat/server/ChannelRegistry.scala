package me.ethanbell.liberchat.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import me.ethanbell.liberchat.AkkaUtil.RegistryCompanion
import me.ethanbell.liberchat.IRCString

import scala.collection.mutable

object ChannelRegistry extends RegistryCompanion {
  final val ACTOR_NAME = "channel-registry"
  sealed trait Command

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("Initializing user registry")
    ChannelRegistry(ctx)
  }
}

final case class ChannelRegistry(ctx: ActorContext[ChannelRegistry.Command])
    extends AbstractBehavior[ChannelRegistry.Command](ctx) {

  val channels: mutable.Map[IRCString, ActorRef[Channel.Command]] = mutable.Map.empty

  override def onMessage(msg: ChannelRegistry.Command): Behavior[ChannelRegistry.Command] =
    this
}

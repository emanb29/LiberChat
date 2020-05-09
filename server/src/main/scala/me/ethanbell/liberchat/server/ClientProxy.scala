package me.ethanbell.liberchat.server

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}

object ClientProxy {
  sealed trait Command
}
case class ClientProxy(override val context: ActorContext[ClientProxy.Command])
    extends AbstractBehavior[ClientProxy.Command](context) {
  override def onMessage(msg: ClientProxy.Command): Behavior[ClientProxy.Command] =
    this
}

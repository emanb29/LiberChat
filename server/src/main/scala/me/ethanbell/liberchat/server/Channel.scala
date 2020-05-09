package me.ethanbell.liberchat.server

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl._
import me.ethanbell.liberchat.UserMode

import scala.collection.mutable

object Channel {
  sealed trait Command
  case class AddUser(client: ClientProxy) extends Command
}

case class Channel(override val context: ActorContext[Channel.Command], name: String)
    extends AbstractBehavior[Channel.Command](context) {
  val clients: mutable.Map[ClientProxy, Set[UserMode]] = mutable.Map.empty

  override def onMessage(msg: Channel.Command): Behavior[Channel.Command] = {
    msg match {
      case Channel.AddUser(client) =>
        clients.addOne(client -> Set.empty)
    }
    this
  }
}

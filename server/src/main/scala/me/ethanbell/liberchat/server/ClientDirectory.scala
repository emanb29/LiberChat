package me.ethanbell.liberchat.server

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext}

import scala.collection.mutable

object ClientDirectory {
  sealed trait Command
  final case class ListClients(replyTo: ActorRef[List[ClientProxy]])               extends Command
  final case class AddClient(chan: ClientProxy, replyTo: ActorRef[ClientProxy])        extends Command
  final case class GetClient(name: String, replyTo: ActorRef[Option[ClientProxy]]) extends Command
}
/**
 * The ClientDirectory actor manages the list of all Clients on this server, and creates them
 *
 * @todo Client removal is not yet supported
 * @param context
 */
case class ClientDirectory(override val context: ActorContext[ClientDirectory.Command])
    extends AbstractBehavior[ClientDirectory.Command](context) {
  import me.ethanbell.liberchat.server.ClientDirectory._
  val clients: mutable.ListBuffer[ClientProxy] = mutable.ListBuffer.empty
  override def onMessage(msg: ClientDirectory.Command): Behavior[ClientDirectory.Command] = {
    msg match {
      case ListClients(replyTo) => replyTo.tell(clients.toList)
      case AddClient(client, replyTo) =>
        clients.append(client)
        replyTo.tell(client)
      case GetClient(name, replyTo) => replyTo.tell(rooms.get(name))
    }
    this
  }
}

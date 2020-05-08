package me.ethanbell.liberchat.server
import akka.actor.ActorSystem
import akka.stream.scaladsl.Tcp

import scala.jdk.CollectionConverters._

case object Main extends App {
  println("Server starting...")
  implicit val system: ActorSystem = ActorSystem("irc-server")
  private val env                  = System.getenv().asScala

  val host = env.getOrElse("HOST", "0.0.0.0")
  val port = env.get("PORT").flatMap(_.toIntOption).getOrElse(6667)

  Tcp().bind(host, port).runForeach { conn =>
    conn.handleWith(Server.flow)
  }
}

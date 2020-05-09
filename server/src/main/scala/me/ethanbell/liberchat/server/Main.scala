package me.ethanbell.liberchat.server
import java.util.concurrent.TimeUnit

import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.Tcp
import akka.util.Timeout

import scala.jdk.CollectionConverters._

case object Main extends App {
  println("Server starting...")
  implicit val system           = ActorSystem(IRCCommandActor(), "irc-server")
  implicit val timeout: Timeout = Timeout(2, TimeUnit.SECONDS)
  private val env               = System.getenv().asScala
  val hookup                    = IRCCommandActor()

  val host = env.getOrElse("HOST", "0.0.0.0")
  val port = env.get("PORT").flatMap(_.toIntOption).getOrElse(6667)

  Tcp()(system.classicSystem).bind(host, port).runForeach { conn =>
    conn.handleWith(Server(system).flow)
  }
}

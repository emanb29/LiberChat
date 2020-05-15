package me.ethanbell.liberchat.server
import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._
import akka.stream.scaladsl.{Keep, Tcp}
import akka.util.Timeout
import me.ethanbell.liberchat.server.SessionActor.Shutdown

import scala.concurrent.Await
import scala.jdk.CollectionConverters._

case object Main extends App {
  case object IRCActorSystem {
    def apply(): Behavior[SpawnProtocol.Command] = Behaviors.setup { ctx =>
      SpawnProtocol()
    }
  }
  println("Server starting...")
  implicit val system: ActorSystem[SpawnProtocol.Command] =
    ActorSystem(IRCActorSystem(), "irc-server")
  implicit val timeout: Timeout = Timeout(2, TimeUnit.SECONDS)
  private val env               = System.getenv().asScala

  val host = env.getOrElse("HOST", "0.0.0.0")
  val port = env.get("PORT").flatMap(_.toIntOption).getOrElse(6667)

  Tcp()(system.classicSystem).bind(host, port).runForeach { conn =>
    import akka.actor.typed.scaladsl.AskPattern._
    implicit val ec = system.executionContext

    implicit val sessionActor: ActorRef[SessionActor.Command] =
      Await.result(
        system
          .ask(
            SpawnProtocol.Spawn(
              SessionActor(),
              conn.remoteAddress.getHostName.filter(_.isLetter),
              Props.empty,
              _
            )
          ),
        timeout.duration
      )

    val (_, termination) = conn.handleWith(Server().flow.watchTermination()(Keep.both))
    termination.onComplete(_ => sessionActor.tell(Shutdown))
  }
}

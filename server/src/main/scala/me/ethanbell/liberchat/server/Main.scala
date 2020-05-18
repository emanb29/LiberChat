package me.ethanbell.liberchat.server
import java.util.concurrent.TimeUnit

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{Source, Tcp}
import akka.stream.{ActorAttributes, OverflowStrategy, Supervision}
import akka.util.Timeout
import me.ethanbell.liberchat.Response
import me.ethanbell.liberchat.server.SessionActor.Shutdown
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters._

case object Main extends App {
  case object IRCActorSystem {
    def apply(): Behavior[SpawnProtocol.Command] = Behaviors.setup { ctx =>
      SpawnProtocol()
    }
  }

  implicit val system: ActorSystem[SpawnProtocol.Command] =
    ActorSystem(IRCActorSystem(), "irc-server")
  implicit val timeout: Timeout = Timeout(2, TimeUnit.SECONDS)

  val userRegistryFut = system.ask[ActorRef[UserRegistryActor.Command]](
    SpawnProtocol.Spawn(UserRegistryActor(), UserRegistryActor.ACTOR_NAME, Props.empty, _)
  )

  val chattySupervisor: Supervision.Decider = { err =>
    system.log.error("Unhandled exception in stream", err)
    Supervision.Stop
  }

  private val env = System.getenv().asScala
  val host        = env.getOrElse("HOST", "0.0.0.0")
  val port        = env.get("PORT").flatMap(_.toIntOption).getOrElse(6667)

  system.log.info("Server starting...")
  Tcp()(system.classicSystem).bind(host, port).runForeach { conn =>
    system.log.debug(s"Got new connection from ${conn.remoteAddress}")
    implicit val ec = system.executionContext

    val (actorResponseQueue, actorResponseSource) =
      Source.queue[Response](20, OverflowStrategy.backpressure).preMaterialize()

    val sessionActorFut: Future[ActorRef[SessionActor.Command]] = for {
      userRegistry <- userRegistryFut
      sessionActor <- system
        .ask[ActorRef[SessionActor.Command]](
          SpawnProtocol.Spawn(
            SessionActor(actorResponseQueue, userRegistry),
            conn.remoteAddress.getHostName.filter(_.isLetter),
            Props.empty,
            _
          )
        )
    } yield sessionActor

    implicit val sessionActor: ActorRef[SessionActor.Command] =
      Await.result(sessionActorFut, timeout.duration)

    val (termination, killSwitch) =
      conn.handleWith(
        Server(actorResponseSource).flow
          .withAttributes(ActorAttributes.supervisionStrategy(chattySupervisor))
      )
    termination.onComplete { _ =>
      system.log.debug(s"Disconnecting ${conn.remoteAddress}")
      sessionActor.tell(Shutdown)
    }
  }
}

package me.ethanbell.liberchat.client
import akka.actor
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import korolev.akka.{akkaHttpService, AkkaHttpServerConfig}
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import korolev.server._
import korolev.state.javaSerialization._
import korolev.{akka => _, _}
import levsha.dsl._
import levsha.dsl.html._

import scala.concurrent.{ExecutionContextExecutor, Future}

case object Main extends App {
  implicit private val actorSystem: ActorSystem[Nothing] = akka.actor.ActorSystem().toTyped
  implicit private val classicSystem: actor.ActorSystem  = actorSystem.classicSystem
  implicit private val mat                               = Materializer.matFromSystem(actorSystem)
  implicit private val ec: ExecutionContextExecutor      = actorSystem.executionContext

  val ctx = Context[Future, Unit, Any]

  val config = KorolevServiceConfig[Future, Unit, Any](
    stateLoader = StateLoader.default(()),
    render = state =>
      optimize {
        body(s"Hello, world! State was $state")
      }
  )

  val httpService: Route = akkaHttpService(config).apply(AkkaHttpServerConfig())

  Http()(actorSystem.classicSystem).bindAndHandle(route2HandlerFlow(httpService), "localhost", 80)

}

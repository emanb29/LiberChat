package me.ethanbell.liberchat.client
import akka.actor
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import me.ethanbell.liberchat.client.view.ViewRoot

import scala.concurrent.{ExecutionContextExecutor, Future}

case object Main extends App {
  implicit private val actorSystem: ActorSystem[_]      = akka.actor.ActorSystem().toTyped
  implicit private val classicSystem: actor.ActorSystem = actorSystem.classicSystem
  implicit private val mat                              = Materializer.matFromSystem(actorSystem)
  implicit private val ec: ExecutionContextExecutor     = actorSystem.executionContext

  val viewRoot = ViewRoot()

  Http()(actorSystem.classicSystem)
    .bindAndHandle(route2HandlerFlow(viewRoot.httpService), "localhost", 80)

}

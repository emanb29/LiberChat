package me.ethanbell.liberchat.client.view

import akka.actor
import akka.http.scaladsl.server.Route
import korolev.Router._
import korolev.{Context, Router}
import korolev.akka.{akkaHttpService, AkkaHttpServerConfig}
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.state.javaSerialization._
import levsha.dsl._
import levsha.dsl.html._
import levsha.dsl.optimize

import scala.concurrent.{ExecutionContext, Future}

case class ViewRoot()(implicit classicSystem: actor.ActorSystem, ec: ExecutionContext) {

  case class ViewState(connected: Option[String])

  val ctx = Context[Future, Unit, Any]

  val config = KorolevServiceConfig[Future, Unit, Any](
    stateLoader = StateLoader.default(()),
    router = Router(
      fromState = {
        case _ => Root
      },
      toState = {
        case Root     => defaultState => Future.successful(())
        case Root / v => defaultState => Future.successful(println(s"On path $v"))
      }
    ),
    render = state =>
      optimize {
        import ctx._
        Html(
          clazz := "h-100",
          head(
            title(
              "LiberChat"
            ),
            link(
              rel := "stylesheet",
              href := "https://stackpath.bootstrapcdn.com/bootstrap/4.5.0/css/bootstrap.min.css",
              lang := "css"
            )
          ),
          body(
            clazz := "h-100",
            ChatComponent.silent()
          )
        )
      }
  )

  val httpService: Route = akkaHttpService(config).apply(AkkaHttpServerConfig())
}

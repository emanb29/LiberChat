package me.ethanbell.liberchat.client.view

import akka.actor
import akka.http.scaladsl.server.Route
import korolev.{Component, Context}
import korolev.akka.{akkaHttpService, AkkaHttpServerConfig}
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.state.javaSerialization._
import levsha.dsl._
import levsha.dsl.html._
import levsha.dsl.optimize

import scala.concurrent.{ExecutionContext, Future}

object ChatComponent extends Component[Future, Unit, Unit, Unit](()) {
  override def render(parameters: Unit, state: Unit): ChatComponent.context.Node =
    optimize {
      div(
        clazz := "container h-100 px-0 pt-5 d-flex flex-column",
        style := "max-width: 1000px; max-height: 600px;",
        div(
          clazz := "px-0 flex-grow-1 d-flex flex-wrap align-items-stretch",
          select(
            clazz := "form-control col-2",
            name := "channels",
            multiple,
            option(
              value := "#a-channel",
              "#a-channel"
            ),
            option(
              value := "#other",
              "#other"
            )
          ),
          div(
            clazz := "chat col-8 border rounded",
            style := "overflow-y: scroll; max-height: 562px;",
            pre(
              clazz := "m-0 border-bottom text-wrap",
              "1: This is message 1"
            ),
            pre(
              clazz := "m-0 border-bottom text-wrap",
              "4: Lorem ipsum dolor sit amet consectetur adipisicing elit. Quibusdam ullam, ratione veritatis consequatur esse vel dolor? Est quos quod hic culpa. Cum et architecto alias natus id! Dicta, voluptate excepturi?"
            )
          ),
          select(
            clazz := "form-control col-2",
            name := "users",
            multiple,
            disabled,
            option(
              value := "erin",
              "erin"
            ),
            option(
              value := "ethan",
              "ethan"
            )
          )
        ),
        div(
          clazz := "input-group px-0 d-flex-root",
          input(
            clazz := "form-control",
            `type` := "text",
            name := "message",
            placeholder := "Enter your message"
          ),
          div(
            clazz := "input-group-append",
            button(
              clazz := "btn btn-primary",
              `type` := "submit",
              "Send"
            )
          )
        )
      )
    }
}

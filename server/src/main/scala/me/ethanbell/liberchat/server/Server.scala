package me.ethanbell.liberchat.server

import java.nio.charset.Charset

import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitch, KillSwitches, OverflowStrategy}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import me.ethanbell.liberchat.AkkaUtil._
import me.ethanbell.liberchat.Message.LexError
import me.ethanbell.liberchat.Response.ERR_UNKNOWNCOMMAND
import me.ethanbell.liberchat._
import me.ethanbell.liberchat.server.SessionActor.NoOp

import scala.concurrent.Future

case class Server(actorResponseSource: Source[Response, NotUsed])(
  implicit sessionActor: ActorRef[SessionActor.Command],
  timeout: Timeout
) {
  private val UTF8 = Charset.forName("UTF-8")
  def flow: Flow[ByteString, ByteString, (Future[Done], KillSwitch)] =
    Flow[ByteString]
      .watchTermination()(Keep.right)
      .viaMat(KillSwitches.single)(Keep.both)
      .map(_.decodeString(UTF8))
      .via(handleMessageStrings)
      .map(_.serialize)
      .map(ByteString(_, UTF8))

  def handleMessageStrings: Flow[String, Response, NotUsed] =
    Flow[String]
      .via(IRC.parseMessagesFlow)
      .viaEither(handleParseErrors, handleMessages)

  def handleParseErrors: Flow[LexError, Response, NotUsed] = Flow[LexError].map {
    case LexError.Impossible                        => ???
    case LexError.UnknownCommand(commandName, args) => ERR_UNKNOWNCOMMAND(commandName)
    case LexError.UnknownResponseCode(responseCode, args) =>
      ERR_UNKNOWNCOMMAND(responseCode.toString) // TODO
    case LexError.TooFewCommandParams(commandName, args, expectedArity) =>
      Response.ERR_NEEDMOREPARAMS(commandName)
    case LexError.TooFewResponseParams(responseCode, args, expectedArity) =>
      Response.ERR_NEEDMOREPARAMS(responseCode.toString) // TODO
  }

  def handleMessages: Flow[Message, Response, NotUsed] = Flow[Message]
    .map {
      case cm: CommandMessage  => Left(cm)
      case rm: ResponseMessage => Right(rm)
    }
    .viaEither(
      Flow.fromSinkAndSource(
        Flow[CommandMessage]
          .map[SessionActor.Command](SessionActor.HandleIRCMessage.apply)
          .to(
            ActorSink.actorRef[SessionActor.Command](
              sessionActor,
              NoOp,
              _ => SessionActor.Shutdown
            )
          ),
        actorResponseSource
      ),
      Flow[ResponseMessage].map(rm => Response.ERR_UNKNOWNCOMMAND(rm.response.name))
    )

}

package me.ethanbell.liberchat.server

import java.nio.charset.Charset

import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.stream.{KillSwitch, KillSwitches}
import akka.util.ByteString
import akka.{Done, NotUsed}
import me.ethanbell.liberchat.AkkaUtil._
import me.ethanbell.liberchat.Message.LexError
import me.ethanbell.liberchat.Response.ERR_UNKNOWNCOMMAND
import me.ethanbell.liberchat._

import scala.concurrent.Future

case class Server(actorResponseSource: Source[Message, NotUsed])(
  implicit sessionActor: ActorRef[Client.Command]
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

  def handleMessageStrings: Flow[String, Message, NotUsed] =
    Flow[String]
      .via(IRC.parseMessagesFlow)
      .viaEither(handleParseErrors, handleMessages)

  def handleParseErrors: Flow[LexError, ResponseMessage, NotUsed] = Flow[LexError]
    .map {
      case LexError.Impossible                        => ???
      case LexError.UnknownCommand(commandName, args) => ERR_UNKNOWNCOMMAND(commandName)
      case LexError.UnknownResponseCode(responseCode, args) =>
        ERR_UNKNOWNCOMMAND(responseCode.toString) // TODO should be response-related
      case LexError.TooFewCommandParams(commandName, args, expectedArity) =>
        Response.ERR_NEEDMOREPARAMS(commandName)
      case LexError.TooFewResponseParams(responseCode, args, expectedArity) =>
        Response.ERR_NEEDMOREPARAMS(responseCode.toString) // TODO should probably? be response-related
      case LexError.GenericResponseError(response) =>
        response
    }
    .map(ResponseMessage(None, _))

  def handleMessages: Flow[Message, Message, NotUsed] = Flow[Message]
    .map {
      case cm: CommandMessage  => Left(cm)
      case rm: ResponseMessage => Right(rm)
    }
    .viaEither(
      Flow.fromSinkAndSource(
        Flow[CommandMessage]
          .map[Client.Command](Client.HandleIRCMessage.apply)
          .to(
            ActorSink.actorRef[Client.Command](
              sessionActor,
              Client.NoOp,
              _ => Client.NoOp
            )
          ),
        actorResponseSource
      ),
      Flow[ResponseMessage]
    )

}

package me.ethanbell.liberchat.server

import java.nio.charset.Charset

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.typed.scaladsl.ActorFlow
import akka.stream.{KillSwitch, KillSwitches}
import akka.util.{ByteString, Timeout}
import me.ethanbell.liberchat.AkkaUtil._
import me.ethanbell.liberchat.Message.LexError
import me.ethanbell.liberchat.server.IRCCommandActor.ReplyableCommandMsg
import me.ethanbell.liberchat._

case class Server(actorSystemLink: ActorRef[ReplyableCommandMsg])(implicit timeout: Timeout) {
  private val UTF8 = Charset.forName("UTF-8")
  def flow: Flow[ByteString, ByteString, KillSwitch] =
    Flow[ByteString]
      .viaMat(KillSwitches.single)(Keep.right)
      .map(_.decodeString(UTF8))
      .via(handleMessageStrings)
      .map(_.serialize)
      .map(ByteString(_, UTF8))

  def handleMessageStrings: Flow[String, Response, NotUsed] =
    Flow[String]
      .via(IRC.parseMessagesFlow)
      .viaEither(handleParseErrors, handleMessages)

  def handleParseErrors: Flow[LexError, Response, NotUsed] = ???

  def handleMessages: Flow[Message, Response, NotUsed] = Flow[Message]
    .map {
      case cm: CommandMessage  => Left(cm)
      case rm: ResponseMessage => Right(rm)
    }
    .viaEither(
      ActorFlow.ask(actorSystemLink)((cm, replyTo) => ReplyableCommandMsg(replyTo, cm)),
      Flow[ResponseMessage].map(rm => Response.ERR_UNKNOWNCOMMAND(rm.response.name))
    )

}

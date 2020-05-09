package me.ethanbell.liberchat.server

import java.nio.charset.Charset

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep}
import akka.stream.{KillSwitch, KillSwitches}
import akka.util.ByteString
import me.ethanbell.liberchat.AkkaStreamsUtil._
import me.ethanbell.liberchat.Message.LexError
import me.ethanbell.liberchat.{Command, CommandMessage, IRC, Message, Response, ResponseMessage}

case object Server {
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

  def handleMessages: Flow[Message, Response, NotUsed] =
    Flow[Message].map {
      case CommandMessage(nickname, command) => command match {
        case Command.User(username, hostname, servername, realname) =>
      }
      case ResponseMessage(prefix, response) => Response.ERR_UNKNOWNCOMMAND(response.name)
    }

}

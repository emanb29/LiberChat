package me.ethanbell.liberchat

import akka.NotUsed
import akka.stream.scaladsl.Flow
import fastparse.Parsed
import me.ethanbell.liberchat.Message.LexError

case object IRC {

  val MAX_BYTES = 100 * 1000
  def parseMessagesFlow: Flow[String, Either[LexError, Message], NotUsed] =
    Flow[String].statefulMapConcat { () =>
      // The portion of the string we couldn't yet make work. This is bounded at MAX_BYTES to mitigate denial of service
      var unparsedString = ""
      str => {
        // TODO time permitting, get rid of some of this imperative code by making Message.parse
        //  return a Seq[Message] instead of a Message
        unparsedString += str
        var parseAttempt   = Message.parse(unparsedString)
        var parsedMessages = List.empty[Either[LexError, Message]]
        while (parseAttempt.isSuccess) { // parse as many messages as we can
          val Parsed.Success(parsed, index) = parseAttempt.get
          unparsedString = unparsedString.drop(index)
          parsedMessages = parsedMessages ::: parsed.toList
          parseAttempt = Message.parse(unparsedString)
        }
        parseAttempt match {
          case _: Parsed.Failure if unparsedString.length < MAX_BYTES =>
            parsedMessages // When we finally hit a failure, return the results we did manage to accrue
          case _: Parsed.Failure =>
            throw new RuntimeException(s"failed to parse after $MAX_BYTES bytes")
        }
      }
    }
}

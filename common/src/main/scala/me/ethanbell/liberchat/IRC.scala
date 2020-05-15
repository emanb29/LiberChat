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
          case _: Parsed.Failure
              if unparsedString.length < MAX_BYTES => // When we finally hit a failure, return the results we did manage to accrue
            // clear extra garbage
            if (unparsedString.endsWith("\r\n"))
              unparsedString = "" // \r\n was at the end of the unparsed section. If it wasn't already parsed, it never will be.
            else
              unparsedString.split("\r\n").toList match {
                case twoOrMoreUnparsedLines @ (_ :: _ :: _) =>
                  unparsedString = twoOrMoreUnparsedLines.last
                case _ => () // careful not to crash your function assuming totality
              }
            // return the accumulated results
            parsedMessages
          case _: Parsed.Failure =>
            throw new RuntimeException(s"failed to parse after $MAX_BYTES bytes")
        }
      }
    }
}

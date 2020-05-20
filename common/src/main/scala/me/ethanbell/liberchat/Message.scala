package me.ethanbell.liberchat

import com.typesafe.scalalogging.LazyLogging
import fastparse.{parse => fparse, _}

/**
 * An IRC message
 */
sealed trait Message {
  def prefix: Option[String]
  def commandLike: CommandLike
  def serialize: String =
    prefix.fold("")(':' +: _ :+ ' ') ++ commandLike.serialize
}

case class CommandMessage(nickname: Option[String] = None, command: Command) extends Message {
  override def prefix: Option[String]   = nickname
  override def commandLike: CommandLike = command
}
case class ResponseMessage(override val prefix: Option[String], response: Response)
    extends Message {
  override def commandLike: CommandLike = response
}

object Message extends LazyLogging {
  sealed trait LexError extends Exception

  /**
   * Lex errors with a natural response associated with them
   */
  sealed trait KnownResponse {
    def response: Response
  }
  object LexError {
    case object Impossible extends LexError

    /**
     * The command name provided was not recognized
     * @param commandName
     * @param args
     */
    case class UnknownCommand(commandName: String, args: Seq[String])
        extends LexError
        with KnownResponse {
      def response: Response = Response.ERR_UNKNOWNCOMMAND(commandName)
    }

    /**
     * The command was recognized, but there were not enough valid parameters for the command
     * @note this may also represent a type error in one or more of the arguments
     * @param commandName
     * @param args
     * @param expectedArity
     */
    case class TooFewCommandParams(commandName: String, args: Seq[String], expectedArity: Int)
        extends LexError
        with KnownResponse {
      def response: Response = Response.ERR_NEEDMOREPARAMS(commandName)
    }

    /**
     * The response code of the message was not recognized
     * @param responseCode
     * @param args
     */
    case class UnknownResponseCode(responseCode: Int, args: Seq[String]) extends LexError

    /**
     * The response code of the message was recognized, but there were fewer parameters than expected
     * @param responseCode
     * @param args
     * @param expectedArity
     */
    case class TooFewResponseParams(responseCode: Int, args: Seq[String], expectedArity: Int)
        extends LexError

    /**
     * An error with a prescribed response.
     * @param response A strong suggestion for what the response yielded by this error should be
     */
    case class GenericResponseError(response: Response) extends LexError with KnownResponse
  }

  /**
   * Attempt to parse an IRC message
   * @param str
   * @note a failure to parse is a protocol failure and the client will be unceremoniously dropped
   * @return None when there was no message, just a CRLF
   *         Some(Left(error)) when an error occurred during parsing
   *         Some(Right(message)) when the message was successfully parsed
   */
  def parse(str: String): Parsed[Option[Either[Message.LexError, Message]]] =
    fparse(str, Parser.message(_))
  // TODO optimize ~~s to ~~/s where appropriate

  /**
   * A parser to parse IRC message grammar
   * @note we distinguish at the type level between "command messages" and "response messages", though both extend "Message"
   * @see https://tools.ietf.org/html/rfc1459#section-2.3.1
   */
  object Parser {
    def nonSpecial(char: Char): Boolean = char match {
      case '\u000D' | '\u000A' | '\u0000' => false
      case _                              => true
    }
    def nonSpecialOrSpace(char: Char): Boolean = nonSpecial(char) && char != '\u0020'

    def SingleChar[_: P](c: Char): P[Char] = CharPred(_ == c).!.map(_.head)

    def message[_: P]: P[Option[Either[Message.LexError, Message]]] =
      ((prefix.? ~~ commandLike).? ~~/ crlf).map {
        case None                                    => None // an empty line with just a crlf is "silently ignored"
        case Some((_, Left(parseError)))             => Some(Left(parseError))
        case Some((prefix, Right(command: Command))) => Some(Right(CommandMessage(prefix, command)))
        case Some((prefix, Right(response: Response))) =>
          Some(Right(ResponseMessage(prefix, response)))
        case Some((prefix, Right(_))) =>
          logger.error(
            s"Received an IRC message which was neither a command nor a response from $prefix"
          )
          Some(Left(LexError.Impossible)) // This is impossible because the _only_ things that inherit [[CommandLike]] are [[Command]] and [[Response]]
      }

    def commandLike[_: P]: P[Either[Message.LexError, CommandLike]] =
      (commandId ~~ params.?.map(_.toList.flatten)).map {
        case (Left(commandName), args)   => Command.parse(commandName, args)
        case (Right(responseCode), args) => Response.parse(responseCode, args)
      }

    def prefix[_: P]: P[String] =
      ":" ~~ CharsWhile(nonSpecialOrSpace).! // TODO maybe parse further?

    def commandId[_: P]: P[Either[String, Int]] =
      (CharIn("a-zA-Z").repX(1).!.map(Left(_))
        | CharIn("0-9").repX(0, null, 0, 3).!.map(num => Right(num.toInt)))

    def params[_: P]: P[Seq[String]] =
      space ~~
          (":" ~~ trailing.map(List(_))
            | (middle ~~ params).map { case (head, tail) => head +: tail } // These parsers are tested in order, so the recursive one _must_ come first.
            | middle.map(List(_)))

    def middle[_: P]: P[String]   = CharsWhile(nonSpecialOrSpace, 1).!
    def trailing[_: P]: P[String] = !SingleChar(':') ~~ CharsWhile(nonSpecial).!

    def space[_: P]: P[Unit] = P("\u0020").repX(1)
    def crlf[_: P]: P[Unit]  = P("\u000D\u000A")
  }
}

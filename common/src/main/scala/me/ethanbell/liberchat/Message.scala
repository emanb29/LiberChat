package me.ethanbell.liberchat

import com.typesafe.scalalogging.LazyLogging
import fastparse.{parse => fparse, _}

/**
 * An IRC message
 */
sealed trait Message {
  def prefix: Option[String]
  def commandLike: CommandLike
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
  def parse(str: String): Parsed[Option[Message]] = fparse(str, Parser.message(_))
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

    def message[_: P]: P[Option[Message]] = ((prefix.? ~~ commandLike).? ~~ crlf).map {
      case None                               => None // an empty line with just a crlf is "silently ignored"
      case Some((prefix, command: Command))   => Some(CommandMessage(prefix, command))
      case Some((prefix, response: Response)) => Some(ResponseMessage(prefix, response))
      case Some((prefix, _)) =>
        logger.error(
          s"Received an IRC message which was neither a command nor a response from $prefix"
        )
        ???
    }

    def commandLike[_: P]: P[CommandLike] = (commandId ~~ params).map {
      case (Left(commandName), args)   => ??? // TODO
      case (Right(responseCode), args) => ??? // TODO
    }

    def prefix[_: P]: P[String] =
      ":" ~~ CharsWhile(nonSpecialOrSpace).! // TODO maybe parse further?

    def commandId[_: P]: P[Either[String, Int]] =
      (CharIn("a-zA-Z").repX(1).!.map(Left(_))
        | CharIn("0-9").repX(0, null, 0, 3).!.map(num => Right(num.toInt)))

    def params[_: P]: P[Seq[String]] =
      space ~~
          (":" ~~ trailing.map(List(_))
            | (middle ~~ params).map { case (head, tail) => head +: tail })

    def middle[_: P]: P[String]   = CharsWhile(nonSpecialOrSpace, 1).!
    def trailing[_: P]: P[String] = !SingleChar(':') ~~ CharsWhile(nonSpecial).!

    def space[_: P]: P[Unit] = P("\u0020").repX(1)
    def crlf[_: P]: P[Unit]  = P("\u000D\u000A")
  }
}

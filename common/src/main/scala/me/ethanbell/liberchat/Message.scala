package me.ethanbell.liberchat

import com.typesafe.scalalogging.LazyLogging
import fastparse._

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

  // TODO optimize ~~s to ~~/s where appropriate
  object Parser {
    def SingleChar[_: P](c: Char): P[Char] = CharIn(c.toString).!.map(_.head)

    def message[_: P]: P[Message] = (prefix.? ~~ command).map {
      case (nickname, Left(command)) =>
        CommandMessage(nickname, command) // TODO check that nickname is valid?
      case (prefix, Right(response)) =>
        ResponseMessage(prefix, response) // if prefix is None... "assumed to have originated from the connection from which it was received from"
    }

    def prefix[_: P]: P[String] = CharIn(":") ~~ CharPred(_ != ' ').repX.! ~~ space

    def command[_: P]: P[Either[Command, Response]] =
      (commandIdent ~~ params).map(t => t.copy(_2 = t._2.toVector)).map {
        case (Left(cmdName), args)       => ??? // TODO
        case (Right(responseCode), args) => ??? // TODO
      }

    /**
     * A command (or response "identity")
     * @return either the string name of the command (left) or the status code of the response (right)
     */
    def commandIdent[_: P]: P[Either[String, Int]] = (commandStr | responseCode).map {
      case commandName: String => Left(commandName)
      case responseCode: Int   => Right(responseCode)
    }

    def commandStr[_: P]: P[String] = CharIn("a-zA-Z").repX(1).!
    def responseCode[_: P]: P[Int]  = CharIn("0-9").repX(exactly = 3).!.map(_.toInt)

    def varparams[_: P]: P[Seq[String]] =
      ((space ~~ middle).repX(min = 0, max = 14) ~~ (space ~~ SingleChar(':') ~~ trailing).?)
        .map(_._1)
    def fixedparams[_: P]: P[Seq[String]] =
      ((space ~~ middle).repX(exactly = 14) ~~ (space ~~ SingleChar(':') ~~ trailing).?)
        .map(_._1)
    def params[_: P]: P[Seq[String]] = varparams | fixedparams

    /**
     * Any 8-bit character except one of " \r\n:"
     * @return the character matched
     */
    def nonspecial[_: P]: P[Char] = CharIn(
      "\u0001-\u0009",
      "\u000B-\u000C",
      "\u000E-\u001F",
      "\u0021-\u0039",
      "\u003B-\u00FF"
    ).!.map(_.head)

    def middle[_: P]: P[String] = (nonspecial ~~ (nonspecial | SingleChar(':')).repX).map {
      case (head, tail) => head +: tail.mkString
    }
    def trailing[_: P]: P[String] =
      (SingleChar(':') | SingleChar('|') | nonspecial).repX.map(_.mkString)
    def space[_: P]: P[Unit] = P("\u0020")
    def crlf[_: P]: P[Unit]  = P("\u000D\u000A")
  }
}

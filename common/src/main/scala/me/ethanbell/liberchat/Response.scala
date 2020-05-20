package me.ethanbell.liberchat

import java.util.Date
import IRCString._

sealed trait Response extends CommandLike {

  /**
   * It's a strange artefact of the way IRC is written that response codes are syntactic Commands.
   */
  def code: Int
  override def name: String = String.format("%03d", code)
}
case object Response {
  import Message.LexError._

  /**
   * Try to construct a [[Response]] given a code and a sequence of arguments
   * @note we use the minimum number of semantic arguments as a minimum -- in particular, the trailing "description" parameters are not considered required.
   * @param code
   * @param args
   * @return
   */
  def parse(code: Int, args: Seq[String]): Either[Message.LexError, Response] =
    (code, args.toList) match {
      case (1, welcomeStr :: _) => Right(RPL_WELCOME(welcomeStr))
      case (1, Nil)             => Right(RPL_WELCOME("")) // we'll assume an empty string is valid
      case (2, hostStr :: _)    => Right(RPL_YOURHOST(hostStr))
      case (2, Nil)             => Right(RPL_YOURHOST("")) // we'll again assume an empty string is valid
      case (3, createdStr :: _) => Right(RPL_CREATED(createdStr))
      case (3, Nil)             => Right(RPL_CREATED("")) // we'll yet again assume an empty string is valid
      case (4, server :: version :: userModes :: chanModes :: _) =>
        Right(RPL_MYINFO(server, version, userModes.toSet, chanModes.toSet))
      case (5, _)                  => Left(TooFewResponseParams(code, args, 4))
      case (403, channelName :: _) => Right(ERR_NOSUCHCHANNEL(channelName))
      case (403, Nil)              => Left(TooFewResponseParams(code, args, 1))
      case (421, commandName :: _) => Right(ERR_UNKNOWNCOMMAND(commandName))
      case (421, Nil)              => Left(TooFewResponseParams(code, args, 1))
      case (433, nick :: _)        => Right(ERR_NICKNAMEINUSE(nick.irc))
      case (433, Nil)              => Left(TooFewResponseParams(code, args, 1))
      case (451, _)                => Right(ERR_NOTREGISTERED)
      case (461, commandName :: _) => Right(ERR_NEEDMOREPARAMS(commandName))
      case (461, Nil)              => Left(TooFewResponseParams(code, args, 1))
      case _                       => Left(UnknownResponseCode(code, args))
    }

  case object RPL_WELCOME {
    def apply(nick: IRCString, username: String, hostname: String): RPL_WELCOME = RPL_WELCOME(
      s"Welcome to the LiberChat server $nick!$username@$hostname"
    )
  }
  case class RPL_WELCOME(welcomeStr: String) extends Response {
    val code = 1
    val args = Seq(welcomeStr)
  }
  case object RPL_YOURHOST {
    def apply(servername: String, versionStr: String): RPL_YOURHOST = RPL_YOURHOST(
      s"Your host is $servername, running version $versionStr"
    )
  }
  case class RPL_YOURHOST(hostStr: String) extends Response {
    val code = 2
    val args = Seq(hostStr)
  }
  case object RPL_CREATED {
    def apply(created: Date): RPL_CREATED = RPL_CREATED(
      s"This server was created ${created.toString}"
    )
  }
  case class RPL_CREATED(createdStr: String) extends Response {
    val code = 3
    val args = Seq(createdStr)
  }
  case class RPL_MYINFO(
    servername: String,
    versionStr: String,
    userModes: Set[Char],
    chanModes: Set[Char]
  ) extends Response {
    val code = 4
    val args = Seq(servername, versionStr, userModes.mkString, chanModes.mkString)
  }
  case class ERR_NOSUCHCHANNEL(channelName: String) extends Response {
    val code = 403
    val args = Seq(channelName, "No such channel")
  }
  case class ERR_UNKNOWNCOMMAND(commandName: String) extends Response {
    val code = 421
    val args = Seq(commandName, "Unknown command")
  }
  case class ERR_NICKNAMEINUSE(nick: IRCString) extends Response {
    val code = 433
    val args = Seq(nick.str, "Nickname is already in use")
  }
  case object ERR_NOTREGISTERED extends Response {
    val code = 451
    val args = Seq("You have not registered")
  }
  case class ERR_NEEDMOREPARAMS(commandName: String) extends Response {
    val code = 461
    val args = Seq(commandName, "Not enough parameters")
  }
}

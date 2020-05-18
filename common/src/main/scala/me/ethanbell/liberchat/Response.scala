package me.ethanbell.liberchat

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
      case (1, welcomeStr :: _)    => Right(RPL_WELCOME(welcomeStr))
      case (1, Nil)                => Right(RPL_WELCOME("")) // we'll assume an empty string is valid
      case (461, commandName :: _) => Right(ERR_NEEDMOREPARAMS(commandName))
      case (461, Nil)              => Left(TooFewResponseParams(code, args, 1))
      case (421, commandName :: _) => Right(ERR_UNKNOWNCOMMAND(commandName))
      case (421, Nil)              => Left(TooFewResponseParams(code, args, 1))
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
  case class ERR_NEEDMOREPARAMS(commandName: String) extends Response {
    val code = 461
    val args = Seq(commandName, "Not enough parameters")
  }
  case class ERR_UNKNOWNCOMMAND(commandName: String) extends Response {
    val code = 421
    val args = Seq(commandName, "Unknown command")
  }
}

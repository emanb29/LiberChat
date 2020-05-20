package me.ethanbell.liberchat

protected[liberchat] trait CommandLike {
  def name: String
  def args: Seq[String]

  def serialize: String =
    s"$name ${args.updated(args.length - 1, args.last.prepended(':')).mkString("", " ", "\r\n")}"

}

/**
 * The type of IRC commands, including their parameter values
 * @see
 */
sealed trait Command extends CommandLike

case object Command {
  import Message.LexError._
  def parse(commandName: String, args: Seq[String]): Either[Message.LexError, Command] =
    (commandName.toUpperCase(), args.toList) match {
      case ("NICK", nick :: hops :: _) if hops.toIntOption.isDefined =>
        Right(Nick(IRCString(nick), hops.toIntOption))
      case ("NICK", nick :: _) => Right(Nick(IRCString(nick), None))
      case ("NICK", Nil)       => Left(TooFewCommandParams(commandName, args, 1))
      case ("USER", username :: hostname :: servername :: realname :: _) =>
        Right(User(username, hostname, servername, realname))
      case ("USER", _)          => Left(TooFewCommandParams(commandName, args, 4))
      case ("JOIN", "0" :: Nil) => Right(LeaveAll)
      case ("JOIN", channels :: _ /*keys*/ :: _) =>
        val chans = channels.split(",")
        if (chans.forall(_.startsWith("#")))
          Right(JoinChannels(channels.split(",").toSeq))
        else
          Left(GenericResponseError(Response.ERR_NOSUCHCHANNEL(chans.find(!_.startsWith("#")).get)))
      case ("JOIN", _) => Left(TooFewCommandParams(commandName, args, 2))
      case ("SERVER", servername :: hopcount :: info :: _) if hopcount.toIntOption.isDefined =>
        Right(Server(servername, hopcount.toInt, info))
      case ("SERVER", _)                     => Left(TooFewCommandParams(commandName, args, 3))
      case ("OPER", user :: pass :: _)       => Right(Oper(user, pass))
      case ("OPER", _)                       => Left(TooFewCommandParams(commandName, args, 2))
      case ("QUIT", Nil)                     => Right(Quit(None))
      case ("QUIT", message :: _)            => Right(Quit(Some(message)))
      case ("SQUIT", server :: message :: _) => Right(SQuit(server, message))
      case ("SQUIT", _)                      => Left(TooFewCommandParams(commandName, args, 2))
      case _                                 => Left(UnknownCommand(commandName, args))
    }

  /**
   *
   * @see https://tools.ietf.org/html/rfc1459#section-4.1.2
   * @param nick
   * @param hopcount
   */
  case class Nick(nick: IRCString, hopcount: Option[Int]) extends Command {
    override def name: String = "NICK"

    override def args: Seq[String] = nick.str +: hopcount.map(_.toString).toList
  }

  /**
   *
   * @see https://tools.ietf.org/html/rfc1459#section-4.1.3
   * @param username
   * @param hostname
   * @param servername
   * @param realname
   */
  case class User(username: String, hostname: String, servername: String, realname: String)
      extends Command {
    override def name: String = "USER"

    override def args: Seq[String] = List(username, hostname, servername, realname)
  }

  sealed trait Join extends Command {
    override def name: String = "JOIN"
  }
  final case object LeaveAll extends Join {
    override def args: Seq[String] = Seq("0")
  }
  final case class JoinChannels(channels: Seq[String]) extends Join { // TODO add keys support
    override def args: Seq[String] = Seq(channels.mkString(","))
  }

  /**
   * @see https://tools.ietf.org/html/rfc1459#section-4.1.4
   * @param servername
   * @param hopcount
   * @param info
   */
  case class Server(servername: String, hopcount: Int, info: String) extends Command {
    override def name: String = "SERVER"

    override def args: Seq[String] = List(servername, hopcount.toString, info)
  }

  /**
   * @see https://tools.ietf.org/html/rfc1459#section-4.1.5
   * @param user
   * @param password
   */
  case class Oper(user: String, password: String) extends Command {
    override def name: String = "OPER"

    override def args: Seq[String] = List(user, password)
  }

  /**
   * @see https://tools.ietf.org/html/rfc1459#section-4.1.6
   * @param message
   */
  case class Quit(message: Option[String]) extends Command {
    override def name: String = "QUIT"

    override def args: Seq[String] = message.toList
  }

  /**
   * @see https://tools.ietf.org/html/rfc1459#section-4.1.7
   * @param server
   * @param comment
   */
  case class SQuit(server: String, comment: String) extends Command {
    override def name: String = "SQUIT"

    override def args: Seq[String] = List(server, comment)
  }
}

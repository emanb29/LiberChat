package me.ethanbell.liberchat

protected[liberchat] trait CommandLike {
  def name: String
  def args: Seq[String]

  def serialize: String =
    s"$name ${args.updated(args.length - 1, args.last.prepended(':')).mkString("", " ", "\r\n")}"

}

/**
 * The type of IRC commands, including their parameter values
 */
sealed trait Command extends CommandLike

case object Command {
  import Message.ParseError._
  def parse(commandName: String, args: Seq[String]): Either[Message.ParseError, Command] =
    (commandName, args.toList) match {
      case _ => Left(UnknownCommand(commandName, args))
    }
}

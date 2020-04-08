package me.ethanbell.liberchat

trait CommandLike {
  def name: String
}

/**
 * The type of IRC commands, including their parameter values
 */
sealed trait Command extends CommandLike {
  override def name: String
}

case object Command {}

package me.ethanbell.liberchat

trait UserMode {
  val flag: Char
}
object UserMode {
  case object Away extends UserMode {
    override val flag: Char = 'a'
  }
  case object Invisible extends UserMode {
    override val flag: Char = 'i'
  }
  case object Wallops extends UserMode {
    override val flag: Char = 'w'
  }
  case object Restricted extends UserMode {
    override val flag: Char = 'r'
  }
  case object Operator extends UserMode {
    override val flag: Char = 'o'
  }
  case object LocalOperator extends UserMode {
    override val flag: Char = 'O'
  }
  val Registry: Map[Char, UserMode] = Map(
    Away.flag          -> Away,
    Invisible.flag     -> Invisible,
    Wallops.flag       -> Wallops,
    Restricted.flag    -> Restricted,
    Operator.flag      -> Operator,
    LocalOperator.flag -> LocalOperator
  )
}

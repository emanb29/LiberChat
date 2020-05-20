package me.ethanbell.liberchat

object IRCString {
  implicit class IRCStringOps(str: String) {
    def irc: IRCString = IRCString(str)
  }
}

/**
 * An IRC-aware string w.r.t case sensitivity
 * @see https://tools.ietf.org/html/rfc2812#section-2.2
 * @param str
 */
case class IRCString(str: String) {
  def toUpper: IRCString = IRCString(str.map {
    case '{' => '['
    case '}' => ']'
    case '|' => '\\'
    case '^' => '~'
    case c   => c.toUpper
  })
  def toLower: IRCString = IRCString(str.map {
    case '['  => '{'
    case ']'  => '}'
    case '\\' => '|'
    case '~'  => '^'
    case c    => c.toLower
  })

  override def toString: String = str

  def caseInsensitiveCompare(other: IRCString): Boolean = other.toUpper.str == this.toUpper.str

  override def equals(obj: Any): Boolean =
    super.equals(obj) ||
      (obj.isInstanceOf[IRCString] && caseInsensitiveCompare(obj.asInstanceOf[IRCString]))

  override def hashCode(): Int = this.toUpper.str.hashCode

}

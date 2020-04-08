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
  def caseInsensitiveCompare(other: IRCString): Boolean = other.toUpper == this.toUpper
}

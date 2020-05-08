package me.ethanbell.liberchat

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class MessageTest extends AnyFunSuiteLike with Matchers {
  test("Response.serialize should be the inverse-ish of Message.parse") {
    val resp            = Response.ERR_UNKNOWNCOMMAND("notarealcommand")
    val parsed: Message = Message.parse(resp.serialize).get.value.get.getOrElse(???)
    parsed.commandLike shouldEqual resp
  }
}

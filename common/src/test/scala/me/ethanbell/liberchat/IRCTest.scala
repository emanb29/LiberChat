package me.ethanbell.liberchat

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import me.ethanbell.liberchat.Command.Nick
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import IRCString._

class IRCTest extends AnyFunSuite {
  implicit val system: ActorSystem = ActorSystem("irc-server-test")
  test("IRC.parseMessagesFlow should parse multiple messages") {
    val parsed = Await
      .result(
        Source
          .single("NICK :emanb29\r\nNICK ethan :2\r\n\r\n")
          .via(IRC.parseMessagesFlow)
          .runWith(Sink.seq),
        Duration(50, "sec")
      )
      .toList
    assert(parsed.length === 2)
    val firstEither :: secondEither :: Nil = parsed
    firstEither match {
      case Right(CommandMessage(_, Nick(nick, hopcount))) =>
        assert(nick.caseInsensitiveCompare("emanb29".irc))
        assert(hopcount === None)
      case _ =>
    }
    secondEither match {
      case Right(CommandMessage(_, Nick(nick, hopcount))) =>
        assert(nick.caseInsensitiveCompare("ethan".irc))
        assert(hopcount === Some(2))
      case _ =>
        fail(s"Expected a Right(CommandMessage(_, Nick)) but got $firstEither")
    }
  }
  test("IRC.parseMessagesFlow should gracefully fail to parse invalid messages") {
    val parsed = Await
      .result(
        Source
          .single(":NICK :emanb29\r\n\r\n")
          .via(IRC.parseMessagesFlow)
          .runWith(Sink.seq),
        Duration(2, "sec")
      )
      .toList
    assert(parsed.length === 1)
    val firstEither :: secondEither :: Nil = parsed
    firstEither match {
      case Right(CommandMessage(_, Nick(nick, hopcount))) =>
        assert(nick.caseInsensitiveCompare("emanb29".irc))
        assert(hopcount === None)
      case _ =>
    }
    secondEither match {
      case Right(CommandMessage(_, Nick(nick, hopcount))) =>
        assert(nick.caseInsensitiveCompare("ethan".irc))
        assert(hopcount === Some(2))
      case _ =>
        fail(s"Expected a Right(CommandMessage(_, Nick)) but got $firstEither")
    }
  }
  test("IRC.parseMessagesFlow should recombine multiple messages") {
    val testMessages = List("NICK :emanb", "29\r", "\nNICK ethan :2\r\n", "\r\n")
    val parsed = Await
      .result(
        Source
          .fromIterator(() => testMessages.iterator)
          .via(IRC.parseMessagesFlow)
          .runWith(Sink.seq),
        Duration(50, "sec")
      )
      .toList
    assert(parsed.length === 2)
    val firstEither :: secondEither :: Nil = parsed
    firstEither match {
      case Right(CommandMessage(_, Nick(nick, hopcount))) =>
        assert(nick.caseInsensitiveCompare("emanb29".irc))
        assert(hopcount === None)
      case _ =>
    }
    secondEither match {
      case Right(CommandMessage(_, Nick(nick, hopcount))) =>
        assert(nick.caseInsensitiveCompare("ethan".irc))
        assert(hopcount === Some(2))
      case _ =>
        fail(s"Expected a Right(CommandMessage(_, Nick)) but got $firstEither")
    }
  }

}

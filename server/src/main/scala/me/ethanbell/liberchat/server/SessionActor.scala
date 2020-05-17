package me.ethanbell.liberchat.server

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.stream.scaladsl.SourceQueueWithComplete
import javax.management.monitor.StringMonitor
import me.ethanbell.liberchat.Command.Nick
import me.ethanbell.liberchat.{CommandMessage, IRCString, Response, Command => IRCCommand}
import me.ethanbell.liberchat.IRCString._
import me.ethanbell.liberchat.server.SessionActor.{HandleIRCMessage, TryReserveNickCallback}

object SessionActor {
  sealed trait Command
  case object NoOp                                extends Command
  case object Shutdown                            extends Command
  case class HandleIRCMessage(cm: CommandMessage) extends Command

  /**
   * The result of attempting to reserve a nick
   * @param nick The nick we tried to reserve
   * @param success Whether it was successfully reserved.
   */
  case class TryReserveNickCallback(nick: IRCString, success: Boolean) extends Command

  def apply(responseQueue: SourceQueueWithComplete[Response]): Behavior[SessionActor.Command] =
    Behaviors.setup { ctx =>
      ctx.log.debug("Creating a new SessionActor")
      SessionActor(ctx, responseQueue)
    }
}

/**
 * A sessionactor is responsible for managing a user's session. This involves proxying requests to the rest of the
 * actors, handling session initialization protocol, and session teardown.
 * @param ctx
 * @param responseQueue
 */
case class SessionActor(
  ctx: ActorContext[SessionActor.Command],
  responseQueue: SourceQueueWithComplete[Response]
) extends AbstractBehavior[SessionActor.Command](ctx) {
  private type MessageHandler =
    PartialFunction[SessionActor.Command, Behavior[SessionActor.Command]]

  var currentMessageHandler: MessageHandler = onMessageDuringInit

  private lazy val onMessageMain: MessageHandler = {
    case HandleIRCMessage(cm) =>
      ctx.log.info(s"In main phase, got IRC message $cm")
      this
  }

  /**
   * Behavior during initialization.
   * This behavior never directly stops the actor -- behavior changes are handled by setting
   * currentMessageHandler (therefore the concluding "this")
   */
  private lazy val onMessageDuringInit: MessageHandler = {
    case class InitState(
      var nick: Option[IRCString] = None,
      var username: Option[String] = None,
      var hostname: Option[String] = None,
      var servername: Option[String] = None,
      var realname: Option[String] = None
    ) {
      def isComplete: Boolean =
        nick.isDefined && Vector(username, hostname, servername, realname).forall(_.isDefined)
    }
    def movetoMainPhase(): Unit = {
      ctx.log.debug(s"Connection initialization completed for actor ${ctx.self}")
      currentMessageHandler = onMessageMain
      // TODO send ready (101) response to client
      responseQueue.offer(???)
    }

    val initState: InitState = InitState()
    ({
      // If the message is relevant to auth, change the initialization state
      case relevantMessage @ (SessionActor.HandleIRCMessage(CommandMessage(_, _: IRCCommand.User)) |
          SessionActor.HandleIRCMessage(CommandMessage(_, _: IRCCommand.Nick))) =>
        relevantMessage.asInstanceOf[HandleIRCMessage].cm.command match {
          case IRCCommand.Nick(nick, _) =>
            // TODO attempt to reserve nick. For now, we just pretend every nick is free
            ctx.self.tell(TryReserveNickCallback(nick, success = true))
          case IRCCommand.User(username, hostname, servername, realname) =>
            initState.username = Some(username)
            initState.hostname = Some(hostname)
            initState.servername = Some(servername)
            initState.realname = Some(realname)
        }
        if (initState.isComplete) movetoMainPhase()
      // For the above behavior, if we got a Nick command, then asked the client registry if the nick was free
      case TryReserveNickCallback(nick, true) =>
        if (initState.nick.isDefined) {
          // TODO if there was a reserved nick, un-reserve it.
        }
        initState.nick = Some(nick)
        if (initState.isComplete) movetoMainPhase()
      case TryReserveNickCallback(nick, false) =>
        ctx.log.info(s"User at ${ctx.self} requested nick $nick, but that nick was taken")
        responseQueue.offer(???) // TODO error nick taken
      // Any other IRC message is invalid at this stage (PASS notwithstanding)
      case SessionActor.HandleIRCMessage(_) =>
        ctx.log.info("User sent non-init related message during init phase")
        responseQueue.offer(???) // TODO error session needs to be established
    }: PartialFunction[SessionActor.Command, Unit]).andThen(_ => this)
  }

  override def onMessage(msg: SessionActor.Command): Behavior[SessionActor.Command] =
    currentMessageHandler.applyOrElse[SessionActor.Command, Behavior[SessionActor.Command]](
      msg, { // use the current message handler, or fall back to global behaviors
        case SessionActor.Shutdown =>
          ctx.log.debug(s"Shutting down $this")
          Behaviors.stopped
        case SessionActor.NoOp => this
      }
    )
}

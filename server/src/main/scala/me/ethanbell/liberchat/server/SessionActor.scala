package me.ethanbell.liberchat.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.SourceQueueWithComplete
import me.ethanbell.liberchat.server.SessionActor.{HandleIRCMessage, ReserveNickCallback}
import me.ethanbell.liberchat.{CommandMessage, IRCString, Response, Command => IRCCommand}

object SessionActor {
  sealed trait Command
  final case object NoOp                                extends Command
  final case object Shutdown                            extends Command
  final case class HandleIRCMessage(cm: CommandMessage) extends Command

  /**
   * The result of attempting to reserve a nick
   * @param nick The nick we tried to reserve
   * @param success Whether it was successfully reserved.
   */
  final case class ReserveNickCallback(nick: IRCString, success: Boolean) extends Command

  def apply(
    responseQueue: SourceQueueWithComplete[Response],
    userRegistry: ActorRef[UserRegistry.Command]
  ): Behavior[SessionActor.Command] =
    Behaviors.setup { ctx =>
      ctx.log.debug("Creating a new SessionActor")
      SessionActor(ctx, responseQueue, userRegistry)
    }
}

/**
 * A sessionactor is responsible for managing a user's session. This involves proxying requests to the rest of the
 * actors, handling session initialization protocol, and session teardown.
 * @param ctx
 * @param responseQueue
 */
final case class SessionActor(
  ctx: ActorContext[SessionActor.Command],
  responseQueue: SourceQueueWithComplete[Response],
  userRegistry: ActorRef[UserRegistry.Command]
) extends AbstractBehavior[SessionActor.Command](ctx) {
  private type MessageHandler =
    PartialFunction[SessionActor.Command, Behavior[SessionActor.Command]]
  var currentMessageHandler: MessageHandler = onMessageDuringInit

  private lazy val onMessageMain: MessageHandler = {
    case HandleIRCMessage(cm) =>
      ctx.log.info(s"In main phase, got IRC message $cm")
      this
  }

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
  val initState: InitState = InitState()

  /**
   * Behavior during initialization.
   * This behavior never directly stops the actor -- behavior changes are handled by setting
   * currentMessageHandler (therefore the concluding "this")
   */
  private lazy val onMessageDuringInit: MessageHandler = {
    def movetoMainPhase(): Unit = {
      ctx.log.debug(s"Connection initialization completed for actor ${ctx.self}")
      currentMessageHandler = onMessageMain
      assert(initState.isComplete)
      responseQueue.offer(
        Response.RPL_WELCOME(initState.nick.get, initState.username.get, initState.hostname.get)
      )
    }

    ({
      case SessionActor.HandleIRCMessage(commandMsg) =>
        commandMsg.command match {
          case IRCCommand.Nick(nick, _) =>
            userRegistry.tell(UserRegistry.ReserveNick(nick, ctx.self))
          case IRCCommand.User(username, hostname, servername, realname) =>
            initState.username = Some(username)
            initState.hostname = Some(hostname)
            initState.servername = Some(servername)
            initState.realname = Some(realname)
          // Any other IRC message is invalid at this stage (PASS notwithstanding)
          case _ =>
            ctx.log.info("User sent non-init related message during init phase")
            responseQueue.offer(Response.ERR_NOTREGISTERED)
        }
        // If the message might be relevant to auth, change the initialization state
        if (initState.isComplete) movetoMainPhase()
      // For the above behavior, if we got a Nick command, then asked the client registry if the nick was free
      case ReserveNickCallback(nick, true) =>
        initState.nick.foreach { oldNick =>
          userRegistry.tell(UserRegistry.FreeNick(oldNick))
        }
        initState.nick = Some(nick)
        if (initState.isComplete) movetoMainPhase()
      case ReserveNickCallback(nick, false) =>
        ctx.log.info(s"User at ${ctx.self} requested nick $nick, but that nick was taken")
        responseQueue.offer(Response.ERR_NICKNAMEINUSE(nick))
    }: PartialFunction[SessionActor.Command, Unit]).andThen(_ => this)
  }

  override def onMessage(msg: SessionActor.Command): Behavior[SessionActor.Command] =
    currentMessageHandler.applyOrElse[SessionActor.Command, Behavior[SessionActor.Command]](
      msg, { // use the current message handler, or fall back to global behaviors
        case SessionActor.Shutdown =>
          ctx.log.debug(s"Shutting down $this")
          // Free external allocations
          initState.nick.foreach(nick => userRegistry.tell(UserRegistry.FreeNick(nick)))
          Behaviors.stopped
        case SessionActor.NoOp => this
        case c @ _ =>
          ctx.log.error(s"Unexpected unhandled command message $c at ${this}")
          Behaviors.unhandled
      }
    )
}

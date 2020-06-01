package me.ethanbell.liberchat.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.SourceQueueWithComplete
import me.ethanbell.liberchat.Command.PrivMsg
import me.ethanbell.liberchat.server.Client.{
  HandleIRCMessage,
  NotifyJoin,
  NotifyMessage,
  ReserveNickCallback
}
import me.ethanbell.liberchat.{
  CommandMessage,
  IRCString,
  Message,
  Response,
  ResponseMessage,
  Command => IRCCommand
}

object Client {
  sealed trait Command
  final case object NoOp                                extends Command
  final case object Shutdown                            extends Command
  final case class HandleIRCMessage(cm: CommandMessage) extends Command

  /**
   * Notify the client of a new join in a channel they are in. This also serves as a callback to joining a channel
   * @param newUserPrefix The prefix cooresponding to the user who joined
   * @param channel The channel the new user joined
   */
  final case class NotifyJoin(newUserPrefix: Prefix, channel: IRCString) extends Command

  /**
   * Notify the client of a new message
   * @param sourcePrefix The prefix cooresponding to the user who wrote the message
   * @param msgTarget The channel or private message channel on which the message was sent. If this is the user's own nick, this was a private message
   * @param msg The message itself
   */
  final case class NotifyMessage(sourcePrefix: Prefix, msgTarget: IRCString, msg: String)
      extends Command

  final case class Prefix(nick: IRCString, username: String, hostname: String) {
    override def toString: String = s"$nick!$username@$hostname"
  }

  /**
   * The result of attempting to reserve a nick
   * @param nick The nick we tried to reserve
   * @param success Whether it was successfully reserved.
   */
  final case class ReserveNickCallback(nick: IRCString, success: Boolean) extends Command

  final case class Passthru(message: Message) extends Command

  def apply(
    responseQueue: SourceQueueWithComplete[Message],
    userRegistry: ActorRef[UserRegistry.Command]
  ): Behavior[Client.Command] =
    Behaviors.setup { ctx =>
      ctx.log.debug("Creating a new SessionActor")
      Client(ctx, responseQueue, userRegistry)
    }

}

/**
 * A sessionactor is responsible for managing a user's session. This involves proxying requests to the rest of the
 * actors, handling session initialization protocol, and session teardown.
 * @param ctx
 * @param responseQueue
 */
final case class Client(
  ctx: ActorContext[Client.Command],
  responseQueue: SourceQueueWithComplete[Message],
  userRegistry: ActorRef[UserRegistry.Command]
) extends AbstractBehavior[Client.Command](ctx) {
  private type MessageHandler =
    PartialFunction[Client.Command, Behavior[Client.Command]]
  var currentMessageHandler: MessageHandler = onMessageDuringInit

  /**
   * During this phase, [[initState.isComplete]] _must_ return true
   */
  private lazy val onMessageMain: MessageHandler = {
    val prefix: Client.Prefix =
      Client.Prefix(initState.nick.get, initState.username.get, initState.hostname.get)

    ({
      case HandleIRCMessage(CommandMessage(_, IRCCommand.PrivMsg(target, msg)))
          if (!target.str.startsWith("#")) =>
        userRegistry.tell(UserRegistry.SendMessage(prefix, target, ctx.self, msg))
        this
      case HandleIRCMessage(CommandMessage(_, IRCCommand.PrivMsg(target, msg)))
          if (target.str.startsWith("#")) =>
        ??? // TODO message channel, if joined
        this
      case HandleIRCMessage(CommandMessage(_, IRCCommand.JoinChannels(channels))) =>
        ??? // TODO join the room
        this
      case NotifyJoin(newUserPrefix, channel) =>
        responseQueue.offer(
          CommandMessage(Some(newUserPrefix.toString), IRCCommand.JoinChannels(Vector(channel)))
        )
        this
      case NotifyMessage(sourcePrefix, msgTarget, msg) =>
        responseQueue.offer(
          CommandMessage(Some(sourcePrefix.toString), PrivMsg(msgTarget, msg))
        )
        this
    })
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
        ResponseMessage(
          None,
          Response.RPL_WELCOME(initState.nick.get, initState.username.get, initState.hostname.get)
        )
      )
    }

    ({
      case Client.HandleIRCMessage(commandMsg) =>
        commandMsg.command match {
          case IRCCommand.Nick(nick, _) =>
            userRegistry.tell(UserRegistry.ReserveNick(nick, ctx.self))
          case IRCCommand.User(username, hostname, servername, realname) =>
            initState.username = Some(username)
            initState.hostname = Some(hostname)
            initState.servername = Some(servername)
            initState.realname = Some(realname)
          // Any other IRC message is invalid at this stage (PASS notwithstanding)
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
        responseQueue.offer(
          ResponseMessage(
            None,
            Response.ERR_NICKNAMEINUSE(nick)
          )
        )
    }: PartialFunction[Client.Command, Unit]).andThen(_ => this)
  }

  override def onMessage(msg: Client.Command): Behavior[Client.Command] =
    currentMessageHandler.applyOrElse[Client.Command, Behavior[Client.Command]](
      msg, { // use the current message handler, or fall back to global behaviors
        case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.Quit(message))) =>
          // TODO do I need to do something with message? I forget
          Behaviors.stopped
        case Client.Passthru(message) =>
          responseQueue.offer(message)
          this
        case Client.Shutdown =>
          ctx.log.debug(s"Shutting down $this")
          // Free external allocations
          initState.nick.foreach(nick => userRegistry.tell(UserRegistry.FreeNick(nick)))
          Behaviors.stopped
        case Client.NoOp => this
        case c @ _ =>
          ctx.log.error(s"Received unhandled command message $c at ${this}")
          Behaviors.unhandled
      }
    )
}

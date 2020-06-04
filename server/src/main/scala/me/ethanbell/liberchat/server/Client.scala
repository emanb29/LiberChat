package me.ethanbell.liberchat.server

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.scaladsl.SourceQueueWithComplete
import me.ethanbell.liberchat.AkkaUtil.ActorCompanion
import me.ethanbell.liberchat.Command.PrivMsg
import me.ethanbell.liberchat.Response.ERR_NOTONCHANNEL
import me.ethanbell.liberchat.{
  CommandMessage,
  IRCString,
  Message,
  Response,
  ResponseMessage,
  Command => IRCCommand
}

import scala.collection.mutable

object Client extends ActorCompanion {
  sealed trait Command

  /**
   * Do nothing
   */
  final case object NoOp extends Command

  /**
   * Shutdown this client and clean it up from the system (ie, remove it from all connected channels, etc)
   */
  final case object Shutdown extends Command

  /**
   * Ask this client to handle an IRC command
   * @param cm the commandmessage to handle
   */
  final case class HandleIRCMessage(cm: CommandMessage) extends Command

  /**
   * Notify the client of a new join in a channel they are in. This also serves as a callback to joining a channel
   * @param newUserPrefix The prefix cooresponding to the user who joined
   * @param channel The channel the new user joined
   * @param channelRef The handle of the channel
   */
  final case class NotifyJoin(
    newUserPrefix: Prefix,
    channel: IRCString,
    channelRef: ActorRef[Channel.Command]
  ) extends Command

  final case class NotifyPart(leavingPrefix: Prefix, channel: IRCString, reason: Option[String])
      extends Command

  /**
   * Notify the client of a new message
   * @param sourcePrefix The prefix cooresponding to the user who wrote the message
   * @param msgTarget The channel or private message channel on which the message was sent. If this is the user's own nick, this was a private message
   * @param msg The message itself
   */
  final case class NotifyMessage(sourcePrefix: Prefix, msgTarget: IRCString, msg: String)
      extends Command

  /**
   * The result of attempting to reserve a nick
   * @param nick The nick we tried to reserve
   * @param success Whether it was successfully reserved.
   */
  final case class ReserveNickCallback(nick: IRCString, success: Boolean) extends Command

  final case class SendChannelList(channels: Seq[(IRCString, Int, String)]) extends Command

  final case class NotifyNames(channel: IRCString, names: Seq[IRCString]) extends Command

  /**
   * Request this client directly return an IRCMessage to the user without further modification
   * @param message
   */
  final case class Passthru(message: Message) extends Command

  final case class Prefix(nick: IRCString, username: String, hostname: String) {
    override def toString: String = s"$nick!$username@$hostname"
  }

  def apply(
    responseQueue: SourceQueueWithComplete[Message],
    userRegistry: ActorRef[UserRegistry.Command],
    channelRegistry: ActorRef[ChannelRegistry.Command]
  ): Behavior[Client.Command] =
    Behaviors.setup { ctx =>
      ctx.log.debug("Creating a new SessionActor")
      Client(ctx, responseQueue, userRegistry, channelRegistry)
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
  userRegistry: ActorRef[UserRegistry.Command],
  channelRegistry: ActorRef[ChannelRegistry.Command]
) extends AbstractBehavior[Client.Command](ctx) {
  private type MessageHandler =
    PartialFunction[Client.Command, Behavior[Client.Command]]
  var currentMessageHandler: MessageHandler                                = onMessageDuringInit
  val connectedChannels: mutable.Map[IRCString, ActorRef[Channel.Command]] = mutable.Map.empty
  def prefix: Client.Prefix =
    if (!initState.isComplete)
      throw new RuntimeException("Tried to get the prefix of an uninitialized client.")
    else Client.Prefix(initState.nick.get, initState.username.get, initState.hostname.get)

  /**
   * During this phase, [[initState.isComplete]] _must_ return true
   */
  private lazy val onMessageMain: MessageHandler = {
    case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.PrivMsg(target, msg)))
        if (!target.str.startsWith("#")) =>
      userRegistry.tell(UserRegistry.SendMessage(prefix, target, ctx.self, msg))
      this
    case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.PrivMsg(target, msg)))
        if (target.str.startsWith("#")) =>
      if (connectedChannels.contains(target)) {
        connectedChannels(target).tell(Channel.SendMessage(prefix, msg))
      } else {
        responseQueue.offer(ResponseMessage(None, Response.ERR_CANNOTSENDTOCHAN(target)))
      }
      this
    case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.LeaveAll)) =>
      connectedChannels.foreach {
        case (_, chan) => chan.tell(Channel.Part(prefix, None))
      }
      connectedChannels.clear()
      this
    case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.JoinChannels(channels))) =>
      channels.foreach { chan =>
        channelRegistry.tell(ChannelRegistry.JoinOrCreate(chan, prefix, ctx.self))
      }
      this
    case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.Part(channels, reason))) =>
      val unconnectedChannels = channels.filterNot(connectedChannels.contains)
      if (unconnectedChannels.nonEmpty) // if a channel listed is not connected
        {
          responseQueue.offer(ResponseMessage(None, ERR_NOTONCHANNEL(unconnectedChannels.head)))
        } else {
        channels.flatMap(connectedChannels.remove).foreach { channel =>
          channel.tell(Channel.Part(prefix, reason))
        }
      }
      this
    case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.Names(channels))) =>
      channelRegistry.tell(ChannelRegistry.ListNames(channels, ctx.self))
      this
    case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.ListChannels(channels))) =>
      channelRegistry.tell(ChannelRegistry.GetChannelList(channels, ctx.self))
      this
    case Client.NotifyJoin(newUserPrefix, channelName, channelRef) =>
      if (newUserPrefix == prefix) {
        connectedChannels += (channelName -> channelRef)
      }
      responseQueue.offer(
        CommandMessage(Some(newUserPrefix.toString), IRCCommand.JoinChannels(Vector(channelName)))
      )
      this
    case Client.NotifyPart(leavingPrefix, channel, reason) =>
      if (leavingPrefix == prefix) {
        connectedChannels -= channel
      }
      responseQueue.offer(
        CommandMessage(Some(leavingPrefix.toString), IRCCommand.Part(channel, reason))
      )
      this
    case Client.NotifyMessage(sourcePrefix, msgTarget, msg) =>
      responseQueue.offer(
        CommandMessage(Some(sourcePrefix.toString), PrivMsg(msgTarget, msg))
      )
      this
    case Client.SendChannelList(channels) =>
      val responses: Seq[Response] = channels.map {
          case (name, users, topic) => Response.RPL_LIST(name, users, topic)
        } :+ Response.RPL_LISTEND
      responses.foreach(r => responseQueue.offer(ResponseMessage(None, r)))
      this
    case Client.NotifyNames(channel, names) =>
      names.grouped(5).foreach { fiveNames =>
        responseQueue.offer(ResponseMessage(None, Response.PublicChannelNames(channel, fiveNames)))
      }
      responseQueue.offer(ResponseMessage(None, Response.RPL_ENDOFNAMES(channel)))
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
        ResponseMessage(
          None,
          Response.RPL_WELCOME(initState.nick.get, initState.username.get, initState.hostname.get)
        )
      )
    }

    ({
      case Client.HandleIRCMessage(CommandMessage(_, IRCCommand.Nick(nick, _))) =>
        userRegistry.tell(UserRegistry.ReserveNick(nick, ctx.self))
      case Client.HandleIRCMessage(
          CommandMessage(_, IRCCommand.User(username, hostname, servername, realname))
          ) =>
        initState.username = Some(username)
        initState.hostname = Some(hostname)
        initState.servername = Some(servername)
        initState.realname = Some(realname)
        if (initState.isComplete) movetoMainPhase()
      // For the above behavior, if we got a Nick command, then asked the client registry if the nick was free
      case Client.ReserveNickCallback(nick, true) =>
        initState.nick.foreach { oldNick =>
          userRegistry.tell(UserRegistry.FreeNick(oldNick))
        }
        initState.nick = Some(nick)
        if (initState.isComplete) movetoMainPhase()
      case Client.ReserveNickCallback(nick, false) =>
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
          // TODO could ack with an ERROR
          responseQueue.offer(
            CommandMessage(None, IRCCommand.Quit(Some("Disconnecting at client's request")))
          )
          doCleanup()
          Behaviors.stopped
        case Client.Passthru(message) =>
          responseQueue.offer(message)
          this
        case Client.Shutdown =>
          ctx.log.debug(s"Shutting down $this")
          doCleanup()
          Behaviors.stopped
        case Client.NoOp => this
        case c @ _ =>
          ctx.log.error(s"Received unhandled command message $c at ${this}")
          Behaviors.unhandled
      }
    )

  private def doCleanup(): Unit = {
    // Free external allocations
    initState.nick.foreach(nick => userRegistry.tell(UserRegistry.FreeNick(nick)))
    connectedChannels.foreach {
      case (_, channelRef) => channelRef.tell(Channel.Part(prefix, None))
    }
  }

}

# 1. Abstract

The LiberChat protocol is a near-subset of the text-based [Internet Relay Chat Client protocol (RFC 2812)](https://tools.ietf.org/html/rfc2812) for text-based conferencing and private messaging. This document describes the messages and expected behavior of agents handling those messages.

# 2. Basic Information

## 2.1. Overall functionality

The high-level functionality this specification documents is the ability for multiple people to communicate with one another. These communications MAY take place directly between two people, or MAY take place in a many-to-many context (a "channel", see below). The parties to these communications ("users", see below) MAY identify themselves by a "nickname", and users MAY communicate to multiple users and/or multiple channels simultaneously. Channels are identified by name, similar to users' nicknames.

This protocol MAY be implemented on any system which supports streaming character data between multiple hosts, such as TCP. Furthermore, the protocol is designed for ease of implementation and near-compatibility with IRC. In some cases, espescially command parameters, functionality is scaffolded, but intentionally unimplemented. These functions are not necessary for the basic operations of LiberChat, but would need to be implemented in IRC.

## 2.2. Definitions

<!--
<dl>
<dt>
</dt>
<dd>
</dd>
-->
<dt>
User
</dt>
<dd>
An entity communicating via the LiberChat protocol.
</dd>
<dt>
Client
</dt>
<dd>
A software converting a user's messages and other input to LiberChat protocol messages.
</dd>
<dt>
Server
</dt>
<dd>
A single program which listens to and handles messages from multiple clients.
</dd>
<dl>
Case-insensitive
<dt>
</dt>
<dd>
For the purposes of this document, case-insensitivity has special semantics. `[` is the uppercase variant of `{`, `]` is considered to be the uppercase variant of `}`, `\` is considered to be the uppercase variant of `|`, and `~` is considered to be the uppercase variant of `^`. These variants hold with respect to case-\[in\]sensitivity.
</dd>
<dt>
Nickname
</dt>
<dd>
A case-insenitive identifier provided by the client that uniquely identifies the client on a given server.
</dd>
<dt>
Channel
</dt>
<dd>
A case-insensitive name and set of clients on a server to which messages MAY be broadcast.
</dd>
</dl>

# 3. Messages

## 3.1. Structure

Messages consist of 3 parts: an optional prefix, a message identifier (either a command name or a response code), and parameters. The last parameter MAY be a "trailing parameter" which starts with a ":" and MAY include spaces. Empty messages MUST be silently ignored. Malformed messages, delimited by valid crlf sequences, MAY be ignored.

Messages adhere to the following CFG (in rough ABNF notation):

```abnf
message      = [command | response] crlf
command      = [":" userprefix " "] commandname params
commandname  = 1*letter
response     = [":" serverprefix " "] responsecode params
responsecode = 3digit

params   = " " (": " trailing / middle / middle params)
middle   = 1*linesafe
trailing = 1*linesafe *(linesafe / " ")

userprefix = nick "!" username "@" hostname
nick       = 1*nicksafe
username   = *usersafe
hostname   = *prefixsafe

crlf       = %x0D %x0A

linesafe    = usersafe / nicksafe ; (all but NUL CR LF : SPACE)
usersafe    = prefixsafe / %x33 ; ! is safe in general
nicksafe    = prefixsafe / %x40 ; @ is safe in general
prefixsafe  = %x01-09 / %x0B-0C / %x0E-1F / %x21-32 / %x34-39 / %x3B-3F / %x41-FF ; any octet except %x00, %x0A, %x0D %x20, %x30, %x40 (characters NUL CR LF : @ ! SPACE)

letter = %x41-5A / %x61-7A
digit  = %x30-39

```

## 3.2. Commands

Commands MAY be issued by the client or the server, and generally communicate client-supplied information. Any command sent to a server MAY yield `ERR_UNKNOWNCOMMAND` or `ERR_NEEDMOREPARAMS`. Note that commands will not always receive a response. Any command sent during the Connection Initialization phase MAY yield `ERR_NOTREGISTERED` Servers MAY ignore unrecognized commands.

### 3.2.1. NICK

The NICK command is issued by a client to request a nickname.

This command SHOULD only be issued from a client to a server.

Arguments: `NICK <nickname> [hops]`

Where nickname is a case-insensitive string, and hops is an integer (currently unused)

Possible responses: `ERR_NICKNAMEINUSE`, `RPL_WELCOME`

### 3.2.2. USER

The USER command is issued by a client to specify the user's information

This command SHOULD only be issued from a client to a server.

Arguments: `USER <username> <hostname> <servername> <realname>`

Where username is a string specifying the user's username (or "\*"), hostname is a string specifying the name of the user's host machine (or "\*"), servername is a string specifying the name of the server to connect to (or "\*"), and realname is a string specifying the real name of the user

Possible responses: `RPL_WELCOME`

### 3.2.3. JOIN

The JOIN command is issued by a client or a server to notify the recipient of a client's intent to join channel\[s\], or (if the parameter is `0`, its intent to leave all channels).

This command MAY be issued by a client or a server:

- If issued by a client, that client is the one which will be joining the specified channel\[s\]
- If issued by a server, exactly one channel MUST be specified, and the message MUST contain a prefix cooresponding to the user joining the channel.
- The `0` variant of this command SHOULD only be issued by a client.

Arguments: `JOIN <channels>`

Where channels is one of the following:

- A comma-delimited string specifying the (case-insensitive) channel names to join or create, each starting with a `#`.
- The character `0`.

Possible responses: `ERR_NOSUCHCHANNEL`

### 3.2.4. PART

The PART command is issued by a client or a server to notify the recipient of a client's intent to leave channel\[s\].

This command MAY be issued by a client or a server:

- If issued by a client, that client is the one which will be leaving the specified channel\[s\].
- If issued by a server, exactly one channel MUST be specified, and the message MUST contain a prefix cooresponding to the user leaving the channel.

Arguments: `PART <channels> [message]`

Where channels is a comma-delimited string specifying the (case-insensitive) channel names to leave, each starting with a `#`, and message is an (optional) reason for leaving the channels.

Possible responses: `ERR_NOTONCHANNEL`, `ERR_NOSUCHCHANNEL`

### 3.2.5. LIST

The LIST command is issued by a client to request a list of channel metadata

This command SHOULD only be issued by a client

Arguments: `LIST [<channels> [target]]`

Where channels is an optional comma-delimited string specifying the (case-insensitive) channel names to describe. If no valid channel names are provided, the server will describe all channels. target is unused.

Possible responses: `RPL_LIST`, `RPL_LISTEND`

### 3.2.6. NAMES

The NAMES command is issued by a client to request a list of users connected to channel\[s\]

This command SHOULD only be issued by a client

Arguments: `NAMES [<channels> [target]]`

Where channels is an optional comma-delimited string specifying the (case-insensitive) channel names to describe. If no valid channel names are provided, the server will describe all channels. target is unused.

Possible responses: `RPL_NAMREPLY`, `RPL_ENDOFNAMES`

### 3.2.7. QUIT

The QUIT command is issued by a client or a server to notify the recipient that the sender is disconnecting.

This command MAY be issued by a client or a server

Arguments: `QUIT [message]`

Where message is an optional final message, which MAY describe why the sender disconnected.

Possible responses: None

### 3.2.8. PRIVMSG

The PRIVMSG command is issued by a client or a server to notify the recipient of a client's intent to send a text message. If sending to a channel, the channel name MUST begin with `#`, and the user MUST be connected to that channel.

This command MAY be issued by a client or a server:

- If issued by a client, that client is the one which will be sending the text message.
- If issued by a server, the message MUST contain a prefix cooresponding to the user sending the text message.

Arguments: `PRIVMSG <target> <message>`

Where target is a case-insensitive nickname or channel name, and message is a string containing the text message to be relayed to target.

Possible responses: `ERR_CANNOTSENDTOCHAN`, `ERR_NOSUCHNICK`

## 3.3. Responses

Responses MUST only be issued by the server, and generally communicate server-supplied information or control information. Clients MUST ignore unrecognzied responses.

# 4. Protocol

## 4.1. Connection Initialization

To initiate a connection, a client MUST send a NICK command and a USER command. These MAY be sent in any order. Until both NICK and USER have been received, the client SHOULD NOT send any other commands. When the server has received both a NICK command reserving a valid, unused nickname, and a USER command, the server MUST send a RPL_WELCOME and both parties MUST transition to the Main Protocol phase.

During the Connection Initialization phase, the server MUST reply to any commands except NICK, USER, and QUIT with ERR_NOTREGISTERED.

## 4.2. Main Protocol

Any commands MAY be sent by the client. The server MUST respond to any unknown commands with ERR_UNKNOWNCOMMAND. The server MUST respond to any commands missing arguments with ERR_NEEDMOREPARAMS. The server MUST handle any known commands to the best of its ability.

## 4.3. Connection Teardown

Either the client or the server MAY terminate a connection at any point by sending a QUIT command. The recipient SHOULD respond with a QUIT command of its own. If a connection to a client terminates unexpectedly, without a QUIT command, the server application MUST NOT crash. If a connection to a server terminates unexpectedly, without a QUIT command, the client application SHOULD NOT crash.

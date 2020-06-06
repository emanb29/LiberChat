# 1. Abstract

The LiberChat protocol is a near-subset of the text-based [Internet Relay Chat Client protocol (RFC 2812)](https://tools.ietf.org/html/rfc2812) for text-based conferencing and private messaging. This document describes the messages and expected behavior of agents handling those messages.

# 2. Basic Information

## 2.1. Overall functionality

The high-level functionality this specification documents is the ability for multiple people to communicate with one another. These communications may take place directly between two people, or may take place in a many-to-many context (a "channel", see below). The parties to these communications ("users", see below) may identify themselves by a "nickname", and users may communicate to multiple users and/or multiple channels simultaneously. Channels are identified by name, similar to users' nicknames. Furthermore, the protocol is designed for ease of implementation and near-compatibility with IRC.

This protocol may be implemented on any system which supports streaming character data between multiple hosts, such as TCP.

## 2.1. Definitions

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
A case-insensitive name and set of clients on a server to which messages may be broadcast.
</dd>
</dl>

# 3. Messages

## 3.1. Structure

Messages consist of 3 parts: an optional prefix, a message identifier (either a command name or a response code), and parameters. The last parameter may be a "trailing parameter" which starts with a ":" and may include spaces.

Messages adhere to the following CFG (in rough ABNF notation):

```abnf
message      = (command | response) crlf
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

Commands may be issued by the client or the server, and generally communicate client-supplied information.

### 3.2.1. NICK
TODO

## 3.3. Responses

Responses may only be issued by the server, and generally communicate server-supplied information or control information.
/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.persistence

import java.util.{ List ⇒ JList }

import akka.actor.ActorRef

/**
 * Persistent message.
 */
sealed abstract class Persistent {
  /**
   * This persistent message's payload.
   */
  //#payload
  def payload: Any
  //#payload

  /**
   * This persistent message's sequence number.
   */
  //#sequence-nr
  def sequenceNr: Long
  //#sequence-nr

  /**
   * Creates a new persistent message with the specified `payload`.
   */
  def withPayload(payload: Any): Persistent

  /**
   * Called by [[Channel]] destinations to confirm the receipt of a persistent message.
   */
  def confirm(): Unit
}

object Persistent {
  /**
   * Java API.
   *
   * Creates a new persistent message. Must only be used outside processors.
   *
   * @param payload payload of new persistent message.
   */
  def create(payload: Any): Persistent =
    create(payload, null)

  /**
   * Java API.
   *
   * Creates a new persistent message, derived from the specified current message. The current
   * message can be obtained inside a [[Processor]] by calling `getCurrentPersistentMessage()`.
   *
   * @param payload payload of new persistent message.
   * @param currentPersistentMessage current persistent message.
   */
  def create(payload: Any, currentPersistentMessage: Persistent): Persistent =
    apply(payload)(Option(currentPersistentMessage))

  /**
   * Creates a new persistent message, derived from an implicit current message.
   * When used inside a [[Processor]], this is the optional current [[Persistent]]
   * message of that processor.
   *
   * @param payload payload of the new persistent message.
   * @param currentPersistentMessage optional current persistent message, defaults to `None`.
   */
  def apply(payload: Any)(implicit currentPersistentMessage: Option[Persistent] = None): Persistent =
    currentPersistentMessage.map(_.withPayload(payload)).getOrElse(PersistentImpl(payload))

  /**
   * Persistent message extractor.
   */
  def unapply(persistent: Persistent): Option[(Any, Long)] =
    Some((persistent.payload, persistent.sequenceNr))
}

/**
 * Plugin API.
 *
 * Internal [[Persistent]] message representation.
 *
 * @param resolved `true` by default, `false` for replayed messages. Set to `true` by a channel if this
 *                message is replayed and its sender reference was resolved. Channels use this field to
 *                avoid redundant sender reference resolutions.
 * @param processorId Id of processor that journaled the message.
 * @param channelId Id of last channel that delivered the message to a destination.
 * @param sender Serialized sender reference.
 * @param deleted `true` if this message is marked as deleted.
 * @param confirms Channel ids of delivery confirmations that are available for this message. Only non-empty
 *                 for replayed messages.
 * @param confirmTarget Delivery confirmation target.
 * @param confirmMessage Delivery confirmation message.
 *
 * @see [[Processor]]
 * @see [[Channel]]
 * @see [[Deliver]]
 */
case class PersistentImpl(
  payload: Any,
  sequenceNr: Long = 0L,
  resolved: Boolean = true,
  processorId: String = "",
  channelId: String = "",
  sender: String = "",
  deleted: Boolean = false,
  confirms: Seq[String] = Nil,
  confirmTarget: ActorRef = null,
  confirmMessage: Confirm = null) extends Persistent {

  def withPayload(payload: Any): Persistent =
    copy(payload = payload)

  def confirm(): Unit =
    if (confirmTarget != null) confirmTarget ! confirmMessage

  import scala.collection.JavaConverters._

  /**
   * Java Plugin API.
   */
  def getConfirms: JList[String] = confirms.asJava
}

object PersistentImpl {
  /**
   * Java Plugin API.
   */
  def create(payload: Any, sequenceNr: Long, resolved: Boolean, processorId: String, channelId: String, sender: String, deleted: Boolean, confirms: Seq[String]): PersistentImpl =
    PersistentImpl(payload, sequenceNr, resolved, processorId, channelId, sender, deleted, confirms)

  /**
   * Java Plugin API.
   */
  def create(payload: Any, sequenceNr: Long, resolved: Boolean, processorId: String, channelId: String, sender: String, deleted: Boolean, confirms: Seq[String], confirmTarget: ActorRef, confirmMessage: Confirm): PersistentImpl =
    PersistentImpl(payload, sequenceNr, resolved, processorId, channelId, sender, deleted, confirms, confirmTarget, confirmMessage)
}

/**
 * Receive by a processor when a journal failed to write a [[Persistent]] message.
 *
 * @param payload payload of the persistent message.
 * @param sequenceNr sequence number of the persistent message.
 * @param cause failure cause.
 */
case class PersistenceFailure(payload: Any, sequenceNr: Long, cause: Throwable)

/**
 * Message to confirm the receipt of a persistent message (sent via a [[Channel]]).
 */
@SerialVersionUID(1L)
private[persistence] case class Confirm(processorId: String, sequenceNr: Long, channelId: String)

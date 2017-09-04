package org.wallet.transfer

import akka.actor.{ActorRef, Props}
import akka.persistence.PersistentActor
import org.wallet.transfer.TransferRegisterActor.{RegisterTransaction, ShardConfig, TransactionCompleted, WakeUp}


class TransferRegisterActor() extends PersistentActor {
  var state: Set[String] = Set()

  override def persistenceId: String = "transferRegisterActor"

  override def receiveRecover: Receive = {
    case RegisterTransaction(transactionId: String) =>
      state = state + transactionId

    case TransactionCompleted(transactionId: String) =>
      state = state - transactionId
  }

  override def receiveCommand: Receive = {
    case e@ShardConfig(transferShard) =>
      state.foreach { id => transferShard ! WakeUp(id) }

    case c@RegisterTransaction(transactionId: String) =>
      persist(c) { c =>
        state = state + c.transferId
      }
    case c@TransactionCompleted(transactionId: String) =>
      persist(c) { c =>
        state = state - c.transferId
      }
  }
}

object TransferRegisterActor {
  def props() = Props(classOf[TransferRegisterActor])

  case class RegisterTransaction(transferId: String)

  case class TransactionCompleted(transferId: String)

  case class ShardConfig(transferShard: ActorRef)

  case class WakeUp(transferId: String)

}

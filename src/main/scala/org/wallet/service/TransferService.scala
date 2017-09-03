package org.wallet.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.wallet.service.TransferService._
import org.wallet.transfer.TransferActor
import org.wallet.transfer.TransferActor._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class TransferService(transferShard: ActorRef) {
  implicit val timeout = Timeout(5 seconds)


  def transfer(donorId: String, recipientId: String, amount: Double): Future[TransferResult] = {
    val transferId = uuid()
    validateTransferArgs(transferId, donorId, recipientId, amount).
      map(Future(_)).
      getOrElse {
        (transferShard ? SyncTransfer(uuid(), donorId, recipientId, amount)).mapTo[TransferActor.Event].
          map {
            case t: TransferActor.Transferred =>
              TransferService.Transferred(t.transferId, t.donorId, t.recipientId, t.amount, t.donorBalance)

            case t: TransferActor.InsufficientFunds =>
              TransferService.InsufficientFunds(t.transferId, t.donorId, t.recipientId, t.amount, t.donorBalance)
          }
      }
  }

  def asyncTransfer(donorId: String, recipientId: String, amount: Double): TransferResult = {
    val transferId = uuid()
    validateTransferArgs(transferId, donorId, recipientId, amount).getOrElse {
      transferShard ! AsyncTransfer(transferId, donorId, recipientId, amount)
      Accepted(transferId, donorId, recipientId, amount)
    }
  }

  private def uuid() = java.util.UUID.randomUUID.toString

  private def validateTransferArgs(transferId: String, donorId: String, recipientId: String, amount: Double): Option[Incorrect] = {
    if (amount <= 0) Some(Incorrect(transferId, donorId, recipientId, amount, msg = POSITIVE_AMOUNT))
    else {
      if (donorId == recipientId) Some(Incorrect(transferId, donorId, recipientId, amount, msg = SAME_IDS_ERROR))
      else None
    }
  }

  def transferState(transactionId: String): Future[TransferActor.State] =
    (transferShard ? GetState(transactionId)).mapTo[TransferActor.State]
}

object TransferService {

  val SAME_IDS_ERROR = "donor and recipient id's must be different"
  val POSITIVE_AMOUNT = "amount must be positive value"

  sealed trait TransferResult {
    val transactionId: String
    val donorId: String
    val recipientId: String
    val amount: Double
  }

  case class Transferred(transactionId: String, donorId: String, recipientId: String, amount: Double, donorBalance: Double) extends TransferResult

  case class InsufficientFunds(transactionId: String, donorId: String, recipientId: String, amount: Double, donorBalance: Double) extends TransferResult

  case class Accepted(transactionId: String, donorId: String, recipientId: String, amount: Double) extends TransferResult

  case class Incorrect(transactionId: String, donorId: String, recipientId: String, amount: Double, msg: String) extends TransferResult

}
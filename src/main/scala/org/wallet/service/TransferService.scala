package org.wallet.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.wallet.service.TransferService.{Accepted, Incorrect, TransferResult}
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
            case t:TransferActor.Transferred =>
              TransferService.Transfered(t.transferId, t.donorId, t.recipientId, t.amount, t.donorBalance)

            case t:TransferActor.InsufficientFunds =>
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


  def transferState(transactionId: String): Future[TransferActor.State] =
    (transferShard ? GetState(transactionId)).mapTo[TransferActor.State]

  private def uuid()

  = java.util.UUID.randomUUID.toString

  private def validateTransferArgs(transferId: String, donorId: String, recipientId: String, amount: Double): Option[Incorrect]

  = {
    if (amount <= 0) Some(Incorrect(transferId, donorId, recipientId, amount, msg = "amount must be positive value"))
    else {
      if (donorId == recipientId) Some(Incorrect(transferId, donorId, recipientId, amount, msg = "donor and recipient id's must be different"))
      else None
    }
  }
}

object TransferService {

  sealed trait TransferResult {
    val transactionId: String
    val donorId: String
    val recipientId: String
    val amount: Double
  }

  case class Transfered(transactionId: String, donorId: String, recipientId: String, amount: Double, donorBalance: Double) extends TransferResult

  case class InsufficientFunds(transactionId: String, donorId: String, recipientId: String, amount: Double, donorBalance: Double) extends TransferResult

  case class Accepted(transactionId: String, donorId: String, recipientId: String, amount: Double) extends TransferResult

  case class Incorrect(transactionId: String, donorId: String, recipientId: String, amount: Double, msg: String) extends TransferResult

}
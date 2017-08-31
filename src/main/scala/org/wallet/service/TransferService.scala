package org.wallet.service

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.wallet.transfer.TransferActor
import org.wallet.transfer.TransferActor.{GetState, SyncTransfer}

import scala.concurrent.Future
import scala.concurrent.duration._


class TransferService(transferShard: ActorRef) {
  implicit val timeout = Timeout(5 seconds)


  def transfer(transferId:String, donorId: String, recipientId: String, amount: Double): Future[TransferActor.Event] =
    (transferShard ? SyncTransfer(transferId, donorId, recipientId, amount)).mapTo[TransferActor.Event]

  //todo add async transfer

  def transferState(transactionId: String): Future[TransferActor.State] =
    (transferShard ? GetState(transactionId)).mapTo[TransferActor.State]

}
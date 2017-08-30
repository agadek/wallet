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


  def transfer(donorId: String, recipientId: String, amount: Double): Future[TransferActor.Event] =
    (transferShard ? SyncTransfer(donorId, recipientId, amount)).mapTo[TransferActor.Event]


  def transferState(transactionId: String): Future[TransferActor.State] =
    (transferShard ? GetState(transactionId)).mapTo[TransferActor.State]

}
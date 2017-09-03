package org.wallet

import akka.actor.{ActorRef, ActorSystem}
import akka.routing.ConsistentHashingPool
import akka.routing.ConsistentHashingRouter.ConsistentHashMapping
import org.wallet.account.{Account, AccountActor}
import org.wallet.service.{AccountService, TransferService}
import org.wallet.transfer.TransferActor


trait WalletTestInfra {

  implicit val system:ActorSystem
  val hashingPollSize:Int

  val accountsShard: ActorRef =
    system.actorOf(ConsistentHashingPool(100, hashMapping = accountsHashMapping).
      props(AccountActor.props()), name = "accounts")
  val accountsService = new AccountService(accountsShard)
  val transferShard: ActorRef =
    system.actorOf(ConsistentHashingPool(100, hashMapping = transferHashMapping).
      props(TransferActor.props(accountsService.deposit, accountsService.withdraw)), name = "transfers")
  val transferService = new TransferService(transferShard)

  def accountsHashMapping: ConsistentHashMapping = {
    case command: Account.Command => command.id
  }

  def transferHashMapping: ConsistentHashMapping = {
    case transfer: TransferActor.Transfer => transfer.transferId
    case msg: TransferActor.GetState => msg.transferId
  }

}

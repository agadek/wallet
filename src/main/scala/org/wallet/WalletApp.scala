package org.wallet

import akka.actor.ActorSystem
import org.wallet.account.AccountActor
import org.wallet.service.{AccountService, TransferService}
import org.wallet.transfer.TransferActor

class WalletApp extends App {
  implicit val system: ActorSystem = ActorSystem("wallet-system")

  val accountsShard = AccountActor.shard(system)

  val accountsService = new AccountService(accountsShard)

  val transferShard = TransferActor.shard(system, accountsService.deposit, accountsService.withdraw)

  val transferService = new TransferService(transferShard)


}

package org.wallet

import akka.actor.ActorSystem
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import org.wallet.account.{Account, AccountActor}
import org.wallet.service.AccountService

class WalletApp extends App {
  implicit val system: ActorSystem = ActorSystem("wallet-system")


  val numberOfShards = 100

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case e => ???
      }

  val extractShardId: ShardRegion.ExtractShardId = {
    case _ => ???
  }


  val accountsShard = ClusterSharding(system).start(
    typeName = "account",
    entityProps = AccountActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)


  val accountsService = new AccountService(accountsShard)


}

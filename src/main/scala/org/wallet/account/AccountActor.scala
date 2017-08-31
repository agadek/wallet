package org.wallet.account

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}


class AccountActor() extends Actor with ActorLogging {

  val id: String = self.path.name


  override def receive: Receive = normal(Account(id, 0d))

  def normal(state: Account): Receive = {
    case command: Account.Command =>
      log.info("account {} got command {}", id, command)
      val event = state.process(command)
      val newState = state.apply(event)
      context.become(normal(newState))
      log.info("account {} published response {}", id, event)
      sender() ! event
  }
}

object AccountActor{
  def props() = Props(new AccountActor())

  private val numberOfShards = 10

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case command:Account.Command => (command.id , command)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case command:Account.Command => scala.math.abs(command.id.hashCode % numberOfShards).toString
  }

  def shard(system:ActorSystem): ActorRef = ClusterSharding(system).start(
    typeName = "account",
    entityProps = AccountActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)


}

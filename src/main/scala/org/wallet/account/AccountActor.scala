package org.wallet.account

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.persistence.PersistentActor


class AccountActor() extends PersistentActor with ActorLogging {

  var state: Account = Account(persistenceId, 0d)
  var idempotency: Map[String, Account.Event] = Map.empty

  override def receiveRecover: Receive = {
    case event: Account.Event => updateState(event)
  }

  def updateState(event: Account.Event): Unit = {
    if (!idempotency.contains(event.commandId)) {
      state = state.apply(event)
      idempotency += (event.commandId -> event)
    }
  }

  override def receiveCommand: Receive = {
    case command: Account.Command if idempotency.contains(command.commandId) =>
      log.info("account {} got command {}, already found in journal", persistenceId, command)
      idempotency.get(command.commandId).foreach { msg =>
        sender() ! msg
      }

    case command: Account.Command =>
      log.info("account {} got command {}", persistenceId, command)
      val event = state.process(command)
      persist(event) { event =>
        updateState(event)
        log.info("account {} published response {}", persistenceId, event)
        sender() ! event
      }
  }

  override def persistenceId: String = self.path.name
}

object AccountActor {
  private val numberOfShards = 10
  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case command: Account.Command => (command.accountId, command)
  }
  private val extractShardId: ShardRegion.ExtractShardId = {
    case command: Account.Command => scala.math.abs(command.accountId.hashCode % numberOfShards).toString
  }

  def shard(system: ActorSystem): ActorRef = ClusterSharding(system).start(
    typeName = "account",
    entityProps = AccountActor.props(),
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  def props() = Props(new AccountActor())


}

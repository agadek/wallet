package org.wallet.transfer


import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.pipe
import akka.persistence.PersistentActor
import org.wallet.service.AccountService
import org.wallet.transfer.TransferActor._
import org.wallet.transfer.TransferRegisterActor.{RegisterTransaction, TransactionCompleted, WakeUp}

import scala.concurrent.Future


//todo rewrite to fsm
class TransferActor(deposit: Deposit,
                    withdraw: Withdraw,
                    transferRegister:ActorRef) extends PersistentActor with ActorLogging {
  import context._

  override val persistenceId: String = self.path.name

  var command: Option[Transfer] = None
  var source: Option[ActorRef] = None

  override def receiveCommand: Receive = {
    case transfer: Transfer =>
      persist((transfer, sender())) { case (persistedTransfer, sender) =>

        log.info("transfer {} processing: {}", persistenceId, persistedTransfer)
        withdraw(transfer.transferId, persistedTransfer.donorId, persistedTransfer.amount) pipeTo self
        command = Some(persistedTransfer)

        persistedTransfer match {
          case _: SyncTransfer => source = Some(sender)
          case _ =>
        }

        transferRegister ! RegisterTransaction(persistenceId)
      }
      context become withdrawalState()

    case GetState(_) => sender() ! Uninitialized()
    case WakeUp(_) => log.info("WakeUp msg recived")
    case msg => unexpectedMsgHandling("uninitialized")(msg)
  }

  def withdrawalState(): Receive = {
    case AccountService.Success(amount, balance) =>
      log.info("transfer {} withdrawal succeeded", persistenceId)
      command.foreach { command =>
        deposit(command.transferId, command.recipientId, command.amount) pipeTo self
      }
      context become depositState(balance)

    case AccountService.Fail(amount, balance, msg) =>
      log.info("transfer {} withdrawal failed", persistenceId)
      context become failedState(balance, msg)
      for {
        source <- source
        command <- command
      } yield source ! InsufficientFunds(command.transferId, command.donorId, command.recipientId, command.amount, balance)
      transferRegister ! TransactionCompleted(persistenceId)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingWithdrawal(persistenceId, command.donorId, command.recipientId, command.amount)
      }
    case msg => unexpectedMsgHandling("withdrawal")(msg)
  }

  def depositState(donorBalance: Double): Receive = {
    case AccountService.Success(amount, balance) =>
      log.info("transfer {} deposit succeeded", persistenceId)
      context become successState(donorBalance)
      for {
        source <- source
        command <- command
      } yield source ! Transferred(command.transferId, command.donorId, command.recipientId, command.amount, donorBalance)
      transferRegister ! TransactionCompleted(persistenceId)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingDeposit(persistenceId, command.donorId, command.recipientId, command.amount)
      }
    case msg => unexpectedMsgHandling("deposit")(msg)
  }

  def successState(donorBalance: Double): Receive = {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! Succeeded(persistenceId, command.donorId, command.recipientId, command.amount)
      }
    case msg => unexpectedMsgHandling("success")(msg)
  }

  def failedState(donorBalance: Double, msg: String): Receive = {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! Failed(command.transferId, command.donorId, command.recipientId, command.amount, msg)
      }
    case other => unexpectedMsgHandling("failed")(other)
  }

  def unexpectedMsgHandling(stateName: String): Receive = {
    case transferCommand: Transfer => command match {
      case Some(command) =>
        if (command.transferId == transferCommand.transferId &&
          command.amount == transferCommand.amount &&
          command.donorId == transferCommand.donorId &&
          command.recipientId == transferCommand.recipientId)
          log.warning("duplicated transfer command received: {}", transferCommand)
        else
          log.error("state {}, got msg: {}", stateName, transferCommand)
        sender() ! CommandRejected(transferCommand.transferId, transferCommand.donorId, transferCommand.recipientId, transferCommand.amount)

      case None =>
        log.error("state inconsistency {}, got msg: {}", stateName, transferCommand)
    }

    case msg => log.error("state {}, got msg: {}", stateName, msg)
  }



  override def receiveRecover: Receive = {
    case (transfer: Transfer, sender: ActorRef) =>
      self.tell(transfer,sender)
  }
}


object TransferActor {
  type Deposit = (String, String, Double) => Future[AccountService.Response]
  type Withdraw = (String, String, Double) => Future[AccountService.Response]
  private val numberOfShards = 10

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case transfer: Transfer => (transfer.transferId, transfer)
    case msg: GetState => (msg.transferId, msg)
    case msg: WakeUp => (msg.transferId, msg)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case transfer: Transfer => scala.math.abs(transfer.transferId.hashCode % numberOfShards).toString
    case msg: GetState => scala.math.abs(msg.transferId.hashCode % numberOfShards).toString
    case WakeUp(transferId) => scala.math.abs(transferId.hashCode % numberOfShards).toString
  }

  def shard(system: ActorSystem,
            deposit: Deposit,
            withdraw: Withdraw,
            registrator:ActorRef): ActorRef = ClusterSharding(system).start(
    typeName = "transfer",
    entityProps = props(deposit, withdraw, registrator),
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  def props(deposit: Deposit,
            withdraw: Withdraw,
            transferRegister:ActorRef) = Props(new TransferActor(deposit, withdraw, transferRegister))

  sealed trait State

  sealed trait Command

  sealed trait Transfer {
    val transferId: String
    val donorId: String
    val recipientId: String
    val amount: Double
  }

  sealed trait Event

  case class Uninitialized() extends State

  case class ProcessingWithdrawal(transferId: String, donorId: String, recipientId: String, amount: Double) extends State

  case class ProcessingDeposit(transferId: String, donorId: String, recipientId: String, amount: Double) extends State

  case class Succeeded(transferId: String, donorId: String, recipientId: String, amount: Double) extends State

  case class Failed(transferId: String, donorId: String, recipientId: String, amount: Double, msg: String) extends State


  case class SyncTransfer(transferId: String, donorId: String, recipientId: String, amount: Double) extends Transfer

  case class AsyncTransfer(transferId: String, donorId: String, recipientId: String, amount: Double) extends Transfer

  case class GetState(transferId: String) extends Command

  case class Transferred(transferId: String, donorId: String, recipientId: String, amount: Double, donorBalance: Double) extends Event

  case class InsufficientFunds(transferId: String, donorId: String, recipientId: String, amount: Double, donorBalance: Double) extends Event

  case class CommandRejected(transferId: String, donorId: String, recipientId: String, amount: Double) extends Event

}

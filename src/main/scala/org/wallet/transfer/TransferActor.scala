package org.wallet.transfer


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.pipe
import org.wallet.service.AccountService
import org.wallet.transfer.TransferActor._

import scala.concurrent.Future


class TransferActor(deposit: Deposit,
                    withdraw: Withdraw) extends Actor with ActorLogging {

  val transferId: String = self.path.name

  import context._

  var command: Option[Transfer] = None
  var source: Option[ActorRef] = None

  override def receive: Receive = {
    case transfer: Transfer =>
      log.info("transfer {} processing: {}", transferId, transfer)
      withdraw(transfer.donorId, transfer.amount) pipeTo self
      command = Some(transfer)

      transfer match {
        case _: SyncTransfer => source = Some(sender())
        case _ =>
      }
      context become withdrawalState().orElse(unexpectedMsgHandling("init"))

    case GetState(_) => sender() ! Uninitialized()
    case msg => unexpectedMsgHandling("uninitialized")(msg)
  }

  def withdrawalState(): Receive = {
    case AccountService.Success(amount, balance) =>
      log.info("transfer {} withdrawal succeeded", transferId)
      command.foreach { command =>
        deposit(command.recipientId, command.amount) pipeTo self
      }
      context become depositState(balance)

    case AccountService.Fail(amount, balance, msg) =>
      log.info("transfer {} withdrawal failed", transferId)
      context become failedState(balance, msg)
      for {
        source <- source
        command <- command
      } yield source ! InsufficientFunds(command.transferId, command.donorId, command.recipientId, command.amount, balance)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingWithdrawal(transferId, command.donorId, command.recipientId, command.amount)
      }
    case msg => unexpectedMsgHandling("withdrawal")(msg)
  }

  def depositState(donorBalance: Double): Receive = {
    case AccountService.Success(amount, balance) =>
      log.info("transfer {} deposit succeeded", transferId)
      context become successState(donorBalance).orElse(unexpectedMsgHandling("success"))
      for {
        source <- source
        command <- command
      } yield source ! Transferred(command.transferId, command.donorId, command.recipientId, command.amount, donorBalance)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingDeposit(transferId, command.donorId, command.recipientId, command.amount)
      }
    case msg => unexpectedMsgHandling("deposit")(msg)
  }

  def successState(donorBalance: Double): Receive = {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! Succeeded(transferId, command.donorId, command.recipientId, command.amount)
      }
    case msg => unexpectedMsgHandling("success")(msg)
  }

  def failedState(donorBalance: Double, msg: String): Receive = {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! Failed(command.transferId, command.donorId, command.recipientId, command.amount, msg)
      }
    case msg => unexpectedMsgHandling("failed")(msg)
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
    }

    case msg => log.error("state {}, got msg: {}", stateName, msg)

  }
}


object TransferActor {
  type Deposit = (String, Double) => Future[AccountService.Response]
  type Withdraw = (String, Double) => Future[AccountService.Response]
  private val numberOfShards = 10

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case transfer: Transfer => (transfer.transferId, transfer)
    case msg: GetState => (msg.transferId, msg)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case transfer: Transfer => scala.math.abs(transfer.transferId.hashCode % numberOfShards).toString
    case msg: GetState => scala.math.abs(msg.transferId.hashCode % numberOfShards).toString

  }

  def shard(system: ActorSystem,
            deposit: Deposit,
            withdraw: Withdraw): ActorRef = ClusterSharding(system).start(
    typeName = "transfer",
    entityProps = props(deposit, withdraw),
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  def props(deposit: Deposit,
            withdraw: Withdraw) = Props(new TransferActor(deposit, withdraw))

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

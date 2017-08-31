package org.wallet.transfer


import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.pattern.pipe
import org.wallet.account.{Account, AccountActor}
import org.wallet.service.AccountService
import org.wallet.transfer.TransferActor._

import scala.concurrent.Future


class TransferActor(deposit:Deposit,
                    withdraw:Withdraw) extends Actor with ActorLogging {

  val transferId: String = self.path.name

  import context._

  var command: Option[Transfer] = None
  var source: Option[ActorRef] = None

  override def receive: Receive = {
    case transfer:Transfer =>
      log.info("transfer {} processing: {}", transferId, transfer)
      withdraw(transfer.donorId, transfer.amount) pipeTo self
      command = Some(transfer)

     transfer match {
       case _:SyncTransfer => source = Some(sender())
       case _ =>
     }
      context become withdrawalState()

    case GetState(_) => sender() ! Uninitialized()
  }

  def withdrawalState(): Receive = {
    case AccountService.Success(amount, balance) =>
      log.info("transfer {} withdrawal succeeded", transferId)
      command.foreach { command =>
       deposit(command.recipientId, command.amount) pipeTo self
      }
      context become depositState(balance)

    case AccountService.Fail(amount, balance) =>
      log.info("transfer {} withdrawal failed", transferId)
      context become failedState(balance)
      for {
        source <- source
        command <- command
      } yield source ! InsufficientFunds(command.donorId, command.recipientId, command.amount, balance)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingWithdrawal(command.donorId, command.recipientId, command.amount)
      }
  }

  def depositState(donorBalance:Double): Receive = {
    case AccountService.Success(amount, balance) =>
      log.info("transfer {} deposit succeeded", transferId)
      context become successState(donorBalance)
      for {
        source <- source
        command <- command
      } yield source ! Transferred(command.donorId, command.recipientId, command.amount, donorBalance)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingDeposit(command.donorId, command.recipientId, command.amount)
      }
  }

  def successState(donorBalance:Double): Receive = {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! Succeeded(command.donorId, command.recipientId, command.amount)
      }
  }

  def failedState(donorBalance:Double):Receive= {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! InsufficientFunds(command.donorId, command.recipientId, command.amount, donorBalance)
      }
  }
}


object TransferActor {
  type Deposit = (String,Double) => Future[AccountService.Response]
  type Withdraw = (String,Double) => Future[AccountService.Response]

    sealed trait State

  case class Uninitialized() extends State

  case class ProcessingWithdrawal(donorId: String, recipientId: String, amount: Double) extends State

  case class ProcessingDeposit(donorId: String, recipientId: String, amount: Double) extends State

  case class Succeeded(donorId: String, recipientId: String, amount: Double) extends State

  case class Failed(donorId: String, recipientId: String, amount: Double) extends State


  sealed trait Command
  sealed trait Transfer{
    val transferId: String
    val donorId: String
    val recipientId: String
    val amount: Double
  }

  case class SyncTransfer(transferId: String, donorId: String, recipientId: String, amount: Double) extends Transfer
  case class AsyncTransfer(transferId: String, donorId: String, recipientId: String, amount: Double) extends Transfer

  case class GetState(transferId: String) extends Command

  sealed trait Event

  case class Transferred(donorId: String, recipientId: String, amount: Double, donorBalance:Double)

  case class InsufficientFunds(donorId: String, recipientId: String, amount: Double, donorBalance:Double)

  def props(deposit:Deposit,
            withdraw:Withdraw) = Props(new TransferActor(deposit,withdraw))



  private val numberOfShards = 10

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case transfer:Transfer => (transfer.transferId , transfer)
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case transfer:Transfer=> scala.math.abs(transfer.transferId.hashCode % numberOfShards).toString
  }

  def shard(system:ActorSystem,
            deposit:Deposit,
            withdraw:Withdraw): ActorRef = ClusterSharding(system).start(
    typeName = "transfer",
    entityProps = props(deposit,withdraw),
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)
}

package org.wallet.transfer


import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import org.wallet.transfer.TransferActor._

import scala.concurrent.Future

class TransferActor(deposit:(String,Double) => Future[Double],
                    withdraw:(String,Double) => Future[Double]) extends Actor with ActorLogging {

  val transferId: String = self.path.name

  import context._

  var command: Option[Transfer] = None
  var source: Option[ActorRef] = None

  override def receive: Receive = {
    case transfer@SyncTransfer(donorId, _, amount) =>
      log.info("transfer {} processing: {}", transferId, transfer)
      withdraw(donorId, amount) pipeTo self
      command = Some(transfer)
      source = Some(sender())
      context become withdrawalState()

    case transfer@AsyncTransfer(donorId, _, amount) =>
      log.info("transfer {} processing: {}", transferId, transfer)
      withdraw(donorId, amount) pipeTo self
      command = Some(transfer)
      context become withdrawalState()

    case GetState(_) => sender() ! Uninitialized()
  }

  def withdrawalState(): Receive = {
    case amount:Double if amount > 0 =>
      log.info("transfer {} withdrawal succeeded", transferId)
      command.foreach { command =>
       deposit(command.recipientId, command.amount) pipeTo self
      }
      context become depositState()

    case amount:Double =>
      log.info("transfer {} withdrawal failed", transferId)
      context become failedState()
      for {
        source <- source
        command <- command
      } yield source ! InsufficientFunds(command.donorId, command.recipientId, command.amount)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingWithdrawal(command.donorId, command.recipientId, command.amount)
      }
  }

  def depositState(): Receive = {
    case amount:Double =>
      log.info("transfer {} deposit succeeded", transferId)
      context become successState()
      for {
        source <- source
        command <- command
      } yield source ! Transferred(command.donorId, command.recipientId, command.amount)

    case GetState(_) =>
      command.foreach { command =>
        sender() ! ProcessingDeposit(command.donorId, command.recipientId, command.amount)
      }
  }

  def successState(): Receive = {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! Succeeded(command.donorId, command.recipientId, command.amount)
      }
  }

  def failedState():Receive= {
    case GetState(_) =>
      command.foreach { command =>
        sender() ! InsufficientFunds(command.donorId, command.recipientId, command.amount)
      }
  }
}


object TransferActor {

  sealed trait State

  case class Uninitialized() extends State

  case class ProcessingWithdrawal(donorId: String, recipientId: String, amount: Double) extends State

  case class ProcessingDeposit(donorId: String, recipientId: String, amount: Double) extends State

  case class Succeeded(donorId: String, recipientId: String, amount: Double) extends State

  case class Failed(donorId: String, recipientId: String, amount: Double) extends State


  sealed trait Command
  sealed trait Transfer{
    val donorId: String
    val recipientId: String
    val amount: Double
  }

  case class SyncTransfer(donorId: String, recipientId: String, amount: Double) extends Transfer
  case class AsyncTransfer(donorId: String, recipientId: String, amount: Double) extends Transfer

  case class GetState(transferId: String) extends Command

  sealed trait Event

  case class Transferred(donorId: String, recipientId: String, amount: Double)

  case class InsufficientFunds(donorId: String, recipientId: String, amount: Double)

  def props(deposit:(String,Double) => Future[Double],
            withdraw:(String,Double) => Future[Double]) = Props(new TransferActor(deposit,withdraw))
}

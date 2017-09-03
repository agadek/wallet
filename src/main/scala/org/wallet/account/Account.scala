package org.wallet.account

import org.wallet.account.Account._

case class Account(id: String, balance: Double) {

  def process(command: Command): Event = {
    command match {
      case Deposit(cid, _, amount) => Deposited(cid, id, amount, balance+amount)
      case Withdraw(cid, _,amount) if checkBalance(amount) => Withdrawn(cid, id, amount, balance-amount)
      case Withdraw(cid, _,amount) => InsufficientFunds(cid, id, amount, balance)
      case GetBalance(cid, _) => CurrentBalance(cid, id, balance)
    }
  }

  def apply(event: Event): Account = event match {
    case Deposited(cid, _, amount,_) => copy(balance = balance + amount)
    case Withdrawn(cid, _, amount,_) => copy(balance = balance - amount)
    case CurrentBalance(cid, _, _) => this
    case InsufficientFunds(cid, _, _, _) => this
  }

  private def checkBalance(amount: Double) = balance >= amount
}

object Account {

  type AccountId = String
  type CommandId = String

  sealed trait Command{
    val commandId:CommandId
    val accountId:AccountId
  }

  case class Deposit(commandId:CommandId, accountId:AccountId, amount: Double) extends Command

  case class Withdraw(commandId:CommandId, accountId:AccountId, amount: Double) extends Command

  case class GetBalance(commandId:CommandId, accountId:AccountId) extends Command

  sealed trait Event{
    val commandId:CommandId
    val accountId:AccountId
  }

  case class Deposited(commandId:CommandId, accountId:AccountId, amount: Double, balanceAfter:Double) extends Event

  case class Withdrawn(commandId:CommandId, accountId:AccountId, amount: Double, balanceAfter:Double) extends Event

  case class CurrentBalance(commandId:CommandId, accountId:AccountId, balance: Double) extends Event

  case class InsufficientFunds(commandId:CommandId, accountId:AccountId, amount: Double, balance:Double) extends Event

}
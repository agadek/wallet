package org.wallet.account

import org.wallet.account.Account._

case class Account(id: String, balance: Double) {

  def process(command: Command): Event = {
    command match {
      case Deposit(_, amount) => Deposited(id, amount, balance+amount)
      case Withdraw(_,amount) if checkBalance(amount) => Withdrawn(id, amount, balance-amount)
      case Withdraw(_,amount) => InsufficientFunds(id, amount, balance)
      case GetBalance(_) => CurrentBalance(id, balance)
    }
  }

  def apply(event: Event): Account = event match {
    case Deposited(_, amount,_) => copy(balance = balance + amount)
    case Withdrawn(_, amount,_) => copy(balance = balance - amount)
    case CurrentBalance(_, _) => this
    case InsufficientFunds(_, _, _) => this
  }

  private def checkBalance(amount: Double) = balance >= amount
}

object Account {

  type AccountId = String

  sealed trait Command{
    val id:AccountId
  }

  case class Deposit(id:AccountId, amount: Double) extends Command

  case class Withdraw(id:AccountId, amount: Double) extends Command

  case class GetBalance(id:AccountId) extends Command

  sealed trait Event

  case class Deposited(id:AccountId, amount: Double, balanceAfter:Double) extends Event

  case class Withdrawn(id:AccountId, amount: Double, balanceAfter:Double) extends Event

  case class CurrentBalance(id:AccountId, balance: Double) extends Event

  case class InsufficientFunds(id:AccountId, amount: Double, balance:Double) extends Event

}
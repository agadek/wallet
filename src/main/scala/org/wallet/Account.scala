package org.wallet

import org.wallet.Account._


case class Account(id: String, balance: Double) {

  def process(command: Command): Event = {
    command match {
      case Deposit(amount) => Deposited(amount)
      case Withdraw(amount) if checkBalance(amount) => Withdrawn(amount)
      case Withdraw(amount) => InsufficientFunds()
      case GetBalance() => CurrentBalance(balance)
    }
  }

  def apply(event: Event): Account = event match {
    case Deposited(amount) => copy(balance = balance + amount)
    case Withdrawn(amount) => copy(balance = balance - amount)
    case CurrentBalance(_) => this
    case InsufficientFunds() => this
  }

  private def checkBalance(amount: Double) = balance >= amount
}

object Account {

  sealed trait Command

  case class Deposit(amount: Double) extends Command

  case class Withdraw(amount: Double) extends Command

  case class GetBalance() extends Command

  sealed trait Event

  case class Deposited(amount: Double) extends Event

  case class Withdrawn(amount: Double) extends Event

  case class CurrentBalance(balance: Double) extends Event

  case class InsufficientFunds() extends Event

}
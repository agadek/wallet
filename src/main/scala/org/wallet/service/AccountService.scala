package org.wallet.service

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.wallet.account.Account
import org.wallet.account.Account._
import org.wallet.service.AccountService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._


class AccountService(accountsShard: ActorRef) {
  implicit val timeout = Timeout(5 seconds)
  private def uuid() = java.util.UUID.randomUUID.toString

  def getBalance(id: String): Future[Response] =
    (accountsShard ? GetBalance(uuid(), id)).mapTo[Account.CurrentBalance]
      .map { a =>
        Success(0d, a.balance)
      }

  def deposit(id: String, amount: Double): Future[Response] = deposit(uuid(), id, amount)

  def deposit(requestId:String, id: String, amount: Double): Future[Response] =
    if (amount > 0) {
      (accountsShard ? Deposit(uuid(), id, amount)).mapTo[Account.Deposited]
        .map { a =>
          Success(a.amount, a.balanceAfter)
        }
    } else getBalance(id).map {
      currentBalance =>
        Fail(-amount, currentBalance.balance, DEPOSIT_POSITIVE_VALUE)
    }

  def withdraw(id: String, amount: Double): Future[Response] = withdraw(uuid(), id, amount)

  def withdraw(requestId:String, id: String, amount: Double): Future[Response] =
      if (amount > 0) {
      (accountsShard ? Withdraw(requestId, id, amount)).mapTo[Account.Event]
        .map {
          case Withdrawn(_, _, withdrawnAmount, balanceAfter) => Success(-withdrawnAmount, balanceAfter)
          case InsufficientFunds(_, _, change, balance) => Fail(-change, balance, INSUFFICIENT_FUNDS)
        }
    } else getBalance(id).map {
      currentBalance =>
        Fail(-amount, currentBalance.balance, WITHDRAWAL_POSITIVE_VALUE)
    }
}


object AccountService {

  val INSUFFICIENT_FUNDS = "Insufficient Funds"
  val DEPOSIT_POSITIVE_VALUE = "Deposit amount must be positive value"
  val WITHDRAWAL_POSITIVE_VALUE = "Withdrawal amount must be positive value"

  sealed trait Response {
    val change: Double
    val balance: Double
  }

  case class Success(change: Double, balance: Double) extends Response

  case class Fail(change: Double, balance: Double, msg: String) extends Response

}

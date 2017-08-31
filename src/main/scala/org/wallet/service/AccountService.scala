package org.wallet.service

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import org.wallet.account.Account
import org.wallet.account.Account._
import org.wallet.service.AccountService.{Fail, Response, Success}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._


class AccountService(accountsShard: ActorRef) {
  implicit val timeout = Timeout(5 seconds)

  def getBalance(id: String): Future[Response] =
    (accountsShard ? GetBalance(id)).mapTo[Account.CurrentBalance]
      .map { a =>
        Success(0d, a.balance)
      }

  def deposit(id: String, amount: Double): Future[Response] =
    (accountsShard ? Deposit(id, amount)).mapTo[Account.Deposited]
      .map { a =>
        Success(a.amount, a.balanceAfter)
      }

  def withdraw(id: String, amount: Double): Future[Response] = {
    (accountsShard ? Withdraw(id, amount)).mapTo[Account.Event]
      .map {
        case Withdrawn(_, withdrawnAmount, balanceAfter) => Success(-withdrawnAmount, balanceAfter)
        case InsufficientFunds(_, change, balance) => Fail(-change, balance)
      }
  }

}


object AccountService {

  sealed trait Response

  case class Success(change: Double, balance: Double) extends Response

  case class Fail(change: Double, balance: Double) extends Response

}

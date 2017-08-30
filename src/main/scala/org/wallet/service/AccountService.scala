package org.wallet.service

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import org.wallet.account.Account
import org.wallet.account.Account.{Deposit, GetBalance, Withdraw, Withdrawn}
import org.wallet.account.Account.InsufficientFunds
import org.wallet.service.ServiceResponse.{Fail, Response, Success}

import scala.concurrent.ExecutionContext.Implicits.global


class AccountService(accountsShard: ActorRef) {
  implicit val timeout = Timeout(5 seconds)

  def getBalance(id: String): Future[Response] =
    (accountsShard ? GetBalance()).mapTo[Account.CurrentBalance]
      .map { a =>
        Success(a.balance)
      }

  def deposit(id: String, amount: Double): Future[Response] =
    (accountsShard ? Deposit(amount)).mapTo[Account.Deposited]
      .map { a =>
        Success(a.amount)
      }

  def withdraw(id: String, amount: Double): Future[Response] = {
    (accountsShard ? Withdraw(amount)).mapTo[Account.Event]
      .map {
        case Withdrawn(withdrawnAmount) => Success(withdrawnAmount)
        case InsufficientFunds() => Fail(0d)
      }
  }

}



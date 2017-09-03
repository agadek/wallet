package org.wallet.service

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.account.AccountActor
import org.wallet.service.AccountService.{Fail, Success}

import scala.concurrent.Await
import scala.concurrent.duration._


class AccountServiceSpec extends TestKit(ActorSystem("AccountServiceSpec")) with FunSpecLike with Matchers with ImplicitSender {

  implicit val timeout = Timeout(1 seconds)

  describe("Account Service should ") {
    it("serve account requests") {
      //given
      val id = "testId"
      val account = system.actorOf(AccountActor.props(), id)
      val service = new AccountService(account)
      val depositAmount = 100d
      val withdrawAmount = 10d

      //when
      val Success(0d, zeroBalance) = Await.result(service.getBalance(id), timeout.duration) //0d
      val Success(depositedAmount, afterDepositBalance) = Await.result(service.deposit(id, depositAmount), timeout.duration) //+100d
      val Success(withdrawnAmount,  afterWithdrawalBalance) = Await.result(service.withdraw(id, withdrawAmount), timeout.duration)//-10d
      val Fail(notWithdrawnAmount, afterRefusedWithdrawalBalance, _) = Await.result(service.withdraw(id, depositAmount), timeout.duration)
      val Success(0d, finalBalance) = Await.result(service.getBalance(id), timeout.duration)


      //expected
      zeroBalance shouldBe 0d
      depositedAmount shouldBe depositAmount
      afterDepositBalance shouldBe depositAmount

      withdrawnAmount shouldBe -withdrawAmount
      afterWithdrawalBalance shouldBe depositAmount-withdrawAmount

      notWithdrawnAmount shouldBe -depositAmount
      afterRefusedWithdrawalBalance shouldBe depositAmount-withdrawAmount

      finalBalance shouldBe depositAmount-withdrawAmount
    }
  }
}

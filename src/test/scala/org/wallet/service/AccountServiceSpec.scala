package org.wallet.service

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.account.AccountActor
import org.wallet.service.ServiceResponse.{Fail, Success}

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
      val Success(zeroBalance) = Await.result(service.getBalance(id), timeout.duration)
      val Success(afterDepositBalance) = Await.result(service.deposit(id, depositAmount), timeout.duration)
      val Success(withdawnAmount) = Await.result(service.withdraw(id, withdrawAmount), timeout.duration)
      val Fail(refusedWithdrawal) = Await.result(service.withdraw(id, depositAmount), timeout.duration)
      val Success(finnalBalance) = Await.result(service.getBalance(id), timeout.duration)


      //expected
      zeroBalance shouldBe 0d
      afterDepositBalance shouldBe depositAmount
      withdawnAmount shouldBe withdrawAmount
      refusedWithdrawal shouldBe 0d
      finnalBalance shouldBe depositAmount-withdrawAmount
    }
  }
}

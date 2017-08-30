package org.wallet.account

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.account.Account.{CurrentBalance, _}


class AccountActorSpec extends TestKit(ActorSystem("AccountActorSpec")) with FunSpecLike with Matchers with ImplicitSender{

  describe("AccountActor should process given commands") {
    it("add balance and respond") {
      //given
      val amount = 100d
      val account = system.actorOf(AccountActor.props())
      val probe = TestProbe()
      //when
      val command = Deposit(amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(Deposited(amount))
      probe.send(account, GetBalance())
      probe.expectMsg(CurrentBalance(amount))
    }

    it("withdraw balance and respond") {
      //given
      val amount = 100d
      val probe = TestProbe()
      val account = system.actorOf(AccountActor.props())
      account ! Deposit(amount)
      probe.send(account, GetBalance())
      probe.expectMsg(CurrentBalance(amount))

      //when
      val command = Withdraw(amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(Withdrawn(amount))
      probe.send(account, GetBalance())
      probe.expectMsg(CurrentBalance(0d))
    }

    it("refuse withdraw below 0d") {
      //given
      val amount = 100d
      val probe = TestProbe()
      val account = system.actorOf(AccountActor.props())
      account ! Deposit(amount-1d)
      probe.send(account, GetBalance())
      probe.expectMsg(CurrentBalance(amount-1d))

      //when
      val command = Withdraw(amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(InsufficientFunds())
      probe.send(account, GetBalance())
      probe.expectMsg(CurrentBalance(amount-1d))
    }
  }
}
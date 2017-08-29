package org.wallet

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{FunSpec, FunSpecLike, Matchers}
import org.wallet.Account.{CurrentBalance, _}


class AccountActorSpec extends TestKit(ActorSystem("AccountActorSpec")) with FunSpecLike with Matchers with ImplicitSender{

  describe("AccountActor should process given commands") {
    it("generate event and add balance") {
      //given
      val amount = 100d
      val id = "testId"
      val account = system.actorOf(AccountActor.props(id))
      val probe = TestProbe()
      //when
      val command = Deposit(amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(Deposited(amount))
      probe.send(account, GetBalance())
      probe.expectMsg(CurrentBalance(amount))
    }

    it("generate event and withdraw balance") {
      //given
      val amount = 100d
      val id = "testId"
      val probe = TestProbe()
      val account = system.actorOf(AccountActor.props(id))
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

    it("generate event and refuse withdraw below 0d") {
      //given
      val amount = 100d
      val id = "testId"
      val probe = TestProbe()
      val account = system.actorOf(AccountActor.props(id))
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
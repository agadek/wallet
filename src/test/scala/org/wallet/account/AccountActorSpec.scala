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
      val id = "accTest1"
      val account = system.actorOf(AccountActor.props(), id)
      val probe = TestProbe()
      //when
      val command = Deposit(id, amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(Deposited(id, amount,amount))
      probe.send(account, GetBalance(id))
      probe.expectMsg(CurrentBalance(id, amount))
    }

    it("withdraw balance and respond") {
      //given
      val amount = 100d
      val probe = TestProbe()
      val id = "accTest2"
      val account = system.actorOf(AccountActor.props(), id)
      account ! Deposit(id, amount)
      probe.send(account, GetBalance(id))
      probe.expectMsg(CurrentBalance(id, amount))

      //when
      val command = Withdraw(id, amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(Withdrawn(id, amount,0d))
      probe.send(account, GetBalance(id))
      probe.expectMsg(CurrentBalance(id, 0d))
    }

    it("refuse withdraw below 0d") {
      //given
      val amount = 100d
      val probe = TestProbe()
      val id = "accTest3"
      val account = system.actorOf(AccountActor.props(), id)
      account ! Deposit(id, amount-1d)
      probe.send(account, GetBalance(id))
      probe.expectMsg(CurrentBalance(id, amount-1d))

      //when
      val command = Withdraw(id, amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(InsufficientFunds(id, amount,amount-1d))
      probe.send(account, GetBalance(id))
      probe.expectMsg(CurrentBalance(id, amount-1d))
    }
  }
}
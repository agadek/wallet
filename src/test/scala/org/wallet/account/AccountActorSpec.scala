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
      val commandId = "command1"
      val commandId2 = "command2"
      //when

      probe.send(account, Deposit(commandId, id, amount))
      probe.send(account, GetBalance(commandId2, id))

      //expected
      probe.expectMsg(Deposited(commandId, id, amount,amount))
      probe.expectMsg(CurrentBalance(commandId2, id, amount))
    }

    it("withdraw balance and respond") {
      //given
      val amount = 100d
      val probe = TestProbe()
      val id = "accTest2"
      val account = system.actorOf(AccountActor.props(), id)
      val commandId = "command1"
      val commandId2 = "command2"
      val commandId3 = "command3"
      val commandId4 = "command4"
      account ! Deposit(commandId, id, amount)
      probe.send(account, GetBalance(commandId2, id))
      probe.expectMsg(CurrentBalance(commandId2, id, amount))

      //when
      val command = Withdraw(commandId3,id, amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(Withdrawn(commandId3, id, amount,0d))
      probe.send(account, GetBalance(commandId4, id))
      probe.expectMsg(CurrentBalance(commandId4, id, 0d))
    }

    it("refuse withdraw below 0d") {
      //given
      val amount = 100d
      val probe = TestProbe()
      val id = "accTest3"
      val account = system.actorOf(AccountActor.props(), id)
      val commandId = "command1"
      val commandId2 = "command2"
      val commandId3 = "command3"
      val commandId4 = "command4"
      account ! Deposit(commandId, id, amount-1d)
      probe.send(account, GetBalance(commandId2, id))
      probe.expectMsg(CurrentBalance(commandId2, id, amount-1d))

      //when
      val command = Withdraw(commandId3, id, amount)
      val event = probe.send(account, command)

      //expected
      val response = probe.expectMsg(InsufficientFunds(commandId3, id, amount,amount-1d))
      probe.send(account, GetBalance(commandId4, id))
      probe.expectMsg(CurrentBalance(commandId4, id, amount-1d))
    }

    it("replay response for duplicated requestId") {
      //given
      val amount = 100d
      val probe = TestProbe()
      val id = "accTest4"
      val account = system.actorOf(AccountActor.props(), id)
      val commandId = "commandId"

      //when
      val command = Deposit(commandId, id, amount)
      val event1 = probe.send(account, command)
      val event2 = probe.send(account, command)

      //expected
      val response1 = probe.expectMsg(Deposited(commandId, id, amount,amount))
      val response2 = probe.expectMsg(Deposited(commandId, id, amount,amount))
    }
  }
}
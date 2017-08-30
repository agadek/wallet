package org.wallet.transfer

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.transfer.TransferActor.{InsufficientFunds, SyncTransfer, Transferred}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TransferActorSpec extends TestKit(ActorSystem("AccountActorSpec")) with FunSpecLike with Matchers with ImplicitSender {

  describe("TransferActor should process atomic transaction") {
    it("transfer happy path") {
      //given
      val amount = 100d
      val did = "acc1"
      val rid = "acc1"

      val probe = TestProbe()
      val transfer = system.actorOf(TransferActor.props(
        deposit = (id: String, amount: Double) => Future(amount),
        withdraw = (id: String, amount: Double) => Future(amount)))

      //when
      val command = SyncTransfer(did, rid, amount)
      val event = probe.send(transfer, command)

      //expected
      val response = probe.expectMsg(Transferred(did, rid, amount))
    }

    it("transfer fail because low balance") {
      //given
      val amount = 100d
      val did = "acc1"
      val rid = "acc1"

      val probe = TestProbe()
      val transfer = system.actorOf(TransferActor.props(
        deposit = (id: String, amount: Double) => Future(amount),
        withdraw = (id: String, amount: Double) => Future(0d)))

      //when
      val command = SyncTransfer(did, rid, amount)
      val event = probe.send(transfer, command)

      //expected
      val response = probe.expectMsg(InsufficientFunds(did, rid, amount))
    }
  }
}
package org.wallet.transfer

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{FunSpecLike, Matchers}
import org.wallet.service.AccountService.{Fail, Success}
import org.wallet.transfer.TransferActor.{InsufficientFunds, SyncTransfer, Transferred}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TransferActorSpec extends TestKit(ActorSystem("AccountActorSpec")) with FunSpecLike with Matchers with ImplicitSender {

  describe("TransferActor should process atomic transaction") {
    it("transfer happy path") {
      //given
      val amount = 100d
      val tid = "123"
      val did = "acc1"
      val rid = "acc1"

      val probe = TestProbe()
      val transfer = system.actorOf(TransferActor.props(
        deposit = (id: String, change: Double) => Future(Success(change, change)),
        withdraw = (id: String, change: Double) => Future(Success(change, 13d))), tid)

      //when
      val command = SyncTransfer(tid, did, rid, amount)
      val event = probe.send(transfer, command)

      //expected
      val response = probe.expectMsg(Transferred(tid, did, rid, amount, 13d))
    }

    it("transfer fails because low balance") {
      //given
      val amount = 100d
      val tid = "1234"
      val did = "acc1"
      val rid = "acc1"

      val probe = TestProbe()
      val transfer = system.actorOf(TransferActor.props(
        deposit = (id: String, change: Double) => Future(Success(change, change)),
        withdraw = (id: String, change: Double) => Future(Fail(change, 10d, ""))), tid)

      //when
      val command = SyncTransfer(tid, did, rid, amount)
      val event = probe.send(transfer, command)

      //expected
      val response = probe.expectMsg(InsufficientFunds(tid, did, rid, amount, 10d))
    }
  }
}
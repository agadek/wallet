package org.wallet.account

import org.scalatest.{FunSpec, Matchers}
import org.wallet.account.Account._


class AccountSpec extends FunSpec with Matchers {


  describe("Account should ") {
    it("generate event and add balance") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, 0d)
      val commandId = "commandId"

      //when
      val command = Deposit(commandId, id, amount)
      val event = account.process(command)

      val accountAfterDeposit = account(event)

      //expected
      event shouldBe Deposited(commandId, id, amount,amount)
      accountAfterDeposit shouldBe Account(id, amount)
      accountAfterDeposit.process(GetBalance(commandId, id)) shouldBe CurrentBalance(commandId, id, amount)
    }

    it("generate event and withdraw balance") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, amount)
      val commandId = "commandId"

      //when
      val command = Withdraw(commandId, id, amount)
      val event = account.process(command)
      val accountAfterDeposit = account(event)

      //expected
      event shouldBe Withdrawn(commandId, id, amount, 0d)
      accountAfterDeposit shouldBe Account(id, 0d)
      accountAfterDeposit.process(GetBalance(commandId, id)) shouldBe CurrentBalance(commandId, id, 0d)
    }

    it("generate event and refuse Withdraw below 0d") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, amount-1d)
      val commandId = "commandId"

      //when
      val command = Withdraw(commandId, id, amount)
      val event = account.process(command)
      val accountAfterDeposit = account(event)

      //expected
      event shouldBe InsufficientFunds(commandId, id, amount, amount-1d)
      accountAfterDeposit shouldBe Account(id, amount-1d)
      accountAfterDeposit.process(GetBalance(commandId, id)) shouldBe CurrentBalance(commandId, id, amount-1d)
    }
  }
}
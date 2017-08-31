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

      //when
      val command = Deposit(id, amount)
      val event = account.process(command)

      val accountAfterDeposit = account(event)

      //expected
      event shouldBe Deposited(id, amount,amount)
      accountAfterDeposit shouldBe Account(id, amount)
      accountAfterDeposit.process(GetBalance(id)) shouldBe CurrentBalance(id, amount)
    }

    it("generate event and withdraw balance") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, amount)

      //when
      val command = Withdraw(id, amount)
      val event = account.process(command)
      val accountAfterDeposit = account(event)

      //expected
      event shouldBe Withdrawn(id, amount, 0d)
      accountAfterDeposit shouldBe Account(id, 0d)
      accountAfterDeposit.process(GetBalance(id)) shouldBe CurrentBalance(id, 0d)
    }

    it("generate event and refuse Withdraw below 0d") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, amount-1d)

      //when
      val command = Withdraw(id, amount)
      val event = account.process(command)
      val accountAfterDeposit = account(event)

      //expected
      event shouldBe InsufficientFunds(id, amount, amount-1d)
      accountAfterDeposit shouldBe Account(id, amount-1d)
      accountAfterDeposit.process(GetBalance(id)) shouldBe CurrentBalance(id, amount-1d)
    }
  }
}
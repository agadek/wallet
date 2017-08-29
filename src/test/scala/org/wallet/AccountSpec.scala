package org.wallet

import org.scalatest.{FunSpec, Matchers}
import org.wallet.account.Account
import org.wallet.account.Account._


class AccountSpec extends FunSpec with Matchers {

  describe("Account should ") {
    it("generate event and add balance") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, 0d)

      //when
      val command = Deposit(amount)
      val event = account.process(command)

      val accountAfterDeposit = account(event)

      //expected
      event shouldBe Deposited(amount)
      accountAfterDeposit shouldBe Account(id, amount)
      accountAfterDeposit.process(GetBalance()) shouldBe CurrentBalance(amount)
    }

    it("generate event and withdraw balance") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, amount)

      //when
      val command = Withdraw(amount)
      val event = account.process(command)
      val accountAfterDeposit = account(event)

      //expected
      event shouldBe Withdrawn(amount)
      accountAfterDeposit shouldBe Account(id, 0d)
      accountAfterDeposit.process(GetBalance()) shouldBe CurrentBalance(0d)
    }

    it("generate event and refuse Withdraw below 0d") {
      //given
      val amount = 100d
      val id = "testId"
      val account = Account(id, amount-1d)

      //when
      val command = Withdraw(amount)
      val event = account.process(command)
      val accountAfterDeposit = account(event)

      //expected
      event shouldBe InsufficientFunds()
      accountAfterDeposit shouldBe Account(id, amount-1d)
      accountAfterDeposit.process(GetBalance()) shouldBe CurrentBalance(amount-1d)
    }
  }
}
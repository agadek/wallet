package org.wallet.transfer

import org.scalatest.{FunSpec, Matchers}

class CountLongStringSpec extends FunSpec with Matchers {

  describe("count class ") {

    it("count empty string") {
      CountLongString()("").value shouldBe Map.empty[Char, Int]
    }

    it("count non empty string") {

      CountLongString()("abbccc").value shouldBe
        Map[Char, Int](
          'a' -> 1,
          'b' -> 2,
          'c' -> 3)
    }

    it("count non empty string with resued counter") {

      val fistUse = CountLongString()("abbcccd")

      fistUse.value shouldBe
        Map[Char, Int](
          'a' -> 1,
          'b' -> 2,
          'c' -> 3,
          'd' -> 1)

      fistUse("cbbaaa").value shouldBe
        Map[Char, Int](
          'a' -> 4,
          'b' -> 4,
          'c' -> 4)

    }
  }

}

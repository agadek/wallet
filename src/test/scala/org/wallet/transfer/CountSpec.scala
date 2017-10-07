package org.wallet.transfer

import org.scalatest.{FunSpec, Matchers}

/**
  * Created by admin on 03/10/17.
  */
class CountSpec extends FunSpec with Matchers {


  describe("count method ") {

    it("count empty string") {

      CountCharsOcurrences("") shouldBe Map.empty[Char, Int]
    }

    it("count non empty string") {

      CountCharsOcurrences("abbccc") shouldBe
        Map[Char, Int](
          'a' -> 1,
          'b' -> 2,
          'c' -> 3)
    }

    it("count non empty string with space ") {

      CountCharsOcurrences("abb ccc") shouldBe
        Map[Char, Int](
          'a' -> 1,
          ' ' -> 1,
          'b' -> 2,
          'c' -> 3)
    }
  }
}

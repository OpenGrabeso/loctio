package com.github.opengrabeso.loctio.frontend.views

import io.udash.bootstrap.form._

trait PageFactoryUtils {
  class NumericRangeValidator(from: Int, to: Int) extends Validator[Int] {
    def apply(value: Int): Validation = {
      if (value >= from && value <= to) Valid
      else Invalid(DefaultValidationError(s"Expected value between $from and $to"))
    }
  }


}

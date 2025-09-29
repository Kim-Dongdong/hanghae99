package kr.hhplus.be.server.domain.model.exceptions;

import kr.hhplus.be.server.domain.model.Money;

public class PriceMismatchException extends RuntimeException {
  public PriceMismatchException(Money expected, Money actual) {
    super("Price mismatch. expected=" + expected + ", actual=" + actual);
  }
}

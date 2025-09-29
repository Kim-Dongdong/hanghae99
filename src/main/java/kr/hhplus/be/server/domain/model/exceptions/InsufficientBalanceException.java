package kr.hhplus.be.server.domain.model.exceptions;

public class InsufficientBalanceException extends RuntimeException {
  public InsufficientBalanceException() { super("Insufficient balance"); }
}

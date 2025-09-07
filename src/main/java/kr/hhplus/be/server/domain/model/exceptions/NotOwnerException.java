package kr.hhplus.be.server.domain.model.exceptions;

public class NotOwnerException extends RuntimeException {
  public NotOwnerException(Long caller) { super("Not owner: caller=" + caller); }
}

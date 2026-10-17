package vn.tramsach.shared.domain;

public class BusinessException extends RuntimeException {
  public BusinessException(String message) {
    super(message);
  }
}

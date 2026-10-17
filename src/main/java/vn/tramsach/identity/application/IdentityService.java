package vn.tramsach.identity.application;

import java.util.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.tramsach.identity.domain.*;
import vn.tramsach.shared.domain.BusinessException;

@Service
@Transactional(readOnly = true)
public class IdentityService implements UserDetailsService {
  private final MemberRepository repository;
  private final PasswordEncoder encoder;

  public IdentityService(MemberRepository repository, PasswordEncoder encoder) {
    this.repository = repository;
    this.encoder = encoder;
  }

  public Member get(String email) {
    return repository
        .byEmail(email.toLowerCase(Locale.ROOT))
        .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản."));
  }

  public UserDetails loadUserByUsername(String email) {
    var m = get(email);
    return User.withUsername(m.email()).password(m.passwordHash()).roles(m.role()).build();
  }

  @Transactional
  public Member lock(String email) {
    return repository.lock(get(email).id());
  }

  @Transactional
  public void register(String email, String name, String password) {
    if (password == null
        || password.length() < 10
        || password.length() > 72
        || name == null
        || name.isBlank()
        || name.length() > 100
        || email == null
        || email.length() > 180
        || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
      throw new BusinessException("Kiểm tra email, họ tên và mật khẩu (10–72 ký tự).");
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("Mật khẩu tối đa 72 byte UTF-8.");
    email = email.trim().toLowerCase(Locale.ROOT);
    if (repository.byEmail(email).isPresent())
      throw new BusinessException("Email này đã được đăng ký.");
    repository.insert(
        new Member(UUID.randomUUID(), email, name.trim(), encoder.encode(password), "CUSTOMER"));
  }

  @Transactional
  public void bootstrap(String email, String name, String password, String role) {
    if (email != null
        && password != null
        && !password.isBlank()
        && repository.byEmail(email).isEmpty())
      repository.insert(new Member(UUID.randomUUID(), email, name, encoder.encode(password), role));
  }
}

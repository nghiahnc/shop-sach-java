package vn.tramsach.identity.infrastructure;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "members")
public class MemberEntity {
  @Id public UUID id;
  public String email;
  public String name;

  @Column(name = "password_hash")
  public String passwordHash;

  public String role;
}

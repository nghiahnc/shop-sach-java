package vn.tramsach.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import vn.tramsach.identity.application.IdentityService;
import vn.tramsach.shared.domain.BusinessException;

@Controller
public class IdentityController {
  private final IdentityService identity;
  private final vn.tramsach.demo.application.DemoDefaults demo;

  public IdentityController(
      IdentityService identity, vn.tramsach.demo.application.DemoDefaults demo) {
    this.identity = identity;
    this.demo = demo;
  }

  public record RegisterForm(
      @NotBlank(message = "Nhập họ tên.") @Size(max = 100) String name,
      @NotBlank @Email(message = "Email không hợp lệ.") @Size(max = 180) String email,
      @Size(min = 10, max = 72, message = "Mật khẩu cần 10–72 ký tự.") String password) {}

  @GetMapping("/login")
  String login(Model model, jakarta.servlet.http.HttpSession session) {
    model.addAttribute(
        "loginEmail",
        demo.enabled()
            ? Objects.toString(session.getAttribute("demoLoginEmail"), demo.email())
            : "");
    model.addAttribute(
        "loginPassword",
        demo.enabled() && !Boolean.FALSE.equals(session.getAttribute("demoUsePassword"))
            ? demo.password()
            : "");
    return "login";
  }

  @GetMapping("/register")
  String register(Model model) {
    model.addAttribute(
        "form",
        new RegisterForm(
            demo.enabled() ? demo.name() : "",
            demo.enabled() ? demo.registrationEmail() : "",
            demo.enabled() ? demo.password() : ""));
    return "register";
  }

  @PostMapping("/register")
  String register(
      @Valid @ModelAttribute("form") RegisterForm form,
      BindingResult errors,
      Model model,
      jakarta.servlet.http.HttpSession session) {
    if (errors.hasErrors()) return "register";
    try {
      identity.register(form.email(), form.name(), form.password());
    } catch (BusinessException e) {
      errors.rejectValue("email", "duplicate", e.getMessage());
      return "register";
    }
    if (demo.enabled()) {
      session.setAttribute("demoLoginEmail", form.email());
      session.setAttribute("demoUsePassword", demo.password().equals(form.password()));
    }
    return "redirect:/login?registered";
  }
}

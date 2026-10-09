package com.example.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    public AuthController(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> invalidRegistration() {
        // Avoid Spring's default validation logging, which includes rejected passwords.
        return java.util.Map.of("error", "Invalid username or password");
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody Registration request) {
        // BCrypt accepts at most 72 bytes, including multibyte Unicode passwords.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 bytes");
        }
        try {
            Account account = accounts.saveAndFlush(new Account(request.username(), passwords.encode(request.password())));
            return new AccountResponse(account.getId(), account.getUsername());
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username unavailable");
        }
    }

    @GetMapping("/me")
    public AccountResponse me(Principal principal) {
        Account account = accounts.findByUsername(principal.getName()).orElseThrow();
        return new AccountResponse(account.getId(), account.getUsername());
    }

    public record Registration(
            @NotNull @Pattern(regexp = "[a-zA-Z0-9_-]{3,50}") String username,
            @NotNull @Size(min = 12, max = 72) String password) {}
    public record AccountResponse(Long id, String username) {}
    public record CsrfResponse(String headerName, String token) {}
}

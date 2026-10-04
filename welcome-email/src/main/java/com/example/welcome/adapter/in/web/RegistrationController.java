package com.example.welcome.adapter.in.web;

import com.example.welcome.application.RegisterUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class RegistrationController {
    private final RegisterUser registerUser;

    public RegistrationController(RegisterUser registerUser) {
        this.registerUser = registerUser;
    }

    public record Request(@NotBlank @Email @Size(max = 254) String email,
                          @NotBlank @Size(max = 100) String name) {}
    public record Response(UUID id, String message) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response register(@Valid @RequestBody Request request) {
        var user = registerUser.execute(request.email(), request.name());
        return new Response(user.id(), "Registration successful");
    }
}

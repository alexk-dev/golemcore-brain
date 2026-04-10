package dev.golemcore.brain.adapter.in.web.auth.dto;

import dev.golemcore.brain.domain.auth.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @NotBlank
    private String username;

    @Email
    @NotBlank
    private String email;

    private String password;

    @NotNull
    private UserRole role;
}

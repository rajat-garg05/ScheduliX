package com.schedulix.setup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeacherRequest(
        @NotBlank(message = "Please enter a teacher code.")
        @Size(max = 30, message = "Teacher code must be 30 characters or fewer.")
        String code,

        @NotBlank(message = "Please enter the teacher's full name.")
        @Size(max = 150, message = "Full name is too long.")
        String fullName,

        @NotBlank(message = "Please enter the teacher's official email.")
        @Email(message = "Please enter a valid email address.")
        @Size(max = 254, message = "Email address is too long.")
        String email,

        @Size(max = 100, message = "Designation must be 100 characters or fewer.")
        String designation
) {
}
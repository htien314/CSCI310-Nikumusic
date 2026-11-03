
package com.example.trojanrideshare.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.trojanrideshare.R;
import com.example.trojanrideshare.service.AuthService;

public class RegisterFragment extends Fragment {

    private final AuthService authService = new AuthService();

    public RegisterFragment() {
        super(R.layout.fragment_register);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        EditText fullName = view.findViewById(R.id.inputFullName);
        EditText nickname = view.findViewById(R.id.inputNickname);
        EditText email = view.findViewById(R.id.inputEmail);
        EditText uscId = view.findViewById(R.id.inputUscId);
        EditText year = view.findViewById(R.id.inputGraduationYear);
        EditText password = view.findViewById(R.id.inputPassword);

        TextView message =
                view.findViewById(R.id.textRegistrationError);

        Button register =
                view.findViewById(R.id.buttonRegister);

        register.setOnClickListener(v -> {

            String name = fullName.getText().toString().trim();
            String nick = nickname.getText().toString().trim();
            String userEmail = email.getText().toString().trim();
            String id = uscId.getText().toString().trim();
            String pass = password.getText().toString();
            String yearText = year.getText().toString().trim();

            if (yearText.isEmpty()) {
                message.setText("Graduation year is required.");
                return;
            }

            int graduationYear;

            try {
                graduationYear = Integer.parseInt(yearText);
            } catch (NumberFormatException e) {
                message.setText("Enter a valid graduation year.");
                return;
            }

            String error = authService.validateRegistration(
                    name, userEmail, id, pass, graduationYear
            );

            if (error != null) {
                message.setText(error);
                return;
            }

            register.setEnabled(false);
            message.setText("Creating account...");

            authService.register(
                    name,
                    nick,
                    userEmail,
                    id,
                    pass,
                    graduationYear,

                    // Registration succeeded.
                    user -> {
                        if (!isAdded() || getView() == null) {
                            return;
                        }

                        register.setEnabled(true);
                        message.setText("Account created successfully!");

                        Navigation.findNavController(
                                requireView()
                        ).navigate(R.id.profileFragment);
                    },

                    // Registration failed.
                    exception -> {
                        if (!isAdded() || getView() == null) {
                            return;
                        }

                        register.setEnabled(true);

                        String errorMessage = exception.getMessage();

                        message.setText(
                                errorMessage != null
                                        ? errorMessage
                                        : "Registration failed. Please try again."
                        );
                    }
            );
        });
    }
}

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

public class LoginFragment extends Fragment {

    private final AuthService authService = new AuthService();

    public LoginFragment() {
        super(R.layout.fragment_login);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        EditText email =
                view.findViewById(R.id.inputLoginEmail);

        EditText password =
                view.findViewById(R.id.inputLoginPassword);

        TextView message =
                view.findViewById(R.id.textLoginError);

        Button login =
                view.findViewById(R.id.buttonLogin);

        Button goToRegister =
                view.findViewById(R.id.buttonGoToRegister);

        login.setOnClickListener(v -> {

            String userEmail =
                    email.getText().toString().trim();

            String pass =
                    password.getText().toString();

            if (userEmail.isEmpty() || pass.isEmpty()) {
                message.setText(
                        "Email and password are required."
                );
                return;
            }

            login.setEnabled(false);
            message.setText("Logging in...");

            authService.login(
                    userEmail,
                    pass,

                    // Successful login
                    result -> {
                        if (!isAdded() || getView() != view) {
                            return;
                        }

                        login.setEnabled(true);
                        message.setText("");

                        Navigation.findNavController(view)
                                .navigate(R.id.profileFragment);
                    },

                    // Failed login
                    error -> {
                        if (!isAdded() || getView() != view) {
                            return;
                        }

                        login.setEnabled(true);
                        message.setText(
                                "Login failed. Check your email and password."
                        );
                    }
            );
        });

        goToRegister.setOnClickListener(v ->
                Navigation.findNavController(view)
                        .navigate(R.id.registerFragment)
        );
    }
}

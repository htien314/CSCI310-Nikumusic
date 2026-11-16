package com.example.trojanrideshare.service;

import com.example.trojanrideshare.data.UserRepository;
import com.example.trojanrideshare.model.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.time.Year;
import java.time.ZoneOffset;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Member 1 / Riya: WB01–WB05. Each method maps to one assigned scenario. */
public class AccountProfileWhiteBoxTest {
    private static final String EMAIL = "student@usc.edu";
    private static final String ID = "7100000001";
    private static final String PASSWORD = "LocalTest123!";
    private FirebaseAuth auth;
    private UserRepository users;
    private AuthService service;
    private OnSuccessListener<User> registered;
    private OnFailureListener failed;
    private int year;

    @Before @SuppressWarnings("unchecked") public void setUp() {
        auth = mock(FirebaseAuth.class);
        users = mock(UserRepository.class);
        registered = mock(OnSuccessListener.class);
        failed = mock(OnFailureListener.class);
        service = new AuthService(auth, users);
        year = Year.now(ZoneOffset.UTC).getValue();
    }

    @Test public void wb01_validRegistrationNormalizesFieldsAndReturnsSavedProfile() {
        FirebaseUser firebaseUser = mock(FirebaseUser.class);
        when(firebaseUser.getUid()).thenReturn("student-uid");
        AuthResult result = mock(AuthResult.class);
        when(result.getUser()).thenReturn(firebaseUser);
        Task<AuthResult> registrationTask = completedTask(result, null);
        when(auth.createUserWithEmailAndPassword(EMAIL, PASSWORD)).thenReturn(registrationTask);
        doAnswer(call -> {
            OnSuccessListener<Void> saved = call.getArgument(1);
            saved.onSuccess(null);
            return null;
        }).when(users).registerUser(any(User.class), any(), any());

        // Both display-name branches must pass every registration guard.
        service.register("  Student One  ", "  Traveler  ", "  STUDENT@USC.EDU  ", ID,
                PASSWORD, year, registered, failed);
        service.register("  Student One  ", "   ", EMAIL, ID,
                PASSWORD, year + 6, registered, failed);
        ArgumentCaptor<User> profiles = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).registerUser(profiles.capture(), any(), any());
        User supplied = profiles.getAllValues().get(0);
        assertEquals("student-uid", supplied.getUid());
        assertEquals("Student One", supplied.getFullName());
        assertEquals("Traveler", supplied.getNickname());
        assertEquals(EMAIL, supplied.getUscEmail());
        assertEquals(ID, supplied.getUscId());
        assertEquals(year, supplied.getGraduationYear());
        User fallback = profiles.getAllValues().get(1);
        assertEquals("Student One", fallback.getNickname());
        assertEquals(year + 6, fallback.getGraduationYear());
        verify(registered).onSuccess(supplied);
        verify(registered).onSuccess(fallback);
        verifyNoInteractions(failed);
    }

    @Test public void wb02_invalidEmailStopsBeforeAuthenticationOrProfileWrites() {
        for (String email : new String[]{null, "", "student@gmail.com", "student@usc.edu.evil", "x".repeat(247) + "@usc.edu"}) {
            service.register("Student One", "Traveler", email, ID, PASSWORD, year, registered, failed);
        }
        ArgumentCaptor<Exception> errors = ArgumentCaptor.forClass(Exception.class);
        verify(failed, times(5)).onFailure(errors.capture());
        for (Exception error : errors.getAllValues()) {
            assertTrue(error instanceof IllegalArgumentException);
            assertEquals("Enter a valid @usc.edu email address.", error.getMessage());
        }
        assertNull(service.validateRegistration("Student One", EMAIL, ID, PASSWORD, year));
        verifyNoInteractions(auth, users, registered);
    }

    @Test public void wb03_uscIdGuardsRejectNineElevenAndNondigitInputs() {
        for (String id : new String[]{null, "710000001", "71000000011", "710000000x"}) {
            service.register("Student One", "Traveler", EMAIL, id, PASSWORD, year, registered, failed);
        }
        ArgumentCaptor<Exception> errors = ArgumentCaptor.forClass(Exception.class);
        verify(failed, times(4)).onFailure(errors.capture());
        for (Exception error : errors.getAllValues()) {
            assertEquals("USC ID must contain exactly 10 digits.", error.getMessage());
        }
        assertNull(service.validateRegistration("Student One", EMAIL, ID, PASSWORD, year));
        verifyNoInteractions(auth, users, registered);
    }

    @Test @SuppressWarnings("unchecked") public void wb04_loginPropagatesFailureAndSuccessAndRejectsMissingCredentials() {
        OnSuccessListener<AuthResult> loggedIn = mock(OnSuccessListener.class);
        Exception incorrectPassword = new IllegalArgumentException("Incorrect password fixture");
        Task<AuthResult> failedTask = completedTask(null, incorrectPassword);
        when(auth.signInWithEmailAndPassword(EMAIL, "wrong-password")).thenReturn(failedTask);
        service.login("  " + EMAIL + "  ", "wrong-password", loggedIn, failed);
        verify(failed).onFailure(incorrectPassword);
        verifyNoInteractions(loggedIn);

        AuthResult result = mock(AuthResult.class);
        Task<AuthResult> successfulTask = completedTask(result, null);
        when(auth.signInWithEmailAndPassword(EMAIL, PASSWORD)).thenReturn(successfulTask);
        service.login(EMAIL, PASSWORD, loggedIn, failed);
        verify(loggedIn).onSuccess(result);
        for (String[] input : new String[][]{{null, PASSWORD}, {" ", PASSWORD}, {EMAIL, null}, {EMAIL, ""}}) {
            service.login(input[0], input[1], loggedIn, failed);
        }
        ArgumentCaptor<Exception> errors = ArgumentCaptor.forClass(Exception.class);
        verify(failed, times(5)).onFailure(errors.capture());
        for (Exception error : errors.getAllValues().subList(1, 5)) {
            assertEquals("Email and password are required.", error.getMessage());
        }
        verify(auth).signInWithEmailAndPassword(EMAIL, "wrong-password");
        verify(auth).signInWithEmailAndPassword(EMAIL, PASSWORD);
        verifyNoMoreInteractions(auth, loggedIn);
        verifyNoInteractions(users);
    }

    @Test public void wb05_nicknameValidationCoversEmptyAndLengthBranchesBeforeSaving() {
        assertEquals("Nickname cannot be empty.", RegistrationValidator.validateNickname(null));
        assertEquals("Nickname cannot be empty.", RegistrationValidator.validateNickname("   "));
        assertNull(RegistrationValidator.validateNickname("  Traveler One  "));
        assertNull(RegistrationValidator.validateNickname("x".repeat(100)));
        assertEquals("Nickname must be at most 100 characters.",
                RegistrationValidator.validateNickname("x".repeat(101)));
        service.register("Student One", "x".repeat(101), EMAIL, ID, PASSWORD, year, registered, failed);
        ArgumentCaptor<Exception> error = ArgumentCaptor.forClass(Exception.class);
        verify(failed).onFailure(error.capture());
        assertEquals("Nickname must be at most 100 characters.", error.getValue().getMessage());
        verifyNoInteractions(auth, users, registered);
    }

    /** Synchronous listener fixture: no Android main Looper or real Firebase calls. */
    @SuppressWarnings("unchecked") private static <T> Task<T> completedTask(T value, Exception error) {
        Task<T> task = mock(Task.class);
        when(task.addOnSuccessListener(any(OnSuccessListener.class))).thenAnswer(call -> {
            if (error == null) ((OnSuccessListener<T>) call.getArgument(0)).onSuccess(value);
            return task;
        });
        when(task.addOnFailureListener(any(OnFailureListener.class))).thenAnswer(call -> {
            if (error != null) ((OnFailureListener) call.getArgument(0)).onFailure(error);
            return task;
        });
        return task;
    }
}

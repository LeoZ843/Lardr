package com.zanoni.lardr.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.zanoni.lardr.R
import com.zanoni.lardr.ui.components.AppLogo
import com.zanoni.lardr.ui.components.EmailTextField
import com.zanoni.lardr.ui.components.LardrOutlinedButton
import com.zanoni.lardr.ui.components.LardrTextButton
import com.zanoni.lardr.ui.components.LoadingButton
import com.zanoni.lardr.ui.components.PasswordTextField
import androidx.compose.ui.res.stringResource

@Composable
fun LoginScreen(
    onNavigateToMain: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account.idToken?.let { token ->
                    viewModel.loginWithGoogle(token)
                }
            } catch (e: ApiException) {
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onNavigateToMain()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val defaultWebClientId = stringResource(R.string.default_web_client_id)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LoginContent(
            email = email,
            onEmailChange = { email = it },
            password = password,
            onPasswordChange = { password = it },
            isLoading = uiState.isLoading,
            onLoginClick = { viewModel.login(email, password) },
            onGoogleSignInClick = {
                @Suppress("DEPRECATION")
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(defaultWebClientId)
                    .requestEmail()
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(context, gso)
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            },
            onRegisterClick = { showRegisterDialog = true },
            onForgotPasswordClick = { showForgotPasswordDialog = true },
            onSkipLoginClick = { viewModel.showSkipDialog() },
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (uiState.showSkipDialog) {
        SkipLoginDialog(
            onConfirm = { viewModel.skipLogin() },
            onDismiss = { viewModel.hideSkipDialog() }
        )
    }

    if (showRegisterDialog) {
        RegisterDialog(
            onDismiss = { showRegisterDialog = false },
            onRegister = { registerEmail, username, registerPassword ->
                viewModel.register(registerEmail, username, registerPassword)
                showRegisterDialog = false
            },
            isLoading = uiState.isLoading
        )
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            onDismiss = { showForgotPasswordDialog = false },
            onResetPassword = { resetEmail ->
                viewModel.resetPassword(resetEmail)
                showForgotPasswordDialog = false
            },
            isLoading = uiState.isLoading
        )
    }
}

@Composable
private fun LoginContent(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSkipLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        AppLogo()

        Spacer(modifier = Modifier.height(48.dp))

        LoginForm(
            email = email,
            onEmailChange = onEmailChange,
            password = password,
            onPasswordChange = onPasswordChange,
            isLoading = isLoading,
            onLoginClick = onLoginClick,
            onGoogleSignInClick = onGoogleSignInClick,
            onRegisterClick = onRegisterClick,
            onForgotPasswordClick = onForgotPasswordClick,
            onSkipLoginClick = onSkipLoginClick
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LoginForm(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSkipLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier.widthIn(max = 400.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmailTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        PasswordTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(24.dp))

        LoginButtons(
            isLoading = isLoading,
            onLoginClick = onLoginClick,
            onGoogleSignInClick = onGoogleSignInClick,
            onRegisterClick = onRegisterClick,
            onForgotPasswordClick = onForgotPasswordClick,
            onSkipLoginClick = onSkipLoginClick
        )
    }
}

@Composable
private fun LoginButtons(
    isLoading: Boolean,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSkipLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingButton(
            text = "Login",
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth(),
            isLoading = isLoading,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(8.dp))

        LardrTextButton(
            text = "Don't have an account? Register",
            onClick = onRegisterClick,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(8.dp))

        LardrTextButton(
            text = "Forgot password?",
            onClick = onForgotPasswordClick,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        LardrOutlinedButton(
            text = "Sign in with Google",
            onClick = onGoogleSignInClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.google),
                    contentDescription = "Google",
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified  // Important! Preserves original colors
                )
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        LardrTextButton(
            text = "Continue without login",
            onClick = onSkipLoginClick,
            enabled = !isLoading
        )
    }
}

@Composable
private fun RegisterDialog(
    onDismiss: () -> Unit,
    onRegister: (email: String, username: String, password: String) -> Unit,
    isLoading: Boolean
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var isValid = true

        if (email.isBlank()) {
            emailError = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Invalid email format"
            isValid = false
        } else {
            emailError = null
        }

        if (username.isBlank()) {
            usernameError = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            usernameError = "Username must be at least 3 characters"
            isValid = false
        } else {
            usernameError = null
        }

        if (password.isBlank()) {
            passwordError = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            isValid = false
        } else {
            passwordError = null
        }

        if (confirmPassword.isBlank()) {
            confirmPasswordError = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordError = "Passwords do not match"
            isValid = false
        } else {
            confirmPasswordError = null
        }

        return isValid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Create Account",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                EmailTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                if (emailError != null) {
                    Text(
                        text = emailError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        usernameError = null
                    },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true,
                    isError = usernameError != null
                )
                if (usernameError != null) {
                    Text(
                        text = usernameError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PasswordTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    label = "Password"
                )
                if (passwordError != null) {
                    Text(
                        text = passwordError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                PasswordTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmPasswordError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    label = "Confirm Password"
                )
                if (confirmPasswordError != null) {
                    Text(
                        text = confirmPasswordError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            LoadingButton(
                text = "Register",
                onClick = {
                    if (validate()) {
                        onRegister(email, username, password)
                    }
                },
                isLoading = isLoading,
                enabled = !isLoading
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ForgotPasswordDialog(
    onDismiss: () -> Unit,
    onResetPassword: (email: String) -> Unit,
    isLoading: Boolean
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        return if (email.isBlank()) {
            emailError = "Email is required"
            false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Invalid email format"
            false
        } else {
            emailError = null
            true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Reset Password",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    "Enter your email address and we'll send you a link to reset your password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                EmailTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                if (emailError != null) {
                    Text(
                        text = emailError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            LoadingButton(
                text = "Send Reset Link",
                onClick = {
                    if (validate()) {
                        onResetPassword(email)
                    }
                },
                isLoading = isLoading,
                enabled = !isLoading
            )
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SkipLoginDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Continue without login?") },
        text = {
            Text(
                "You can use the app without logging in, but your data will only be stored locally on this device and won't sync across devices.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
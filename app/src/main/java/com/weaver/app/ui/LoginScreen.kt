package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weaver.app.auth.AuthController
import com.weaver.app.auth.AuthState
import com.weaver.app.ui.common.DottedCanvas
import com.weaver.app.ui.theme.TextDim
import com.weaver.app.ui.theme.Voltage
import com.weaver.app.ui.theme.VoltageInk
import com.weaver.app.ui.theme.WeaverType
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authController: AuthController,
    state: AuthState,
    onAuthenticated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(state) {
        if (state is AuthState.Authenticated) onAuthenticated()
    }

    DottedCanvas {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Voltage),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "W",
                        style = WeaverType.Display.copy(
                            color = VoltageInk,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text("Weaver", style = WeaverType.Display)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sign in with your work Google account to continue.",
                    style = WeaverType.BodyDim,
                    color = TextDim,
                )
                Spacer(Modifier.height(28.dp))

                when (state) {
                    is AuthState.Authenticating -> CircularProgressIndicator(color = Voltage)
                    else -> SignInButton(onClick = { scope.launch { authController.signIn(context) } })
                }

                if (state is AuthState.Failed) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.reason,
                        style = WeaverType.Caption,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Voltage)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Continue with Google",
            style = WeaverType.Body.copy(
                color = VoltageInk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            ),
        )
    }
}

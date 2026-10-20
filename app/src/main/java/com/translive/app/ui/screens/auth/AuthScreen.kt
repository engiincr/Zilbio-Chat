package com.translive.app.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.translive.app.data.local.UserPreferences
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.domain.model.Language
import com.translive.app.domain.model.User
import com.translive.app.ui.theme.AccentAmberOrange
import com.translive.app.ui.theme.BackgroundAlabasterCream
import com.translive.app.ui.theme.BorderWarmSubtle
import com.translive.app.ui.theme.CallEndCoralRed
import com.translive.app.ui.theme.InputFieldBackground
import com.translive.app.ui.theme.OnlineStatusGreen
import com.translive.app.ui.theme.OutgoingBubblePeach
import com.translive.app.ui.theme.SurfaceCardWhite
import com.translive.app.ui.theme.TextPrimaryEspresso
import com.translive.app.ui.theme.TextSecondaryWarmGrey
import kotlinx.coroutines.launch
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    navController: NavController? = null,
    onNavigateToHome: () -> Unit = {
        navController?.navigate("home") {
            popUpTo("auth") { inclusive = true }
        }
    }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Kayıt Ol, 1: Giriş Yap

    // Register State
    var regUsername by remember { mutableStateOf("") }
    var regDisplayName by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regPasswordVisible by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(Language.TURKISH) }
    var regErrorMessage by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }

    // Login State
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }
    var loginErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundAlabasterCream)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderWarmSubtle),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo Icon & Title
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(OutgoingBubblePeach),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = "TransLive Logo",
                        tint = AccentAmberOrange,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "TransLive",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentAmberOrange,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Gerçek Zamanlı Çeviri & İletişim",
                    fontSize = 13.sp,
                    color = TextSecondaryWarmGrey,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = AccentAmberOrange,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = AccentAmberOrange,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            regErrorMessage = null
                        },
                        text = {
                            Text(
                                text = "Kayıt Ol",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) AccentAmberOrange else TextSecondaryWarmGrey,
                                fontSize = 15.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            loginErrorMessage = null
                        },
                        text = {
                            Text(
                                text = "Giriş Yap",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) AccentAmberOrange else TextSecondaryWarmGrey,
                                fontSize = 15.sp
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (selectedTab == 0) {
                    // =================== REGISTER TAB ===================
                    // Username
                    OutlinedTextField(
                        value = regUsername,
                        onValueChange = {
                            regUsername = it.filter { char -> char.isLetterOrDigit() || char == '_' }
                            regErrorMessage = null
                        },
                        label = { Text("Kullanıcı Adı", fontSize = 13.sp) },
                        placeholder = { Text("orn: ahmet_01") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondaryWarmGrey)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = InputFieldBackground,
                            unfocusedContainerColor = InputFieldBackground,
                            focusedIndicatorColor = AccentAmberOrange,
                            unfocusedIndicatorColor = BorderWarmSubtle,
                            focusedTextColor = TextPrimaryEspresso,
                            unfocusedTextColor = TextPrimaryEspresso
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Display Name
                    OutlinedTextField(
                        value = regDisplayName,
                        onValueChange = {
                            regDisplayName = it
                            regErrorMessage = null
                        },
                        label = { Text("Görünen İsim", fontSize = 13.sp) },
                        placeholder = { Text("orn: Ahmet Yılmaz") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondaryWarmGrey)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = InputFieldBackground,
                            unfocusedContainerColor = InputFieldBackground,
                            focusedIndicatorColor = AccentAmberOrange,
                            unfocusedIndicatorColor = BorderWarmSubtle,
                            focusedTextColor = TextPrimaryEspresso,
                            unfocusedTextColor = TextPrimaryEspresso
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password
                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = {
                            regPassword = it
                            regErrorMessage = null
                        },
                        label = { Text("Şifre (En az 6 karakter)", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondaryWarmGrey)
                        },
                        trailingIcon = {
                            IconButton(onClick = { regPasswordVisible = !regPasswordVisible }) {
                                Icon(
                                    imageVector = if (regPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Şifreyi Göster",
                                    tint = TextSecondaryWarmGrey
                                )
                            }
                        },
                        visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = InputFieldBackground,
                            unfocusedContainerColor = InputFieldBackground,
                            focusedIndicatorColor = AccentAmberOrange,
                            unfocusedIndicatorColor = BorderWarmSubtle,
                            focusedTextColor = TextPrimaryEspresso,
                            unfocusedTextColor = TextPrimaryEspresso
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Language Selector
                    Text(
                        text = "Ana Dilinizi Seçin:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondaryWarmGrey,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Language.entries.forEach { lang ->
                            val isSelected = selectedLanguage == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) OutgoingBubblePeach else InputFieldBackground)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) AccentAmberOrange else BorderWarmSubtle,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedLanguage = lang }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(text = lang.flag, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = lang.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) AccentAmberOrange else TextPrimaryEspresso
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = regErrorMessage != null) {
                        regErrorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = msg,
                                color = CallEndCoralRed,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Register Button
                    Button(
                        onClick = {
                            val trimmedUser = regUsername.trim()
                            val trimmedName = regDisplayName.trim()
                            val pass = regPassword.trim()

                            if (trimmedUser.length < 3) {
                                regErrorMessage = "Kullanıcı adı en az 3 karakter olmalıdır"
                                return@Button
                            }
                            if (trimmedName.isEmpty()) {
                                regErrorMessage = "Lütfen görünen adınızı girin"
                                return@Button
                            }
                            if (pass.length < 6) {
                                regErrorMessage = "Şifre en az 6 karakter olmalıdır"
                                return@Button
                            }

                            coroutineScope.launch {
                                isRegistering = true
                                regErrorMessage = null
                                try {
                                    val isAvailable = CloudRealtimeService.checkUsernameAvailable(trimmedUser)
                                    if (!isAvailable) {
                                        regErrorMessage = "Bu kullanıcı adı zaten alınmış"
                                        isRegistering = false
                                        return@launch
                                    }

                                    val generatedId = "TL-" + (1..6).map {
                                        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".random()
                                    }.joinToString("")

                                    val hashedPassword = hashPasswordSha256(pass)
                                    val newUser = User(
                                        userId = generatedId,
                                        username = trimmedUser,
                                        displayName = trimmedName,
                                        passwordHash = hashedPassword,
                                        language = selectedLanguage.code,
                                        avatarBase64 = null,
                                        status = "online",
                                        createdAt = System.currentTimeMillis()
                                    )

                                    val success = CloudRealtimeService.registerUser(newUser)
                                    if (success) {
                                        UserPreferences.saveUser(context, newUser)
                                        onNavigateToHome()
                                    } else {
                                        regErrorMessage = "Kayıt işlemi başarısız. Lütfen internet bağlantınızı kontrol edin."
                                    }
                                } catch (e: Exception) {
                                    regErrorMessage = "Hata oluştu: ${e.message}"
                                } finally {
                                    isRegistering = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isRegistering,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmberOrange,
                            contentColor = Color.White
                        )
                    ) {
                        if (isRegistering) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Kayıt Ol",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // =================== LOGIN TAB ===================
                    // Username
                    OutlinedTextField(
                        value = loginUsername,
                        onValueChange = {
                            loginUsername = it.trim()
                            loginErrorMessage = null
                        },
                        label = { Text("Kullanıcı Adı", fontSize = 13.sp) },
                        placeholder = { Text("Kullanıcı adınızı girin") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondaryWarmGrey)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = InputFieldBackground,
                            unfocusedContainerColor = InputFieldBackground,
                            focusedIndicatorColor = AccentAmberOrange,
                            unfocusedIndicatorColor = BorderWarmSubtle,
                            focusedTextColor = TextPrimaryEspresso,
                            unfocusedTextColor = TextPrimaryEspresso
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = {
                            loginPassword = it
                            loginErrorMessage = null
                        },
                        label = { Text("Şifre", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = TextSecondaryWarmGrey)
                        },
                        trailingIcon = {
                            IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                                Icon(
                                    imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Şifreyi Göster",
                                    tint = TextSecondaryWarmGrey
                                )
                            }
                        },
                        visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = InputFieldBackground,
                            unfocusedContainerColor = InputFieldBackground,
                            focusedIndicatorColor = AccentAmberOrange,
                            unfocusedIndicatorColor = BorderWarmSubtle,
                            focusedTextColor = TextPrimaryEspresso,
                            unfocusedTextColor = TextPrimaryEspresso
                        )
                    )

                    AnimatedVisibility(visible = loginErrorMessage != null) {
                        loginErrorMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = msg,
                                color = CallEndCoralRed,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login Button
                    Button(
                        onClick = {
                            val trimmedUser = loginUsername.trim()
                            val pass = loginPassword.trim()

                            if (trimmedUser.isEmpty()) {
                                loginErrorMessage = "Lütfen kullanıcı adınızı girin"
                                return@Button
                            }
                            if (pass.isEmpty()) {
                                loginErrorMessage = "Lütfen şifrenizi girin"
                                return@Button
                            }

                            coroutineScope.launch {
                                isLoggingIn = true
                                loginErrorMessage = null
                                try {
                                    val hashedPassword = hashPasswordSha256(pass)
                                    val user = CloudRealtimeService.loginUser(trimmedUser, hashedPassword)
                                    if (user != null) {
                                        UserPreferences.saveUser(context, user)
                                        CloudRealtimeService.updateUserStatus(user.userId, "online")
                                        onNavigateToHome()
                                    } else {
                                        loginErrorMessage = "Kullanıcı bulunamadı veya şifre hatalı"
                                    }
                                } catch (e: Exception) {
                                    loginErrorMessage = "Giriş başarısız: ${e.message}"
                                } finally {
                                    isLoggingIn = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isLoggingIn,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentAmberOrange,
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Giriş Yap",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hashPasswordSha256(password: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(password.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

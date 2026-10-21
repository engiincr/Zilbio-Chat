package com.android.zilbio.ui.screens.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavyElevated
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.data.remote.UsernameChangeResult
import com.android.zilbio.ui.theme.DestructiveCoralRed
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.CircularProgressIndicator
import com.android.zilbio.data.rewards.BadgeRewardManager
import com.android.zilbio.ui.components.ProfileBadgesCard

import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalProfileScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unlockedBadges by BadgeRewardManager.unlockedBadgesFlow.collectAsState()

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)

    var username by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var usernameSuccess by remember { mutableStateOf(false) }
    var isCheckingUsername by remember { mutableStateOf(false) }

    var displayName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var profileVisibility by remember { mutableStateOf("EVERYONE") }
    var avatarBase64 by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        val user = currentUser ?: return@LaunchedEffect
        if (!hasInitialized) {
            hasInitialized = true
            username = user.username
            displayName = user.displayName
            bio = user.bio
            ageText = if (user.age > 0) user.age.toString() else ""
            birthDate = user.birthDate
            profileVisibility = user.profileVisibility
            avatarBase64 = user.avatarBase64
            BadgeRewardManager.init(context, user.userId)
        }
    }

    // Live username validation
    LaunchedEffect(username) {
        val user = currentUser ?: return@LaunchedEffect
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean == user.username.lowercase()) {
            usernameError = null
            usernameSuccess = false
            isCheckingUsername = false
            return@LaunchedEffect
        }

        val formatErr = CloudRealtimeService.validateUsernameFormat(clean)
        if (formatErr != null) {
            usernameError = formatErr
            usernameSuccess = false
            isCheckingUsername = false
            return@LaunchedEffect
        }

        isCheckingUsername = true
        val available = CloudRealtimeService.checkUsernameAvailable(clean, user.userId)
        isCheckingUsername = false
        if (available) {
            usernameError = null
            usernameSuccess = true
        } else {
            usernameError = "Bu kullanıcı adı zaten alınmış."
            usernameSuccess = false
        }
    }

    // Photo picker (supports up to 6MB with downsampling - Item #8)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val compressedBase64 = processAndCompressImage(context, uri)
                if (compressedBase64 != null) {
                    avatarBase64 = compressedBase64
                    currentUser?.let {
                        UserPreferences.updateAvatar(context, compressedBase64)
                        CloudRealtimeService.updateUserProfile(it.userId, mapOf("avatarBase64" to compressedBase64))
                    }
                    Toast.makeText(context, "Profil resmi güncellendi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Fotoğraf yüklenemedi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = BackgroundMidnightNavy,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profil Bilgileri",
                        color = TextOffWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextOffWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundMidnightNavy)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar picker
            val avatarBitmap = remember(avatarBase64) {
                avatarBase64?.let { base64 ->
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.BottomEnd
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Profil Resmi",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(2.dp, AccentNeonLime, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(SurfaceSlateNavyElevated)
                            .border(2.dp, BorderSlateNavy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = AccentNeonLime, modifier = Modifier.size(48.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(AccentNeonLime)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Değiştir", tint = BackgroundMidnightNavy)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Fotoğrafı Değiştir (En fazla 6MB)", color = TextMutedSlateGrey, fontSize = 12.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // Form fields card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Username (Unique Username Engine)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Kullanıcı Adı", color = AccentNeonLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (currentUser?.isOwnerOrAdmin() == true) {
                            AdminBadge(customRole = currentUser?.permissions?.customRoleLabel, isCompact = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val canChangeUser = currentUser?.permissions?.canChangeUsername ?: true
                    OutlinedTextField(
                        value = username,
                        onValueChange = { if (canChangeUser) username = it },
                        enabled = canChangeUser,
                        leadingIcon = {
                            Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = AccentNeonLime, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (isCheckingUsername) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentNeonLime)
                            }
                        },
                        singleLine = true,
                        placeholder = { Text("kullanici_adi", color = TextMutedSlateGrey) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (usernameError != null) DestructiveCoralRed else AccentNeonLime,
                            unfocusedBorderColor = if (usernameError != null) DestructiveCoralRed else BorderSlateNavy,
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite
                        )
                    )
                    if (!canChangeUser) {
                        Text(
                            text = "Yönetici tarafından kullanıcı adı değiştirmeniz kısıtlanmıştır.",
                            color = TextMutedSlateGrey,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (usernameError != null) {
                        Text(
                            text = usernameError ?: "",
                            color = DestructiveCoralRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (usernameSuccess) {
                        Text(
                            text = "✓ Bu kullanıcı adı kullanılabilir.",
                            color = AccentNeonLime,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display Name
                    Text(text = "Görünen Ad", color = AccentNeonLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentNeonLime,
                            unfocusedBorderColor = BorderSlateNavy,
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bio
                    Text(text = "Biyografi (Hakkında)", color = AccentNeonLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        placeholder = { Text("Kendinizden kısaca bahsedin...", color = TextMutedSlateGrey) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentNeonLime,
                            unfocusedBorderColor = BorderSlateNavy,
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Age & Birth Date row
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Yaş", color = AccentNeonLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = { if (it.all { ch -> ch.isDigit() }) ageText = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentNeonLime,
                                    unfocusedBorderColor = BorderSlateNavy,
                                    focusedTextColor = TextOffWhite,
                                    unfocusedTextColor = TextOffWhite
                                )
                            )
                        }

                        Column(modifier = Modifier.weight(1.5f)) {
                            Text(text = "Doğum Tarihi", color = AccentNeonLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = birthDate,
                                onValueChange = { birthDate = it },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Tarih Seç",
                                            tint = AccentNeonLime,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                placeholder = { Text("GG.AA.YYYY", color = TextMutedSlateGrey) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDatePicker = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentNeonLime,
                                    unfocusedBorderColor = BorderSlateNavy,
                                    focusedTextColor = TextOffWhite,
                                    unfocusedTextColor = TextOffWhite
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Profile Visibility (Item #13)
                    Text(text = "Profil Görünürlüğü", color = AccentNeonLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { profileVisibility = "EVERYONE" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = profileVisibility == "EVERYONE",
                            onClick = { profileVisibility = "EVERYONE" },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentNeonLime, unselectedColor = BorderSlateNavy)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Herkese Açık", color = TextOffWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Tüm kullanıcılar yaş ve biyografinizi görebilir", color = TextMutedSlateGrey, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { profileVisibility = "FRIENDS" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = profileVisibility == "FRIENDS",
                            onClick = { profileVisibility = "FRIENDS" },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentNeonLime, unselectedColor = BorderSlateNavy)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Yalnızca Arkadaşlarım", color = TextOffWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Sadece ekli arkadaşlarınız bilgilerinizi görebilir", color = TextMutedSlateGrey, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.height(16.dp))

            ProfileBadgesCard(
                badges = BadgeRewardManager.getBadgesWithStatus(unlockedBadges),
                title = "Başarı Rozetlerim 🏆"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Save Button
            Button(
                onClick = {
                    val user = currentUser ?: return@Button
                    isSaving = true
                    scope.launch {
                        try {
                            val cleanNewUsername = username.trim().removePrefix("@").lowercase()
                            if (cleanNewUsername != user.username.lowercase()) {
                                if (usernameError != null) {
                                    Toast.makeText(context, usernameError ?: "Geçersiz kullanıcı adı", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val changeResult = CloudRealtimeService.changeUsername(
                                    userId = user.userId,
                                    oldUsername = user.username,
                                    newUsername = cleanNewUsername
                                )
                                if (changeResult is UsernameChangeResult.Error) {
                                    Toast.makeText(context, changeResult.message, Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                UserPreferences.saveUser(context, user.copy(username = cleanNewUsername))
                            }

                            val ageInt = ageText.toIntOrNull() ?: 0
                            UserPreferences.updateDisplayName(context, displayName)
                            UserPreferences.updateProfileDetails(
                                context = context,
                                bio = bio,
                                age = ageInt,
                                birthDate = birthDate,
                                profileVisibility = profileVisibility
                            )

                            // Sync to Firebase
                            val updates = mapOf(
                                "displayName" to displayName,
                                "bio" to bio,
                                "age" to ageInt,
                                "birthDate" to birthDate,
                                "profileVisibility" to profileVisibility
                            )
                            CloudRealtimeService.updateUserProfile(user.userId, updates)

                            Toast.makeText(context, "Profil başarıyla kaydedildi", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isSaving = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentNeonLime,
                    contentColor = BackgroundMidnightNavy
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSaving) "Kaydediliyor..." else "Değişiklikleri Kaydet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val instant = java.time.Instant.ofEpochMilli(millis)
                            val localDate = instant.atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            birthDate = "%02d.%02d.%04d".format(localDate.dayOfMonth, localDate.monthValue, localDate.year)
                            val today = java.time.LocalDate.now()
                            val calculatedAge = java.time.Period.between(localDate, today).years
                            if (calculatedAge in 0..125) {
                                ageText = calculatedAge.toString()
                            }
                        }
                    }
                ) {
                    Text("Tamam", color = AccentNeonLime, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("İptal", color = TextMutedSlateGrey)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = SurfaceSlateNavy
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = SurfaceSlateNavy,
                    titleContentColor = TextOffWhite,
                    headlineContentColor = AccentNeonLime,
                    weekdayContentColor = TextMutedSlateGrey,
                    subheadContentColor = TextOffWhite,
                    yearContentColor = TextOffWhite,
                    currentYearContentColor = AccentNeonLime,
                    selectedYearContentColor = BackgroundMidnightNavy,
                    selectedYearContainerColor = AccentNeonLime,
                    dayContentColor = TextOffWhite,
                    selectedDayContentColor = BackgroundMidnightNavy,
                    selectedDayContainerColor = AccentNeonLime,
                    todayContentColor = AccentNeonLime,
                    todayDateBorderColor = AccentNeonLime
                )
            )
        }
    }
}

private suspend fun processAndCompressImage(context: android.content.Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        var inputStream: InputStream? = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(inputStream, null, boundsOptions)
        inputStream?.close()

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return@withContext null

        val reqWidth = 512
        val reqHeight = 512
        var sampleSize = 1
        if (origHeight > reqHeight || origWidth > reqWidth) {
            val halfHeight = origHeight / 2
            val halfWidth = origWidth / 2
            while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        inputStream.close()

        val scaledBitmap = if (decodedBitmap != null && (decodedBitmap.width > 512 || decodedBitmap.height > 512)) {
            Bitmap.createScaledBitmap(decodedBitmap, 512, 512, true)
        } else {
            decodedBitmap
        } ?: return@withContext null

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}

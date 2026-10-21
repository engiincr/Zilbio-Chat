package com.android.zilbio.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.PrivacySettings
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.theme.AccentWarmPeach
import com.android.zilbio.ui.theme.BackgroundDarkEspresso
import com.android.zilbio.ui.theme.BorderDarkSubtle
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.SurfaceDarkWarm
import com.android.zilbio.ui.theme.SurfaceDarkWarmElevated
import com.android.zilbio.ui.theme.TextMutedWarmGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(navController: NavController) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    val privacyFlow = remember { UserPreferences.getPrivacySettings(context) }
    val currentPrivacy by privacyFlow.collectAsState(initial = PrivacySettings())

    var archiveUnknown by remember { mutableStateOf(false) }
    var silenceNonContacts by remember { mutableStateOf(false) }
    var showAge by remember { mutableStateOf(true) }
    var sensitiveFilter by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    LaunchedEffect(currentPrivacy) {
        archiveUnknown = currentPrivacy.archiveUnknownChats
        silenceNonContacts = currentPrivacy.silenceNonContactCalls
        showAge = currentPrivacy.showAge
        sensitiveFilter = currentPrivacy.sensitiveContentFilter
    }

    fun saveUpdatedPrivacy(
        newArchive: Boolean = archiveUnknown,
        newSilence: Boolean = silenceNonContacts,
        newShowAge: Boolean = showAge,
        newFilter: Boolean = sensitiveFilter
    ) {
        scope.launch {
            val updated = currentPrivacy.copy(
                archiveUnknownChats = newArchive,
                silenceNonContactCalls = newSilence,
                showAge = newShowAge,
                sensitiveContentFilter = newFilter
            )
            UserPreferences.savePrivacySettings(context, updated)
            val user = UserPreferences.getUserSync(context)
            if (user != null) {
                CloudRealtimeService.savePrivacySettings(user.userId, updated)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.privacyTitle,
                        color = TextOffWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = strings.cancel,
                            tint = TextOffWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDarkEspresso
                )
            )
        },
        containerColor = BackgroundDarkEspresso
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Chat & Call Protection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkWarm)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = strings.privacyAndSecurity,
                        color = AccentWarmPeach,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    // Silence Non-Contact Calls
                    PrivacyToggleRow(
                        title = strings.silenceNonContactCalls,
                        subtitle = strings.silenceNonContactCallsDesc,
                        isChecked = silenceNonContacts,
                        onCheckedChange = {
                            silenceNonContacts = it
                            saveUpdatedPrivacy(newSilence = it)
                        }
                    )

                    // Archive Unknown Chats
                    PrivacyToggleRow(
                        title = strings.archiveUnknownChats,
                        subtitle = strings.archiveUnknownChatsDesc,
                        isChecked = archiveUnknown,
                        onCheckedChange = {
                            archiveUnknown = it
                            saveUpdatedPrivacy(newArchive = it)
                        }
                    )

                    // Sensitive Content Filter
                    PrivacyToggleRow(
                        title = strings.sensitiveContentFilter,
                        subtitle = strings.sensitiveContentFilterDesc,
                        isChecked = sensitiveFilter,
                        onCheckedChange = {
                            sensitiveFilter = it
                            saveUpdatedPrivacy(newFilter = it)
                        }
                    )

                    // Show Age
                    PrivacyToggleRow(
                        title = strings.showAge,
                        subtitle = strings.showAgeDesc,
                        isChecked = showAge,
                        onCheckedChange = {
                            showAge = it
                            saveUpdatedPrivacy(newShowAge = it)
                        }
                    )
                }
            }

            // Section 2: Account Security & Logins
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkWarm)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, strings.activeLoginsDesc, Toast.LENGTH_SHORT).show()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = AccentWarmPeach,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.activeLogins,
                                color = TextOffWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = strings.activeLoginsDesc,
                                color = TextMutedWarmGrey,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, strings.blockedAccountsDesc, Toast.LENGTH_SHORT).show()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = AccentWarmPeach,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.blockedAccounts,
                                color = TextOffWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = strings.blockedAccountsDesc,
                                color = TextMutedWarmGrey,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Section 3: Danger Zone (Delete Account)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeleteDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkWarm)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = DestructiveCoralRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.deleteAccount,
                            color = DestructiveCoralRed,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.deleteAccountDesc,
                            color = TextMutedWarmGrey,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = {
                Text(
                    text = strings.deleteAccountConfirmTitle,
                    color = TextOffWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = strings.deleteAccountConfirmMsg,
                    color = TextMutedWarmGrey
                )
            },
            containerColor = SurfaceDarkWarmElevated,
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            try {
                                val user = UserPreferences.getUserSync(context)
                                if (user != null) {
                                    CloudRealtimeService.deleteAccount(user.userId, user.username)
                                }
                                UserPreferences.clear(context)
                                showDeleteDialog = false
                                navController.navigate("auth") {
                                    popUpTo(0) { inclusive = true }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, strings.error, Toast.LENGTH_SHORT).show()
                            } finally {
                                isDeleting = false
                            }
                        }
                    }
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            color = DestructiveCoralRed,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(text = strings.delete, color = DestructiveCoralRed, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = strings.cancel, color = TextOffWhite)
                }
            }
        )
    }
}

@Composable
fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextOffWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = TextMutedWarmGrey,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentWarmPeach,
                uncheckedThumbColor = TextMutedWarmGrey,
                uncheckedTrackColor = SurfaceDarkWarmElevated
            )
        )
    }
}

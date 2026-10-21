package com.android.zilbio.ui.screens.admin

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.User
import com.android.zilbio.domain.model.UserPermissions
import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.ui.components.VerifiedBadge
import com.android.zilbio.ui.screens.friends.AvatarInitials
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavyElevated
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    navController: NavController? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)

    var usersList by remember { mutableStateOf<List<User>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var expandedUserId by remember { mutableStateOf<String?>(null) }

    fun loadUsers() {
        scope.launch {
            isLoading = true
            usersList = CloudRealtimeService.getAllUsers()
            isLoading = false
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser?.isOwnerOrAdmin() == true) {
            loadUsers()
        }
    }

    // Access control: Only @tugkancr or admin accounts
    if (currentUser != null && currentUser?.isOwnerOrAdmin() != true) {
        Scaffold(
            containerColor = BackgroundMidnightNavy
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Access Denied",
                            tint = DestructiveCoralRed,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Erişim Reddedildi",
                            color = TextOffWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bu ekran yalnızca @tugkancr ve yetkili yöneticilere açıktır.",
                            color = TextMutedSlateGrey,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNeonLime)
                        ) {
                            Text("Geri Dön", color = BackgroundMidnightNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val filteredUsers = remember(usersList, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            usersList
        } else {
            usersList.filter {
                it.username.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q) ||
                it.userId.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        containerColor = BackgroundMidnightNavy,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Yönetici Paneli",
                            color = TextOffWhite,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        AdminBadge(customRole = "Owner")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextOffWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadUsers() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = AccentNeonLime
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceSlateNavy)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Kullanıcı ara (kullanıcı adı, isim, ID)", color = TextMutedSlateGrey) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMutedSlateGrey)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Temizle", tint = TextMutedSlateGrey)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentNeonLime,
                    unfocusedBorderColor = BorderSlateNavy,
                    focusedTextColor = TextOffWhite,
                    unfocusedTextColor = TextOffWhite,
                    focusedContainerColor = SurfaceSlateNavy,
                    unfocusedContainerColor = SurfaceSlateNavy
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Users Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kayıtlı Kullanıcılar (${filteredUsers.size})",
                    color = TextMutedSlateGrey,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AccentNeonLime
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Users List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredUsers, key = { it.userId }) { user ->
                    val isExpanded = expandedUserId == user.userId
                    UserAdminCard(
                        user = user,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedUserId = if (isExpanded) null else user.userId
                        },
                        onSavePermissions = { updatedPermissions, updatedRole ->
                            scope.launch {
                                val success = CloudRealtimeService.updateUserPermissions(
                                    targetUserId = user.userId,
                                    permissions = updatedPermissions,
                                    role = updatedRole
                                )
                                if (success) {
                                    Toast.makeText(context, "${user.username} yetkileri güncellendi", Toast.LENGTH_SHORT).show()
                                    loadUsers()
                                } else {
                                    Toast.makeText(context, "Güncelleme başarısız oldu", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserAdminCard(
    user: User,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSavePermissions: (UserPermissions, String) -> Unit
) {
    var role by remember(user) { mutableStateOf(user.role) }
    var permissions by remember(user) { mutableStateOf(user.permissions) }
    var customRoleLabel by remember(user) { mutableStateOf(user.permissions.customRoleLabel ?: "") }
    var roleDropdownExpanded by remember { mutableStateOf(false) }

    val rolesList = listOf("user", "vip", "moderator", "admin", "owner")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (permissions.isAccountSuspended) DestructiveCoralRed else if (user.isOwnerOrAdmin()) AccentNeonLime.copy(alpha = 0.5f) else BorderSlateNavy,
                shape = RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarInitials(
                    name = user.displayName.ifEmpty { user.username },
                    size = 46.dp,
                    avatarBase64 = user.avatarBase64
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.displayName.ifEmpty { user.username },
                            color = TextOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (user.isOwnerOrAdmin()) {
                            AdminBadge(customRole = user.permissions.customRoleLabel, isCompact = true)
                        } else if (user.permissions.isVerifiedBadge) {
                            VerifiedBadge(isCompact = true)
                        }
                    }

                    Text(
                        text = "@${user.username} • ID: ${user.userId}",
                        color = TextMutedSlateGrey,
                        fontSize = 12.sp
                    )

                    if (permissions.isAccountSuspended) {
                        Text(
                            text = "⛔ HESAP DONDURULDU / ASKIYA ALINDI",
                            color = DestructiveCoralRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Detay",
                        tint = TextOffWhite
                    )
                }
            }

            // Expandable Permission Controls
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderSlateNavy)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Rol ve Yetki Yapılandırması",
                        color = AccentNeonLime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Role Dropdown
                    ExposedDropdownMenuBox(
                        expanded = roleDropdownExpanded,
                        onExpandedChange = { roleDropdownExpanded = !roleDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = role.uppercase(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sistem Rolü", color = TextMutedSlateGrey) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentNeonLime,
                                unfocusedBorderColor = BorderSlateNavy,
                                focusedTextColor = TextOffWhite,
                                unfocusedTextColor = TextOffWhite,
                                focusedContainerColor = SurfaceSlateNavyElevated,
                                unfocusedContainerColor = SurfaceSlateNavyElevated
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = roleDropdownExpanded,
                            onDismissRequest = { roleDropdownExpanded = false },
                            modifier = Modifier.background(SurfaceSlateNavyElevated)
                        ) {
                            rolesList.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r.uppercase(), color = TextOffWhite) },
                                    onClick = {
                                        role = r
                                        if (r in listOf("owner", "admin")) {
                                            permissions = permissions.copy(isAdmin = true)
                                        }
                                        roleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom Role Label
                    OutlinedTextField(
                        value = customRoleLabel,
                        onValueChange = { customRoleLabel = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Özel Rol Etiketi (opsiyonel)", color = TextMutedSlateGrey) },
                        placeholder = { Text("Örn: VIP Destek, Kurucu", color = TextMutedSlateGrey) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentNeonLime,
                            unfocusedBorderColor = BorderSlateNavy,
                            focusedTextColor = TextOffWhite,
                            unfocusedTextColor = TextOffWhite,
                            focusedContainerColor = SurfaceSlateNavyElevated,
                            unfocusedContainerColor = SurfaceSlateNavyElevated
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Granular Switches
                    PermissionToggleRow("Yönetici Yetkisi (isAdmin)", permissions.isAdmin) {
                        permissions = permissions.copy(isAdmin = it)
                    }
                    PermissionToggleRow("Profil Düzenleme", permissions.canEditProfile) {
                        permissions = permissions.copy(canEditProfile = it)
                    }
                    PermissionToggleRow("Kullanıcı Adı Değiştirme", permissions.canChangeUsername) {
                        permissions = permissions.copy(canChangeUsername = it)
                    }
                    PermissionToggleRow("Mesaj Gönderebilir", permissions.canSendMessages) {
                        permissions = permissions.copy(canSendMessages = it)
                    }
                    PermissionToggleRow("Medya/Ek Gönderebilir", permissions.canSendMedia) {
                        permissions = permissions.copy(canSendMedia = it)
                    }
                    PermissionToggleRow("Arama Başlatabilir", permissions.canStartCalls) {
                        permissions = permissions.copy(canStartCalls = it)
                    }
                    PermissionToggleRow("Grup/Konferans Başlatabilir", permissions.canCreateConference) {
                        permissions = permissions.copy(canCreateConference = it)
                    }
                    PermissionToggleRow("Sınırsız Çeviri Kotası", permissions.unlimitedTranslationQuota) {
                        permissions = permissions.copy(unlimitedTranslationQuota = it)
                    }
                    PermissionToggleRow("Doğrulanmış Hesap Rozeti", permissions.isVerifiedBadge) {
                        permissions = permissions.copy(isVerifiedBadge = it)
                    }

                    // Suspension Switch (Destructive)
                    PermissionToggleRow(
                        title = "Hesabı Askıya Al / Dondur",
                        checked = permissions.isAccountSuspended,
                        isDestructive = true
                    ) {
                        permissions = permissions.copy(isAccountSuspended = it)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Save Button
                    Button(
                        onClick = {
                            val finalPerm = permissions.copy(
                                customRoleLabel = customRoleLabel.trim().ifEmpty { null }
                            )
                            onSavePermissions(finalPerm, role)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentNeonLime),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = BackgroundMidnightNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Yetkileri ve Rolü Kaydet",
                            color = BackgroundMidnightNavy,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    checked: Boolean,
    isDestructive: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (isDestructive && checked) DestructiveCoralRed else TextOffWhite,
            fontSize = 13.sp,
            fontWeight = if (isDestructive) FontWeight.Bold else FontWeight.Normal
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = if (isDestructive) DestructiveCoralRed else AccentNeonLime,
                checkedTrackColor = if (isDestructive) DestructiveCoralRed.copy(alpha = 0.3f) else AccentNeonLime.copy(alpha = 0.3f),
                uncheckedThumbColor = TextMutedSlateGrey,
                uncheckedTrackColor = SurfaceSlateNavyElevated
            )
        )
    }
}

package com.android.zilbio.ui.screens.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.FriendRequest
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.ui.components.VerifiedBadge
import com.android.zilbio.ui.theme.AccentAmberOrange
import com.android.zilbio.ui.theme.BackgroundAlabasterCream
import com.android.zilbio.ui.theme.BorderWarmSubtle
import com.android.zilbio.ui.theme.CallAcceptGreen
import com.android.zilbio.ui.theme.CallEndCoralRed
import com.android.zilbio.ui.theme.OnlineStatusGreen
import com.android.zilbio.ui.theme.OutgoingBubblePeach
import com.android.zilbio.ui.theme.SurfaceCardWhite
import com.android.zilbio.ui.theme.TextPrimaryEspresso
import com.android.zilbio.ui.theme.TextSecondaryWarmGrey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    navController: NavController? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() },
    onNavigateToChat: (friendId: String) -> Unit = { friendId -> navController?.navigate("chat/$friendId") },
    onNavigateToCall: (friendId: String) -> Unit = { friendId -> navController?.navigate("call/$friendId") },
    onNavigateToAddFriend: () -> Unit = { navController?.navigate("addFriend") }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0: Arkadaşlar, 1: Gelen İstekler, 2: Gönderilen
    var friendsList by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var incomingList by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var sentList by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Friend options dialog state
    var selectedFriendForOptions by remember { mutableStateOf<Friend?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val refreshData: () -> Unit = {
        val uid = currentUser?.userId
        if (!uid.isNullOrEmpty()) {
            coroutineScope.launch {
                isLoading = true
                try {
                    friendsList = CloudRealtimeService.getFriends(uid)
                    incomingList = CloudRealtimeService.getIncomingRequests(uid)
                    sentList = CloudRealtimeService.getSentRequests(uid)
                } catch (e: Exception) {
                    // Gracefully ignore error and retain last state
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(currentUser?.userId) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Arkadaşlar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryEspresso
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimaryEspresso
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundAlabasterCream
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddFriend,
                containerColor = AccentAmberOrange,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Arkadaş Ekle"
                )
            }
        },
        containerColor = BackgroundAlabasterCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Three Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = BackgroundAlabasterCream,
                contentColor = AccentAmberOrange,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = AccentAmberOrange,
                        height = 3.dp
                    )
                },
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderWarmSubtle)
                    )
                }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Text(
                            text = "Arkadaşlar (${friendsList.size})",
                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTabIndex == 0) AccentAmberOrange else TextSecondaryWarmGrey,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Gelen",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == 1) AccentAmberOrange else TextSecondaryWarmGrey,
                                fontSize = 13.sp
                            )
                            if (incomingList.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(AccentAmberOrange)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${incomingList.size}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = {
                        Text(
                            text = "Gönderilen (${sentList.size})",
                            fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTabIndex == 2) AccentAmberOrange else TextSecondaryWarmGrey,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentAmberOrange, strokeWidth = 2.dp)
                }
            } else {
                when (selectedTabIndex) {
                    0 -> FriendsListTab(
                        friends = friendsList,
                        onFriendClick = { friend -> onNavigateToChat(friend.userId) },
                        onFriendLongClick = { friend -> selectedFriendForOptions = friend }
                    )
                    1 -> IncomingRequestsTab(
                        requests = incomingList,
                        onAccept = { request ->
                            val user = currentUser ?: return@IncomingRequestsTab
                            coroutineScope.launch {
                                val senderUser = CloudRealtimeService.searchUserById(request.senderId)
                                val senderLang = senderUser?.language?.ifBlank { request.senderLanguage }
                                    ?: request.senderLanguage.ifBlank { "tr" }
                                val myFriend = Friend(
                                    userId = request.senderId,
                                    username = request.senderUsername,
                                    displayName = request.senderDisplayName,
                                    avatarBase64 = request.senderAvatarBase64,
                                    status = "online",
                                    language = senderLang
                                )
                                val theirFriend = Friend(
                                    userId = user.userId,
                                    username = user.username,
                                    displayName = user.displayName,
                                    avatarBase64 = user.avatarBase64,
                                    status = "online",
                                    language = user.language
                                )
                                CloudRealtimeService.acceptFriendRequest(
                                    myUserId = user.userId,
                                    friendUserId = request.senderId,
                                    myFriend = myFriend,
                                    theirFriend = theirFriend
                                )
                                refreshData()
                            }
                        },
                        onDecline = { request ->
                            val user = currentUser ?: return@IncomingRequestsTab
                            coroutineScope.launch {
                                CloudRealtimeService.declineFriendRequest(
                                    myUserId = user.userId,
                                    senderId = request.senderId
                                )
                                refreshData()
                            }
                        }
                    )
                    2 -> SentRequestsTab(
                        requests = sentList
                    )
                }
            }
        }
    }

    // Friend Options Dialog
    selectedFriendForOptions?.let { friend ->
        AlertDialog(
            onDismissRequest = { selectedFriendForOptions = null },
            title = {
                Text(
                    text = friend.displayName,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryEspresso
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "@${friend.username}", color = TextSecondaryWarmGrey, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val fId = friend.userId
                            selectedFriendForOptions = null
                            onNavigateToCall(fId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OutgoingBubblePeach),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = AccentAmberOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Arama Yap", color = AccentAmberOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            val fId = friend.userId
                            selectedFriendForOptions = null
                            onNavigateToChat(fId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OutgoingBubblePeach),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = AccentAmberOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sohbet Aç", color = AccentAmberOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardWhite),
                        border = BorderStroke(1.dp, CallEndCoralRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = CallEndCoralRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Arkadaşlıktan Çıkar", color = CallEndCoralRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedFriendForOptions = null }) {
                    Text("Kapat", color = TextSecondaryWarmGrey)
                }
            },
            containerColor = SurfaceCardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Confirmation dialog for removing friend
    if (showDeleteConfirmDialog && selectedFriendForOptions != null) {
        val friendToDelete = selectedFriendForOptions!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Arkadaşı Kaldır", fontWeight = FontWeight.Bold, color = TextPrimaryEspresso) },
            text = { Text("${friendToDelete.displayName} kişisini arkadaş listenizden kaldırmak istediğinize emin misiniz?") },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = currentUser?.userId
                        if (!uid.isNullOrEmpty()) {
                            coroutineScope.launch {
                                CloudRealtimeService.declineFriendRequest(uid, friendToDelete.userId)
                                showDeleteConfirmDialog = false
                                selectedFriendForOptions = null
                                refreshData()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CallEndCoralRed)
                ) {
                    Text("Kaldır", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("İptal", color = TextSecondaryWarmGrey)
                }
            },
            containerColor = SurfaceCardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FriendsListTab(
    friends: List<Friend>,
    onFriendClick: (Friend) -> Unit,
    onFriendLongClick: (Friend) -> Unit
) {
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PersonOutline,
                    contentDescription = null,
                    tint = TextSecondaryWarmGrey,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Henüz arkadaş eklemediniz",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryEspresso
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Yeni arkadaşlar eklemek için aşağıdaki '+' butonuna dokunun.",
                    fontSize = 13.sp,
                    color = TextSecondaryWarmGrey,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(friends, key = { it.userId }) { friend ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onFriendClick(friend) },
                            onLongClick = { onFriendLongClick(friend) }
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
                    border = BorderStroke(1.dp, BorderWarmSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar with Initials & Online Dot
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AvatarInitials(name = friend.displayName, size = 46)
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (friend.status == "online") OnlineStatusGreen else Color.LightGray)
                                    .padding(2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = friend.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryEspresso
                                )
                                if (friend.isOwnerOrAdmin()) {
                                    AdminBadge(customRole = friend.permissions.customRoleLabel, isCompact = true)
                                } else if (friend.permissions.isVerifiedBadge) {
                                    VerifiedBadge(isCompact = true)
                                }
                                val lang = Language.fromCode(friend.language)
                                Text(text = lang.flag, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "@${friend.username}",
                                fontSize = 12.sp,
                                color = TextSecondaryWarmGrey
                            )
                        }

                        IconButton(onClick = { onFriendLongClick(friend) }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Seçenekler",
                                tint = TextSecondaryWarmGrey
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingRequestsTab(
    requests: List<FriendRequest>,
    onAccept: (FriendRequest) -> Unit,
    onDecline: (FriendRequest) -> Unit
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Bekleyen gelen arkadaşlık isteği yok.",
                fontSize = 14.sp,
                color = TextSecondaryWarmGrey,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(requests, key = { it.senderId }) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
                    border = BorderStroke(1.dp, BorderWarmSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarInitials(name = req.senderDisplayName, size = 44)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = req.senderDisplayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryEspresso
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@${req.senderUsername}",
                                    fontSize = 12.sp,
                                    color = TextSecondaryWarmGrey
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onAccept(req) },
                                colors = ButtonDefaults.buttonColors(containerColor = CallAcceptGreen),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kabul Et", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onDecline(req) },
                                colors = ButtonDefaults.buttonColors(containerColor = CallEndCoralRed),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reddet", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentRequestsTab(
    requests: List<FriendRequest>
) {
    if (requests.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Gönderilen arkadaşlık isteği yok.",
                fontSize = 14.sp,
                color = TextSecondaryWarmGrey,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(requests, key = { it.senderId + it.timestamp }) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
                    border = BorderStroke(1.dp, BorderWarmSubtle),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarInitials(name = req.senderDisplayName.ifEmpty { "Kullanıcı" }, size = 44)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = req.senderDisplayName.ifEmpty { "İstek Gönderildi" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryEspresso
                            )
                            if (req.senderUsername.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "@${req.senderUsername}",
                                    fontSize = 12.sp,
                                    color = TextSecondaryWarmGrey
                                )
                            }
                        }

                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(OutgoingBubblePeach)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Onay Bekleniyor",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentAmberOrange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarInitials(
    name: String,
    size: Dp = 44.dp,
    avatarBase64: String? = null
) {
    val bitmap = remember(avatarBase64) {
        avatarBase64?.let { base64 ->
            try {
                val decoded = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, BorderWarmSubtle, CircleShape)
        )
    } else {
        val initials = name.trim().split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifEmpty { "U" }

        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(OutgoingBubblePeach)
                .border(1.dp, BorderWarmSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = (size.value / 2.6).sp,
                fontWeight = FontWeight.Bold,
                color = AccentAmberOrange
            )
        }
    }
}

@Composable
fun AvatarInitials(
    name: String,
    size: Int,
    avatarBase64: String? = null
) {
    AvatarInitials(name = name, size = size.dp, avatarBase64 = avatarBase64)
}

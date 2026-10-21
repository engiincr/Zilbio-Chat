package com.android.zilbio.ui.screens.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.FriendRequest
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.User
import com.android.zilbio.ui.theme.AccentAmberOrange
import com.android.zilbio.ui.theme.BackgroundAlabasterCream
import com.android.zilbio.ui.theme.BorderWarmSubtle
import com.android.zilbio.ui.theme.CallEndCoralRed
import com.android.zilbio.ui.theme.InputFieldBackground
import com.android.zilbio.ui.theme.OutgoingBubblePeach
import com.android.zilbio.ui.theme.SurfaceCardWhite
import com.android.zilbio.ui.theme.TextPrimaryEspresso
import com.android.zilbio.ui.theme.TextSecondaryWarmGrey
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendScreen(
    navController: NavController? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    val searchHistory by UserPreferences.getSearchHistory(context).collectAsState(initial = emptyList())

    var searchModeIndex by remember { mutableIntStateOf(0) } // 0: Kullanıcı Adı, 1: ID ile Ara
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchedUser by remember { mutableStateOf<User?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    var existingFriends by remember { mutableStateOf<List<Friend>>(emptyList()) }
    var sentRequests by remember { mutableStateOf<List<FriendRequest>>(emptyList()) }
    var isRequestSentRecently by remember { mutableStateOf(false) }
    var isSendingRequest by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.userId) {
        val uid = currentUser?.userId
        if (!uid.isNullOrEmpty()) {
            try {
                existingFriends = CloudRealtimeService.getFriends(uid)
                sentRequests = CloudRealtimeService.getSentRequests(uid)
            } catch (e: Exception) {
                // Ignore load error
            }
        }
    }

    val performSearch: (String?) -> Unit = { queryOverride ->
        val query = (queryOverride ?: searchQuery).trim()
        if (query.isNotEmpty()) {
            if (queryOverride != null) searchQuery = queryOverride
            focusManager.clearFocus()
            coroutineScope.launch {
                UserPreferences.addSearchQuery(context, query)
                isSearching = true
                hasSearched = true
                searchedUser = null
                isRequestSentRecently = false
                try {
                    val result = if (searchModeIndex == 0) {
                        CloudRealtimeService.searchUserByUsername(query) ?: CloudRealtimeService.searchUserById(query)
                    } else {
                        CloudRealtimeService.searchUserById(query) ?: CloudRealtimeService.searchUserByUsername(query)
                    }
                    searchedUser = result
                } catch (e: Exception) {
                    searchedUser = null
                } finally {
                    isSearching = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Arkadaş Ekle",
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundAlabasterCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Search Mode Tabs
            TabRow(
                selectedTabIndex = searchModeIndex,
                containerColor = BackgroundAlabasterCream,
                contentColor = AccentAmberOrange,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[searchModeIndex]),
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
                    selected = searchModeIndex == 0,
                    onClick = {
                        searchModeIndex = 0
                        searchQuery = ""
                        hasSearched = false
                        searchedUser = null
                    },
                    text = {
                        Text(
                            text = "Kullanıcı Adı",
                            fontWeight = if (searchModeIndex == 0) FontWeight.Bold else FontWeight.Medium,
                            color = if (searchModeIndex == 0) AccentAmberOrange else TextSecondaryWarmGrey,
                            fontSize = 14.sp
                        )
                    }
                )
                Tab(
                    selected = searchModeIndex == 1,
                    onClick = {
                        searchModeIndex = 1
                        searchQuery = ""
                        hasSearched = false
                        searchedUser = null
                    },
                    text = {
                        Text(
                            text = "ID ile Ara",
                            fontWeight = if (searchModeIndex == 1) FontWeight.Bold else FontWeight.Medium,
                            color = if (searchModeIndex == 1) AccentAmberOrange else TextSecondaryWarmGrey,
                            fontSize = 14.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Search TextField
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = if (searchModeIndex == 0) "Kullanıcı adı girin (örn: ahmet123)" else "Kullanıcı ID girin (örn: TL-ABC123)",
                        fontSize = 13.sp,
                        color = TextSecondaryWarmGrey
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = AccentAmberOrange
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            hasSearched = false
                            searchedUser = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Temizle",
                                tint = TextSecondaryWarmGrey
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch(null) }),
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

            Button(
                onClick = { performSearch(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmberOrange),
                enabled = searchQuery.trim().isNotEmpty() && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Kullanıcıyı Ara", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Recent Searches Chips
            if (searchHistory.isNotEmpty() && !hasSearched) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Son Aramalar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryWarmGrey
                    )
                    Text(
                        text = "Temizle",
                        fontSize = 12.sp,
                        color = AccentAmberOrange,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            coroutineScope.launch { UserPreferences.clearSearchHistory(context) }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(searchHistory) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = InputFieldBackground,
                            border = BorderStroke(1.dp, BorderWarmSubtle),
                            modifier = Modifier.clickable { performSearch(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = item, fontSize = 12.sp, color = TextPrimaryEspresso)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Sil",
                                    tint = TextSecondaryWarmGrey,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                UserPreferences.removeSearchQuery(context, item)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Search Results Section
            if (hasSearched && !isSearching) {
                val foundUser = searchedUser
                if (foundUser != null) {
                    val isSelf = foundUser.userId == currentUser?.userId
                    val isAlreadyFriend = existingFriends.any { it.userId == foundUser.userId }
                    val isAlreadySent = sentRequests.any { it.senderId == foundUser.userId } || isRequestSentRecently

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
                        border = BorderStroke(1.dp, BorderWarmSubtle),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Avatar
                            AvatarInitials(name = foundUser.displayName, size = 64)

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = foundUser.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryEspresso
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = "@${foundUser.username}",
                                fontSize = 13.sp,
                                color = TextSecondaryWarmGrey
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = BackgroundAlabasterCream,
                                border = BorderStroke(1.dp, BorderWarmSubtle)
                            ) {
                                Text(
                                    text = "ID: ${foundUser.userId}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondaryWarmGrey,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val lang = Language.fromCode(foundUser.language)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = lang.flag, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = lang.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondaryWarmGrey
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Action Button on Found User
                            when {
                                isSelf -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = InputFieldBackground,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Bu hesap sizin profiliniz",
                                            fontSize = 13.sp,
                                            color = TextSecondaryWarmGrey,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                isAlreadyFriend -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = OutgoingBubblePeach,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Zaten Arkadaşsınız ✓",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentAmberOrange,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                isAlreadySent -> {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            disabledContainerColor = OutgoingBubblePeach,
                                            disabledContentColor = AccentAmberOrange
                                        )
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "İstek Gönderildi (Onay Bekleniyor)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            val myUser = currentUser ?: return@Button
                                            coroutineScope.launch {
                                                isSendingRequest = true
                                                try {
                                                    val request = FriendRequest(
                                                        senderId = myUser.userId,
                                                        senderUsername = myUser.username,
                                                        senderDisplayName = myUser.displayName,
                                                        senderAvatarBase64 = myUser.avatarBase64,
                                                        senderLanguage = myUser.language.ifBlank { "tr" },
                                                        status = "PENDING",
                                                        timestamp = System.currentTimeMillis()
                                                    )
                                                    val success = CloudRealtimeService.sendFriendRequest(
                                                        senderId = myUser.userId,
                                                        receiverId = foundUser.userId,
                                                        request = request
                                                    )
                                                    if (success) {
                                                        isRequestSentRecently = true
                                                        snackbarHostState.showSnackbar("Arkadaşlık isteği gönderildi ✓")
                                                    } else {
                                                        snackbarHostState.showSnackbar("İstek gönderilemedi. Tekrar deneyin.")
                                                    }
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar("Hata: ${e.message}")
                                                } finally {
                                                    isSendingRequest = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmberOrange),
                                        enabled = !isSendingRequest
                                    ) {
                                        if (isSendingRequest) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Arkadaş Ekle",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Not found state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = TextSecondaryWarmGrey,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Kullanıcı bulunamadı / User not found",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryEspresso,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Lütfen kullanıcı adını veya ID numarasını doğru girdiğinizden emin olun.",
                                fontSize = 12.sp,
                                color = TextSecondaryWarmGrey,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

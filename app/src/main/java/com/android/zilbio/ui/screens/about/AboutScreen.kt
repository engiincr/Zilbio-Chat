package com.android.zilbio.ui.screens.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.updater.GitHubAutoUpdater
import com.android.zilbio.data.updater.UpdateState
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.OnlineStatusGreen
import com.android.zilbio.ui.theme.QuotaWarningAmber
import com.android.zilbio.ui.theme.SurfaceMidnightDark
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavyElevated
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() }
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val updateState by GitHubAutoUpdater.updateState.collectAsState()
    val isDownloading by GitHubAutoUpdater.isDownloading.collectAsState()
    val downloadProgress by GitHubAutoUpdater.downloadProgress.collectAsState()
    val installedSha by UserPreferences.getInstalledCommitSha(context).collectAsState(initial = "")
    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)

    val currentLang = currentUser?.language?.lowercase() ?: "tr"

    // Description localized by user language preference
    val appDescription = when (currentLang) {
        "de" -> "Zilbio ist eine KI-gestützte Echtzeit-Übersetzungs-, Sprach-/Videokommunikations- und Messaging-Plattform der nächsten Generation, die Sprachbarrieren überwindet."
        "en" -> "Zilbio is a next-generation real-time AI translation, voice/video communication, and messaging platform designed to break down language barriers. It empowers people speaking different languages to chat and talk seamlessly in their native tongues with zero latency."
        else -> "Zilbio, dil sınırlarını ortadan kaldırmak için geliştirilmiş yeni nesil gerçek zamanlı yapay zeka çeviri, sesli/görüntülü iletişim ve mesajlaşma platformudur. Farklı dilleri konuşan insanların kendi ana dillerinde yazışarak ve konuşarak sıfır gecikmeyle anlaşmasını sağlar."
    }

    fun openBrowserUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = BackgroundMidnightNavy,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.aboutTitle,
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
                actions = {
                    IconButton(onClick = { GitHubAutoUpdater.checkForUpdates(context, manual = true) }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = strings.checkForUpdates,
                            tint = AccentNeonLime
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundMidnightNavy
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ==========================================
            // 1. TOP SIGNATURE HEADER (Prominent Glowing Modern Badge)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = AccentNeonLime),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceSlateNavyElevated,
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(AccentNeonLime, Color(0xFFF2B896), AccentNeonLime)))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚡",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dev & Software by Engin ⚡",
                            color = AccentNeonLime,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "2026 • Open Source Developer",
                        color = Color(0xFFF2B896),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // 2. APP IDENTITY & PURPOSE SECTION
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Zilbio Modern Glowing Logo
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.android.zilbio.R.drawable.zilbio_logo_transparent),
                        contentDescription = "Zilbio Logo",
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Zilbio",
                        color = TextOffWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceMidnightDark,
                        border = BorderStroke(1.dp, BorderSlateNavy)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = AccentNeonLime,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Version ${GitHubAutoUpdater.DEFAULT_VERSION_NAME}",
                                color = AccentNeonLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = appDescription,
                        color = TextOffWhite.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Key Architectural Highlights
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FeatureMiniPill(icon = Icons.Default.Language, label = "Zero-Latency")
                        FeatureMiniPill(icon = Icons.Default.Speed, label = "PiP & WebRTC")
                        FeatureMiniPill(icon = Icons.Default.Security, label = "P2P Privacy")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 3. INTERACTIVE GITHUB & OPEN SOURCE CARDS
            // ==========================================
            Text(
                text = "Kaynak Kod & Güncelleme",
                color = AccentNeonLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 8.dp)
            )

            // GitHub Repository Card
            InteractiveLinkCard(
                icon = Icons.Default.Code,
                title = "GitHub Repository",
                subtitle = "github.com/engiincr/Zilbio-Chat",
                badgeText = "Açık Kaynak",
                onClick = { openBrowserUrl(GitHubAutoUpdater.GITHUB_REPO_URL) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Source Code ZIP Archive Card
            InteractiveLinkCard(
                icon = Icons.Default.FolderZip,
                title = "Kaynak Kod Arşivi (ZIP)",
                subtitle = "Main branch son sürüm kodlarını indir",
                badgeText = "main.zip",
                onClick = { openBrowserUrl(GitHubAutoUpdater.GITHUB_MAIN_ZIP_URL) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // OTA Auto-Update Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(AccentNeonLime.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = AccentNeonLime,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GitHub OTA Otomatik Güncelleme",
                                color = TextOffWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val statusDescription = when (val state = updateState) {
                                is UpdateState.Checking -> "GitHub API kontrol ediliyor..."
                                is UpdateState.Downloading -> "İndiriliyor: %${state.progressPercent}"
                                is UpdateState.ReadyToInstall -> "Yeni paket kuruluma hazır"
                                is UpdateState.UpdateAvailable -> "Yeni commit: ${state.latestSha.take(7)}"
                                is UpdateState.UpToDate -> "Sistem en güncel sürümde"
                                is UpdateState.Error -> state.message
                                UpdateState.Idle -> if (installedSha.isNotEmpty()) "Kurulu SHA: ${installedSha.take(7)}" else "GitHub CI/CD otomatik senkronizasyon"
                            }
                            Text(
                                text = statusDescription,
                                color = TextMutedSlateGrey,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Download Progress Bar
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentNeonLime,
                            trackColor = SurfaceMidnightDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Yeni APK indiriliyor...", color = AccentNeonLime, fontSize = 11.sp)
                            Text(text = "%$downloadProgress", color = TextOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Button based on state
                    when (val state = updateState) {
                        is UpdateState.Checking -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceSlateNavyElevated)
                            ) {
                                CircularProgressIndicator(
                                    color = AccentNeonLime,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kontrol Ediliyor...", color = TextOffWhite)
                            }
                        }
                        is UpdateState.UpdateAvailable -> {
                            Button(
                                onClick = {
                                    GitHubAutoUpdater.startDownload(
                                        context = context,
                                        downloadUrl = state.downloadUrl,
                                        latestSha = state.latestSha
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentNeonLime)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = BackgroundMidnightNavy)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Şimdi İndir ve Güncelle (${state.latestSha.take(7)})",
                                    color = BackgroundMidnightNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            Button(
                                onClick = {
                                    GitHubAutoUpdater.installApk(context, state.apkFile, state.latestSha)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OnlineStatusGreen)
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = BackgroundMidnightNavy)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Yüklemeyi Başlat", color = BackgroundMidnightNavy, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { GitHubAutoUpdater.checkForUpdates(context, manual = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceSlateNavyElevated),
                                border = BorderStroke(1.dp, BorderSlateNavy)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentNeonLime, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = strings.checkForUpdates, color = TextOffWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ==========================================
            // 4. BOTTOM SIGNATURE FOOTER (Pinned Developer Signature)
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceMidnightDark,
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Dev & Software by Engin ⚡",
                        color = AccentNeonLime,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "2026 | Open Source Developer",
                        color = TextMutedSlateGrey,
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureMiniPill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceMidnightDark,
        border = BorderStroke(0.5.dp, BorderSlateNavy)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AccentNeonLime, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, color = TextOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InteractiveLinkCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
        border = BorderStroke(1.dp, BorderSlateNavy)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentNeonLime.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentNeonLime,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextOffWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMutedSlateGrey,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SurfaceMidnightDark,
                border = BorderStroke(0.5.dp, BorderSlateNavy)
            ) {
                Text(
                    text = badgeText,
                    color = AccentNeonLime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = TextMutedSlateGrey,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

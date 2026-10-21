package com.android.zilbio.ui.screens.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.SurfaceMidnightDark
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavyElevated
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    navController: NavController? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var totalCacheBytes by remember { mutableLongStateOf(0L) }
    var voiceCacheBytes by remember { mutableLongStateOf(0L) }
    var mediaCacheBytes by remember { mutableLongStateOf(0L) }
    var otherCacheBytes by remember { mutableLongStateOf(0L) }
    var isCalculating by remember { mutableStateOf(true) }

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showClearVoiceDialog by remember { mutableStateOf(false) }
    var showClearMediaDialog by remember { mutableStateOf(false) }

    fun refreshStorageSizes() {
        isCalculating = true
        scope.launch(Dispatchers.IO) {
            val cacheDir = context.cacheDir
            var total = 0L
            var voice = 0L
            var media = 0L
            var other = 0L

            fun calculateDir(file: File) {
                if (file.isDirectory) {
                    file.listFiles()?.forEach { child ->
                        calculateDir(child)
                    }
                } else if (file.isFile) {
                    val length = file.length()
                    total += length
                    val name = file.name.lowercase()
                    when {
                        name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".mp3") || name.contains("voice") -> {
                            voice += length
                        }
                        name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".mp4") -> {
                            media += length
                        }
                        else -> {
                            other += length
                        }
                    }
                }
            }

            if (cacheDir != null && cacheDir.exists()) {
                calculateDir(cacheDir)
            }

            val externalCache = context.externalCacheDir
            if (externalCache != null && externalCache.exists()) {
                calculateDir(externalCache)
            }

            withContext(Dispatchers.Main) {
                totalCacheBytes = total
                voiceCacheBytes = voice
                mediaCacheBytes = media
                otherCacheBytes = other
                isCalculating = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStorageSizes()
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format("%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    fun deleteFilesMatching(predicate: (File) -> Boolean) {
        scope.launch(Dispatchers.IO) {
            fun cleanDir(dir: File) {
                dir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        cleanDir(file)
                        if (file.listFiles().isNullOrEmpty()) {
                            file.delete()
                        }
                    } else if (file.isFile && predicate(file)) {
                        file.delete()
                    }
                }
            }

            context.cacheDir?.let { cleanDir(it) }
            context.externalCacheDir?.let { cleanDir(it) }

            refreshStorageSizes()
            snackbarHostState.showSnackbar("Seçilen önbellek başarıyla temizlendi.")
        }
    }

    Scaffold(
        containerColor = BackgroundMidnightNavy,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Depolama ve Veri",
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
                .padding(16.dp)
        ) {
            // Main Overview Card
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
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AccentNeonLime.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AccentNeonLime,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Toplam Zilbio Önbelleği",
                        color = TextMutedSlateGrey,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (isCalculating) {
                        CircularProgressIndicator(
                            color = AccentNeonLime,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = formatBytes(totalCacheBytes),
                            color = TextOffWhite,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showClearAllDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DestructiveCoralRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Tüm Önbelleği Temizle", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Kategori Ayrımı",
                color = AccentNeonLime,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Category Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Voice Notes Row
                    StorageCategoryRow(
                        icon = Icons.Default.Audiotrack,
                        title = "Sesli Mesajlar",
                        subtitle = "Geçici kaydedilen ve oynatılan ses notları",
                        sizeStr = formatBytes(voiceCacheBytes),
                        onClear = { showClearVoiceDialog = true }
                    )

                    HorizontalDivider(color = BorderSlateNavy, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Media Files Row
                    StorageCategoryRow(
                        icon = Icons.Default.Image,
                        title = "Fotoğraf & Belgeler",
                        subtitle = "Sohbetlerde gönderilen medya önbelleği",
                        sizeStr = formatBytes(mediaCacheBytes),
                        onClear = { showClearMediaDialog = true }
                    )

                    HorizontalDivider(color = BorderSlateNavy, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 12.dp))

                    // Other Temp Data Row
                    StorageCategoryRow(
                        icon = Icons.Default.Folder,
                        title = "Diğer Geçici Veriler",
                        subtitle = "Çeviri ve uygulama çalışma verileri",
                        sizeStr = formatBytes(otherCacheBytes),
                        onClear = {
                            deleteFilesMatching { file ->
                                val name = file.name.lowercase()
                                !name.endsWith(".m4a") && !name.endsWith(".aac") && !name.endsWith(".mp3") &&
                                        !name.endsWith(".jpg") && !name.endsWith(".png") && !name.endsWith(".mp4")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Info Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = SurfaceMidnightDark,
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = AccentNeonLime,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Önbelleği temizlemek sohbet geçmişinizi veya arkadaş listenizi silmez. Yalnızca cihazınızda saklanan geçici dosyaları kaldırarak yer açar.",
                        color = TextMutedSlateGrey,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    // Dialog: Clear All
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = "Tüm Önbelleği Temizle", color = TextOffWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Tüm geçici ses kayıtları, medya dosyaları ve önbellek silinecektir (${formatBytes(totalCacheBytes)}). Onaylıyor musunuz?",
                    color = TextMutedSlateGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        deleteFilesMatching { true }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveCoralRed)
                ) {
                    Text("Temizle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("İptal", color = TextOffWhite)
                }
            },
            containerColor = SurfaceSlateNavyElevated
        )
    }

    // Dialog: Clear Voice Notes
    if (showClearVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showClearVoiceDialog = false },
            title = { Text(text = "Ses Kayıtlarını Temizle", color = TextOffWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Önbelleğe alınmış tüm ses kayıtları silinecektir (${formatBytes(voiceCacheBytes)}).",
                    color = TextMutedSlateGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearVoiceDialog = false
                        deleteFilesMatching { file ->
                            val n = file.name.lowercase()
                            n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".mp3") || n.contains("voice")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveCoralRed)
                ) {
                    Text("Temizle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearVoiceDialog = false }) {
                    Text("İptal", color = TextOffWhite)
                }
            },
            containerColor = SurfaceSlateNavyElevated
        )
    }

    // Dialog: Clear Media
    if (showClearMediaDialog) {
        AlertDialog(
            onDismissRequest = { showClearMediaDialog = false },
            title = { Text(text = "Görsel ve Belgeleri Temizle", color = TextOffWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Önbellekte tutulan tüm görsel ve belgeler silinecektir (${formatBytes(mediaCacheBytes)}).",
                    color = TextMutedSlateGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearMediaDialog = false
                        deleteFilesMatching { file ->
                            val n = file.name.lowercase()
                            n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".mp4")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DestructiveCoralRed)
                ) {
                    Text("Temizle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMediaDialog = false }) {
                    Text("İptal", color = TextOffWhite)
                }
            },
            containerColor = SurfaceSlateNavyElevated
        )
    }
}

@Composable
private fun StorageCategoryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    sizeStr: String,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(SurfaceSlateNavyElevated),
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
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = sizeStr,
            color = AccentNeonLime,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(6.dp))

        IconButton(
            onClick = onClear,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Temizle",
                tint = DestructiveCoralRed,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

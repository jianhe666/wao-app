package app.zhizhang.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.navArgument
import app.zhizhang.BuildConfig
import app.zhizhang.ZhizhangApp
import app.zhizhang.R
import app.zhizhang.data.GithubRelease
import app.zhizhang.data.UpdateAutoCheckStore
import app.zhizhang.data.UpdateChecker
import app.zhizhang.ui.components.BackgroundContrast
import app.zhizhang.ui.components.GlassDialog
import app.zhizhang.ui.components.MarkdownText
import app.zhizhang.ui.chat.HomeChatScreen
import app.zhizhang.ui.components.LiquidBackground
import app.zhizhang.ui.components.LocalDockBackdrop
import app.zhizhang.ui.components.LocalGlassBackdrop
import app.zhizhang.ui.components.LocalGlassTransparency
import app.zhizhang.ui.components.isRobolectric
import app.zhizhang.ui.components.LocalHazeState
import app.zhizhang.ui.components.liquidGlass
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import app.zhizhang.ui.components.liquidHazeStyle
import app.zhizhang.ui.components.pressScale
import app.zhizhang.ui.editor.EditorScreen
import app.zhizhang.ui.home.LedgerScreen
import app.zhizhang.ui.reimbursements.ReimbursementsScreen
import app.zhizhang.ui.aicategory.AiCategoryHistoryScreen
import app.zhizhang.ui.aicategory.AiCategoryScreen
import app.zhizhang.ui.appearance.BackgroundScreen
import app.zhizhang.ui.assets.AssetsScreen
import app.zhizhang.ui.categories.CategoryManagerScreen
import app.zhizhang.ui.review.ReviewScreen
import app.zhizhang.ui.importflow.ImportScreen
import app.zhizhang.ui.settings.SettingsScreen
import app.zhizhang.ui.stats.StatsScreen
import app.zhizhang.ui.theme.ZhizhangTheme
import app.zhizhang.ui.theme.zh
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val LEDGER = "ledger"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val REIMBURSEMENTS = "reimbursements"
    const val REVIEW = "review"
    const val CATEGORIES = "categories"
    const val AI_CATEGORY = "ai_category"
    const val AI_CATEGORY_HISTORY = "ai_category_history"
    const val ASSETS = "assets"
    const val BACKGROUND = "background"
    const val EDITOR = "editor?txId={txId}"

    fun editor(id: Long? = null): String = if (id == null) "editor" else "editor?txId=$id"
}

private data class MainTab(
    val route: String,
    val label: String,
    val iconRes: Int,
    val emphasized: Boolean = false,
)

// 五个入口，对话在正中间，并做成蓝色玻璃主按钮。
private val mainTabs = listOf(
    MainTab(Routes.LEDGER, "流水", R.drawable.ic_book),
    MainTab(Routes.STATS, "统计", R.drawable.ic_chart),
    MainTab(Routes.HOME, "对话", R.drawable.ic_chat, emphasized = true),
    MainTab(Routes.IMPORT, "导入", R.drawable.ic_spark),
    MainTab(Routes.SETTINGS, "我的", R.drawable.ic_settings),
)

@Composable
fun ZhizhangRoot(
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    sharedImage: Uri? = null,
    onSharedImageConsumed: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember { (context.applicationContext as ZhizhangApp).settings }
    val backgroundId by settings.backgroundId.collectAsStateWithLifecycle(initialValue = "default")
    val backgroundOpacity by settings.backgroundOpacity.collectAsStateWithLifecycle(initialValue = 1f)
    val customBackgroundPath by settings.customBackgroundPath.collectAsStateWithLifecycle(initialValue = "")
    val updateUseMirror by settings.updateUseMirror.collectAsStateWithLifecycle(initialValue = false)
    val glassTransparency by settings.glassTransparency.collectAsStateWithLifecycle(initialValue = 0.30f)
    var customBackgroundNeedsDarkText by remember { mutableStateOf(false) }
    LaunchedEffect(backgroundId, customBackgroundPath, backgroundOpacity) {
        customBackgroundNeedsDarkText = if (backgroundId == "custom") {
            BackgroundContrast.customNeedsDarkText(customBackgroundPath, backgroundOpacity)
        } else {
            false
        }
    }
    val darkContent = when (backgroundId) {
        "night" -> BackgroundContrast.presetNeedsDarkText(backgroundId, backgroundOpacity)
        "custom" -> customBackgroundNeedsDarkText
        else -> false
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in mainTabs.map { it.route }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hazeState = remember {
        // Robolectric 的软件渲染不支持 Haze 使用的 RenderNode；截图测试使用玻璃降级样式。
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) null else HazeState()
    }
    val glassBackdrop = rememberLayerBackdrop {
        drawContent()
    }
    val contentBackdrop = rememberLayerBackdrop {
        drawContent()
    }
    var pendingChatText by remember { mutableStateOf<String?>(null) }
    var pendingChatImage by remember { mutableStateOf<Uri?>(null) }
    var highlightAiRow by remember { mutableStateOf(false) }
    var autoUpdate by remember { mutableStateOf<GithubRelease?>(null) }

    fun openTab(route: String) {
        // 不要在 lambda 里捕获 currentRoute 快照；导航后旧闭包会误判仍在原页面。
        val activeRoute = navController.currentBackStackEntry?.destination?.route
        if (route == activeRoute) return
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(Unit) {
        if (Build.FINGERPRINT.contains("robolectric", ignoreCase = true)) return@LaunchedEffect
        val store = UpdateAutoCheckStore(context)
        if (!store.shouldCheck()) return@LaunchedEffect
        val release = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                UpdateChecker.fetchLatest()
            }
        }.getOrNull()
        store.markChecked()
        if (release != null && UpdateChecker.isNewer(release.tagName, BuildConfig.VERSION_NAME)) {
            autoUpdate = release
        }
    }

    LaunchedEffect(sharedText) {
        if (!sharedText.isNullOrBlank()) {
            pendingChatText = sharedText
            openTab(Routes.HOME)
            onSharedTextConsumed()
        }
    }

    LaunchedEffect(sharedImage) {
        if (sharedImage != null) {
            pendingChatImage = sharedImage
            openTab(Routes.HOME)
            onSharedImageConsumed()
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    ZhizhangTheme(darkTheme = darkContent) {
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalHazeState provides hazeState,
            LocalGlassBackdrop provides glassBackdrop,
            LocalDockBackdrop provides contentBackdrop,
            LocalGlassTransparency provides glassTransparency,
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(glassBackdrop),
        ) {
            LiquidBackground(
                presetId = backgroundId,
                opacity = backgroundOpacity,
                customImagePath = customBackgroundPath.takeIf { it.isNotBlank() },
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = if (showBottomBar) 78.dp else 0.dp),
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = Color.White,
                        contentColor = Color(0xFF131A2A),
                        actionColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                enterTransition = { fadeIn(tween(160)) },
                exitTransition = { fadeOut(tween(110)) },
            ) {
                composable(Routes.HOME) {
                    HomeChatScreen(
                        prefillText = pendingChatText,
                        onPrefillConsumed = { pendingChatText = null },
                        onOpenSettings = {
                            highlightAiRow = true
                            openTab(Routes.SETTINGS)
                        },
                        onAddBill = { navController.navigate(Routes.editor()) },
                        onOpenLedger = { openTab(Routes.LEDGER) },
                        sharedImage = pendingChatImage,
                        onSharedImageConsumed = { pendingChatImage = null },
                    )
                }
                composable(Routes.LEDGER) {
                    LedgerScreen(
                        onAdd = { navController.navigate(Routes.editor()) },
                        onEdit = { id -> navController.navigate(Routes.editor(id)) },
                        onOpenReimbursements = { navController.navigate(Routes.REIMBURSEMENTS) },
                        onOpenReview = { navController.navigate(Routes.REVIEW) },
                    )
                }
                composable(Routes.REIMBURSEMENTS) {
                    ReimbursementsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.REVIEW) {
                    ReviewScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { id -> navController.navigate(Routes.editor(id)) },
                        onManualAdd = { navController.navigate(Routes.editor()) },
                    )
                }
                composable(Routes.STATS) {
                    StatsScreen()
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onShowMessage = ::showMessage,
                        onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                        onOpenAiCategory = { navController.navigate(Routes.AI_CATEGORY) },
                        onOpenAssets = { navController.navigate(Routes.ASSETS) },
                        onOpenBackground = { navController.navigate(Routes.BACKGROUND) },
                        highlightAi = highlightAiRow,
                        onHighlightAiConsumed = { highlightAiRow = false },
                    )
                }
                composable(Routes.CATEGORIES) {
                    CategoryManagerScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.AI_CATEGORY) {
                    AiCategoryScreen(
                        onBack = { navController.popBackStack() },
                        onOpenHistory = { navController.navigate(Routes.AI_CATEGORY_HISTORY) },
                    )
                }
                composable(Routes.AI_CATEGORY_HISTORY) {
                    AiCategoryHistoryScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.ASSETS) {
                    AssetsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.BACKGROUND) {
                    BackgroundScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.IMPORT) {
                    ImportScreen(
                        onImported = { count ->
                            openTab(Routes.LEDGER)
                            showMessage(
                                if (count > 0) "已导入 $count 笔账目" else "没有新增，可能已经导入过",
                            )
                        },
                    )
                }
                composable(
                    route = Routes.EDITOR,
                    arguments = listOf(
                        navArgument("txId") {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                    ),
                ) {
                    EditorScreen(
                        onClose = { navController.popBackStack() },
                    )
                }
            }
        }
        }
        }
        // 底栏不随输入法隐藏；窗口使用 adjustNothing，键盘会直接盖住它。
        if (showBottomBar) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onTab = ::openTab,
                )
            }
        }
        }
    }

    autoUpdate?.let { release ->
        GlassDialog(onDismissRequest = { autoUpdate = null }) {
            Text(
                text = "发现新版本 ${release.tagName}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (release.notes.isBlank()) {
                    "有新版本可以更新。"
                } else {
                    MarkdownText.toPlainText(release.notes)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { autoUpdate = null }) { Text("稍后") }
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(
                                        UpdateChecker.downloadUrl(
                                            release.apkUrl ?: release.releaseUrl,
                                            updateUseMirror,
                                        ),
                                    ),
                                ),
                            )
                        }
                        autoUpdate = null
                    },
                ) { Text(if (release.apkUrl == null) "打开 GitHub" else "下载 APK") }
            }
        }
    }
    }
}

@Composable
private fun AppBottomBar(
    currentRoute: String?,
    onTab: (String) -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val haze = LocalHazeState.current
    val hazeStyle = liquidHazeStyle(tintAlpha = 0.30f)
    val dockBackdrop = LocalDockBackdrop.current
    val canLens = dockBackdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isRobolectric
    val dockTransparency = LocalGlassTransparency.current.coerceIn(0f, 1f)
    val dockOpacity = 1f - dockTransparency
    val dockTint = MaterialTheme.zh.glassTop.copy(alpha = (0.06f + 0.26f * dockOpacity))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(8.dp, shape, ambientColor = MaterialTheme.zh.glassShadow, spotColor = MaterialTheme.zh.glassShadow)
                .then(
                    if (canLens && dockBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = dockBackdrop,
                            shape = { shape as androidx.compose.foundation.shape.CornerBasedShape },
                            effects = {
                                vibrancy()
                                blur((2.dp + 16.dp * dockOpacity).toPx())
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    lens(
                                        (8.dp + 6.dp * dockOpacity).toPx(),
                                        (16.dp + 16.dp * dockOpacity).toPx(),
                                        depthEffect = true,
                                        chromaticAberration = true,
                                    )
                                }
                            },
                            onDrawSurface = {
                                drawRect(dockTint)
                            },
                        )
                    } else if (haze != null) {
                        Modifier
                            .hazeEffect(haze, hazeStyle)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.zh.glassTop.copy(alpha = 0.18f),
                                        MaterialTheme.zh.glassBottom.copy(alpha = 0.07f),
                                    ),
                                ),
                            )
                    } else {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.zh.glassTop.copy(alpha = 0.36f),
                                    MaterialTheme.zh.glassBottom.copy(alpha = 0.18f),
                                ),
                            ),
                        )
                    },
                )
                .border(0.7.dp, Color.White.copy(alpha = 0.14f), shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mainTabs.forEach { tab ->
                BottomBarTab(
                    tab = tab,
                    selected = currentRoute == tab.route,
                    onClick = { onTab(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarTab(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        animationSpec = tween(180),
        label = "tabIcon",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
        animationSpec = tween(180),
        label = "tabLabel",
    )
    val orbScale by animateFloatAsState(
        targetValue = if (selected) 1.10f else 0.90f,
        animationSpec = tween(180),
        label = "tabOrb",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = true),
                onClick = onClick,
            )
            .testTag("tab-${tab.route}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (tab.emphasized) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .shadow(
                        4.dp,
                        CircleShape,
                        ambientColor = MaterialTheme.zh.glassShadow,
                        spotColor = MaterialTheme.zh.glassShadow,
                    )
                    .graphicsLayer {
                        scaleX = orbScale
                        scaleY = orbScale
                    }
                    .liquidGlass(
                        shape = RoundedCornerShape(50),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                        } else {
                            MaterialTheme.zh.glassTop.copy(alpha = 0.08f)
                        },
                        lensHeight = 6.dp,
                        lensAmount = 14.dp,
                        blurRadius = 3.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = tab.label,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.size(2.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = tab.label,
                    tint = iconColor,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.size(2.dp))
        }
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = labelColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

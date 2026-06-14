package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val sessions by viewModel.allSessions.collectAsState()
    val selectedSession by viewModel.selectedSession.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val inputText by viewModel.inputText.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Enforce RTL for Arabic Experience flawlessly
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                ) {
                    ChatHistoryDrawerContent(
                        sessions = sessions,
                        selectedSession = selectedSession,
                        onSessionSelected = { session ->
                            viewModel.selectSession(session)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onSessionDelete = { session ->
                            viewModel.deleteSession(session)
                        },
                        onAddSessionClick = {
                            showCreateDialog = true
                        },
                        onClearAllClick = {
                            showDeleteAllConfirm = true
                        }
                    )
                }
            }
        ) {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = selectedSession?.title ?: "مساعدي الذكي",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!selectedSession?.systemInstruction.isNullOrBlank()) {
                                    Text(
                                        text = "وضع مخصص نشط",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Light,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                },
                                modifier = Modifier.testTag("menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "القائمة الجانبية"
                                )
                            }
                        },
                        actions = {
                            if (selectedSession != null) {
                                IconButton(onClick = { showEditDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "خصائص الجلسة",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { viewModel.clearActiveChatHistory() }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "مسح محادثة الحالي",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        // Error message banner
                        errorMessage?.let { errorMsg ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "خطأ",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.clearError() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إغلاق",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        // Message area
                        if (messages.isEmpty()) {
                            // Empty State Box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "ابدأ محادثتك الذكية الجديدة!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "يمكنك طرح أي سؤال، ترجمة النصوص، المساعدة في التعليم أو مجرد الدردشة التفاعلية باللغة العربية مع حفظ تلقائي لمحتويات الدردشة.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (selectedSession?.systemInstruction != null) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Text(
                                                text = "التعليمات الخاصة: \"${selectedSession?.systemInstruction}\"",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.primary,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            val listState = rememberLazyListState()
                            LaunchedEffect(messages.size, isLoading) {
                                if (messages.isNotEmpty()) {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(messages) { message ->
                                    MessageBubble(message = message)
                                }
                                if (isLoading) {
                                    item {
                                        ThinkingBubble()
                                    }
                                }
                            }
                        }

                        // Bottom Input Fields
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 8.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { viewModel.updateInput(it) },
                                    placeholder = {
                                        Text(
                                            text = "اكتب رسالة للمساعد تفصيلاً...",
                                            fontSize = 14.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .testTag("chat_input"),
                                    maxLines = 4,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        )
                                        .clickable(enabled = inputText.isNotBlank() && !isLoading) {
                                            viewModel.sendMessage()
                                        }
                                        .testTag("send_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "إرسال",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom creation and configuration Dialogs
        if (showCreateDialog) {
            CreateSessionDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { title, instr ->
                    viewModel.createNewSession(title, instr)
                    showCreateDialog = false
                }
            )
        }

        if (showEditDialog && selectedSession != null) {
            EditSessionDialog(
                session = selectedSession!!,
                onDismiss = { showEditDialog = false },
                onConfirm = { title, instr ->
                    viewModel.renameSession(selectedSession!!, title)
                    // If instruction changed/not changed we can support updating the entire session in DB
                    val updatedSess = selectedSession!!.copy(
                        title = title,
                        systemInstruction = instr.ifBlank { null }
                    )
                    viewModel.updateSession(updatedSess)
                    showEditDialog = false
                }
            )
        }

        if (showDeleteAllConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteAllConfirm = false },
                title = { Text("هل أنت متأكد؟", fontWeight = FontWeight.Bold) },
                text = { Text("سيؤدي هذا الخيار إلى حذف كافة المحادثات والرسائل المحفوظة نهائياً من قاعدة البيانات المحلية.") },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            viewModel.clearAll()
                            showDeleteAllConfirm = false
                        }
                    ) {
                        Text("مسح الكل")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllConfirm = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
fun ChatHistoryDrawerContent(
    sessions: List<ChatSession>,
    selectedSession: ChatSession?,
    onSessionSelected: (ChatSession) -> Unit,
    onSessionDelete: (ChatSession) -> Unit,
    onAddSessionClick: () -> Unit,
    onClearAllClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // Drawer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "مساعدي الذكي",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        // Large action button: Create New Conversation
        Button(
            onClick = onAddSessionClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("add_chat_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("محادثة مخصصة جديدة", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "المحادثات السابقة",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Previous Chat Sessions list
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد محادثات سابقة",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    val isSelected = selectedSession?.id == session.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSessionSelected(session) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        ),
                        border = if (!isSelected) BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                        ) else null,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (session.systemInstruction != null) Icons.Default.Settings else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = session.title,
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Show delete option (only if we have more than 1 session, to stay friendly)
                            if (sessions.size > 1) {
                                var deleteConfirm by remember { mutableStateOf(false) }

                                if (deleteConfirm) {
                                    IconButton(
                                        onClick = { onSessionDelete(session) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "تأكيد الحذف",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { deleteConfirm = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "إلغاء",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = { deleteConfirm = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف المحادثة",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Reset All Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClearAllClick() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "مسح كافة المحادثات",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondaryContainer

    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSecondaryContainer

    val customShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp)
    }

    val format = remember { SimpleDateFormat("hh:mm a", Locale("ar")) }
    val timeStr = remember(message.timestamp) { format.format(Date(message.timestamp)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (isUser) "user_message" else "ai_message"),
        horizontalAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp, top = 4.dp)
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "ذكاء اصطناعي",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                Surface(
                    color = bubbleColor,
                    shape = customShape,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.text,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition()
    val dotScale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 0
                1f at 300
                0.3f at 600
            },
            repeatMode = RepeatMode.Restart
        )
    )

    val dotScale2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 150
                1f at 450
                0.3f at 750
            },
            repeatMode = RepeatMode.Restart
        )
    )

    val dotScale3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.3f at 300
                1f at 600
                0.3f at 900
            },
            repeatMode = RepeatMode.Restart
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("thinking_indicator"),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .padding(end = 6.dp, top = 4.dp)
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "المساعد يفكر",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .drawBehind { drawCircle(color = Color(0xFF625b71), radius = size.minDimension / 2 * dotScale1) }
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .drawBehind { drawCircle(color = Color(0xFF625b71), radius = size.minDimension / 2 * dotScale2) }
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .drawBehind { drawCircle(color = Color(0xFF625b71), radius = size.minDimension / 2 * dotScale3) }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, systemInstruction: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var instruction by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "إنشاء محادثة جديدة مخصصة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("عنوان المحادثة (اختياري)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("مثال: مشروع كتاب تخرج أو برمجة كوتلن", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("توجيه مسبق أو تخصيص سلوك الذكاء الاصطناعي (اختياري)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    placeholder = { Text("مثال: تحدث معي بأسلوب خبير لغوي، أو كأفلاطون الفيلسوف.", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(title, instruction) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("إنشاء المحادثة")
                    }
                }
            }
        }
    }
}

@Composable
fun EditSessionDialog(
    session: ChatSession,
    onDismiss: () -> Unit,
    onConfirm: (title: String, systemInstruction: String) -> Unit
) {
    var title by remember { mutableStateOf(session.title) }
    var instruction by remember { mutableStateOf(session.systemInstruction ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "خصائص وتعديل المحادثة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("اسم المحادثة", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("التعليمات المخصصة للذكاء الاصطناعي", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    placeholder = { Text("مثال: ساعدني على التفكير بإيجابية دائماً أو ترجم كل كلامي.", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(title, instruction) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("حفظ التغييرات")
                    }
                }
            }
        }
    }
}

package com.bitchat.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.android.R
import com.bitchat.android.rooms.PrivateRooms
import com.bitchat.android.ui.theme.BitchatFontFamily
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Private tab: one-to-one conversations and invitation-only rooms in one place. A conversation
 * opens the usual private chat sheet; a room opens the rooms sheet on that room.
 */
@Composable
internal fun PrivateTab(viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val rooms by PrivateRooms.rooms.collectAsState()
    val roomMessages by PrivateRooms.messages.collectAsState()
    var showRooms by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    confirmDelete?.let { c ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.private_delete_title), fontFamily = BitchatFontFamily) },
            text = { Text(stringResource(R.string.private_delete_body, c.displayName), fontFamily = BitchatFontFamily) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { viewModel.deletePrivateConversation(c.conversationID) }
                }) { Text(stringResource(R.string.private_delete_confirm), color = colorScheme.error, fontFamily = BitchatFontFamily) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.cancel), fontFamily = BitchatFontFamily)
                }
            }
        )
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(modifier.fillMaxSize().background(colorScheme.background).padding(top = top)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TabTitle(stringResource(R.string.tab_private), Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.private_new_message), tint = colorScheme.primary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.private_new_message), fontFamily = BitchatFontFamily) },
                        onClick = { menu = false; viewModel.showMeshPeerList() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.private_new_room), fontFamily = BitchatFontFamily) },
                        onClick = { menu = false; PrivateRooms.openSheet() }
                    )
                }
            }
        }

        // Messages | Private rooms
        Row(
            Modifier
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(colorScheme.surface)
                .padding(3.dp)
        ) {
            listOf(false to R.string.private_tab_messages, true to R.string.rooms_title).forEach { (rooms, label) ->
                val selected = showRooms == rooms
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) colorScheme.primary else Color.Transparent)
                        .clickable { showRooms = rooms }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(label), fontFamily = BitchatFontFamily, fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!showRooms) {
                if (conversations.isEmpty()) item { EmptyHint(stringResource(R.string.private_empty_dm)) }
                items(conversations, key = { it.conversationID }) { c ->
                    // swipe left (or long press) to delete, always after a confirmation
                    val swipe = androidx.compose.material3.rememberSwipeToDismissBoxState(
                        confirmValueChange = { v ->
                            if (v == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) confirmDelete = c
                            false
                        }
                    )
                    androidx.compose.material3.SwipeToDismissBox(
                        state = swipe,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(colorScheme.error).padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.private_delete_confirm), tint = colorScheme.onError) }
                        }
                    ) {
                    ListRow(
                        onClick = { viewModel.showPrivateChatSheet(c.conversationID) },
                        onLongClick = { confirmDelete = c },
                        leading = { Avatar(c.displayName) },
                        title = c.displayName,
                        subtitle = c.latestMessagePreview,
                        trailing = if (c.latestMessageAt > 0) shortTime(c.latestMessageAt) else "",
                        unread = c.unreadCount
                    )
                    }
                }
            } else {
                if (rooms.isEmpty()) item { EmptyHint(stringResource(R.string.private_empty_rooms)) }
                items(rooms, key = { it.tag }) { room ->
                    val last = roomMessages[room.tag]?.lastOrNull()
                    ListRow(
                        onClick = { PrivateRooms.openSheet(room.tag) },
                        leading = {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Lock, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp)) }
                        },
                        title = room.name,
                        subtitle = last?.let { "${it.nickname}: ${it.text}" } ?: stringResource(R.string.private_room_encrypted),
                        trailing = last?.let { shortTime(it.atMs) }.orEmpty(),
                        unread = 0
                    )
                }
                item {
                    ChatbitButton(
                        text = stringResource(R.string.private_new_room),
                        onClick = { PrivateRooms.openSheet() },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ListRow(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    trailing: String,
    unread: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, if (unread > 0) colorScheme.primary.copy(alpha = 0.5f) else colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontFamily = BitchatFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontFamily = BitchatFontFamily, fontSize = 12.sp, color = colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (trailing.isNotEmpty()) Text(trailing, fontFamily = BitchatFontFamily, fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
                if (unread > 0) Box(
                    Modifier.clip(CircleShape).background(colorScheme.primary).padding(horizontal = 6.dp, vertical = 1.dp)
                ) { Text("$unread", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorScheme.onPrimary) }
            }
        }
    }
}

private val AvatarColors = listOf(0xFF64B5F6, 0xFFFFD54F, 0xFF4DD0C4, 0xFFCE93D8, 0xFFFFB74D, 0xFFFF8A80)

@Composable
private fun Avatar(name: String) {
    val color = Color(AvatarColors[Math.floorMod(name.hashCode(), AvatarColors.size)])
    Box(Modifier.size(40.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(name.trimStart('@').take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text, fontFamily = BitchatFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp)
    )
}

private fun shortTime(ms: Long): String {
    val now = System.currentTimeMillis()
    return if (now - ms < 20 * 3600_000L) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
        else DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ms))
}

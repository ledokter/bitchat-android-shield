package com.bitchat.android.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.core.ui.component.sheet.BitchatBottomSheet
import com.bitchat.android.core.ui.component.sheet.BitchatSheetTitle
import com.bitchat.android.core.ui.component.sheet.BitchatSheetTopBar
import com.bitchat.android.rooms.PrivateRoom
import com.bitchat.android.rooms.PrivateRooms
import com.bitchat.android.security.SecureClipboard
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.text.DateFormat
import java.util.Date

/**
 * chatbit private rooms: list, create, join by link or QR code, chat, invite.
 * Messages are end-to-end encrypted with the room key and kept in memory only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateRoomsSheet(nickname: String, onDismiss: () -> Unit) {
    val rooms by PrivateRooms.rooms.collectAsState()
    val pending by PrivateRooms.pendingInvite.collectAsState()
    var openTag by remember { mutableStateOf<String?>(null) }
    val open = rooms.firstOrNull { it.tag == openTag }

    BitchatBottomSheet(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(top = 72.dp)) {
                if (open != null) {
                    RoomChat(open, nickname, onBack = { openTag = null })
                } else {
                    RoomList(rooms, pending, onOpen = { openTag = it.tag })
                }
            }
            BitchatSheetTopBar(
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (open != null) {
                            IconButton(onClick = { openTag = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.rooms_back))
                            }
                            BitchatSheetTitle(open.name)
                        } else {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            BitchatSheetTitle(stringResource(R.string.rooms_title))
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun RoomList(rooms: List<PrivateRoom>, pending: PrivateRoom.Invite?, onOpen: (PrivateRoom) -> Unit) {
    val messages by PrivateRooms.messages.collectAsState()
    var newName by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val camera = rememberPermissionState(android.Manifest.permission.CAMERA)

    fun joinFrom(text: String) {
        val invite = PrivateRoom.parseInvite(text)
        if (invite == null) {
            linkError = true
        } else {
            linkError = false
            link = ""
            scanning = false
            onOpen(PrivateRooms.join(invite))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pending != null) item {
            Card {
                RoomLabel(stringResource(R.string.rooms_pending_title, pending.name.ifBlank { "?" }), bold = true)
                RoomHint(stringResource(R.string.rooms_invite_warning))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val room = PrivateRooms.join(pending)
                        PrivateRooms.offerInvite(null)
                        onOpen(room)
                    }) { Text(stringResource(R.string.rooms_join), fontFamily = BitchatFontFamily) }
                    TextButton(onClick = { PrivateRooms.offerInvite(null) }) {
                        Text(stringResource(R.string.rooms_ignore), fontFamily = BitchatFontFamily)
                    }
                }
            }
        }
        if (rooms.isEmpty()) item { RoomHint(stringResource(R.string.rooms_empty)) }
        items(rooms, key = { it.tag }) { room ->
            val last = messages[room.tag]?.lastOrNull()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(room) }
            ) {
                Column(Modifier.padding(14.dp)) {
                    RoomLabel(room.name.ifBlank { "?" }, bold = true)
                    if (last != null) RoomHint("${last.nickname}: ${last.text}", maxLines = 1)
                }
            }
        }
        item {
            Card {
                RoomLabel(stringResource(R.string.rooms_create), bold = true)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(40) },
                    label = { Text(stringResource(R.string.rooms_name_hint), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(enabled = newName.isNotBlank(), onClick = {
                    val room = PrivateRooms.create(newName)
                    newName = ""
                    onOpen(room)
                }) { Text(stringResource(R.string.rooms_create_button), fontFamily = BitchatFontFamily) }
            }
        }
        item {
            Card {
                RoomLabel(stringResource(R.string.rooms_join_title), bold = true)
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; linkError = false },
                    label = { Text(stringResource(R.string.rooms_link_hint), fontSize = 12.sp) },
                    isError = linkError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (linkError) RoomHint(stringResource(R.string.rooms_invalid_invite))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = link.isNotBlank(), onClick = { joinFrom(link) }) {
                        Text(stringResource(R.string.rooms_join), fontFamily = BitchatFontFamily)
                    }
                    OutlinedButton(onClick = {
                        if (camera.status.isGranted) scanning = !scanning else camera.launchPermissionRequest()
                    }) { Text(stringResource(R.string.rooms_scan), fontFamily = BitchatFontFamily) }
                }
                if (scanning && camera.status.isGranted) {
                    Box(Modifier.fillMaxWidth().height(260.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        ScannerView(onScan = { text -> if (PrivateRoom.parseInvite(text) != null) joinFrom(text) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomChat(room: PrivateRoom, nickname: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val all by PrivateRooms.messages.collectAsState()
    val messages = all[room.tag].orEmpty()
    var text by remember { mutableStateOf("") }
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showInvite = true }) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Text(" " + stringResource(R.string.rooms_invite), fontFamily = BitchatFontFamily)
            }
            TextButton(onClick = { confirmLeave = true }) {
                Text(stringResource(R.string.rooms_leave), fontFamily = BitchatFontFamily, color = MaterialTheme.colorScheme.error)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(380.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (messages.isEmpty()) item { RoomHint(stringResource(R.string.rooms_no_messages)) }
            items(messages, key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (m.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                "${m.nickname}#${m.senderPubkey.takeLast(4)} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(m.atMs))}",
                                fontFamily = BitchatFontFamily, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(m.text, fontFamily = BitchatFontFamily, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.rooms_message_hint), fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                maxLines = 4
            )
            IconButton(enabled = text.isNotBlank(), onClick = {
                PrivateRooms.send(room, nickname, text)
                text = ""
            }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.rooms_send)) }
        }
    }

    if (showInvite) {
        val link = room.inviteLink()
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text(stringResource(R.string.rooms_invite_title, room.name), fontFamily = BitchatFontFamily) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QRCodeImage(data = link, size = 220.dp)
                    RoomHint(stringResource(R.string.rooms_invite_warning))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.rooms_share_link)))
                }) { Text(stringResource(R.string.rooms_share_link), fontFamily = BitchatFontFamily) }
            },
            dismissButton = {
                TextButton(onClick = { SecureClipboard.copy(context, "chatbit room", link) }) {
                    Text(stringResource(R.string.rooms_copy_link), fontFamily = BitchatFontFamily)
                }
            }
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.rooms_leave_title, room.name), fontFamily = BitchatFontFamily) },
            text = { Text(stringResource(R.string.rooms_leave_desc), fontFamily = BitchatFontFamily) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; PrivateRooms.leave(room); onBack() }) {
                    Text(stringResource(R.string.rooms_leave), fontFamily = BitchatFontFamily, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel), fontFamily = BitchatFontFamily) }
            }
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun RoomLabel(text: String, bold: Boolean = false) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 14.sp, fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal)
}

@Composable
private fun RoomHint(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(text, fontFamily = BitchatFontFamily, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

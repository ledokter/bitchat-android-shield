package com.bitchat.android.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R
import com.bitchat.android.geohash.ZoneDirectory
import com.bitchat.android.security.IncognitoKeyboard
import com.bitchat.android.ui.theme.BitchatFontFamily
import com.bitchat.android.ui.theme.BitchatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Zone directory: browse continent > country > city > district, or search by name, and read
 * each place's zone codes. "Join" returns the geohash like the world map does.
 */
class ZoneDirectoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.bitchat.android.security.SecurityPreferences.blockScreenshots.value &&
            !com.bitchat.android.security.SecurityPreferences.isTestLab(this)) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContent {
            BitchatTheme {
                IncognitoKeyboard {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Directory(onJoin = { gh ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(GeohashPickerActivity.EXTRA_RESULT_GEOHASH, gh))
                        finish()
                    }, onClose = { finish() })
                    }
                }
            }
        }
    }
}

private fun countryName(cc: String): String =
    Locale("", cc).getDisplayCountry(Locale.getDefault()).ifBlank { cc }

@Composable
private fun continentName(code: String): String = stringResource(
    when (code) {
        "EU" -> R.string.continent_eu
        "AF" -> R.string.continent_af
        "NA" -> R.string.continent_na
        "SA" -> R.string.continent_sa
        "AS" -> R.string.continent_as
        "OC" -> R.string.continent_oc
        else -> R.string.continent_an
    }
)

@Composable
private fun Directory(onJoin: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val data by produceState<ZoneDirectory.Data?>(null) {
        value = withContext(Dispatchers.IO) { ZoneDirectory.load(context) }
    }
    var continent by rememberSaveable { mutableStateOf<String?>(null) }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<ZoneDirectory.Place?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(continent, country, query.isBlank()) { list.scrollToItem(0) }

    BackHandler {
        when {
            query.isNotEmpty() -> query = ""
            country != null -> { country = null; open = null }
            continent != null -> continent = null
            else -> onClose()
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                when {
                    country != null -> { country = null; open = null }
                    continent != null -> continent = null
                    else -> onClose()
                }
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.rooms_back)) }
            // where we are: Directory / Europe / France
            Text(
                listOfNotNull(stringResource(R.string.dir_title), continent?.let { continentName(it) }, country?.let(::countryName))
                    .joinToString(" / "),
                fontFamily = BitchatFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.dir_search), fontFamily = BitchatFontFamily) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = null) } }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )
        Spacer(Modifier.size(8.dp))

        val d = data ?: return@Column
        val collator = remember { Collator.getInstance() }
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            when {
                query.isNotBlank() -> {
                    val found = d.search(query)
                    if (found.isEmpty()) item { Hint(stringResource(R.string.dir_empty)) }
                    items(found, key = { "s" + it.geohash + it.name }) { p ->
                        PlaceRow(p, expanded = open === p, showCountry = true, onToggle = { open = if (open === p) null else p }, onJoin = onJoin)
                    }
                }
                country != null -> {
                    items(d.citiesByCountry[country].orEmpty(), key = { it.geohash + it.name }) { p ->
                        PlaceRow(p, expanded = open === p, showCountry = false, onToggle = { open = if (open === p) null else p }, onJoin = onJoin)
                    }
                }
                continent != null -> {
                    val ccs = d.countries(continent!!).sortedWith(compareBy(collator) { countryName(it) })
                    items(ccs, key = { it }) { cc ->
                        NavRow(countryName(cc), stringResource(R.string.dir_cities, d.citiesByCountry[cc]?.size ?: 0)) { country = cc }
                    }
                }
                else -> {
                    items(ZoneDirectory.CONTINENTS, key = { it }) { c ->
                        NavRow(continentName(c), stringResource(R.string.dir_countries, d.countries(c).size)) { continent = c }
                    }
                    item { Hint(stringResource(R.string.dir_source)) }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontFamily = BitchatFontFamily)
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = BitchatFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

/** A city or district; tapped, it shows its zone codes and, for a city, its districts. */
@Composable
private fun PlaceRow(
    p: ZoneDirectory.Place, expanded: Boolean, showCountry: Boolean,
    onToggle: () -> Unit, onJoin: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val city = p.parent ?: p
    // a city is joined at "ville" level, a district at "quartier" level
    val mainCode = p.geohash.take(if (p.parent == null) 5 else 6)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, fontFamily = BitchatFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val where = listOfNotNull(p.parent?.name, if (showCountry) countryName(city.country) else null)
            if (where.isNotEmpty()) Text(where.joinToString(", "), color = colors.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
        }
        Text("#$mainCode", fontFamily = BitchatFontFamily, color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp)
        )
    }
    if (expanded) {
        Column(Modifier.fillMaxWidth().background(colors.surfaceVariant.copy(alpha = 0.35f)).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)) {
            if (p.parent == null) CodeRow(stringResource(R.string.location_level_city), stringResource(R.string.dir_size_city), p.geohash.take(5), onJoin)
            CodeRow(stringResource(R.string.location_level_neighborhood), stringResource(R.string.dir_size_hood), p.geohash.take(6), onJoin)
            CodeRow(stringResource(R.string.location_level_block), stringResource(R.string.dir_size_block), p.geohash.take(7), onJoin)
            if (p.districts.isNotEmpty()) {
                Text(
                    stringResource(R.string.dir_districts), Modifier.padding(top = 10.dp, bottom = 2.dp),
                    color = colors.onSurfaceVariant, fontSize = 12.sp, fontFamily = BitchatFontFamily
                )
                p.districts.forEach { dist ->
                    CodeRow(dist.name, null, dist.geohash.take(6), onJoin)
                }
            }
        }
    }
    HorizontalDivider(color = colors.outline.copy(alpha = 0.15f))
}

@Composable
private fun CodeRow(label: String, size: String?, code: String, onJoin: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (size != null) Text(size, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("#$code", fontFamily = BitchatFontFamily, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, modifier = Modifier.width(96.dp))
        TextButton(onClick = { onJoin(code) }) { Text(stringResource(R.string.dir_join), fontFamily = BitchatFontFamily) }
    }
}

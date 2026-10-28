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
import androidx.compose.ui.res.pluralStringResource
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

/** "" = the country's zones, ALL_CITIES = its city list, otherwise a tile code (1 to 4 characters). */
private const val ALL_CITIES = "*"

/** Approximate tile width by code length, in km (geohash cells). */
private val TILE_SIZE = mapOf(1 to "≈ 5000 km", 2 to "≈ 1250 km", 3 to "≈ 156 km", 4 to "≈ 39 km")

@Composable
private fun tileLevel(length: Int): String = when (length) {
    1 -> stringResource(R.string.dir_level_large)
    2 -> stringResource(R.string.location_level_region)
    3 -> stringResource(R.string.dir_level_zone)
    else -> stringResource(R.string.location_level_province)
} + " · " + TILE_SIZE[length.coerceIn(1, 4)]

@Composable
private fun Directory(onJoin: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val data by produceState<ZoneDirectory.Data?>(null) {
        value = withContext(Dispatchers.IO) { ZoneDirectory.load(context) }
    }
    var continent by rememberSaveable { mutableStateOf<String?>(null) }
    var country by rememberSaveable { mutableStateOf<String?>(null) }
    var path by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<ZoneDirectory.Place?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(continent, country, path, query.isBlank()) { list.scrollToItem(0) }

    // one step up: tile -> parent tile -> country -> continent -> close
    fun up() {
        open = null
        when {
            path == ALL_CITIES || (path.isNotEmpty() && path.length <= 2) -> path = ""
            path.isNotEmpty() -> path = path.dropLast(1)
            country != null -> country = null
            continent != null -> continent = null
            else -> onClose()
        }
    }
    BackHandler { if (query.isNotEmpty()) query = "" else up() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { up() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.rooms_back)) }
            // where we are: Directory / Europe / France / #u0
            Text(
                // while searching, results come from everywhere: the path would mislead
                if (query.isNotBlank()) stringResource(R.string.dir_title) else listOfNotNull(
                    stringResource(R.string.dir_title), continent?.let { continentName(it) }, country?.let(::countryName),
                    path.takeIf { it.isNotEmpty() && it != ALL_CITIES }?.let { "#$it" }
                ).joinToString(" / "),
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
        val cc = country
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            when {
                query.isNotBlank() -> {
                    // a typed zone code ("#u09") comes first, then places by name
                    ZoneDirectory.asCode(query)?.let { code ->
                        item(key = "code") {
                            val inCountries = d.countriesWithTile(code)
                            TileRow(
                                code = code,
                                places = inCountries.take(4).map(::countryName),
                                showLevel = code.length <= 4,
                                onOpen = if (inCountries.size == 1 && code.length <= 4) {
                                    { country = inCountries[0]; continent = d.continentOf[inCountries[0]]; path = if (code.length == 1) "" else code; query = "" }
                                } else null,
                                onJoin = onJoin
                            )
                        }
                    }
                    val found = d.search(query)
                    if (found.isEmpty() && ZoneDirectory.asCode(query) == null) item { Hint(stringResource(R.string.dir_empty)) }
                    items(found, key = { "s" + it.geohash + it.name }) { p ->
                        PlaceRow(p, expanded = open === p, showCountry = true, onToggle = { open = if (open === p) null else p }, onJoin = onJoin)
                    }
                }
                cc != null && path == ALL_CITIES -> {
                    items(d.citiesByCountry[cc].orEmpty(), key = { it.geohash + it.name }) { p ->
                        PlaceRow(p, expanded = open === p, showCountry = false, onToggle = { open = if (open === p) null else p }, onJoin = onJoin)
                    }
                }
                cc != null && path.isEmpty() -> {
                    // the whole territory at a glance: large zones, then regions to open
                    item(key = "large") {
                        Section(stringResource(R.string.dir_level_large) + " · " + TILE_SIZE[1])
                        LargeZones(d.subTiles(cc, "").map { it.code }, onJoin)
                    }
                    item(key = "regions") { Section(stringResource(R.string.location_level_region) + " · " + TILE_SIZE[2]) }
                    items(d.subTiles(cc, "").flatMap { d.subTiles(cc, it.code) }, key = { "t" + it.code }) { t ->
                        TileRow(t.code, t.places, showLevel = false, onOpen = { path = t.code }, onJoin = onJoin)
                    }
                    item(key = "all") {
                        NavRow(stringResource(R.string.dir_all_cities), cityCount(d.citiesByCountry[cc]?.size ?: 0)) { path = ALL_CITIES }
                    }
                }
                cc != null -> {
                    // the current tile on top, so it can be joined as a whole
                    d.tile(cc, path)?.let { t ->
                        item(key = "here") { TileRow(t.code, t.places, showLevel = true, current = true, onOpen = null, onJoin = onJoin) }
                    }
                    if (path.length < 4) {
                        item(key = "sub") { Section(stringResource(R.string.dir_subzones)) }
                        items(d.subTiles(cc, path), key = { "t" + it.code }) { t ->
                            TileRow(t.code, t.places, showLevel = true, onOpen = { path = t.code }, onJoin = onJoin)
                        }
                    } else {
                        item(key = "cities") { Section(stringResource(R.string.dir_cities_here)) }
                        val cities = d.citiesIn(cc, path)
                        if (cities.isEmpty()) item { Hint(stringResource(R.string.dir_no_city)) }
                        items(cities, key = { it.geohash + it.name }) { p ->
                            PlaceRow(p, expanded = open === p, showCountry = false, onToggle = { open = if (open === p) null else p }, onJoin = onJoin)
                        }
                    }
                }
                continent != null -> {
                    val ccs = d.countries(continent!!).sortedWith(compareBy(collator) { countryName(it) })
                    items(ccs, key = { it }) { c ->
                        NavRow(countryName(c), cityCount(d.citiesByCountry[c]?.size ?: 0)) { country = c; path = "" }
                    }
                }
                else -> {
                    items(ZoneDirectory.CONTINENTS, key = { it }) { c ->
                        NavRow(continentName(c), pluralStringResource(R.plurals.dir_countries, d.countries(c).size, d.countries(c).size)) { continent = c }
                    }
                    item { Hint(stringResource(R.string.dir_source)) }
                }
            }
        }
    }
}

@Composable
private fun cityCount(n: Int) = pluralStringResource(R.plurals.dir_cities, n, n)

/** The 1-character tiles of a country, side by side: each is joinable, too large to be worth opening. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LargeZones(codes: List<String>, onJoin: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        codes.forEach { code ->
            androidx.compose.material3.OutlinedButton(onClick = { onJoin(code) }) {
                Text("#$code", fontFamily = BitchatFontFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
}

@Composable
private fun Section(text: String) {
    Text(
        text, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = BitchatFontFamily
    )
}

/**
 * A zone tile: its code, size, largest places and a join button. Tapping the row opens the
 * smaller tiles inside it ([onOpen] null: not openable, e.g. the tile currently shown).
 */
@Composable
private fun TileRow(
    code: String, places: List<String>, showLevel: Boolean, onOpen: (() -> Unit)?,
    onJoin: (String) -> Unit, current: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .then(if (current) Modifier.background(colors.primary.copy(alpha = 0.08f)) else Modifier)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#$code", fontFamily = BitchatFontFamily, color = colors.primary, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, modifier = Modifier.width(92.dp)
        )
        Column(Modifier.weight(1f)) {
            if (showLevel) Text(tileLevel(code.length), fontSize = 12.sp, color = colors.onSurfaceVariant, fontFamily = BitchatFontFamily)
            if (places.isNotEmpty()) Text(places.joinToString(", "), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { onJoin(code) }) { Text(stringResource(R.string.dir_join), fontFamily = BitchatFontFamily) }
        if (onOpen != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.primary)
    }
    HorizontalDivider(color = colors.outline.copy(alpha = 0.15f))
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

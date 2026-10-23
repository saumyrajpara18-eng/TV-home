package com.example.tvhome

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

const val TMDB_KEY = "YOUR_TMDB_API_KEY"
private const val IMG = "https://image.tmdb.org/t/p/"

private val Bg = Color(0xFF121317)
private val Pill = Color(0xFF2B2C30)
private val PillSelected = Color(0xFFE3E3E8)
private val Accent = Color(0xFFA8C7FA)
private val OnAccent = Color(0xFF062E6F)
private val Muted = Color(0xFFC4C7D0)

data class Media(
    val title: String, val overview: String, val backdrop: String?, val poster: String?,
    val year: String?, val rating: Double?, val kind: String
)
data class AppEntry(val label: String, val icon: ImageBitmap, val intent: Intent)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) { HomeScreen() }
            }
        }
    }
    @Deprecated("Home is the root")
    override fun onBackPressed() { }
}

suspend fun fetchTrending(kind: String): List<Media> = withContext(Dispatchers.IO) {
    runCatching {
        val arr = JSONObject(URL("https://api.themoviedb.org/3/trending/$kind/week?api_key=$TMDB_KEY").readText())
            .getJSONArray("results")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun s(k: String): String? = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
            val date = s("release_date") ?: s("first_air_date")
            val type = s("media_type") ?: if (kind == "tv") "tv" else "movie"
            Media(
                title = s("title") ?: s("name") ?: "",
                overview = s("overview") ?: "",
                backdrop = s("backdrop_path"), poster = s("poster_path"),
                year = date?.take(4),
                rating = if (o.isNull("vote_average")) null else o.getDouble("vote_average"),
                kind = if (type == "tv") "TV show" else "Movie"
            )
        }
    }.getOrDefault(emptyList())
}

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    fun query(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
    val list = query(Intent.CATEGORY_LEANBACK_LAUNCHER).ifEmpty { query(Intent.CATEGORY_LAUNCHER) }
    return list.filter { it.activityInfo.packageName != ctx.packageName }.mapNotNull { ri ->
        val pkg = ri.activityInfo.packageName
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return@mapNotNull null
        AppEntry(ri.loadLabel(pm).toString(), ri.loadIcon(pm).toBitmap(192, 192).asImageBitmap(), intent)
    }.sortedBy { it.label.lowercase() }
}

private fun safeStart(ctx: Context, intent: Intent) {
    runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openTrailer(ctx: Context, title: String) = safeStart(
    ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode("$title trailer")))
)

@Composable
fun Focusable(
    modifier: Modifier = Modifier, shape: Shape, focusScale: Float = 1.08f,
    onFocus: () -> Unit = {}, onClick: () -> Unit = {}, content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "s")
    Box(
        modifier.scale(scale).clip(shape)
            .border(if (focused) 3.dp else 0.dp, Color.White, shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .clickable(onClick = onClick),
        content = content
    )
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var picks by remember { mutableStateOf<List<Media>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Media>>(emptyList()) }
    var shows by remember { mutableStateOf<List<Media>>(emptyList()) }
    var heroIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
        picks = fetchTrending("all")
        movies = fetchTrending("movie")
        shows = fetchTrending("tv")
    }
    val heroes = remember(picks) { picks.filter { it.backdrop != null }.take(5) }
    LaunchedEffect(heroes.size) {
        if (heroes.size > 1) while (true) { delay(8000); heroIndex = (heroIndex + 1) % heroes.size }
    }

    when (tab) {
        0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
            item { Hero(heroes, heroIndex, tab, { tab = it }, Modifier.fillParentMaxHeight(0.64f)) }
            item { AppsRow(apps) }
            item { CardRow("Top picks for you", picks) }
            item { CardRow("Trending movies", movies) }
            item { CardRow("Trending shows", shows) }
        }
        1 -> Column(Modifier.fillMaxSize()) {
            TopBar(tab) { tab = it }
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Live", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Connect an antenna or open a live TV app from Apps.", color = Muted, fontSize = 16.sp)
                }
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            TopBar(tab) { tab = it }
            LazyVerticalGrid(
                GridCells.Adaptive(120.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(apps) { a -> AppIcon(a) }
            }
        }
    }
}

@Composable
fun TopBar(selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val ctx = LocalContext.current
    Row(modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Pill.copy(alpha = 0.85f)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Focusable(Modifier.size(44.dp), CircleShape, 1.05f, onClick = {
                safeStart(ctx, Intent("android.search.action.GLOBAL_SEARCH"))
            }) { Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Filled.Search, "Search", Modifier.size(24.dp)) } }
            listOf("Home", "Live", "Apps").forEachIndexed { i, t ->
                val sel = i == selected
                Focusable(Modifier.height(44.dp), RoundedCornerShape(50), 1.05f, onClick = { onSelect(i) }) {
                    Box(
                        Modifier.fillMaxHeight().background(if (sel) PillSelected else Color.Transparent).padding(horizontal = 22.dp),
                        Alignment.Center
                    ) { Text(t, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (sel) Color(0xFF1B1B1F) else Color.White) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Pill.copy(alpha = 0.85f)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Focusable(Modifier.size(44.dp), CircleShape, 1.05f, onClick = { safeStart(ctx, Intent(Settings.ACTION_SETTINGS)) }) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Icon(Icons.Filled.Settings, "Settings", Modifier.size(24.dp)) }
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF5B6B8C)), Alignment.Center) {
                Icon(Icons.Filled.Person, "Profile", Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun Hero(heroes: List<Media>, index: Int, tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val m = heroes.getOrNull(index)
    Box(modifier.fillMaxWidth()) {
        Crossfade(m, label = "hero") { cur ->
            AsyncImage(
                cur?.backdrop?.let { "${IMG}w1280$it" }, null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = 0.6f), Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
        TopBar(tab, Modifier.align(Alignment.TopStart), onTab)
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 24.dp).fillMaxWidth(0.5f)) {
            Text("Top pick for you", fontSize = 14.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            Text(m?.title ?: "Welcome", fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (m != null) {
                Spacer(Modifier.height(6.dp))
                val meta = listOfNotNull(m.kind, m.year, m.rating?.takeIf { it > 0 }?.let { "%.1f".format(it) + " TMDB" }).joinToString("  \u2022  ")
                Text(meta, fontSize = 14.sp, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                m?.overview ?: "Add your TMDB key in MainActivity.kt to see recommendations.",
                fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Color(0xFFE3E3E8)
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m != null) {
                    Focusable(Modifier.height(48.dp), RoundedCornerShape(50), 1.06f, onClick = { openTrailer(ctx, m.title) }) {
                        Row(
                            Modifier.fillMaxHeight().background(Accent).padding(horizontal = 22.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(24.dp), tint = OnAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Watch trailer", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OnAccent)
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                }
                if (heroes.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    heroes.indices.forEach { i ->
                        Box(Modifier.height(6.dp).width(if (i == index) 22.dp else 6.dp).clip(CircleShape)
                            .background(if (i == index) Color.White else Color(0x66FFFFFF)))
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(a: AppEntry) {
    val ctx = LocalContext.current
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Focusable(Modifier.size(72.dp), CircleShape, 1.1f, onClick = { safeStart(ctx, a.intent) }) {
            Box(Modifier.fillMaxSize().background(Pill), Alignment.Center) {
                Image(a.icon, a.label, Modifier.fillMaxSize(0.62f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(a.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = Muted)
    }
}

@Composable
fun AppsRow(apps: List<AppEntry>) {
    val ctx = LocalContext.current
    Section("Your apps")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(apps) { a -> AppIcon(a) }
        item {
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Focusable(Modifier.size(72.dp), CircleShape, 1.1f, onClick = {
                    safeStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=tv&c=apps")))
                }) {
                    Box(Modifier.fillMaxSize().background(Pill), Alignment.Center) { Icon(Icons.Filled.Add, "Add app", Modifier.size(30.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Add app", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

@Composable
fun Section(title: String) =
    Text(title, Modifier.padding(start = 48.dp, top = 16.dp), fontSize = 18.sp, fontWeight = FontWeight.Medium)

@Composable
fun CardRow(title: String, items: List<Media>) {
    val ctx = LocalContext.current
    Section(title)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items) { m ->
            Column(Modifier.width(240.dp)) {
                Focusable(Modifier.fillMaxWidth().height(135.dp), RoundedCornerShape(16.dp), 1.06f, onClick = { openTrailer(ctx, m.title) }) {
                    AsyncImage(
                        (m.backdrop?.let { "${IMG}w500$it" } ?: m.poster?.let { "${IMG}w342$it" }),
                        m.title, Modifier.fillMaxSize().background(Pill), contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(m.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted)
            }
        }
    }
}

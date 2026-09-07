package com.immolink.appname

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import co.opensi.kkiapay.uikit.Kkiapay
import co.opensi.kkiapay.uikit.SdkConfig
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private fun digits(value: String) = value.filter(Char::isDigit)
private fun validPassword(value: String) = value.length >= 8 && value.all { it.isLetterOrDigit() }
private fun validEmail(value: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()
private fun money(value: Long) = NumberFormat.getIntegerInstance(Locale.FRANCE).format(value) + " FCFA"
private fun wa(code: String, phone: String) = "https://wa.me/${digits(code).removePrefix("00")}${digits(phone)}"
private fun readableError(error: Throwable): String {
    val m = error.message.orEmpty()
    return when {
        m.contains("SUPABASE", true) -> m.substringAfter(": ").take(220)
        m.contains("UNAVAILABLE", true) || m.contains("network", true) -> "Connexion indisponible. Vérifiez Internet puis réessayez."
        m.contains("NOT_FOUND", true) -> "La donnée demandée n’existe plus."
        m.isBlank() -> "Une erreur inattendue est survenue."
        else -> m.take(180)
    }
}

class MainActivity : ComponentActivity() {
    private var paymentCallback: ((String, String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            Kkiapay.init(
                applicationContext,
                KkiapayConfig.PUBLIC_KEY,
                SdkConfig(themeColor = R.color.colorPrimary, enableSandbox = false)
            )
        }
        setContent { ImmoLinkApp() }
    }

    fun startKkiapayPayment(
        amount: Long,
        reason: String,
        partnerId: String,
        profile: UserProfile,
        onResult: (status: String, transactionId: String) -> Unit
    ) {
        paymentCallback = onResult
        val phone = digits(profile.countryCode).removePrefix("00") + digits(profile.phone)
        Kkiapay.get().requestPayment(
            this,
            amount.toInt(),
            reason = reason,
            api_key = KkiapayConfig.PUBLIC_KEY,
            sandbox = false,
            name = profile.firstName,
            partnerId = partnerId,
            phone = phone,
            email = profile.email,
            paymentMethods = listOf("momo", "card", "direct_debit")
        )
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            Kkiapay.get().setListener { status, transactionId ->
                paymentCallback?.invoke(status.name, transactionId)
                paymentCallback = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        runCatching { Kkiapay.get().handleActivityResult(requestCode, resultCode, data) }
    }
}

@Composable
fun ImmoLinkApp() {
    val context = LocalContext.current
    val repo = remember { SupabaseRepository(context) }
    var splash by remember { mutableStateOf(true) }
    var currentUid by remember { mutableStateOf(repo.currentUid().takeIf { it.isNotBlank() }) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(5000); splash = false }
    ImmoLinkTheme {
        when {
            splash -> SplashScreen()
            currentUid == null -> AuthFlow { currentUid = it }
            else -> AppShell(currentUid!!) { repo.signOut(); currentUid = null }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.immo_logo), "Logo ImmoLink", Modifier.width(300.dp))
            Spacer(Modifier.height(18.dp))
            Text("Fiable et accessible à tous", color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = Orange, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun AuthFlow(onAuthenticated: (String) -> Unit) {
    var page by remember { mutableStateOf(0) }
    var country by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var login by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repo = remember { SupabaseRepository(context) }

    fun register() {
        scope.launch {
            busy = true; error = ""
            try {
                repo.signUp(
                    email.trim(),
                    password,
                    mapOf(
                        "firstName" to first.trim(),
                        "phone" to digits(phone),
                        "country" to country,
                        "countryCode" to code,
                        "city" to city
                    )
                )
                onAuthenticated(repo.currentUid())
            } catch (e: Exception) {
                error = when {
                    e.message?.contains("SUPABASE_EMAIL_CONFIRMATION_REQUIRED", true) == true ->
                        "La confirmation e-mail est activée dans Supabase. Désactivez-la dans Authentication → Providers → Email."
                    e.message?.contains("already registered", true) == true || e.message?.contains("already been registered", true) == true ->
                        "Cette adresse e-mail possède déjà un compte. Utilisez Connexion."
                    else -> readableError(e)
                }
            } finally { busy = false }
        }
    }

    fun doLogin() {
        scope.launch {
            busy = true; error = ""
            try {
                repo.signIn(loginEmail.trim(), loginPassword)
                onAuthenticated(repo.currentUid())
            } catch (e: Exception) {
                error = "Adresse e-mail ou mot de passe incorrect."
            } finally { busy = false }
        }
    }

    if (login) {
        return AuthCard("Connexion", error, { login = false; error = "" }) {
            BrandHeader(compact = true)
            OutlinedTextField(
                loginEmail,
                { loginEmail = it.take(120) },
                label = { Text("Adresse e-mail") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            )
            PasswordField(loginPassword, { loginPassword = it }, "Mot de passe")
            Button(
                { doLogin() },
                Modifier.fillMaxWidth().height(54.dp),
                enabled = validEmail(loginEmail) && validPassword(loginPassword) && !busy
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("Se connecter")
            }
            TextButton({ login = false; page = 0 }) { Text("Créer un compte") }
        }
    }

    when (page) {
        0 -> AuthCard("Bienvenue sur ImmoLink", error, {}) {
            BrandHeader(); Text("Trouvez rapidement un logement, un terrain ou une maison dans votre pays.", fontSize = 17.sp, color = Muted)
            FeatureLine(Icons.Default.VerifiedUser, "Des annonces filtrées par pays"); FeatureLine(Icons.Default.Chat, "Contact direct par chat ou WhatsApp"); FeatureLine(Icons.Default.Notifications, "Alertes sur vos recherches")
            Button({ page = 1 }, Modifier.fillMaxWidth().height(54.dp)) { Text("Créer mon compte") }
            OutlinedButton({ login = true; error = "" }, Modifier.fillMaxWidth().height(54.dp)) { Text("J’ai déjà un compte") }
        }
        1 -> AuthCard("Votre pays de résidence", error, {}) {
            BrandHeader(compact = true); Text("Choisissez votre pays. Vous ne verrez ensuite que les annonces de ce pays.", color = Muted)
            CountryDropdown(country) { country = it; code = Data.countries.first { c -> c.first == it }.second; city = "" }
            Button({ page = 2 }, Modifier.fillMaxWidth().height(54.dp), enabled = country.isNotBlank()) { Text("Continuer") }
        }
        2 -> AuthCard("Votre ville", error, { page = 1 }) {
            Text("Votre ville permet d’affiner les résultats.", color = Muted); CityDropdown(country, city) { city = it }
            Button({ page = 3 }, Modifier.fillMaxWidth().height(54.dp), enabled = city.isNotBlank()) { Text("Continuer") }
        }
        3 -> AuthCard("Vos coordonnées", error, { page = 2 }) {
            OutlinedTextField(first, { first = it.take(40) }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PhoneField(code, phone) { phone = it }
            Text("Votre numéro WhatsApp permet aux utilisateurs de vous contacter directement sur WhatsApp.", fontSize = 12.sp, color = Muted)
            Button({ page = 4 }, Modifier.fillMaxWidth().height(54.dp), enabled = first.isNotBlank() && phone.length >= 6) { Text("Continuer") }
        }
        4 -> AuthCard("Votre compte", error, { page = 3 }) {
            Text("L’adresse e-mail sera votre identifiant de connexion.", color = Muted)
            OutlinedTextField(
                email,
                { email = it.take(120) },
                label = { Text("Adresse e-mail") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email)
            )
            PasswordField(password, { password = it }, "Mot de passe")
            PasswordField(confirmPassword, { confirmPassword = it }, "Confirmer le mot de passe")
            Text("8 caractères minimum, lettres et chiffres uniquement.", fontSize = 12.sp, color = Muted)
            if (confirmPassword.isNotBlank() && password != confirmPassword) {
                Text("Les deux mots de passe ne correspondent pas.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Button(
                ::register,
                Modifier.fillMaxWidth().height(54.dp),
                enabled = validEmail(email) && validPassword(password) && password == confirmPassword && !busy
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("Créer mon compte")
            }
        }
    }
}

@Composable
fun AuthCard(title: String, error: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Light) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Navy) }
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, color = Navy, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(48.dp))
            }
            if (error.isNotBlank()) {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                }
            }
            content()
        }
    }
}

@Composable
fun BrandHeader(compact: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Image(painterResource(R.drawable.immo_logo), "ImmoLink", Modifier.width(if (compact) 170.dp else 220.dp), contentScale = ContentScale.Fit)
        if (!compact) Text("Votre lien immobilier", color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun FeatureLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(modifier = Modifier.size(38.dp), shape = CircleShape, color = OrangeSoft) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Orange, modifier = Modifier.size(20.dp)) }
        }
        Text(text, color = Ink, modifier = Modifier.weight(1f))
    }
}

@Composable fun CountryDropdown(value: String, onChange: (String) -> Unit) = DropdownField(value.ifBlank { "Sélectionner le pays" }, Data.countries.map { it.first }, onChange)
@Composable fun CityDropdown(country: String, value: String, onChange: (String) -> Unit) = DropdownField(value.ifBlank { "Sélectionner la ville" }, Data.cities[country] ?: emptyList(), onChange)

@Composable
fun DropdownField(label: String, items: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ open = true }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Text(label, Modifier.weight(1f), color = if (label.startsWith("Sélectionner") || label == "Type de bien") Muted else Ink)
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(open, { open = false }) { items.forEach { item -> DropdownMenuItem({ Text(item) }, { open = false; onChange(item) }) } }
    }
}

@Composable
fun PhoneField(code: String, phone: String, onPhone: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(code, {}, label = { Text("Indicatif") }, modifier = Modifier.width(112.dp), readOnly = true, singleLine = true)
        OutlinedTextField(phone, { onPhone(digits(it).take(15)) }, label = { Text("WhatsApp") }, modifier = Modifier.weight(1f), singleLine = true)
    }
}

@Composable
fun PasswordField(value: String, onChange: (String) -> Unit, label: String = "Mot de passe") {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isLetterOrDigit).take(128)) },
        label = { Text(label) },
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton({ visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun AppShell(uid: String, logout: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SupabaseRepository(context) }
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var agency by remember { mutableStateOf<Agency?>(null) }
    var loadError by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf("rent") }
    var screen by remember { mutableStateOf("home") }
    var selected by remember { mutableStateOf<Listing?>(null) }
    var publishMode by remember { mutableStateOf("") }
    var ownerUid by remember { mutableStateOf("") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun load() {
        scope.launch {
            loading = true
            loadError = ""
            try {
                val p = repo.getProfile(uid)
                if (p.country !in Data.countries.map { it.first } || p.city.isBlank()) throw IllegalStateException("Profil incomplet")
                profile = p
                agency = repo.getAgency(uid)
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } catch (e: Exception) {
                profile = null
                loadError = readableError(e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(uid) { load() }

    if (loading && profile == null) return LoadingScreen("Chargement de votre espace…")
    if (loadError.isNotBlank() || profile == null) return StartupErrorScreen(loadError.ifBlank { "Profil introuvable." }, ::load, logout)

    val p = profile!!
    BackHandler(enabled = screen != "home") {
        screen = when (screen) {
            "chat" -> "chats"
            "chats" -> "home"
            "detail" -> "home"
            "owner" -> "detail"
            "search" -> "home"
            "publish" -> "profile"
            "agencyCreate" -> "profile"
            "agency" -> "home"
            "myListings" -> "profile"
            "certification" -> "profile"
            "profile" -> "home"
            else -> "home"
        }
    }
    when (screen) {
        "detail" -> selected?.let { ListingDetail(uid, it, repo, { screen = "home" }, { ownerUid = it.ownerId; screen = "owner" }, { screen = "chat" }) } ?: run { screen = "home" }
        "search" -> SearchScreen(uid, p, repo) { selected = it; screen = "detail" }
        "publish" -> PublishScreen(uid, p, repo, publishMode) {
            publishMode = ""
            scope.launch { agency = runCatching { repo.getAgency(uid) }.getOrNull(); screen = "home" }
        }
        "profile" -> ProfileScreen(uid, p, agency, repo, logout, { publishMode = "sale"; screen = "publish" }, { publishMode = "rent"; screen = "publish" }, { screen = "agencyCreate" }, { screen = "myListings" }, { screen = "certification" }, { screen = "agency" })
        "agencyCreate" -> AgencyCreateScreen(uid, p, repo) { scope.launch { agency = runCatching { repo.getAgency(uid) }.getOrNull(); screen = "profile" } }
        "agency" -> AgencyScreen(uid, repo) { screen = "profile" }
        "myListings" -> MyListingsScreen(uid, repo) { screen = "profile" }
        "certification" -> CertificationScreen(uid, agency, repo) { scope.launch { agency = runCatching { repo.getAgency(uid) }.getOrNull(); screen = "profile" } }
        "owner" -> OwnerProfileScreen(ownerUid, repo) { screen = "detail" }
        "chats" -> ChatsScreen(uid, repo) { selected = it; screen = "chat" }
        "chat" -> selected?.let { ChatDetailScreen(uid, it, repo) { screen = "chats" } } ?: run { screen = "chats" }
        else -> HomeScreen(uid, p, tab, repo, { tab = it }, { selected = it; screen = "detail" }, { screen = "search" }, { publishMode = ""; screen = "publish" }, { screen = "profile" }, { screen = "chats" })
    }
}

@Composable
fun LoadingScreen(message: String) = Box(Modifier.fillMaxSize().background(Light), Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { CircularProgressIndicator(color = Orange); Text(message, color = Muted) } }

@Composable
fun StartupErrorScreen(message: String, retry: () -> Unit, logout: () -> Unit) = Box(Modifier.fillMaxSize().background(Light).padding(24.dp), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = OrangeSoft, modifier = Modifier.size(70.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.CloudOff, null, tint = Orange, modifier = Modifier.size(34.dp)) } }
        Text("Impossible de charger ImmoLink", style = MaterialTheme.typography.headlineMedium, color = Navy)
        Text(message, color = Muted)
        Button(retry, Modifier.fillMaxWidth().height(52.dp)) { Text("Réessayer") }
        OutlinedButton(logout, Modifier.fillMaxWidth().height(52.dp)) { Text("Se déconnecter") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(uid: String, p: UserProfile, tab: String, repo: SupabaseRepository, setTab: (String) -> Unit, open: (Listing) -> Unit, search: () -> Unit, publish: () -> Unit, profile: () -> Unit, chats: () -> Unit) {
    var listings by remember(tab, p.country) { mutableStateOf<List<Listing>>(emptyList()) }
    var promoted by remember(tab, p.country) { mutableStateOf<List<Listing>>(emptyList()) }
    var loading by remember(tab, p.country) { mutableStateOf(true) }
    var error by remember(tab, p.country) { mutableStateOf("") }
    LaunchedEffect(tab, p.country) {
        loading = true
        error = ""
        try {
            listings = repo.getFeatured(uid, tab)
            promoted = repo.getPromotedListings(uid, tab)
        } catch (e: Exception) { error = readableError(e); listings = emptyList(); promoted = emptyList() }
        finally { loading = false }
    }
    Scaffold(
        containerColor = Light,
        topBar = { CenterAlignedTopAppBar(title = { Text("ImmoLink", color = Navy, fontWeight = FontWeight.Bold) }, actions = { IconButton(search) { Icon(Icons.Default.Search, "Rechercher", tint = Navy) } }) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(true, { }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Accueil") })
                NavigationBarItem(false, search, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Rechercher") })
                NavigationBarItem(false, chats, icon = { Icon(Icons.Default.Chat, null) }, label = { Text("Messages") })
                NavigationBarItem(false, profile, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profil") })
            }
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.horizontalGradient(listOf(NavyDark, Navy))).padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bonjour ${p.firstName} 👋", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Les annonces affichées sont limitées à ${p.country}.", color = Color.White.copy(alpha = .82f))
                        Spacer(Modifier.height(4.dp))
                        Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .12f)) { Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Orange); Spacer(Modifier.width(7.dp)); Text(p.city, color = Color.White, fontWeight = FontWeight.SemiBold) } }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("rent" to "À louer", "sale" to "À vendre", "bail" to "À bailler").forEach { (key, label) ->
                        FilterChip(selected = tab == key, onClick = { setTab(key) }, label = { Text(label) }, leadingIcon = if (tab == key) ({ Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }) else null)
                    }
                }
            }
            item { OutlinedButton(search, Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(8.dp)); Text("Recherche avancée") } }
            item { Button(publish, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AddHome, null); Spacer(Modifier.width(8.dp)); Text("Proposer une offre") } }
            if (promoted.isNotEmpty()) {
                item { Text("Annonces sponsorisées", style = MaterialTheme.typography.titleLarge, color = Navy) }
                items(promoted) { item ->
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = OrangeSoft), modifier = Modifier.fillMaxWidth().clickable { open(item) }) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Campaign, null, tint = Orange)
                                Spacer(Modifier.width(6.dp))
                                Text("À LA UNE", color = Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(6.dp))
                            ListingCard(item, open)
                        }
                    }
                }
            }
            if (loading) item { Box(Modifier.fillMaxWidth().padding(30.dp), Alignment.Center) { CircularProgressIndicator(color = Orange) } }
            else if (error.isNotBlank()) item { Surface(color = Color(0xFFFFF4F2), shape = RoundedCornerShape(14.dp)) { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(14.dp)) } }
            else if (listings.isEmpty()) item { EmptyState("Aucune annonce ne correspond pour le moment.") }
            else items(listings) { ListingCard(it, open) }
        }
    }
}

@Composable
fun ListingCard(item: Listing, open: (Listing) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { open(item) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column {
            val url = item.photoUrls.firstOrNull()
            if (url != null) AsyncImage(url, "Photo du bien", Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
            else Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.linearGradient(listOf(Navy, NavyDark))), Alignment.Center) { Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(64.dp)) }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.propertyType, style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(10.dp), color = OrangeSoft) { Text(if (item.mode == "rent") "À louer" else if (item.mode == "sale") "À vendre" else "À bailler", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) }
                }
                Text("${item.city} • ${money(item.price)}", color = Ink, fontWeight = FontWeight.SemiBold)
                if (item.description.isNotBlank()) Text(item.description, color = Muted, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null, tint = Muted, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text(item.ownerName, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (item.agencyCertified) { Icon(Icons.Default.Verified, null, tint = Orange, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(3.dp)); Text("Certifiée", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(uid: String, p: UserProfile, repo: SupabaseRepository, open: (Listing) -> Unit) {
    var mode by remember { mutableStateOf("rent") }
    var type by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf<BudgetRange?>(null) }
    var results by remember { mutableStateOf<List<Listing>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val budgets = if (mode == "rent") Data.rentBudgets else Data.saleBudgets
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Recherche avancée", style = MaterialTheme.typography.headlineMedium, color = Navy)
        Text("Les résultats sont automatiquement limités à ${p.country}.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == "rent", { mode = "rent"; type = ""; budget = null }, label = { Text("À louer") })
            FilterChip(mode == "sale", { mode = "sale"; type = ""; budget = null }, label = { Text("À vendre") })
            FilterChip(mode == "bail", { mode = "bail"; type = ""; budget = null }, label = { Text("À bailler") })
        }
        if (mode != "bail") DropdownField(type.ifBlank { "Type de bien" }, if (mode == "rent") Data.rentTypes else Data.saleTypes) { type = it }
        DropdownField(city.ifBlank { "Toutes les villes" }, listOf("Toutes les villes") + (Data.cities[p.country] ?: emptyList())) { city = if (it == "Toutes les villes") "" else it }
        if (mode != "bail") DropdownField(budget?.label ?: "Votre budget", budgets.map { it.label }) { budget = budgets.first { b -> b.label == it } }
        if (error.isNotBlank()) Surface(color = Color(0xFFFFF4F2), shape = RoundedCornerShape(14.dp)) { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        Button({ scope.launch { busy = true; error = ""; try { results = repo.searchListings(uid, SearchCriteria(mode, type, city, budget?.min ?: 0, budget?.max)) } catch (e: Exception) { error = readableError(e); results = emptyList() } finally { busy = false } } }, Modifier.fillMaxWidth().height(52.dp)) { if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else { Icon(Icons.Default.Search, null); Spacer(Modifier.width(7.dp)); Text("Rechercher") } }
        OutlinedButton({ scope.launch { try { repo.saveSearch(SearchCriteria(mode, type, city, budget?.min ?: 0, budget?.max), p.country, uid); saved = true } catch (e: Exception) { error = readableError(e) } } }, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(7.dp)); Text(if (saved) "Recherche enregistrée" else "M’avertir des nouvelles annonces") }
        results.forEach { ListingCard(it, open) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetail(uid: String, item: Listing, repo: SupabaseRepository, back: () -> Unit, owner: () -> Unit, openChat: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var favorite by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(item.id) {
        runCatching { favorite = repo.isFavorite(uid, item.id) }
        runCatching { repo.incrementViews(item.id) }
    }
    Scaffold(containerColor = Light, topBar = { TopAppBar(title = { Text(item.propertyType, color = Navy, fontWeight = FontWeight.Bold) }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Retour") } }, actions = { IconButton({ favorite = !favorite; scope.launch { runCatching { repo.toggleFavorite(uid, item.id, item.country) }.onFailure { error = readableError(it) } } }) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favori", tint = Orange) } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).background(Light)) {
            if (item.photoUrls.isNotEmpty()) LazyRowPhotos(item.photoUrls) else Box(Modifier.fillMaxWidth().height(220.dp).background(Brush.linearGradient(listOf(Navy, NavyDark))), Alignment.Center) { Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(80.dp)) }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = OrangeSoft) { Text(if (item.mode == "rent") "À louer" else if (item.mode == "sale") "À vendre" else "À bailler", color = Orange, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
                Text(item.propertyType, style = MaterialTheme.typography.headlineMedium, color = Navy)
                Text("${item.city}, ${item.country}", color = Muted)
                Text(money(item.price), fontSize = 25.sp, color = Orange, fontWeight = FontWeight.Bold)
                if (item.depositMonths > 0) Text("Caution : ${item.depositMonths} mois", fontWeight = FontWeight.SemiBold)
                if (item.description.isNotBlank()) Text(item.description)
                if (item.ownerAgencyName.isNotBlank()) Text(if (item.agencyCertified) "✓ ${item.ownerAgencyName} — Agence certifiée" else "Agence : ${item.ownerAgencyName}", color = if (item.agencyCertified) Orange else Navy, fontWeight = FontWeight.Bold)
                Text("Publié par ${item.ownerName} • ${item.views} vue(s)", color = Muted)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                OutlinedButton(owner, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Person, null); Spacer(Modifier.width(6.dp)); Text("Voir le profil") }
                Button({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wa(item.ownerCountryCode, item.ownerPhone)))) }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Phone, null); Spacer(Modifier.width(6.dp)); Text("Contacter sur WhatsApp") }
                Button({ val chatId = java.util.UUID.nameUUIDFromBytes("${listOf(uid, item.ownerId).sorted().joinToString("_")}_${item.id}".toByteArray()).toString(); scope.launch { runCatching { repo.sendMessage(chatId, item.id, item.country, listOf(uid, item.ownerId), uid, "Bonjour, votre annonce m'intéresse.") }.onFailure { error = readableError(it) } }; openChat() }, Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Chat, null); Spacer(Modifier.width(6.dp)); Text("Démarrer une discussion") }
                TextButton({ report = true }) { Icon(Icons.Default.Flag, null); Spacer(Modifier.width(4.dp)); Text("Signaler cette annonce") }
            }
        }
    }
    if (report) AlertDialog(onDismissRequest = { report = false }, title = { Text("Signaler l’annonce") }, text = { OutlinedTextField(reason, { reason = it }, label = { Text("Raison") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { TextButton({ report = false; if (reason.isNotBlank()) scope.launch { runCatching { repo.reportListing(item.id, reason.trim()) }.onFailure { error = readableError(it) } } }) { Text("Envoyer") } }, dismissButton = { TextButton({ report = false }) { Text("Annuler") } })
}

@Composable
fun LazyRowPhotos(urls: List<String>) = LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) { itemsIndexed(urls.take(4)) { _, url -> AsyncImage(url, "Photo du bien", Modifier.size(285.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop) } }

@Composable
fun PublishScreen(uid: String, p: UserProfile, repo: SupabaseRepository, initialMode: String = "", done: () -> Unit) {
    var mode by remember { mutableStateOf(initialMode) }
    var type by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris = it.take(4) }
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Proposer une offre", style = MaterialTheme.typography.headlineMedium, color = Navy)
        Text("Votre annonce reste en ligne 15 jours. Elle se désactive automatiquement ensuite. Maximum 4 photos : la première est l’image principale.", color = Muted)
        DropdownField(if (mode.isBlank()) "Vendre ou mettre en location" else if (mode == "sale") "Vendre" else "Mettre en location", listOf("Vendre", "Mettre en location")) { mode = if (it == "Vendre") "sale" else "rent"; type = "" }
        if (mode.isNotBlank()) DropdownField(type.ifBlank { "Type de bien" }, if (mode == "sale") Data.saleTypes else Data.rentTypes) { type = it }
        if (mode.isNotBlank()) {
            OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Ajouter des photos (${uris.size}/4)") }
            if (uris.isNotEmpty()) Text("${uris.size} photo(s) sélectionnée(s)", color = Success, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(price, { price = digits(it) }, label = { Text(if (mode == "rent") "Loyer mensuel en FCFA" else "Prix en FCFA") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (mode == "rent") OutlinedTextField(deposit, { deposit = digits(it).take(2) }, label = { Text("Nombre de mois de caution") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(desc, { desc = it.take(4000) }, label = { Text("Description : quartier, prix négociable, conditions…") }, modifier = Modifier.fillMaxWidth().height(145.dp))
            DropdownField(relationship.ifBlank { "Votre relation avec le bien" }, Data.relationships) { relationship = it }
            if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error)
            Button({ scope.launch { busy = true; msg = ""; try { val urls = repo.uploadUris(uid, "listing-photos", uris, 4); repo.publishListing(mapOf("p_city" to p.city, "p_mode" to mode, "p_property_type" to type, "p_photo_urls" to org.json.JSONArray(urls), "p_price" to (price.toLongOrNull() ?: 0L), "p_deposit_months" to (deposit.toLongOrNull() ?: 0L), "p_description" to desc, "p_relationship" to relationship)); done() } catch (e: Exception) { msg = readableError(e) } finally { busy = false } } }, Modifier.fillMaxWidth().height(52.dp), enabled = type.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && relationship.isNotBlank() && !busy) { if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("Mettre en ligne") }
        }
    }
}

@Composable
fun ProfileScreen(uid: String, p: UserProfile, agency: Agency?, repo: SupabaseRepository, logout: () -> Unit, sell: () -> Unit, rent: () -> Unit, createAgency: () -> Unit, myListings: () -> Unit, cert: () -> Unit, agencyPage: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mon profil", style = MaterialTheme.typography.headlineMedium, color = Navy)
        Surface(shape = RoundedCornerShape(22.dp), color = Color.White, tonalElevation = 1.dp) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Surface(shape = CircleShape, color = BlueSoft, modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Text(p.firstName.take(1).uppercase(), color = Navy, fontWeight = FontWeight.Bold, fontSize = 20.sp) } }; Spacer(Modifier.width(12.dp)); Column { Text(p.firstName, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Navy); Text(p.phone, color = Muted) } }
                Text("${p.city}, ${p.country}", color = Muted)
                HorizontalDivider(Modifier.padding(vertical = 7.dp))
                if (agency != null) { Text(agency.name, color = Navy, fontWeight = FontWeight.Bold); if (agency.badgeActive()) Text("✓ Agence certifiée", color = Orange, fontWeight = FontWeight.Bold); Text("Quota : ${agency.quota()} annonces") } else Text("Compte particulier • quota : 3 annonces")
            }
        }
        Button(sell, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Sell, null); Spacer(Modifier.width(6.dp)); Text("Je veux vendre un bien") }
        Button(rent, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Key, null); Spacer(Modifier.width(6.dp)); Text("Je veux mettre en location") }
        OutlinedButton(myListings, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.List, null); Spacer(Modifier.width(6.dp)); Text("Voir mes annonces") }
        Button(createAgency, Modifier.fillMaxWidth().height(50.dp), enabled = agency == null) { Icon(Icons.Default.Business, null); Spacer(Modifier.width(6.dp)); Text(if (agency == null) "Créer mon agence immobilière" else "Agence déjà créée") }
        if (agency != null) {
            OutlinedButton(agencyPage, Modifier.fillMaxWidth().height(50.dp)) { Text("Voir ma page agence") }
            OutlinedButton(cert, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Verified, null); Spacer(Modifier.width(6.dp)); Text("Certification de l’agence") }
            if (!agency.extraSlotsPaid) OutlinedButton({
                val activity = context as? MainActivity
                if (activity != null) scope.launch {
                    runCatching { repo.createExtraSlotsIntent() }.onSuccess { intent ->
                        activity.startKkiapayPayment(intent.amount, "10 annonces supplémentaires ImmoLink", intent.paymentId, p) { status, tx ->
                            scope.launch {
                                runCatching { repo.attachPaymentTransaction(intent.paymentId, tx, status == "SUCCESS") }
                                error = if (status == "SUCCESS") "Paiement reçu. Les 10 emplacements seront activés après confirmation sécurisée." else "Paiement non validé."
                            }
                        }
                    }.onFailure { error = readableError(it) }
                }
            }, Modifier.fillMaxWidth().height(50.dp)) { Text("Débloquer 10 annonces supplémentaires — 5 000 FCFA") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        HorizontalDivider()
        OutlinedButton(logout, Modifier.fillMaxWidth().height(50.dp)) { Text("Déconnexion") }
        TextButton({ showDelete = true }) { Text("Supprimer mon compte", color = MaterialTheme.colorScheme.error) }
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Supprimer définitivement le compte ?") }, text = { Text("Cette action supprime votre profil, vos annonces et votre agence.") }, confirmButton = { TextButton({ scope.launch { runCatching { repo.deleteAccount(); logout() }.onFailure { error = readableError(it) }; showDelete = false } }) { Text("Supprimer") } }, dismissButton = { TextButton({ showDelete = false }) { Text("Annuler") } })
}

@Composable
fun AgencyCreateScreen(uid: String, p: UserProfile, repo: SupabaseRepository, done: () -> Unit) {
    var name by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var logo by remember { mutableStateOf<Uri?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { logo = it }; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Créer mon agence", style = MaterialTheme.typography.headlineMedium, color = Navy)
        Text("Une agence bénéficie de 15 annonces actives. Le paiement unique de 5 000 FCFA débloque 10 annonces supplémentaires, soit 25 au total.", color = Muted)
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nom de l’agence immobilière") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it.take(160) }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it.take(120) }, label = { Text("Email de l’agence *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text(if (logo == null) "Logo (facultatif)" else "Logo sélectionné") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button({ scope.launch { busy = true; try { val logoUrl = logo?.let { repo.uploadUris(uid, "agency-logos", listOf(it), 1).first() } ?: ""; repo.createAgency(mapOf("owner_id" to uid, "name" to name.trim(), "address" to address.trim(), "email" to email.trim(), "logo_url" to logoUrl)); done() } catch (e: Exception) { error = readableError(e) } finally { busy = false } } }, Modifier.fillMaxWidth().height(52.dp), enabled = name.isNotBlank() && address.isNotBlank() && email.contains("@") && !busy) { if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("Créer l’agence") }
        TextButton(done) { Text("Annuler") }
    }
}

@Composable
fun AgencyScreen(uid: String, repo: SupabaseRepository, back: () -> Unit) {
    var agency by remember { mutableStateOf<Agency?>(null) }; var listings by remember { mutableStateOf<List<Listing>>(emptyList()) }; var error by remember { mutableStateOf("") }; var promoteListing by remember { mutableStateOf<Listing?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { agency = repo.getAgency(uid); listings = repo.getMyListings(uid) } catch (e: Exception) { error = readableError(e) } }
    if (promoteListing != null) {
        PromotionScreen(uid, promoteListing!!, repo, { promoteListing = null }, { scope.launch { listings = repo.getMyListings(uid); promoteListing = null } })
        return
    }
    if (agency == null && error.isBlank()) return LoadingScreen("Chargement de l’agence…")
    LazyColumn(Modifier.fillMaxSize().background(Light), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Default.ArrowBack, "Retour") }; Text("Page agence", style = MaterialTheme.typography.headlineMedium, color = Navy) } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        agency?.let { a ->
            item { Surface(shape = RoundedCornerShape(22.dp), color = Color.White) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (a.logoUrl.isNotBlank()) AsyncImage(a.logoUrl, "Logo agence", Modifier.size(90.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop); Text(a.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy); if (a.badgeActive()) Text("✓ Agence certifiée", color = Orange, fontWeight = FontWeight.Bold); Text(a.address); Text(a.email, color = Muted); Text("${listings.size} annonce(s)", color = Muted) } } }
            item { Text("Annonces de l’agence", style = MaterialTheme.typography.titleLarge, color = Navy) }
            items(listings) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ListingCard(item) {}
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ scope.launch { runCatching { repo.deleteListing(item.id); listings = repo.getMyListings(uid) }.onFailure { error = readableError(it) } } }, Modifier.weight(1f), enabled = item.active) { Icon(Icons.Default.VisibilityOff, null); Spacer(Modifier.width(4.dp)); Text("Désactiver l'annonce") }
                        Button({ promoteListing = item }, Modifier.weight(1f), enabled = item.active) { Icon(Icons.Default.Campaign, null); Spacer(Modifier.width(4.dp)); Text("Promouvoir") }
                    }
                }
            }
        }
    }
}

@Composable
fun PromotionScreen(uid: String, listing: Listing, repo: SupabaseRepository, cancel: () -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val profile = remember { mutableStateOf<UserProfile?>(null) }
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(5) }
    var targetUsers by remember { mutableIntStateOf(50) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(uid) { profile.value = runCatching { repo.getProfile(uid) }.getOrNull() }
    val cost = ((targetUsers + 49) / 50) * ((days + 4) / 5) * 1000L
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(cancel) { Icon(Icons.Default.ArrowBack, "Retour") }; Text("Promouvoir l’annonce", style = MaterialTheme.typography.headlineMedium, color = Navy) }
        Text("${listing.propertyType} • ${money(listing.price)}", color = Navy, fontWeight = FontWeight.Bold)
        Text("L’annonce sera prioritairement proposée aux utilisateurs de ${listing.city}, puis aux autres villes de ${listing.country}.", color = Muted)
        Text("Durée : $days jours", fontWeight = FontWeight.Bold, color = Navy)
        Slider(value = days.toFloat(), onValueChange = { days = it.toInt().coerceIn(5,15) }, valueRange = 5f..15f, steps = 9)
        Text("Nombre de comptes ciblés : $targetUsers", fontWeight = FontWeight.Bold, color = Navy)
        Slider(value = targetUsers.toFloat(), onValueChange = { targetUsers = (it.toInt() / 50 * 50).coerceIn(50, 5000) }, valueRange = 50f..5000f, steps = 98)
        Surface(shape = RoundedCornerShape(18.dp), color = OrangeSoft) { Column(Modifier.padding(16.dp)) { Text("Budget estimé", color = Muted); Text(money(cost), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Orange); Text("Base : 1 000 FCFA pour 50 comptes pendant 5 jours. Le coût s’ajuste automatiquement.", color = Muted, fontSize = 12.sp) } }
        if (message.isNotBlank()) Text(message, color = if (message.startsWith("Paiement")) Success else MaterialTheme.colorScheme.error)
        Button({
            val a = activity
            val pr = profile.value
            if (a != null && pr != null) {
                scope.launch {
                    busy = true; message = ""
                    try {
                        val intent = repo.createPromotionIntent(listing.id, days, targetUsers)
                        a.startKkiapayPayment(cost, "Promotion ImmoLink - ${listing.propertyType}", intent.paymentId, pr) { status, tx ->
                            scope.launch {
                                runCatching { repo.attachPaymentTransaction(intent.paymentId, tx, status == "SUCCESS") }
                                message = if (status == "SUCCESS") "Paiement reçu. La promotion sera activée après confirmation sécurisée." else "Paiement non validé."
                                if (status == "SUCCESS") refresh()
                            }
                        }
                    } catch (e: Exception) { message = readableError(e) } finally { busy = false }
                }
            }
        }, Modifier.fillMaxWidth().height(54.dp), enabled = !busy && activity != null && profile.value != null) { Text(if (busy) "Préparation…" else "Payer $cost FCFA et promouvoir") }
        OutlinedButton(cancel, Modifier.fillMaxWidth().height(50.dp)) { Text("Annuler") }
    }
}

@Composable
fun OwnerProfileScreen(uid: String, repo: SupabaseRepository, back: () -> Unit) {
    var p by remember { mutableStateOf<UserProfile?>(null) }; var a by remember { mutableStateOf<Agency?>(null) }; var listings by remember { mutableStateOf<List<Listing>>(emptyList()) }; var error by remember { mutableStateOf("") }
    LaunchedEffect(uid) { try { p = repo.getOwner(uid); a = repo.getOwnerAgency(uid); listings = repo.getMyListings(uid) } catch (e: Exception) { error = readableError(e) } }
    if (p == null && error.isBlank()) return LoadingScreen("Chargement du profil…")
    LazyColumn(Modifier.fillMaxSize().background(Light), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Default.ArrowBack, "Retour") }; Text("Profil du vendeur", style = MaterialTheme.typography.headlineMedium, color = Navy) } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
        p?.let { profile -> item { Surface(shape = RoundedCornerShape(22.dp), color = Color.White) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(profile.firstName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy); Text("${profile.city}, ${profile.country}", color = Muted); if (a != null) { Text(a!!.name, color = Navy, fontWeight = FontWeight.Bold); if (a!!.badgeActive()) Text("✓ Agence certifiée", color = Orange, fontWeight = FontWeight.Bold) } } } }; item { Text("Ses annonces", style = MaterialTheme.typography.titleLarge, color = Navy) }; items(listings) { ListingCard(it) {} } }
    }
}

@Composable
fun MyListingsScreen(uid: String, repo: SupabaseRepository, done: () -> Unit) {
    var list by remember { mutableStateOf<List<Listing>>(emptyList()) }; var error by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { try { list = repo.getMyListings(uid); error = "" } catch (e: Exception) { error = readableError(e) } } }
    LaunchedEffect(Unit) { refresh() }
    Column(Modifier.fillMaxSize().background(Light)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(done) { Icon(Icons.Default.ArrowBack, "Retour") }; Text("Mes annonces", style = MaterialTheme.typography.headlineMedium, color = Navy) }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { item -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { item.photoUrls.firstOrNull()?.let { AsyncImage(it, "Photo", Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop) }; Text(item.propertyType, fontWeight = FontWeight.Bold, color = Navy); Text("${item.city} • ${money(item.price)}"); Text(if (item.active) "En ligne" else "Désactivée", color = if (item.active) Success else Muted); Text("Vues : ${item.views}", color = Muted); if (!item.active) Row { TextButton({ scope.launch { runCatching { repo.relist(item.id); refresh() }.onFailure { error = readableError(it) } } }) { Text("Remettre en ligne") }; TextButton({ scope.launch { runCatching { repo.deleteListing(item.id); refresh() }.onFailure { error = readableError(it) } } }) { Text("Supprimer") } } } } }
            if (list.isEmpty()) item { EmptyState("Vous n’avez encore aucune annonce.") }
        }
    }
}

@Composable
fun CertificationScreen(uid: String, agency: Agency?, repo: SupabaseRepository, done: () -> Unit) {
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }; var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }; val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris = it.take(2) }; val context = LocalContext.current; val scope = rememberCoroutineScope(); val activity = context as? MainActivity; var profile by remember { mutableStateOf<UserProfile?>(null) }
    LaunchedEffect(uid) { profile = runCatching { repo.getProfile(uid) }.getOrNull() }
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Certification de l’agence", style = MaterialTheme.typography.headlineMedium, color = Navy)
        Text("Envoyez le recto et le verso de la pièce d’identité du propriétaire. Les documents sont protégés dans Supabase Storage et la validation est faite avant l’activation du badge.", color = Muted)
        OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Badge, null); Spacer(Modifier.width(6.dp)); Text("Ajouter recto + verso (${uris.size}/2)") }
        if (uris.size == 2) Button({
            val a = activity
            val pr = profile
            if (a != null && pr != null) scope.launch { busy = true; msg = ""; try {
                val paths = repo.uploadUris(uid, "agency-verification", uris, 2)
                val intent = repo.createCertificationIntent(paths[0], paths[1])
                a.startKkiapayPayment(intent.amount, "Certification agence ImmoLink - 1 mois", intent.paymentId, pr) { status, tx ->
                    scope.launch {
                        runCatching { repo.attachPaymentTransaction(intent.paymentId, tx, status == "SUCCESS") }
                        msg = if (status == "SUCCESS") "Paiement reçu. La certification sera activée après validation sécurisée." else "Paiement non validé."
                    }
                }
            } catch (e: Exception) { msg = readableError(e) } finally { busy = false } }
        }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy && activity != null && profile != null) { if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("Envoyer et payer 1 000 FCFA / mois") }
        Text("Le badge est activé après validation du dossier et confirmation du paiement. Renouvellement mensuel : 1 000 FCFA.", color = Muted)
        if (agency?.badgeActive() == true) Text("✓ Votre badge est actif jusqu’au ${agency.certificationExpiresAt}", color = Orange, fontWeight = FontWeight.Bold)
        if (msg.isNotBlank()) Text(msg, color = if (msg.startsWith("Paiement reçu")) Success else MaterialTheme.colorScheme.error)
        OutlinedButton(done, Modifier.fillMaxWidth().height(50.dp)) { Text("Retour") }
    }
}

@Composable
fun ChatsScreen(uid: String, repo: SupabaseRepository, open: (Listing) -> Unit) {
    var chats by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }; var error by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { try { chats = repo.getChats(uid) } catch (e: Exception) { error = readableError(e) } }
    Column(Modifier.fillMaxSize().background(Light).padding(16.dp)) {
        Text("Messages", style = MaterialTheme.typography.headlineMedium, color = Navy)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 10.dp))
        if (chats.isEmpty()) EmptyState("Aucune discussion pour le moment.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(chats) { chat -> Card(Modifier.fillMaxWidth().clickable { scope.launch { runCatching { repo.getListing(chat.listingId)?.let(open) }.onFailure { error = readableError(it) } } }, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { Text(chat.displayName.ifBlank { "Discussion" }, fontWeight = FontWeight.Bold, color = Navy); Text("Annonce : ${chat.listingLabel.ifBlank { chat.listingId }}", color = Muted); if (chat.lastText.isNotBlank()) Text(chat.lastText, color = Ink, maxLines = 2) } } } }
    }
}

@Composable
fun ChatDetailScreen(uid: String, item: Listing, repo: SupabaseRepository, back: () -> Unit) {
    val chatId = java.util.UUID.nameUUIDFromBytes("${listOf(uid, item.ownerId).sorted().joinToString("_")}_${item.id}".toByteArray()).toString()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }; var text by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; val scope = rememberCoroutineScope()
    LaunchedEffect(chatId) { try { messages = repo.getMessages(chatId) } catch (e: Exception) { error = readableError(e) } }
    Column(Modifier.fillMaxSize().background(Light)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) { IconButton(back) { Icon(Icons.Default.ArrowBack, "Retour") }; Text("Discussion • ${item.ownerAgencyName.ifBlank { item.ownerName }}", fontWeight = FontWeight.Bold, color = Navy) }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages) { m -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.senderId == uid) Arrangement.End else Arrangement.Start) { Surface(color = if (m.senderId == uid) Navy else Color.White, shape = RoundedCornerShape(14.dp)) { Text(m.text, color = if (m.senderId == uid) Color.White else Ink, modifier = Modifier.padding(12.dp)) } } } }
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, { text = it.take(1000) }, label = { Text("Message") }, modifier = Modifier.weight(1f), singleLine = true); IconButton({ if (text.isNotBlank()) scope.launch { try { repo.sendMessage(chatId, item.id, item.country, listOf(uid, item.ownerId), uid, text.trim()); messages = repo.getMessages(chatId); text = "" } catch (e: Exception) { error = readableError(e) } } }) { Icon(Icons.Default.Send, null, tint = Orange) } }
    }
}

@Composable fun EmptyState(text: String) = Box(Modifier.fillMaxWidth().padding(30.dp), Alignment.Center) { Text(text, color = Muted) }

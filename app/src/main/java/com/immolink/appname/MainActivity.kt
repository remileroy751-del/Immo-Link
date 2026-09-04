package com.immolink.appname

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Locale

private fun digits(s: String) = s.filter(Char::isDigit)
private fun authEmail(code: String, phone: String) = "u${digits(code)}${digits(phone)}@immolink.app"
private fun validPassword(p: String) = p.matches(Regex("^[A-Za-z0-9]{6}$"))
private fun money(v: Long) = NumberFormat.getIntegerInstance(Locale.FRANCE).format(v) + " FCFA"
private fun wa(code: String, phone: String) = "https://wa.me/${digits(code).removePrefix("00")}${digits(phone)}"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ImmoLinkApp() }
    }
}

@Composable fun ImmoLinkApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var splash by remember { mutableStateOf(true) }
    var current by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(Unit) { val l = FirebaseAuth.AuthStateListener { current = it.currentUser }; auth.addAuthStateListener(l); onDispose { auth.removeAuthStateListener(l) } }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(5000); splash = false }
    ImmoLinkTheme { when { splash -> SplashScreen(); current == null -> AuthFlow(); else -> AppShell(current!!.uid) { auth.signOut() } } }
}

@Composable fun SplashScreen() = Box(Modifier.fillMaxSize().background(Color.White), Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Image(painterResource(R.drawable.immo_logo), "ImmoLink", Modifier.size(260.dp)); Spacer(Modifier.height(14.dp)); Text("Fiable et accessible à tous", color = Navy, fontWeight = FontWeight.Bold, fontSize = 19.sp) }
}

@Composable fun AuthFlow() {
    var page by remember { mutableStateOf(0) }; var country by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }; var first by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var login by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }
    val auth = FirebaseAuth.getInstance(); val db = FirebaseFirestore.getInstance()
    fun register() { auth.createUserWithEmailAndPassword(authEmail(code, phone), password).addOnSuccessListener { u -> db.collection("users").document(u.user!!.uid).set(mapOf("firstName" to first.trim(), "phone" to digits(phone), "country" to country, "countryCode" to code, "city" to city, "agency" to false, "createdAt" to FieldValue.serverTimestamp())).addOnSuccessListener { db.collection("publicProfiles").document(u.user!!.uid).set(mapOf("uid" to u.user!!.uid, "firstName" to first.trim(), "country" to country, "city" to city, "agency" to false, "agencyId" to "")) }.addOnFailureListener { error = it.message ?: "Erreur." } }.addOnFailureListener { error = it.message ?: "Inscription impossible." } }
    fun doLogin() { auth.signInWithEmailAndPassword(authEmail(code, phone), password).addOnFailureListener { error = "Numéro, pays ou mot de passe incorrect." } }
    if (login) return AuthCard("Connexion", error, { login = false; error = "" }) { CountryDropdown(country) { country = it; code = Data.countries.first { c -> c.first == it }.second }; PhoneField(code, phone) { phone = it }; PasswordField(password) { password = it }; Button({ doLogin() }, Modifier.fillMaxWidth(), enabled = country.isNotBlank() && phone.length >= 6 && validPassword(password)) { Text("Se connecter") }; TextButton({ login = false }) { Text("Créer un compte") } }
    when (page) {
        0 -> AuthCard("Bienvenue sur ImmoLink", error, {}) { Text("Trouvez rapidement un logement, un terrain ou une maison dans votre pays.", fontSize = 17.sp); Button({ page = 1 }, Modifier.fillMaxWidth()) { Text("Créer mon compte") }; OutlinedButton({ login = true }, Modifier.fillMaxWidth()) { Text("J’ai déjà un compte") } }
        1 -> AuthCard("Votre pays de résidence", error, {}) { CountryDropdown(country) { country = it; code = Data.countries.first { c -> c.first == it }.second; city = "" }; Button({ page = 2 }, Modifier.fillMaxWidth(), enabled = country.isNotBlank()) { Text("Suivant") } }
        2 -> AuthCard("Votre ville", error, { page = 1 }) { CityDropdown(country, city) { city = it }; Button({ page = 3 }, Modifier.fillMaxWidth(), enabled = city.isNotBlank()) { Text("Suivant") } }
        3 -> AuthCard("Vos coordonnées", error, { page = 2 }) { OutlinedTextField(first, { first = it }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth()); PhoneField(code, phone) { phone = it }; Text("De préférence, saisissez votre numéro WhatsApp.", fontSize = 12.sp, color = Color.Gray); Button({ page = 4 }, Modifier.fillMaxWidth(), enabled = first.isNotBlank() && phone.length >= 6) { Text("Suivant") } }
        4 -> AuthCard("Créer votre mot de passe", error, { page = 3 }) { PasswordField(password) { password = it }; Text("6 caractères exactement, lettres et chiffres.", fontSize = 12.sp, color = Color.Gray); Button(::register, Modifier.fillMaxWidth(), enabled = validPassword(password)) { Text("Créer mon compte") } }
    }
}

@Composable fun AuthCard(title: String, error: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) = Box(Modifier.fillMaxSize().background(Light).padding(22.dp)) { Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) { if (title != "Bienvenue sur ImmoLink" && title != "Connexion") IconButton(back) { Icon(Icons.Default.ArrowBack, null) }; Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy); if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error); content() } }
@Composable fun CountryDropdown(value: String, onChange: (String) -> Unit) = DropdownField(value.ifBlank { "Sélectionner le pays" }, Data.countries.map { it.first }, onChange)
@Composable fun CityDropdown(country: String, value: String, onChange: (String) -> Unit) = DropdownField(value.ifBlank { "Sélectionner la ville" }, listOf("Toutes les villes") + (Data.cities[country] ?: emptyList()), { onChange(if (it == "Toutes les villes") "" else it) })
@Composable fun DropdownField(label: String, items: List<String>, onChange: (String) -> Unit) { var open by remember { mutableStateOf(false) }; Box { OutlinedButton({ open = true }, Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }; DropdownMenu(open, { open = false }) { items.forEach { DropdownMenuItem({ Text(it) }, { open = false; onChange(it) }) } } } }
@Composable fun PhoneField(code: String, phone: String, onPhone: (String) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(code, {}, label = { Text("Indicatif") }, modifier = Modifier.width(110.dp), readOnly = true); OutlinedTextField(phone, { onPhone(digits(it).take(15)) }, label = { Text("WhatsApp") }, modifier = Modifier.weight(1f), singleLine = true) }
@Composable fun PasswordField(value: String, onChange: (String) -> Unit) = OutlinedTextField(value, { onChange(it.filter(Char::isLetterOrDigit).take(6)) }, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)

@Composable fun AppShell(uid: String, logout: () -> Unit) {
    val repo = remember { FirebaseRepository() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var agency by remember { mutableStateOf<Agency?>(null) }
    var tab by remember { mutableStateOf("rent") }
    var screen by remember { mutableStateOf("home") }
    var selected by remember { mutableStateOf<Listing?>(null) }
    var publishMode by remember { mutableStateOf("") }
    var ownerUid by remember { mutableStateOf("") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(uid) {
        profile = repo.getProfile(uid)
        agency = repo.getAgency(uid)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching {
            FirebaseMessaging.getInstance().token.await().let {
                FirebaseFirestore.getInstance().collection("users").document(uid).update("fcmToken", it)
            }
        }
    }
    if (profile == null) return Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
    val p = profile!!
    when (screen) {
        "detail" -> ListingDetail(uid, selected!!, repo, { screen = "home" }, { ownerUid = selected!!.ownerId; screen = "owner" }, { screen = "chat" })
        "search" -> SearchScreen(uid, p, repo) { selected = it; screen = "detail" }
        "publish" -> PublishScreen(uid, p, repo, publishMode) {
            publishMode = ""
            scope.launch { agency = repo.getAgency(uid); screen = "home" }
        }
        "profile" -> ProfileScreen(uid, p, agency, repo, logout, { publishMode = "sale"; screen = "publish" }, { publishMode = "rent"; screen = "publish" }, { screen = "agencyCreate" }, { screen = "myListings" }, { screen = "certification" }, { screen = "agency" })
        "agencyCreate" -> AgencyCreateScreen(uid, p, repo) {
            scope.launch { agency = repo.getAgency(uid); screen = "profile" }
        }
        "agency" -> AgencyScreen(uid, p, repo) { screen = "profile" }
        "myListings" -> MyListingsScreen(uid, repo) { screen = "home" }
        "certification" -> CertificationScreen(uid, p, agency, repo) {
            scope.launch { agency = repo.getAgency(uid); screen = "profile" }
        }
        "owner" -> OwnerProfileScreen(ownerUid, repo) { screen = "detail" }
        "chats" -> ChatsScreen(uid, repo) { selected = it; screen = "chat" }
        "chat" -> ChatDetailScreen(uid, selected!!, repo) { screen = "chats" }
        else -> HomeScreen(uid, p, tab, repo, { tab = it }, { selected = it; screen = "detail" }, { screen = "search" }, { screen = "publish" }, { screen = "profile" }, { screen = "chats" })
    }
}

@Composable fun HomeScreen(uid: String, p: UserProfile, tab: String, repo: FirebaseRepository, setTab: (String) -> Unit, open: (Listing) -> Unit, search: () -> Unit, publish: () -> Unit, profile: () -> Unit, chats: () -> Unit) {
    var listings by remember(tab, p.country) { mutableStateOf<List<Listing>>(emptyList()) }; var loading by remember { mutableStateOf(true) }
    LaunchedEffect(tab, p.country) { loading = true; listings = repo.getFeatured(uid, tab); loading = false }
    Scaffold(bottomBar = { NavigationBar { NavigationBarItem(tab == "home", {}, { Icon(Icons.Default.Home, null); Text("Accueil") }); NavigationBarItem(false, search, { Icon(Icons.Default.Search, null); Text("Rechercher") }); NavigationBarItem(false, chats, { Icon(Icons.Default.Chat, null); Text("Messages") }); NavigationBarItem(false, profile, { Icon(Icons.Default.Person, null); Text("Profil") }) } }) { pad -> LazyColumn(Modifier.fillMaxSize().padding(pad).background(Light), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Bonjour ${p.firstName} 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Annonces disponibles uniquement en ${p.country}", color = Color.Gray) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("rent" to "À louer", "sale" to "À vendre", "bail" to "À bailler").forEach { (k, t) -> FilterChip(selected = tab == k, onClick = { setTab(k) }, label = { Text(t) }) } } }
        item { Button(search, Modifier.fillMaxWidth()) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Recherche avancée") } }
        item { Button(publish, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Proposer une offre") } }
        if (loading) item { Box(Modifier.fillMaxWidth(), Alignment.Center) { CircularProgressIndicator() } } else if (listings.isEmpty()) item { EmptyState("Aucune annonce ne correspond pour le moment.") } else items(listings) { ListingCard(it, open) }
    } }
}

@Composable fun ListingCard(item: Listing, open: (Listing) -> Unit) = Card(Modifier.fillMaxWidth().clickable { open(item) }, RoundedCornerShape(18.dp)) { Column { val url = item.photoUrls.firstOrNull(); if (url != null) AsyncImage(url, null, Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxWidth().height(190.dp).background(Navy), Alignment.Center) { Icon(Icons.Default.Home, null, tint = Color.White, modifier = Modifier.size(60.dp)) }; Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.propertyType, fontWeight = FontWeight.Bold, color = Navy); Text("${item.city} • ${money(item.price)}", color = Ink); Text(if (item.mode == "rent") "À louer" else if (item.mode == "sale") "À vendre" else "À bailler", color = Orange, fontWeight = FontWeight.SemiBold); if (item.agencyCertified) Text("✓ Agence certifiée", color = Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold) }; AssistChip({ }, label = { Text("En ligne") }) } } }

@Composable fun SearchScreen(uid: String, p: UserProfile, repo: FirebaseRepository, open: (Listing) -> Unit) {
    var mode by remember { mutableStateOf("rent") }; var type by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }; var budget by remember { mutableStateOf<BudgetRange?>(null) }; var results by remember { mutableStateOf<List<Listing>>(emptyList()) }; var busy by remember { mutableStateOf(false) }; var saved by remember { mutableStateOf(false) }; val scope = rememberCoroutineScope(); val budgets = if (mode == "rent") Data.rentBudgets else Data.saleBudgets
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Recherche avancée", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Les résultats sont automatiquement limités à ${p.country}.", color = Color.Gray); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(mode == "rent", { mode = "rent"; type = ""; budget = null }, { Text("À louer") }); FilterChip(mode == "sale", { mode = "sale"; type = ""; budget = null }, { Text("À vendre") }); FilterChip(mode == "bail", { mode = "bail"; type = ""; budget = null }, { Text("À bailler") }) }; if (mode != "bail") DropdownField(type.ifBlank { "Type de bien" }, if (mode == "rent") Data.rentTypes else Data.saleTypes) { type = it }; DropdownField(city.ifBlank { "Toutes les villes" }, listOf("Toutes les villes") + (Data.cities[p.country] ?: emptyList())) { city = if (it == "Toutes les villes") "" else it }; if (mode != "bail") DropdownField(budget?.label ?: "Votre budget", budgets.map { it.label }) { budget = budgets.first { b -> b.label == it } }; Button({ scope.launch { busy = true; results = repo.searchListings(uid, SearchCriteria(mode, type, city, budget?.min ?: 0, budget?.max)); busy = false } }, Modifier.fillMaxWidth()) { Text("Rechercher") }; OutlinedButton({ scope.launch { repo.saveSearch(SearchCriteria(mode, type, city, budget?.min ?: 0, budget?.max), p.country, uid); saved = true } }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(8.dp)); Text(if (saved) "Recherche enregistrée" else "M’avertir des nouvelles annonces") }; if (busy) CircularProgressIndicator(); results.forEach { ListingCard(it, open) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetail(
    uid: String,
    item: Listing,
    repo: FirebaseRepository,
    back: () -> Unit,
    owner: () -> Unit,
    openChat: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var favorite by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    LaunchedEffect(item.id) {
        favorite = repo.isFavorite(uid, item.id)
        repo.incrementViews(item.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.propertyType) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            favorite = !favorite
                            scope.launch {
                                repo.toggleFavorite(uid, item.id, item.country)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favori",
                            tint = Orange
                        )
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .background(Light)
        ) {
            if (item.photoUrls.isNotEmpty()) {
                LazyRowPhotos(item.photoUrls)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Navy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("En ligne") }
                )
                Text(
                    item.propertyType,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Navy
                )
                Text("${item.city}, ${item.country}")
                Text(
                    money(item.price),
                    fontSize = 23.sp,
                    color = Orange,
                    fontWeight = FontWeight.Bold
                )
                if (item.depositMonths > 0) {
                    Text("Caution : ${item.depositMonths} mois")
                }
                if (item.description.isNotBlank()) {
                    Text(item.description)
                }
                Text("Publié par ${item.ownerName}", fontWeight = FontWeight.SemiBold)

                if (item.ownerAgencyName.isNotBlank()) {
                    Text(
                        text = if (item.agencyCertified) {
                            "✓ ${item.ownerAgencyName} — Agence certifiée"
                        } else {
                            "Agence : ${item.ownerAgencyName}"
                        },
                        color = if (item.agencyCertified) Orange else Navy,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text("Vues : ${item.views}", color = Color.Gray)

                OutlinedButton(
                    onClick = owner,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Voir le profil")
                }

                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(wa(item.ownerCountryCode, item.ownerPhone))
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Contacter sur WhatsApp")
                }

                Button(
                    onClick = {
                        val chatId = listOf(uid, item.ownerId)
                            .sorted()
                            .joinToString("_") + "_${item.id}"
                        scope.launch {
                            repo.sendMessage(
                                chatId,
                                item.id,
                                item.country,
                                listOf(uid, item.ownerId),
                                uid,
                                "Bonjour, votre annonce m'intéresse."
                            )
                        }
                        openChat()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Démarrer une discussion")
                }

                TextButton(onClick = { report = true }) {
                    Icon(Icons.Default.Flag, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Signaler cette annonce")
                }
            }
        }
    }

    if (report) {
        AlertDialog(
            onDismissRequest = { report = false },
            title = { Text("Signaler l’annonce") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Raison") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        report = false
                        if (reason.isNotBlank()) {
                            scope.launch { repo.reportListing(item.id, reason.trim()) }
                        }
                    }
                ) {
                    Text("Envoyer")
                }
            },
            dismissButton = {
                TextButton(onClick = { report = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable fun LazyRowPhotos(urls: List<String>) = LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) { itemsIndexed(urls.take(4)) { _, u -> AsyncImage(u, null, Modifier.size(270.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) } }

@Composable fun PublishScreen(uid: String, p: UserProfile, repo: FirebaseRepository, initialMode: String = "", done: () -> Unit) {
    var mode by remember { mutableStateOf(initialMode) }; var type by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var deposit by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var relationship by remember { mutableStateOf("") }; var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }; var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }; val scope = rememberCoroutineScope(); val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris = it.take(4) }
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Proposer une offre", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Maximum 4 photos. La première sera la photo principale.", color = Color.Gray); DropdownField(if (mode.isBlank()) "Vendre ou mettre en location" else if (mode == "sale") "Vendre" else "Mettre en location", listOf("Vendre", "Mettre en location")) { mode = if (it == "Vendre") "sale" else "rent"; type = "" }; if (mode.isNotBlank()) DropdownField(type.ifBlank { "Type de bien" }, if (mode == "sale") Data.saleTypes else Data.rentTypes) { type = it }; if (mode.isNotBlank()) { OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Ajouter des photos (${uris.size}/4)") }; OutlinedTextField(price, { price = digits(it) }, label = { Text(if (mode == "rent") "Loyer mensuel en FCFA" else "Prix en FCFA") }, modifier = Modifier.fillMaxWidth(), singleLine = true); if (mode == "rent") OutlinedTextField(deposit, { deposit = digits(it).take(2) }, label = { Text("Nombre de mois de caution") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(desc, { desc = it }, label = { Text("Autres informations : quartier, prix à débattre ? conditions...") }, modifier = Modifier.fillMaxWidth().height(140.dp)); DropdownField(relationship.ifBlank { "Votre relation avec le bien" }, Data.relationships) { relationship = it }; if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error); Button({ scope.launch { busy = true; try { val urls = repo.uploadUris(uid, "listingPhotos", uris, 4); repo.publishListing(mapOf("mode" to mode, "propertyType" to type, "photoUrls" to urls, "price" to (price.toLongOrNull() ?: 0L), "depositMonths" to (deposit.toLongOrNull() ?: 0L), "description" to desc, "relationship" to relationship)); done() } catch (e: Exception) { msg = e.message ?: "Publication impossible." } finally { busy = false } } }, Modifier.fillMaxWidth(), enabled = type.isNotBlank() && (price.toLongOrNull() ?: 0) > 0 && relationship.isNotBlank() && !busy) { Text(if (busy) "Publication..." else "Mettre en ligne") } } }
}

@Composable fun ProfileScreen(uid: String, p: UserProfile, agency: Agency?, repo: FirebaseRepository, logout: () -> Unit, sell: () -> Unit, rent: () -> Unit, createAgency: () -> Unit, myListings: () -> Unit, cert: () -> Unit, agencyPage: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var showDelete by remember { mutableStateOf(false) }; Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Mon profil", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy); Surface(shape = RoundedCornerShape(18.dp), color = Color.White) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(p.firstName, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(p.phone); Text("${p.city}, ${p.country}"); if (agency != null) { Text(agency.name, color = Navy, fontWeight = FontWeight.Bold); if (agency.badgeActive()) Text("✓ Agence certifiée", color = Orange, fontWeight = FontWeight.Bold); Text("Quota : ${agency.quota()} annonces") } else Text("Quota : 3 annonces") } }; Button(sell, Modifier.fillMaxWidth()) { Icon(Icons.Default.Sell, null); Spacer(Modifier.width(6.dp)); Text("Je veux vendre un bien") }; Button(rent, Modifier.fillMaxWidth()) { Icon(Icons.Default.Key, null); Spacer(Modifier.width(6.dp)); Text("Je veux mettre en location un bien") }; Button(myListings, Modifier.fillMaxWidth()) { Icon(Icons.Default.List, null); Spacer(Modifier.width(6.dp)); Text("Voir mes annonces") }; Button({ createAgency() }, Modifier.fillMaxWidth(), enabled = agency == null) { Icon(Icons.Default.Business, null); Spacer(Modifier.width(6.dp)); Text(if (agency == null) "Créer mon agence immobilière" else "Agence créée") }; if (agency != null) { OutlinedButton(agencyPage, Modifier.fillMaxWidth()) { Text("Voir ma page agence") }; OutlinedButton(cert, Modifier.fillMaxWidth()) { Icon(Icons.Default.Verified, null); Spacer(Modifier.width(6.dp)); Text("Certification de l’agence") }; if (!agency.extraSlotsPaid) OutlinedButton({ scope.launch { val url = repo.createPayment("agency_extra_slots"); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }, Modifier.fillMaxWidth()) { Text("Débloquer 10 annonces supplémentaires — 5 000 FCFA") } }; HorizontalDivider(); OutlinedButton(logout, Modifier.fillMaxWidth()) { Text("Déconnexion") }; TextButton({ showDelete = true }) { Text("Supprimer mon compte", color = MaterialTheme.colorScheme.error) } }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("Supprimer définitivement le compte ?") }, text = { Text("Cette action supprime votre profil, vos annonces et votre agence.") }, confirmButton = { TextButton({ showDelete = false; scope.launch { repo.deleteAccount(); logout() } }) { Text("Supprimer") } }, dismissButton = { TextButton({ showDelete = false }) { Text("Annuler") } })
}

@Composable fun AgencyCreateScreen(uid: String, p: UserProfile, repo: FirebaseRepository, done: () -> Unit) {
    var name by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var logo by remember { mutableStateOf<Uri?>(null) }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf("") }; val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { logo = it }; val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Créer mon agence", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy); OutlinedTextField(name, { name = it }, label = { Text("Nom de votre agence immobilière") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(address, { address = it }, label = { Text("Adresse") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(email, { email = it }, label = { Text("Email de l’agence immobilière *") }, modifier = Modifier.fillMaxWidth()); OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text(if (logo == null) "Logo (facultatif)" else "Logo sélectionné") }; if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error); Button({ scope.launch { busy = true; try { val logoUrl = if (logo != null) repo.uploadUris(uid, "agencyLogos", listOf(logo!!), 1).first() else ""; repo.createAgency(mapOf("name" to name.trim(), "address" to address.trim(), "email" to email.trim(), "logoUrl" to logoUrl)); done() } catch (e: Exception) { error = e.message ?: "Création impossible." } finally { busy = false } } }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && address.isNotBlank() && email.contains("@") && !busy) { Text("Créer l’agence") }; Text("Une agence bénéficie de 15 annonces actives. Un paiement unique de 5 000 FCFA permet de débloquer 10 annonces supplémentaires, soit 25 au total.", color = Color.Gray); TextButton(done) { Text("Annuler") } }
}

@Composable fun AgencyScreen(uid: String, p: UserProfile, repo: FirebaseRepository, back: () -> Unit) { var agency by remember { mutableStateOf<Agency?>(null) }; var listings by remember { mutableStateOf<List<Listing>>(emptyList()) }; LaunchedEffect(Unit) { agency = repo.getAgency(uid); listings = repo.getMyListings(uid) }; Column(Modifier.fillMaxSize().background(Light).padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Default.ArrowBack, null) }; Text("Page agence", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy) }; if (agency == null) Text("Aucune agence") else { if (agency!!.logoUrl.isNotBlank()) AsyncImage(agency!!.logoUrl, null, Modifier.size(90.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop); Text(agency!!.name, fontSize = 24.sp, fontWeight = FontWeight.Bold); if (agency!!.badgeActive()) Text("✓ Agence certifiée", color = Orange, fontWeight = FontWeight.Bold); Text(agency!!.address); Text(agency!!.email); Text("${listings.size} annonce(s)", color = Color.Gray); LazyColumn { items(listings) { ListingCard(it) {} } } } } }

@Composable fun OwnerProfileScreen(uid: String, repo: FirebaseRepository, back: () -> Unit) { var p by remember { mutableStateOf<UserProfile?>(null) }; var a by remember { mutableStateOf<Agency?>(null) }; var l by remember { mutableStateOf<List<Listing>>(emptyList()) }; LaunchedEffect(uid) { p = repo.getOwner(uid); a = repo.getOwnerAgency(uid); l = repo.getMyListings(uid) }; Column(Modifier.fillMaxSize().background(Light).padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Default.ArrowBack, null) }; Text("Profil du vendeur", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy) }; if (p == null) CircularProgressIndicator() else { Text(p!!.firstName, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("${p!!.city}, ${p!!.country}"); if (a != null) { Text(a!!.name, color = Navy, fontWeight = FontWeight.Bold); if (a!!.badgeActive()) Text("✓ Agence certifiée", color = Orange, fontWeight = FontWeight.Bold) }; Text("Ses annonces", fontWeight = FontWeight.Bold); LazyColumn { items(l) { ListingCard(it) {} } } } } }

@Composable fun MyListingsScreen(uid: String, repo: FirebaseRepository, done: () -> Unit) { var list by remember { mutableStateOf<List<Listing>>(emptyList()) }; val scope = rememberCoroutineScope(); LaunchedEffect(Unit) { list = repo.getMyListings(uid) }; Column(Modifier.fillMaxSize().background(Light).padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(done) { Icon(Icons.Default.ArrowBack, null) }; Text("Mes annonces", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy) }; LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(list) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { if (item.photoUrls.firstOrNull() != null) AsyncImage(item.photoUrls.first(), null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop); Text(item.propertyType, fontWeight = FontWeight.Bold); Text("${item.city} • ${money(item.price)}"); Text(if (item.active) "En ligne" else "Désactivée", color = if (item.active) Orange else Color.Gray); Text("Vues : ${item.views}", color = Color.Gray); if (!item.active) Row { TextButton({ scope.launch { repo.relist(item.id); list = repo.getMyListings(uid) } }) { Text("Remettre en ligne") }; TextButton({ scope.launch { repo.deleteListing(item.id); list = repo.getMyListings(uid) } }) { Text("Supprimer") } } } } } } } }

@Composable fun CertificationScreen(uid: String, p: UserProfile, agency: Agency?, repo: FirebaseRepository, done: () -> Unit) { var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }; var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }; val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris = it.take(2) }; val context = LocalContext.current; val scope = rememberCoroutineScope(); Column(Modifier.fillMaxSize().background(Light).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Certification de l’agence", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Envoyez le recto et le verso de la pièce d’identité du propriétaire de l’agence. Les documents sont privés et réservés à la vérification."); OutlinedButton({ picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Badge, null); Spacer(Modifier.width(6.dp)); Text("Ajouter recto + verso (${uris.size}/2)") }; if (uris.size == 2) Button({ scope.launch { busy = true; try { val urls = repo.uploadUris(uid, "agencyVerification", uris, 2); repo.submitCertification(mapOf("frontUrl" to urls[0], "backUrl" to urls[1])); val pay = repo.createPayment("agency_certification"); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pay))) } catch (e: Exception) { msg = e.message ?: "Erreur." } finally { busy = false } } }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Envoyer les documents et payer 2 000 FCFA / mois") }; Text("Après paiement, la demande doit être validée avant activation du badge. Le renouvellement est mensuel.", color = Color.Gray); if (agency?.badgeActive() == true) Text("✓ Votre badge est actif jusqu’au ${agency.certificationExpiresAt}", color = Orange, fontWeight = FontWeight.Bold); if (msg.isNotBlank()) Text(msg, color = MaterialTheme.colorScheme.error); OutlinedButton(done, Modifier.fillMaxWidth()) { Text("Retour") } } }

@Composable fun ChatsScreen(uid: String, repo: FirebaseRepository, open: (Listing) -> Unit) { var chats by remember { mutableStateOf<List<ChatSummary>>(emptyList()) }; val scope = rememberCoroutineScope(); LaunchedEffect(Unit) { chats = repo.getChats(uid) }; Column(Modifier.fillMaxSize().background(Light).padding(16.dp)) { Text("Messages", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Navy); if (chats.isEmpty()) EmptyState("Aucune discussion pour le moment.") else LazyColumn { items(chats) { c -> Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { scope.launch { repo.getListing(c.listingId)?.let(open) } }) { Column(Modifier.padding(14.dp)) { Text(c.lastText.ifBlank { "Discussion" }, fontWeight = FontWeight.SemiBold); Text("Annonce : ${c.listingId}", color = Color.Gray) } } } } } }

@Composable fun ChatDetailScreen(uid: String, item: Listing, repo: FirebaseRepository, back: () -> Unit) { val chatId = listOf(uid, item.ownerId).sorted().joinToString("_") + "_${item.id}"; var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }; var text by remember { mutableStateOf("") }; val scope = rememberCoroutineScope(); LaunchedEffect(chatId) { messages = repo.getMessages(chatId) }; Column(Modifier.fillMaxSize().background(Light)) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.Default.ArrowBack, null) }; Text("Discussion • ${item.propertyType}", fontWeight = FontWeight.Bold, color = Navy) }; LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages) { m -> Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.senderId == uid) Arrangement.End else Arrangement.Start) { Surface(color = if (m.senderId == uid) Navy else Color.White, shape = RoundedCornerShape(14.dp)) { Text(m.text, color = if (m.senderId == uid) Color.White else Ink, modifier = Modifier.padding(12.dp)) } } } }; Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(text, { text = it }, label = { Text("Message") }, modifier = Modifier.weight(1f)); IconButton({ if (text.isNotBlank()) scope.launch { repo.sendMessage(chatId, item.id, item.country, listOf(uid, item.ownerId), uid, text.trim()); messages = repo.getMessages(chatId); text = "" } }) { Icon(Icons.Default.Send, null, tint = Orange) } } } }

@Composable fun EmptyState(text: String) = Box(Modifier.fillMaxWidth().padding(30.dp), Alignment.Center) { Text(text, color = Color.Gray) }

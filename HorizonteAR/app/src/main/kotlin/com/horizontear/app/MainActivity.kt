package com.horizontear.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.*

const val CHAVE_OPENROUTE = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImUzYjJkZTY5YzkwMjQ0ZGE5NjE3OWM1ZTYzNmNhNjQ4IiwiaCI6Im11cm11cjY0In0="

val AzulPrincipal = Color(0xFF2563EB)
val VerdeVisivel = Color(0xFF10B981)
val IndigoNaoVisivel = Color(0xFF6366F1)
val FundoCamara = Color(0xFF121212)

data class PontoMarcado(
    val id: String = java.util.UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val visivel: Boolean = true,
    val nome: String? = null,
    val distanciaMetros: Double = 0.0
)

class MainActivity : ComponentActivity() {
    private lateinit var localizacaoCliente: FusedLocationProviderClient
    private var minhaLat = mutableStateOf(-23.805)
    private var minhaLng = mutableStateOf(-45.505)

    private val permissaoLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissoes ->
        val okLoc = permissoes[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (okLoc) lerLocalizacao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localizacaoCliente = LocationServices.getFusedLocationProviderClient(this)

        if (temPermissao()) lerLocalizacao()
        else permissaoLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA, Manifest.permission.INTERNET)
        )

        setContent {
            AppTelaPrincipal(
                minhaLat = minhaLat.value,
                minhaLng = minhaLng.value
            )
        }
    }

    private fun temPermissao(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun lerLocalizacao() {
        if (temPermissao()) {
            localizacaoCliente.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    minhaLat.value = it.latitude
                    minhaLng.value = it.longitude
                }
            }
            localizacaoCliente.requestLocationUpdates(
                LocationRequest.Builder(5000).build(),
                object : LocationCallback() {
                    override fun onLocationResult(res: LocationResult) {
                        res.lastLocation?.let {
                            minhaLat.value = it.latitude
                            minhaLng.value = it.longitude
                        }
                    }
                },
                mainLooper
            )
        }
    }
}

@Composable
fun AppTelaPrincipal(
    minhaLat: Double,
    minhaLng: Double
) {
    var modoCamara by remember { mutableStateOf(false) }
    var pontoMarcado by remember { mutableStateOf<PontoMarcado?>(null) }
    var mostrarBusca by remember { mutableStateOf(false) }

    if (modoCamara) {
        TelaCamaraAR(
            ponto = pontoMarcado,
            minhaLat = minhaLat,
            minhaLng = minhaLng,
            aoVoltarMapa = { modoCamara = false },
            aoMarcarPonto = { pontoMarcado = it }
        )
    } else {
        TelaMapaOSM(
            ponto = pontoMarcado,
            minhaLat = minhaLat,
            minhaLng = minhaLng,
            aoAbrirBusca = { mostrarBusca = true },
            aoIrCamara = { modoCamara = true },
            aoSelecionarPonto = { pontoMarcado = it }
        )
    }

    if (mostrarBusca) {
        TelaBuscaOpenRoute(
            minhaLat = minhaLat,
            minhaLng = minhaLng,
            aoEscolher = { ponto ->
                pontoMarcado = ponto
                mostrarBusca = false
            },
            aoFechar = { mostrarBusca = false }
        )
    }
}

@Composable
fun TelaMapaOSM(
    ponto: PontoMarcado?,
    minhaLat: Double,
    minhaLng: Double,
    aoAbrirBusca: () -> Unit,
    aoIrCamara: () -> Unit,
    aoSelecionarPonto: (PontoMarcado) -> Unit
) {
    val pontoInicial = GeoPoint(minhaLat, minhaLng)

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller?.setZoom(15.0)
                    controller?.setCenter(pontoInicial)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { mapa ->
                mapa.overlays.clear()
                val marcadorEu = Marker(mapa).apply {
                    position = GeoPoint(minhaLat, minhaLng)
                    title = "Você está aqui"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                }
                mapa.overlays.add(marcadorEu)
                ponto?.let { p ->
                    val pt = GeoPoint(p.latitude, p.longitude)
                    val marcadorPonto = Marker(mapa).apply {
                        position = pt
                        title = p.nome ?: "Ponto Marcado"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    mapa.overlays.add(marcadorPonto)
                    mapa.controller?.setCenter(pt)
                }
                mapa.invalidate()
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BotaoIcone(icone = Icons.Default.Search, acao = aoAbrirBusca)
            BotaoIcone(icone = Icons.Default.CameraAlt, fundo = AzulPrincipal, corIcone = Color.White, acao = aoIrCamara)
        }
    }
}

@Composable
fun TelaCamaraAR(
    ponto: PontoMarcado?,
    minhaLat: Double,
    minhaLng: Double,
    aoVoltarMapa: () -> Unit,
    aoMarcarPonto: (PontoMarcado) -> Unit
) {
    val contexto = LocalContext.current
    var direcaoBussola by remember { mutableStateOf(0.0) }
    var mostrarCompartilhar by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val gerenciador = contexto.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensorRotacao = gerenciador.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
        val ouvinte = object : android.hardware.SensorEventListener {
            val matriz = FloatArray(9)
            val orientacao = FloatArray(3)
            override fun onSensorChanged(e: android.hardware.SensorEvent?) {
                e ?: return
                android.hardware.SensorManager.getRotationMatrixFromVector(matriz, e.values)
                android.hardware.SensorManager.getOrientation(matriz, orientacao)
                val azimute = Math.toDegrees(orientacao[0].toDouble())
                direcaoBussola = (azimute + 360) % 360
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sensorRotacao?.also { sensor ->
            gerenciador.registerListener(ouvinte, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME)
            onDispose { gerenciador.unregisterListener(ouvinte) }
        }
    }

    LaunchedEffect(ponto) {
        while (true) {
            ponto?.let { p -> mostrarCompartilhar = p.distanciaMetros < 50000 }
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(FundoCamara)) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Apontando para o ponto...", color = Color.White.copy(alpha = 0.6f))
            ponto?.let { p ->
                Spacer(modifier = Modifier.height(24.dp))
                val direcaoAlvo = calcularDirecao(minhaLat, minhaLng, p.latitude, p.longitude)
                val anguloSeta = direcaoAlvo - direcaoBussola
                Icon(Icons.Default.ArrowUpward, null, tint = if (p.visivel) VerdeVisivel else IndigoNaoVisivel,
                    modifier = Modifier.size(64.dp).rotate(anguloSeta.toFloat()))
                Spacer(modifier = Modifier.height(8.dp))
                Text("%.1f m".format(p.distanciaMetros), color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(p.nome ?: "", color = Color.White.copy(alpha = 0.8f))
            }
        }

        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BotaoIcone(icone = Icons.Default.MyLocation, acao = {
                val p = criarPontoNaDirecao(minhaLat, minhaLng, direcaoBussola, precisao = false)
                aoMarcarPonto(p)
            })
            Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White)
                .pointerInput(Unit) { detectTapGestures(onTap = {
                    val p = criarPontoNaDirecao(minhaLat, minhaLng, direcaoBussola, precisao = true)
                    aoMarcarPonto(p)
                }) }) {
                Icon(Icons.Default.LocationOn, null, tint = AzulPrincipal, modifier = Modifier.align(Alignment.Center))
            }
            if (mostrarCompartilhar) BotaoIcone(icone = Icons.Default.Share, acao = {})
            BotaoIcone(icone = Icons.Default.Map, fundo = AzulPrincipal, corIcone = Color.White, acao = aoVoltarMapa)
        }
    }
}

@Composable
fun TelaBuscaOpenRoute(
    minhaLat: Double, minhaLng: Double,
    aoEscolher: (PontoMarcado) -> Unit, aoFechar: () -> Unit
) {
    var texto by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    var resultados by remember { mutableStateOf(listOf<PontoMarcado>()) }
    val escopo = rememberCoroutineScope()
    AlertDialog(onDismissRequest = aoFechar, title = { Text("Buscar Lugar") }, text = {
        Column {
            OutlinedTextField(texto, { texto = it }, placeholder = { Text("Digite nome ou endereço...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { if (texto.isNotBlank()) { carregando = true; escopo.launch(Dispatchers.IO) { resultados = buscarOpenRoute(texto, minhaLat, minhaLng); carregando = false } } },
                modifier = Modifier.align(Alignment.End), enabled = !carregando) { Text(if (carregando) "Buscando..." else "Buscar") }
            Spacer(modifier = Modifier.height(8.dp))
            resultados.forEach { p -> TextButton({ aoEscolher(p); aoFechar() }) { Text("📍 ${p.nome} — %.0fm".format(p.distanciaMetros)) } }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } })
}

fun buscarOpenRoute(consulta: String, minhaLat: Double, minhaLng: Double): List<PontoMarcado> {
    val cliente = OkHttpClient()
    val url = "https://api.openrouteservice.org/geocode/search?api_key=$CHAVE_OPENROUTE&text=${consulta.replace(" ", "+")}&boundary.rect.min_lon=${minhaLng-0.5}&boundary.rect.max_lon=${minhaLng+0.5}&boundary.rect.min_lat=${minhaLat-0.5}&boundary.rect.max_lat=${minhaLat+0.5}&size=5"
    val req = Request.Builder().url(url).build()
    val res = cliente.newCall(req).execute()
    val corpo = res.body?.string() ?: return emptyList()
    val raiz = JsonParser.parseString(corpo).asJsonObject
    val lista = mutableListOf<PontoMarcado>()
    if (raiz.has("features")) {
        for (feat in raiz.getAsJsonArray("features")) {
            val f = feat.asJsonObject
            val coords = f.getAsJsonObject("geometry").getAsJsonArray("coordinates")
            val lng = coords[0].asDouble
            val lat = coords[1].asDouble
            val nome = f.getAsJsonObject("properties").get("name")?.asString ?: "Lugar encontrado"
            val dist = calcularDistancia(minhaLat, minhaLng, lat, lng)
            lista.add(PontoMarcado(lat, lng, true, nome, dist))
        }
    }
    return lista
}

fun calcularDistancia(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return R * c
}

fun calcularDirecao(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val lat1r = Math.toRadians(lat1)
    val lat2r = Math.toRadians(lat2)
    val dLng = Math.toRadians(lng2 - lng1)
    val y = sin(dLng) * cos(lat2r)
    val x = cos(lat1r) * sin(lat2r) - sin(lat1r) * cos(lat2r) * cos(dLng)
    var graus = Math.toDegrees(atan2(y, x))
    return (graus + 360) % 360
}

fun criarPontoNaDirecao(lat: Double, lng: Double, direcaoGraus: Double, precisao: Boolean): PontoMarcado {
    val distancia = if (precisao) 1000.0 else 1500.0
    val anguloRad = Math.toRadians(direcaoGraus)
    val dLat = (distancia / 6371000.0) * sin(anguloRad)
    val dLng = (distancia / 6371000.0) * cos(anguloRad) / cos(Math.toRadians(lat))
    val novaLat = lat + Math.toDegrees(dLat)
    val novaLng = lng + Math.toDegrees(dLng)
    val dist = calcularDistancia(lat, lng, novaLat, novaLng)
    return PontoMarcado(novaLat, novaLng, true, if (precisao) "Ponto Preciso" else "Ponto Marcado", dist)
}

@Composable
fun BotaoIcone(icone: androidx.compose.ui.graphics.vector.ImageVector, corIcone: Color = AzulPrincipal, fundo: Color = Color.White, acao: () -> Unit) {
    Button(onClick = acao, modifier = Modifier.size(56.dp).clip(CircleShape),
        colors = ButtonDefaults.buttonColors(containerColor = fundo, contentColor = corIcone),
        contentPadding = PaddingValues(0.dp)) { Icon(icone, null, modifier = Modifier.size(24.dp)) }
}
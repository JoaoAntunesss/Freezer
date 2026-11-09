package com.example.congelador

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.speech.RecognizerIntent
import android.util.Base64
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navOptions
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// --- MODELOS DE DADOS ---
data class Arca(
    val id: String = UUID.randomUUID().toString(),
    val nome: String = "",
    val tipo: String = "Arca",
    val capacidadeLitros: Float = 0f,
    val criador: String = "",
    val membros: String = ""
)

data class ItemArca(
    val id: String = UUID.randomUUID().toString(),
    val arcaId: String = "",
    val nome: String = "",
    val quantidade: Int = 0,
    val litrosVolume: Float? = null,
    val dataValidade: Long? = null,
    val categoria: String = "Geral",
    val preco: Float? = null,
    val codigoBarras: String? = null,
    val dataRegisto: Long = System.currentTimeMillis()
)

data class ListaCompras(
    val id: String = UUID.randomUUID().toString(),
    val nome: String = "",
    val dataCriacao: Long = System.currentTimeMillis(),
    val criador: String = "",
    val membros: String = "",
    val concluida: Boolean = false,
    val valorGlobal: Float? = null
)

data class ItemCompra(
    val id: String = UUID.randomUUID().toString(),
    val listaId: String = "",
    val nome: String = "",
    val quantidade: String = "1",
    val comprado: Boolean = false,
    val preco: Float? = null,
    val loja: String = "Geral",
    val categoria: String = "Geral"
)

data class RegistoDesperdicio(
    val id: String = UUID.randomUUID().toString(),
    val nomeItem: String = "",
    val dataExpiracao: Long = System.currentTimeMillis(),
    val utilizador: String = ""
)

data class EmentaMensal(
    val id: String = UUID.randomUUID().toString(),
    val mesAno: String = "Outubro 2026",
    val diaMes: String = "1",
    val regimeRefeicao: String = "Almoço",
    val prato: String = "",
    val ingredientesNecessarios: String = "",
    val requerDescongelamento: Boolean = false,
    val criador: String = "",
    val membros: String = ""
)

data class Emprestimo(
    val id: String = UUID.randomUUID().toString(),
    val oQue: String = "",
    val aQuem: String = "",
    val dataEmprestimo: Long = System.currentTimeMillis(),
    val devolvido: Boolean = false,
    val criador: String = ""
)

data class HistoricoPreco(
    val id: String = UUID.randomUUID().toString(),
    val nomeProduto: String = "",
    val preco: Float = 0f,
    val loja: String = "",
    val dataRegisto: Long = System.currentTimeMillis()
)

// A chave vem do local.properties (GROQ_API_KEY=...) via BuildConfig; nunca fica no código nem no Git.
private val GROQ_API_KEY: String = BuildConfig.GROQ_API_KEY
// Os modelos antigos (llama3-8b-8192, llama-3.2-11b-vision-preview) foram desligados pelo Groq e a IA deixou de funcionar.
// Agora a app pergunta ao Groq que modelos existem e usa o primeiro desta lista que estiver disponível.
private val MODELOS_TEXTO = listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b", "qwen/qwen3.6-27b", "llama-3.1-8b-instant")
private val MODELOS_VISAO = listOf("qwen/qwen3.8-27b", "qwen/qwen3.6-27b", "meta-llama/llama-4-scout-17b-16e-instruct", "meta-llama/llama-4-maverick-17b-128e-instruct")
@Volatile private var modelosDisponiveis: Set<String>? = null

private suspend fun escolherModelo(preferidos: List<String>): String = withContext(Dispatchers.IO) {
    val disponiveis = modelosDisponiveis ?: try {
        val conn = URL("https://api.groq.com/openai/v1/models").openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        val dados = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("data")
        (0 until dados.length()).map { dados.getJSONObject(it).getString("id") }.toSet().also { modelosDisponiveis = it }
    } catch (_: Exception) { emptySet() }
    preferidos.firstOrNull { it in disponiveis } ?: preferidos.first()
}

/** Tira o raciocínio interno que alguns modelos devolvem (<think>...</think>). */
private fun limparResposta(texto: String): String = texto.replace(Regex("(?s)<think>.*?</think>"), "").trim()

/** Aceita "1,50" e "1.50". */
private fun numero(texto: String): Float? = texto.replace(",", ".").replace("€", "").trim().toFloatOrNull()
private const val RTDB_URL = "https://congelador-bdba6-default-rtdb.europe-west1.firebasedatabase.app"

private fun mensagemErroIa(e: Exception): String = "Não foi possível usar a IA. Verifica a ligação e tenta de novo. (${e.message})"

private suspend fun chamarGroqApi(payload: JSONObject): String = withContext(Dispatchers.IO) {
    if (GROQ_API_KEY.isBlank()) throw IllegalStateException("Chave da IA não configurada")
    val url = URL("https://api.groq.com/openai/v1/chat/completions")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.connectTimeout = 15_000; conn.readTimeout = 60_000

    OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

    val reader = if (conn.responseCode in 200..299) {
        BufferedReader(InputStreamReader(conn.inputStream))
    } else {
        BufferedReader(InputStreamReader(conn.errorStream))
    }
    val response = reader.readText()
    reader.close()

    if (conn.responseCode !in 200..299) {
        throw Exception("Erro Groq (${conn.responseCode}): $response")
    }

    val jsonResponse = JSONObject(response)
    limparResposta(jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

private fun fimDoDiaLocal(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}

private fun inicioDoDia(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun diasAte(validade: Long): Int =
    Math.round((inicioDoDia(validade) - inicioDoDia(System.currentTimeMillis())) / 86_400_000.0).toInt()

private fun textoValidade(dias: Int): String = when {
    dias < 0 -> "Expirado"
    dias == 0 -> "Expira hoje"
    dias == 1 -> "Expira amanhã"
    else -> "Expira em $dias dias"
}

// --- WIDGETS DECLARADOS ANTES DO USO ---
class WidgetEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Color.Transparent)) {
                var textoNota by rememberSaveable { mutableStateOf("") }
                var grupoIdAtual by rememberSaveable { mutableStateOf<String?>(null) }
                var membrosAtuaisStr by rememberSaveable { mutableStateOf(FirebaseAuth.getInstance().currentUser?.email ?: "") }
                val emailAtual = FirebaseAuth.getInstance().currentUser?.email
                val context = LocalContext.current

                LaunchedEffect(Unit) {
                    if (emailAtual != null) {
                        FirebaseDatabase.getInstance(RTDB_URL).reference.child("quadro_notas_isolado").get().addOnSuccessListener { snapshot ->
                            for (child in snapshot.children) {
                                val membros = child.child("membros").getValue(String::class.java) ?: ""
                                val criador = child.child("criador").getValue(String::class.java) ?: ""
                                if (criador == emailAtual || membros.split(",").map { it.trim() }.contains(emailAtual)) {
                                    grupoIdAtual = child.key
                                    membrosAtuaisStr = membros
                                    textoNota = child.child("texto").getValue(String::class.java) ?: ""
                                    break
                                }
                            }
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = { finish() },
                    containerColor = SurfaceDark,
                    title = { Text("Quadro de Notas", color = TextPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = textoNota,
                            onValueChange = { textoNota = it },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (emailAtual != null) {
                                    val grupoId = grupoIdAtual ?: "nota_${emailAtual.replace(".", "_")}"
                                    val dados = mapOf("criador" to emailAtual, "membros" to membrosAtuaisStr.ifBlank { emailAtual }, "texto" to textoNota)
                                    FirebaseDatabase.getInstance(RTDB_URL).reference.child("quadro_notas_isolado").child(grupoId).setValue(dados)
                                        .addOnSuccessListener {
                                            val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
                                            prefs.edit().putString("nota_widget", textoNota.ifBlank { "Sem notas. Clica para adicionar!" }).apply()

                                            val intent = Intent(context, QuadroNotasWidget::class.java).apply {
                                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                            }
                                            context.sendBroadcast(intent)
                                            finish()
                                        }
                                } else {
                                    finish()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentStart)
                        ) { Text("Gravar", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) { Text("Cancelar", color = Color.Gray) }
                    }
                )
            }
        }
    }
}

class QuadroNotasWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
            val nota = prefs.getString("nota_widget", "Clica para adicionar recado...") ?: ""

            val views = RemoteViews(context.packageName, R.layout.widget_notas_layout).apply {
                setTextViewText(R.id.widgetNotaText, nota)

                val intentEdit = Intent(context, WidgetEditActivity::class.java)
                val pendingIntentEdit = android.app.PendingIntent.getActivity(
                    context, 0, intentEdit,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widgetNotaLayout, pendingIntentEdit)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, QuadroNotasWidget::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }
}

class ListaComprasWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_compras_layout).apply {
                val intentService = Intent(context, FreezerWidgetService::class.java)
                setRemoteAdapter(R.id.widgetListView, intentService)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ListaComprasWidget::class.java))
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.widgetListView)
        }
    }
}

class FreezerWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FreezerWidgetFactory(applicationContext)
    }
}

class FreezerWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val itensList = mutableListOf<String>()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
        val pendentes = prefs.getStringSet("widget_compras_list", setOf()) ?: setOf()

        itensList.clear()
        if (pendentes.isEmpty()) {
            itensList.add("Lista Vazia! 🎉")
        } else {
            pendentes.forEach { item ->
                itensList.add("🛒 $item")
            }
        }
    }

    override fun onDestroy() { itensList.clear() }
    override fun getCount(): Int = itensList.size
    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        views.setTextViewText(android.R.id.text1, itensList[position])
        views.setTextColor(android.R.id.text1, android.graphics.Color.WHITE)
        return views
    }
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}

// --- VIEWMODEL ---
class ArcaViewModel : ViewModel() {
    init {
        try {
            FirebaseDatabase.getInstance(RTDB_URL).setPersistenceEnabled(true)
        } catch (_: Exception) { }
    }

    private val rtdb = FirebaseDatabase.getInstance(RTDB_URL).reference
    val auth = FirebaseAuth.getInstance()

    var arcas by mutableStateOf(listOf<Arca>())
    var arcaSelecionadaIndex by mutableIntStateOf(0)
    var listaItens by mutableStateOf(listOf<ItemArca>())
    var listasCompras by mutableStateOf(listOf<ListaCompras>())
    var itensCompra by mutableStateOf(listOf<ItemCompra>())
    var desperdicios by mutableStateOf(listOf<RegistoDesperdicio>())
    var ementasMensais by mutableStateOf(listOf<EmentaMensal>())
    var emprestimos by mutableStateOf(listOf<Emprestimo>())
    var historicoPrecos by mutableStateOf(listOf<HistoricoPreco>())

    var notaPartilhada by mutableStateOf("")
    var membrosNotaAtual by mutableStateOf(listOf<String>())
    var idNotaAtual by mutableStateOf("")

    var utilizadorAtualEmail by mutableStateOf(auth.currentUser?.email)
    var diasAvisoValidade by mutableIntStateOf(7)

    var aProcessarTalao by mutableStateOf(false)
    var resultadoTalaoIa by mutableStateOf<String?>(null)
    var aGerarPrevisaoCompras by mutableStateOf(false)
    var erroIa by mutableStateOf<String?>(null)

    private var todosItens = listOf<ItemArca>()
    private val listeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

    init {
        if (utilizadorAtualEmail != null) ouvirRealtimeDatabase()
    }

    fun carregarConfigs(context: Context) {
        val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
        diasAvisoValidade = prefs.getInt("dias_aviso", 7)
    }

    fun guardarDiasAviso(context: Context, dias: Int) {
        val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
        prefs.edit { putInt("dias_aviso", dias) }
        diasAvisoValidade = dias
    }

    fun autenticarComGoogle(idToken: String, onResult: (Boolean) -> Unit) {
        val credencial = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credencial)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    utilizadorAtualEmail = auth.currentUser?.email
                    ouvirRealtimeDatabase()
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
    }

    fun logout() {
        pararListeners()
        todosItens = emptyList()
        auth.signOut()
        utilizadorAtualEmail = null
        arcas = emptyList()
        listaItens = emptyList()
        listasCompras = emptyList()
        itensCompra = emptyList()
        desperdicios = emptyList()
        ementasMensais = emptyList()
        emprestimos = emptyList()
        historicoPrecos = emptyList()
        notaPartilhada = ""
        membrosNotaAtual = emptyList()
        idNotaAtual = ""
    }

    fun apagarContaDefinitivamente(onResult: (Boolean) -> Unit) {
        val user = auth.currentUser
        user?.delete()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                logout()
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    private fun ouvir(caminho: String, listener: ValueEventListener) {
        val ref = rtdb.child(caminho)
        ref.addValueEventListener(listener)
        listeners.add(ref to listener)
    }

    private fun pararListeners() {
        listeners.forEach { (ref, l) -> ref.removeEventListener(l) }
        listeners.clear()
    }

    private fun recalcularItens() {
        val meusIds = getMinhasArcas().map { it.id }.toSet()
        // Antes, um artigo fora da validade era apagado do stock automaticamente. Agora fica na lista, marcado
        // a vermelho como "Expirado", e és tu que decides: "Deitar fora" (conta como desperdício) ou continuar a usar.
        listaItens = todosItens.filter { it.arcaId in meusIds }.sortedWith(compareBy(nullsLast()) { it.dataValidade })
    }

    private fun ouvirRealtimeDatabase() {
        pararListeners()
        val meuEmail = utilizadorAtualEmail ?: return

        ouvir("quadro_notas_isolado", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var notaEncontrada = ""
                var membrosLista = listOf<String>()
                var encontradaId = ""

                for (child in snapshot.children) {
                    val criador = child.child("criador").getValue(String::class.java) ?: ""
                    val membrosStr = child.child("membros").getValue(String::class.java) ?: ""
                    val membrosList = membrosStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

                    if (criador == meuEmail || membrosList.contains(meuEmail)) {
                        notaEncontrada = child.child("texto").getValue(String::class.java) ?: ""
                        membrosLista = membrosList
                        encontradaId = child.key ?: ""
                        break
                    }
                }

                if (encontradaId.isBlank()) {
                    val novoId = "nota_${meuEmail.replace(".", "_")}"
                    val dadosIniciais = mapOf("criador" to meuEmail, "membros" to meuEmail, "texto" to "")
                    rtdb.child("quadro_notas_isolado").child(novoId).setValue(dadosIniciais)
                    idNotaAtual = novoId
                    notaPartilhada = ""
                    membrosNotaAtual = listOf(meuEmail)
                } else {
                    idNotaAtual = encontradaId
                    notaPartilhada = notaEncontrada
                    membrosNotaAtual = membrosLista
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("arcas", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<Arca>()
                for (child in snapshot.children) { child.getValue(Arca::class.java)?.let { lista.add(it) } }
                arcas = lista
                if (arcaSelecionadaIndex >= getMinhasArcas().size) arcaSelecionadaIndex = 0
                recalcularItens()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("itens_arca", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<ItemArca>()
                for (child in snapshot.children) { child.getValue(ItemArca::class.java)?.let { lista.add(it) } }
                todosItens = lista
                recalcularItens()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("listas_compras", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<ListaCompras>()
                for (child in snapshot.children) { child.getValue(ListaCompras::class.java)?.let { lista.add(it) } }
                listasCompras = lista
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("itens_compra", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<ItemCompra>()
                for (child in snapshot.children) { child.getValue(ItemCompra::class.java)?.let { lista.add(it) } }
                itensCompra = lista
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("desperdicios", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<RegistoDesperdicio>()
                for (child in snapshot.children) { child.getValue(RegistoDesperdicio::class.java)?.let { if (it.utilizador == meuEmail) lista.add(it) } }
                desperdicios = lista
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("ementas_mensais", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<EmentaMensal>()
                for (child in snapshot.children) { child.getValue(EmentaMensal::class.java)?.let { lista.add(it) } }
                ementasMensais = lista
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("emprestimos", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<Emprestimo>()
                for (child in snapshot.children) { child.getValue(Emprestimo::class.java)?.let { lista.add(it) } }
                emprestimos = lista
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        ouvir("historico_precos", object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lista = mutableListOf<HistoricoPreco>()
                for (child in snapshot.children) { child.getValue(HistoricoPreco::class.java)?.let { lista.add(it) } }
                historicoPrecos = lista
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun deitarFora(item: ItemArca) {
        val desp = RegistoDesperdicio(id = item.id, nomeItem = item.nome, dataExpiracao = item.dataValidade ?: System.currentTimeMillis(), utilizador = utilizadorAtualEmail ?: "")
        rtdb.child("desperdicios").child(desp.id).setValue(desp)
        rtdb.child("itens_arca").child(item.id).removeValue()
    }

    fun limparDesperdicios() {
        desperdicios.forEach { desp ->
            rtdb.child("desperdicios").child(desp.id).removeValue()
        }
    }

    fun gerarTextoInventario(): String {
        if (listaItens.isEmpty()) return "O inventário está vazio."
        val texto = StringBuilder("📦 *Inventário Atual - Freezer*\n\n")
        val arcasAgrupadas = listaItens.groupBy { it.arcaId }

        arcasAgrupadas.forEach { (arcaId, itens) ->
            val nomeArca = arcas.find { it.id == arcaId }?.nome ?: "Desconhecido"
            texto.append("📍 *$nomeArca*\n")
            itens.forEach { item ->
                texto.append(" • ${item.quantidade}x ${item.nome}")
                if (item.dataValidade != null) {
                    val strData = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(item.dataValidade))
                    texto.append(" (Validade: $strData)")
                }
                texto.append("\n")
            }
            texto.append("\n")
        }
        return texto.toString()
    }

    fun atualizarQuadroNotas(context: Context, novoTexto: String) {
        val meuEmail = utilizadorAtualEmail ?: return
        val targetId = idNotaAtual.ifBlank { "nota_${meuEmail.replace(".", "_")}" }
        val membrosAtuais = membrosNotaAtual.toMutableSet()
        membrosAtuais.add(meuEmail)

        val dadosNota = mapOf(
            "criador" to meuEmail,
            "membros" to membrosAtuais.joinToString(","),
            "texto" to novoTexto
        )
        rtdb.child("quadro_notas_isolado").child(targetId).setValue(dadosNota).addOnSuccessListener {
            val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("nota_widget", novoTexto.ifBlank { "Sem notas. Clica para adicionar!" }).apply()

            val intent = Intent(context, QuadroNotasWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            context.sendBroadcast(intent)
        }
    }

    fun adicionarMembroNota(emailConvidado: String) {
        val meuEmail = utilizadorAtualEmail ?: return
        val targetId = idNotaAtual.ifBlank { "nota_${meuEmail.replace(".", "_")}" }
        val membrosAtuais = membrosNotaAtual.toMutableSet()
        membrosAtuais.add(emailConvidado.trim().lowercase())

        val dadosNota = mapOf(
            "criador" to meuEmail,
            "membros" to membrosAtuais.joinToString(","),
            "texto" to notaPartilhada
        )
        rtdb.child("quadro_notas_isolado").child(targetId).setValue(dadosNota)
    }

    fun removerMembroNota(emailParaRemover: String) {
        val meuEmail = utilizadorAtualEmail ?: return
        val targetId = idNotaAtual.ifBlank { "nota_${meuEmail.replace(".", "_")}" }
        val membrosAtuais = membrosNotaAtual.toMutableList()
        membrosAtuais.remove(emailParaRemover)
        if (!membrosAtuais.contains(meuEmail)) membrosAtuais.add(meuEmail)

        val dadosNota = mapOf(
            "criador" to meuEmail,
            "membros" to membrosAtuais.joinToString(","),
            "texto" to notaPartilhada
        )
        rtdb.child("quadro_notas_isolado").child(targetId).setValue(dadosNota)
    }

    fun getMinhasArcas(): List<Arca> {
        val meuEmail = utilizadorAtualEmail ?: return emptyList()
        return arcas.filter { arca ->
            arca.criador == meuEmail || arca.membros.split(",").map { it.trim() }.contains(meuEmail)
        }
    }

    fun getMinhasListas(): List<ListaCompras> {
        val meuEmail = utilizadorAtualEmail ?: return emptyList()
        return listasCompras.filter { lista ->
            lista.criador == meuEmail || lista.membros.split(",").map { it.trim() }.contains(meuEmail)
        }
    }

    fun getMinhasEmentas(): List<EmentaMensal> {
        val meuEmail = utilizadorAtualEmail ?: return emptyList()
        return ementasMensais.filter { ementa ->
            ementa.criador == meuEmail || ementa.membros.split(",").map { it.trim() }.contains(meuEmail)
        }
    }

    fun getMeusEmprestimos(): List<Emprestimo> {
        val meuEmail = utilizadorAtualEmail ?: return emptyList()
        return emprestimos.filter { it.criador == meuEmail }
    }

    fun guardarArca(arca: Arca, onResult: (Boolean) -> Unit) {
        val arcaFinal = arca.copy(criador = utilizadorAtualEmail ?: "")
        rtdb.child("arcas").child(arcaFinal.id).setValue(arcaFinal).addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun partilharArcaIndividual(arca: Arca, emailConvidado: String) {
        val membrosAtuais = arca.membros.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        emailConvidado.trim().lowercase().let { e -> if (e.isNotBlank() && !membrosAtuais.contains(e)) membrosAtuais.add(e) }
        rtdb.child("arcas").child(arca.id).child("membros").setValue(membrosAtuais.joinToString(","))
    }

    fun partilharLista(lista: ListaCompras, emailConvidado: String) {
        val membrosAtuais = lista.membros.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        emailConvidado.trim().lowercase().let { e -> if (e.isNotBlank() && !membrosAtuais.contains(e)) membrosAtuais.add(e) }
        rtdb.child("listas_compras").child(lista.id).child("membros").setValue(membrosAtuais.joinToString(","))
    }

    fun partilharEmentaIndividual(ementa: EmentaMensal, emailConvidado: String) {
        val emailNorm = emailConvidado.trim().lowercase()
        val membrosEmenta = ementa.membros.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (emailNorm.isNotBlank() && !membrosEmenta.contains(emailNorm)) membrosEmenta.add(emailNorm)
        val novosMembros = membrosEmenta.joinToString(",")
        rtdb.child("ementas_mensais").child(ementa.id).child("membros").setValue(novosMembros)
    }

    fun apagarOuSairArca(arca: Arca) {
        val meuEmail = utilizadorAtualEmail ?: return
        if (arca.criador == meuEmail) {
            rtdb.child("arcas").child(arca.id).removeValue()
            todosItens.filter { it.arcaId == arca.id }.forEach { rtdb.child("itens_arca").child(it.id).removeValue() }
        } else {
            val membrosAtuais = arca.membros.split(",").map { it.trim() }.toMutableList()
            membrosAtuais.remove(meuEmail)
            rtdb.child("arcas").child(arca.id).child("membros").setValue(membrosAtuais.joinToString(","))
        }
    }

    fun apagarOuSairLista(lista: ListaCompras) {
        val meuEmail = utilizadorAtualEmail ?: return
        if (lista.criador == meuEmail) {
            rtdb.child("listas_compras").child(lista.id).removeValue()
            itensCompra.filter { it.listaId == lista.id }.forEach { rtdb.child("itens_compra").child(it.id).removeValue() }
        } else {
            val membrosAtuais = lista.membros.split(",").map { it.trim() }.toMutableList()
            membrosAtuais.remove(meuEmail)
            rtdb.child("listas_compras").child(lista.id).child("membros").setValue(membrosAtuais.joinToString(","))
        }
    }

    fun atualizarValorGlobalLista(listaId: String, valor: Float?) {
        rtdb.child("listas_compras").child(listaId).child("valorGlobal").setValue(valor)
    }

    fun adicionarItem(item: ItemArca) { rtdb.child("itens_arca").child(item.id).setValue(item) }
    fun atualizarItemArca(item: ItemArca) { rtdb.child("itens_arca").child(item.id).setValue(item) }
    fun removerItem(item: ItemArca) { rtdb.child("itens_arca").child(item.id).removeValue() }

    fun criarListaCompras(nome: String): String {
        val formatoData = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val nomeFinal = nome.ifBlank { "Lista de ${formatoData.format(Date())}" }
        val novaLista = ListaCompras(nome = nomeFinal, dataCriacao = System.currentTimeMillis(), criador = utilizadorAtualEmail ?: "")
        rtdb.child("listas_compras").child(novaLista.id).setValue(novaLista)
        return novaLista.id
    }

    private fun registarHistoricoColaborativo(nome: String, preco: Float?, loja: String) {
        if (preco != null && preco > 0 && loja.isNotBlank() && loja != "Geral" && nome.isNotBlank()) {
            val nomeKey = nome.trim().lowercase(Locale.getDefault()).replace("[^a-z0-9]".toRegex(), "")
            val lojaKey = loja.trim().lowercase(Locale.getDefault()).replace("[^a-z0-9]".toRegex(), "")
            val idHistorico = "${nomeKey}_${lojaKey}"
            val registo = HistoricoPreco(id = idHistorico, nomeProduto = nome.trim(), preco = preco, loja = loja.trim(), dataRegisto = System.currentTimeMillis())
            rtdb.child("historico_precos").child(idHistorico).setValue(registo)
        }
    }

    fun adicionarItemCompra(item: ItemCompra) {
        rtdb.child("itens_compra").child(item.id).setValue(item)
        registarHistoricoColaborativo(item.nome, item.preco, item.loja)
    }

    fun atualizarItemCompra(item: ItemCompra) {
        rtdb.child("itens_compra").child(item.id).setValue(item)
        registarHistoricoColaborativo(item.nome, item.preco, item.loja)
    }

    fun alternarEstadoCompra(item: ItemCompra) { rtdb.child("itens_compra").child(item.id).child("comprado").setValue(!item.comprado) }
    fun apagarCompra(item: ItemCompra) { rtdb.child("itens_compra").child(item.id).removeValue() }

    fun guardarEmentaMensal(ementa: EmentaMensal) {
        val ementaFinal = ementa.copy(criador = utilizadorAtualEmail ?: "")
        rtdb.child("ementas_mensais").child(ementaFinal.id).setValue(ementaFinal)
    }
    fun apagarEmentaMensal(ementa: EmentaMensal) { rtdb.child("ementas_mensais").child(ementa.id).removeValue() }

    fun adicionarEmprestimo(oQue: String, aQuem: String) {
        val emp = Emprestimo(oQue = oQue, aQuem = aQuem, criador = utilizadorAtualEmail ?: "")
        rtdb.child("emprestimos").child(emp.id).setValue(emp)
    }
    fun alternarEstadoEmprestimo(emp: Emprestimo) { rtdb.child("emprestimos").child(emp.id).child("devolvido").setValue(!emp.devolvido) }
    fun apagarEmprestimo(emp: Emprestimo) { rtdb.child("emprestimos").child(emp.id).removeValue() }

    fun getVolumeOcupadoNum(arcaId: String): Float {
        return listaItens.filter { it.arcaId == arcaId }.sumOf { (it.litrosVolume ?: 0f).toDouble() }.toFloat()
    }

    fun getOcupacaoAtual(arca: Arca?): Float {
        if (arca == null) return 0f
        val volumeOcupado = getVolumeOcupadoNum(arca.id)
        return if (arca.capacidadeLitros > 0) (volumeOcupado / arca.capacidadeLitros).coerceIn(0f, 1f) else 0f
    }

    fun processarVozComGemini(textoFalado: String, onPreenchido: (nome: String, qtd: Int, diasValidade: Int?) -> Unit) {
        viewModelScope.launch {
            try {
                val prompt = """
                    Analisa o seguinte pedido de voz para adicionar um item: "$textoFalado".
                    Extrai o nome do produto, a quantidade (número inteiro, padrão 1) e os dias até à validade a partir de hoje (número inteiro ou null).
                    Responde estritamente no formato: Nome | Quantidade | DiasValidade
                """.trimIndent()

                val json = JSONObject().apply {
                    put("model", escolherModelo(MODELOS_TEXTO))
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }))
                }

                val resposta = chamarGroqApi(json)
                val partes = (resposta.lines().firstOrNull { it.contains("|") } ?: resposta).trim().split("|")
                if (partes.size >= 2) {
                    val nome = partes[0].trim()
                    val qtd = partes[1].trim().toIntOrNull() ?: 1
                    val dias = partes.getOrNull(2)?.trim()?.filter { it.isDigit() || it == '-' }?.toIntOrNull()
                    onPreenchido(nome, qtd, dias)
                }
            } catch (_: Exception) {
                onPreenchido(textoFalado, 1, null)
            }
        }
    }

    fun gerarPrevisaoComprasGemini(listaId: String) {
        aGerarPrevisaoCompras = true
        viewModelScope.launch {
            try {
                val stockAtual = listaItens.joinToString(", ") { it.nome }
                val ementas = getMinhasEmentas().joinToString("; ") { "Dia ${it.diaMes}: ${it.prato} (${it.ingredientesNecessarios})" }
                val prompt = "Com base no stock: [$stockAtual] e ementas: [$ementas], indica o que falta comprar no formato: Nome | Quantidade | Categoria"

                val json = JSONObject().apply {
                    put("model", escolherModelo(MODELOS_TEXTO))
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }))
                }

                val resposta = chamarGroqApi(json)
                resposta.lines().forEach { linha ->
                    val partes = linha.split("|")
                    if (partes.size >= 2) {
                        val nome = partes[0].trim()
                        val qtd = partes[1].trim()
                        val cat = partes.getOrNull(2)?.trim() ?: "Geral"
                        if (nome.isNotBlank() && !nome.startsWith("-") && !nome.equals("Nome", true)) adicionarItemCompra(ItemCompra(listaId = listaId, nome = nome, quantidade = qtd, categoria = cat, loja = "Sugerido por IA"))
                    }
                }
            } catch (e: Exception) {
                erroIa = mensagemErroIa(e)
            } finally { aGerarPrevisaoCompras = false }
        }
    }

    fun processarTalaoComGemini(bitmap: Bitmap, listaId: String) {
        aProcessarTalao = true
        resultadoTalaoIa = null
        viewModelScope.launch {
            try {
                val maxDim = 1600
                val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
                val resizedBitmap = if (scale < 1) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap

                val base64Image = bitmapToBase64(resizedBitmap)

                val json = JSONObject().apply {
                    put("model", escolherModelo(MODELOS_VISAO))
                    put("temperature", 0.0)
                    put("max_tokens", 1024)
                    put("messages", JSONArray().put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Analisa este talão de supermercado. Extrai estritamente os produtos, quantidades e preços unitários ou totais no formato exato por linha: Nome | Quantidade | Preço. Exemplo: Leite Meio Gordo | 2 | 1.50. Não adiciones texto introdutório, cabeçalhos ou notas.")
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Image"))
                            })
                        })
                    }))
                }

                val resposta = chamarGroqApi(json)

                var itensAdicionados = 0
                resposta.lines().forEach { linha ->
                    val partes = linha.split("|")
                    if (partes.size >= 3) {
                        val nome = partes[0].replace("-", "").trim()
                        val qtd = partes[1].trim()
                        val preco = partes[2].replace("€", "").replace(",", ".").trim().toFloatOrNull()

                        if (nome.isNotBlank() && !nome.contains("Nome", ignoreCase = true) && !nome.contains("Produto", ignoreCase = true) && preco != null) {
                            adicionarItemCompra(
                                ItemCompra(
                                    listaId = listaId,
                                    nome = nome,
                                    quantidade = qtd,
                                    preco = preco,
                                    loja = "Supermercado (Talão)",
                                    categoria = "Geral"
                                )
                            )
                            itensAdicionados++
                        }
                    }
                }

                if (itensAdicionados == 0) {
                    erroIa = "A IA não conseguiu detetar produtos legíveis neste talão. Tenta uma foto mais iluminada."
                }
            } catch (e: Exception) {
                erroIa = "Erro no Talão IA: ${e.localizedMessage ?: "Falha desconhecida"}"
            } finally {
                aProcessarTalao = false
            }
        }
    }
}

fun verificarValidadesENotificar(context: Context, viewModel: ArcaViewModel) {
    val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
    val hojeStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val ultimaNotificacao = prefs.getString("ultima_notificacao", "")
    if (hojeStr == ultimaNotificacao) return

    val minhasArcas = viewModel.getMinhasArcas()
    val meusItens = viewModel.listaItens
    val itensFrigorificoOuDespensa = meusItens.filter { item ->
        val arca = minhasArcas.find { it.id == item.arcaId }
        arca != null
    }

    val hoje = System.currentTimeMillis()
    val limiteDias = prefs.getInt("dias_aviso", 7)
    val msLimite = limiteDias * 24L * 60 * 60 * 1000
    val aExpirar = itensFrigorificoOuDespensa.filter { it.dataValidade != null && diasAte(it.dataValidade) in 0..limiteDias }

    if (aExpirar.isNotEmpty() && mostrarNotificacaoValidade(context, aExpirar, limiteDias)) {
        prefs.edit { putString("ultima_notificacao", hojeStr) }
    }
}

@android.annotation.SuppressLint("MissingPermission")
fun mostrarNotificacaoValidade(context: Context, aExpirar: List<ItemArca>, limiteDias: Int): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false

    val channelId = "freezer_validade_channel"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannel(NotificationChannel(channelId, "Validades", NotificationManager.IMPORTANCE_HIGH))
    }

    val abrirApp = android.app.PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    val detalhes = aExpirar.sortedBy { it.dataValidade }.take(6).joinToString("\n") {
        "• ${it.nome} — ${textoValidade(diasAte(it.dataValidade ?: 0L))}"
    }
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Atenção à Validade!")
        .setContentText("Tens ${aExpirar.size} produto(s) a expirar nos próximos $limiteDias dias.")
        .setStyle(NotificationCompat.BigTextStyle().bigText(detalhes))
        .setContentIntent(abrirApp)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    NotificationManagerCompat.from(context).notify(101, builder.build())
    return true
}

private suspend fun <T> Task<T>.aguardar(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

class ValidadeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
        val hojeStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (prefs.getString("ultima_notificacao", "") == hojeStr) return Result.success()

        return try {
            try { FirebaseDatabase.getInstance(RTDB_URL).setPersistenceEnabled(true) } catch (_: Exception) { }
            val db = FirebaseDatabase.getInstance(RTDB_URL).reference

            val meusIds = db.child("arcas").get().aguardar().children
                .mapNotNull { it.getValue(Arca::class.java) }
                .filter { arca -> arca.criador == email || arca.membros.split(",").map { it.trim() }.contains(email) }
                .map { it.id }.toSet()
            if (meusIds.isEmpty()) return Result.success()

            val limiteDias = prefs.getInt("dias_aviso", 7)
            val aExpirar = db.child("itens_arca").get().aguardar().children
                .mapNotNull { it.getValue(ItemArca::class.java) }
                .filter { it.arcaId in meusIds && it.dataValidade != null && diasAte(it.dataValidade) in 0..limiteDias }

            if (aExpirar.isNotEmpty() && mostrarNotificacaoValidade(applicationContext, aExpirar, limiteDias)) {
                prefs.edit().putString("ultima_notificacao", hojeStr).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

fun agendarVerificacaoDiaria(context: Context) {
    val agora = Calendar.getInstance()
    val proxima = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (before(agora)) add(Calendar.DAY_OF_YEAR, 1)
    }
    val pedido = PeriodicWorkRequestBuilder<ValidadeWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(proxima.timeInMillis - agora.timeInMillis, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("validade_diaria", ExistingPeriodicWorkPolicy.UPDATE, pedido)
}

// Paleta "gelo": fundo azul-ardósia, superfícies em camadas, um só acento (céu -> turquesa).
private val BgDark = Color(0xFF0A0F1A)
private val SurfaceDark = Color(0xFF121A29)
private val SurfaceHigh = Color(0xFF1A2438)
private val AccentStart = Color(0xFF38BDF8)
private val AccentEnd = Color(0xFF2DD4BF)
private val ErrorColor = Color(0xFFF87171)
private val WarnColor = Color(0xFFFBBF24)
private val OkColor = Color(0xFF34D399)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val OutlineColor = Color(0xFF2B3A55)
private val GlassBorder = Color.White.copy(alpha = 0.07f)
val AestheticGradient = Brush.horizontalGradient(listOf(AccentStart, AccentEnd))

private val FreezerColors = darkColorScheme(
    primary = AccentStart, onPrimary = BgDark,
    primaryContainer = AccentStart.copy(alpha = 0.18f), onPrimaryContainer = AccentStart,
    secondary = AccentEnd, onSecondary = BgDark,
    secondaryContainer = SurfaceHigh, onSecondaryContainer = TextPrimary,
    tertiary = OkColor, onTertiary = BgDark,
    background = BgDark, onBackground = TextPrimary,
    surface = SurfaceDark, onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh, onSurfaceVariant = TextSecondary,
    surfaceTint = AccentStart,
    surfaceContainerLowest = BgDark, surfaceContainerLow = SurfaceDark, surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceHigh, surfaceContainerHighest = SurfaceHigh,
    outline = OutlineColor, outlineVariant = OutlineColor.copy(alpha = 0.6f),
    error = ErrorColor, onError = BgDark
)

private val FreezerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun FreezerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FreezerColors, shapes = FreezerShapes, content = content)
}

/** Pequeno rótulo colorido (categoria, validade...). */
@Composable
private fun Etiqueta(texto: String, cor: Color, modifier: Modifier = Modifier) {
    Surface(color = cor.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(texto, color = cor, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** Mosaico de resumo: valor grande + legenda. */
@Composable
private fun MosaicoResumo(valor: String, legenda: String, cor: Color, icone: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceDark).border(1.dp, GlassBorder, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(valor, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(legenda, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Estado vazio com ícone num círculo suave, título e sugestão. */
@Composable
private fun EstadoVazio(icone: androidx.compose.ui.graphics.vector.ImageVector, titulo: String, dica: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(AccentStart.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = null, tint = AccentStart, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(titulo, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(dica, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Cartão de um artigo: faixa de estado, etiquetas, contador -/+ e menu. */
@Composable
private fun CartaoArtigo(item: ItemArca, expirou: Boolean, expiraBreve: Boolean, onMenos: () -> Unit, onMais: () -> Unit, onEditar: () -> Unit, onApagar: () -> Unit, onDeitarFora: () -> Unit = {}) {
    val estado = when {
        expirou -> ErrorColor
        expiraBreve -> WarnColor
        item.dataValidade != null -> OkColor
        else -> OutlineColor
    }
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (expirou || expiraBreve) estado.copy(alpha = 0.45f) else GlassBorder)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(estado))
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.nome, fontWeight = FontWeight.SemiBold, color = TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Etiqueta(item.categoria, AccentStart)
                    if (item.dataValidade != null) Etiqueta(textoValidade(diasAte(item.dataValidade)), if (estado == OutlineColor) TextSecondary else estado)
                }
                val partes = mutableListOf<String>()
                if (item.litrosVolume != null && item.litrosVolume > 0f) partes.add("${item.litrosVolume} L")
                if (item.preco != null && item.preco > 0f) partes.add(String.format(Locale.getDefault(), "%.2f €", item.preco))
                if (item.dataValidade != null) partes.add(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(item.dataValidade)))
                if (partes.isNotEmpty()) Text(partes.joinToString("  ·  "), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.padding(end = 2.dp).clip(RoundedCornerShape(14.dp)).background(BgDark),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenos, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Remove, contentDescription = "Retirar uma unidade", tint = TextSecondary, modifier = Modifier.size(18.dp)) }
                Text("${item.quantidade}", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onMais, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Add, contentDescription = "Adicionar uma unidade", tint = AccentStart, modifier = Modifier.size(18.dp)) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Mais opções", tint = TextSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = SurfaceHigh) {
                    DropdownMenuItem(text = { Text("Editar", color = TextPrimary) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = AccentStart) }, onClick = { menu = false; onEditar() })
                    if (item.dataValidade != null) DropdownMenuItem(text = { Text("Deitar fora (desperdício)", color = WarnColor) }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = WarnColor) }, onClick = { menu = false; onDeitarFora() })
                    DropdownMenuItem(text = { Text("Apagar", color = ErrorColor) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = ErrorColor) }, onClick = { menu = false; onApagar() })
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FreezerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
                    val viewModel: ArcaViewModel = viewModel()
                    if (viewModel.utilizadorAtualEmail == null) EcraLogin(viewModel) else AppNavegacao(viewModel)
                }
            }
        }
    }
}

@Composable
fun EcraLogin(viewModel: ArcaViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AccentStart.copy(alpha = 0.16f), BgDark, BgDark), endY = 1400f))
    ) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(32.dp)).background(SurfaceDark).border(1.dp, AestheticGradient, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.AcUnit, contentDescription = "Logo", tint = AccentStart, modifier = Modifier.size(60.dp))
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text("Freezer", fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.displaySmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("O teu stock, sempre sob controlo.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Etiqueta("Validades", AccentStart); Etiqueta("Compras", AccentEnd); Etiqueta("Ementas", OkColor)
        }
        Spacer(modifier = Modifier.height(56.dp))
        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val googleIdOption = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId("761914486599-e9ib4ppkimo74sbv7r3b93okvm2infeq.apps.googleusercontent.com").setAutoSelectEnabled(true).build()
                        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                        val result = CredentialManager.create(context).getCredential(context, request)
                        if (result.credential is androidx.credentials.CustomCredential && result.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val cred = GoogleIdTokenCredential.createFrom(result.credential.data)
                            viewModel.autenticarComGoogle(cred.idToken) { success -> if (!success) Toast.makeText(context, "Erro na autenticação.", Toast.LENGTH_SHORT).show() }
                        }
                    } catch (_: Exception) { Toast.makeText(context, "Adiciona uma conta Google ao dispositivo!", Toast.LENGTH_LONG).show() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(AestheticGradient, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Google", tint = BgDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Continuar com o Google", fontWeight = FontWeight.Bold, color = BgDark)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavegacao(viewModel: ArcaViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val rotaAtual = navBackStackEntry?.destination?.route ?: "equipamentos"
    val context = LocalContext.current
    val launcherPermissoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var mostrarEmprestimos by rememberSaveable { mutableStateOf(false) }
    var mostrarQuadroNotas by rememberSaveable { mutableStateOf(false) }
    val irPara: (String) -> Unit = { rota ->
        navController.navigate(rota, navOptions {
            popUpTo("equipamentos") { saveState = true }
            launchSingleTop = true
            restoreState = true
        })
    }

    var novaVersao by remember { mutableStateOf<NovaVersao?>(null) }
    LaunchedEffect(Unit) { novaVersao = procurarAtualizacao() }
    novaVersao?.let { DialogoAtualizacao(it) { novaVersao = null } }

    LaunchedEffect(Unit) {
        viewModel.carregarConfigs(context)
        agendarVerificacaoDiaria(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcherPermissoes.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel.listaItens) {
        if (viewModel.listaItens.isNotEmpty()) verificarValidadesENotificar(context, viewModel)
    }

    LaunchedEffect(viewModel.notaPartilhada) {
        val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("nota_widget", viewModel.notaPartilhada.ifBlank { "Sem notas. Clica para adicionar!" }).apply()

        val intent = Intent(context, QuadroNotasWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        context.sendBroadcast(intent)
    }

    LaunchedEffect(viewModel.itensCompra, viewModel.listasCompras) {
        val prefs = context.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
        val listaWidgetId = prefs.getString("widget_lista_selecionada_id", "") ?: ""
        val listaAtiva = viewModel.listasCompras.find { it.id == listaWidgetId } ?: viewModel.getMinhasListas().firstOrNull()

        val pendentes = if (listaAtiva != null) {
            viewModel.itensCompra.filter { it.listaId == listaAtiva.id && !it.comprado }.map { "${it.quantidade}x ${it.nome}" }.toSet()
        } else {
            emptySet()
        }

        prefs.edit().putStringSet("widget_compras_list", pendentes).apply()

        val intent = Intent(context, ListaComprasWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        context.sendBroadcast(intent)
    }

    val msLimite = viewModel.diasAvisoValidade * 24L * 60 * 60 * 1000
    val hoje = System.currentTimeMillis()
    val itensAExpirar = viewModel.listaItens.count { it.dataValidade != null && diasAte(it.dataValidade) <= viewModel.diasAvisoValidade }
    val marmitasPendentes = viewModel.getMeusEmprestimos().count { !it.devolvido }

    val navCores = NavigationBarItemDefaults.colors(
        selectedIconColor = AccentStart, selectedTextColor = AccentStart,
        unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary,
        indicatorColor = AccentStart.copy(alpha = 0.16f)
    )
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AcUnit, contentDescription = null, tint = AccentStart, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Freezer", fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarQuadroNotas = true }) {
                        Icon(imageVector = Icons.Default.StickyNote2, contentDescription = "Notas", tint = AccentStart)
                    }
                    IconButton(onClick = { mostrarEmprestimos = true }) {
                        if (marmitasPendentes > 0) {
                            BadgedBox(badge = { Badge(containerColor = ErrorColor) { Text(marmitasPendentes.toString(), color = TextPrimary) } }) {
                                Icon(imageVector = Icons.Default.TakeoutDining, contentDescription = "Marmitas", tint = AccentStart)
                            }
                        } else {
                            Icon(imageVector = Icons.Default.TakeoutDining, contentDescription = "Marmitas", tint = AccentStart)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (!rotaAtual.startsWith("detalhe_lista")) {
                NavigationBar(containerColor = SurfaceDark, tonalElevation = 0.dp, modifier = Modifier.border(1.dp, GlassBorder)) {
                    NavigationBarItem(
                        icon = {
                            if (itensAExpirar > 0) {
                                BadgedBox(badge = { Badge(containerColor = ErrorColor) { Text(itensAExpirar.toString(), color = TextPrimary) } }) {
                                    Icon(imageVector = Icons.Default.Kitchen, contentDescription = "Stock")
                                }
                            } else {
                                Icon(imageVector = Icons.Default.Kitchen, contentDescription = "Stock")
                            }
                        },
                        label = { Text("Stock") },
                        selected = rotaAtual == "equipamentos",
                        onClick = { irPara("equipamentos") },
                        colors = navCores
                    )
                    NavigationBarItem(icon = { Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Ementas") }, label = { Text("Ementas") }, selected = rotaAtual == "ementas", onClick = { irPara("ementas") }, colors = navCores)
                    NavigationBarItem(icon = { Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Compras") }, label = { Text("Compras") }, selected = rotaAtual == "compras", onClick = { irPara("compras") }, colors = navCores)
                    NavigationBarItem(icon = { Icon(imageVector = Icons.Default.PieChart, contentDescription = "Gastos") }, label = { Text("Gastos") }, selected = rotaAtual == "gastos", onClick = { irPara("gastos") }, colors = navCores)
                    NavigationBarItem(icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Configs") }, label = { Text("Definições") }, selected = rotaAtual == "configs", onClick = { irPara("configs") }, colors = navCores)
                }
            }
        },
        containerColor = BgDark
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            NavHost(navController = navController, startDestination = "equipamentos") {
                composable("equipamentos") { EcraEquipamentos(viewModel) }
                composable("ementas") { EcraEmentas(viewModel) }
                composable("compras") { EcraCompras(viewModel, navController) }
                composable("gastos") { EcraGastos(viewModel) }
                composable(
                    route = "detalhe_lista/{listaId}/{listaNome}",
                    arguments = listOf(
                        navArgument("listaId") { type = NavType.StringType; defaultValue = "geral"; nullable = true },
                        navArgument("listaNome") { type = NavType.StringType; defaultValue = "Lista Geral"; nullable = true }
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "congelador://lista/{listaId}/{listaNome}" },
                        navDeepLink { uriPattern = "congelador://lista/{thing.name}" }
                    )
                ) { backStackEntry ->
                    val idArg = backStackEntry.arguments?.getString("listaId") ?: "geral"
                    val nomeArg = backStackEntry.arguments?.getString("listaNome") ?: backStackEntry.arguments?.getString("thing.name") ?: "Lista Geral"
                    EcraDetalheLista(navController, viewModel, idArg, nomeArg)
                }
                composable("configs") { EcraConfiguracoes(viewModel) }
            }
        }
        if (mostrarEmprestimos) {
            DialogoEmprestimos(viewModel = viewModel, onDismiss = { mostrarEmprestimos = false })
        }
        if (mostrarQuadroNotas) {
            DialogoQuadroNotas(viewModel = viewModel, onDismiss = { mostrarQuadroNotas = false })
        }
    }
}

@Composable
fun DialogoEmprestimos(viewModel: ArcaViewModel, onDismiss: () -> Unit) {
    var oQueInput by rememberSaveable { mutableStateOf("") }
    var aQuemInput by rememberSaveable { mutableStateOf("") }
    val meusEmprestimos = viewModel.getMeusEmprestimos()

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TakeoutDining, contentDescription = null, tint = AccentStart)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Marmitas & Empréstimos", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = oQueInput, onValueChange = { oQueInput = it },
                    label = { Text("O que levaste/deste?", color = TextSecondary) },
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = aQuemInput, onValueChange = { aQuemInput = it },
                        label = { Text("A quem?", color = TextSecondary) },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (oQueInput.isNotBlank() && aQuemInput.isNotBlank()) {
                                viewModel.adicionarEmprestimo(oQueInput, aQuemInput)
                                oQueInput = ""; aQuemInput = ""
                            }
                        },
                        modifier = Modifier.background(AccentStart, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = BgDark)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (meusEmprestimos.isEmpty()) {
                    Text("Nenhum tupperware pendente.", color = TextSecondary)
                } else {
                    LazyColumn {
                        items(meusEmprestimos) { emp ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(BgDark, RoundedCornerShape(8.dp)).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = emp.devolvido, onCheckedChange = { viewModel.alternarEstadoEmprestimo(emp) }, colors = CheckboxDefaults.colors(checkedColor = AccentStart))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(emp.oQue, color = TextPrimary, textDecoration = if (emp.devolvido) TextDecoration.LineThrough else null)
                                    Text("Com: ${emp.aQuem}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { viewModel.apagarEmprestimo(emp) }) { Icon(Icons.Default.Close, contentDescription = null, tint = ErrorColor) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = AccentStart) } }
    )
}

@Composable
fun DialogoQuadroNotas(viewModel: ArcaViewModel, onDismiss: () -> Unit) {
    var textoNota by rememberSaveable { mutableStateOf(viewModel.notaPartilhada) }
    var emailConvidado by rememberSaveable { mutableStateOf("") }
    val contextoApp = LocalContext.current
    val meusMembrosNota = viewModel.membrosNotaAtual

    AlertDialog(
        onDismissRequest = {
            viewModel.atualizarQuadroNotas(contextoApp, textoNota)
            onDismiss()
        },
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.StickyNote2, contentDescription = null, tint = AccentStart)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quadro de Notas Partilhado", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                Text("Aparência direta no ecrã inicial através do Widget.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = textoNota, onValueChange = { textoNota = it },
                    placeholder = { Text("Ex: A Salomé já deu a ração à cadela.", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth().height(110.dp), shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Partilhado atualmente com:", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))

                if (meusMembrosNota.isEmpty()) {
                    Text("Apenas tu tens acesso.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        meusMembrosNota.forEach { membro ->
                            Row(
                                modifier = Modifier.fillMaxWidth().background(BgDark, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(membro, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                if (membro != viewModel.utilizadorAtualEmail) {
                                    IconButton(onClick = {
                                        viewModel.removerMembroNota(membro)
                                        Toast.makeText(contextoApp, "Acesso removido para $membro", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = ErrorColor)
                                    }
                                } else {
                                    Text("(Tu)", color = AccentStart, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Adicionar novo email à nota:", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = emailConvidado, onValueChange = { emailConvidado = it },
                        placeholder = { Text("email@exemplo.com", color = TextSecondary) },
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (emailConvidado.isNotBlank()) {
                                viewModel.adicionarMembroNota(emailConvidado.trim())
                                emailConvidado = ""
                                Toast.makeText(contextoApp, "Convidado adicionado à nota!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.background(AccentStart, CircleShape)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = BgDark)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.atualizarQuadroNotas(contextoApp, textoNota)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)
            ) { Text("Gravar e Fechar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraEquipamentos(viewModel: ArcaViewModel) {
    val todasArcas = viewModel.getMinhasArcas()
    var tipoFiltroVisual by rememberSaveable { mutableStateOf("Todos") }
    var categoriaFiltro by rememberSaveable { mutableStateOf("Todas") }
    var pesquisaStock by rememberSaveable { mutableStateOf("") }
    var itemParaApagar by remember { mutableStateOf<ItemArca?>(null) }
    var novoNomeEditado by rememberSaveable { mutableStateOf("") }
    var novoPrecoEditado by rememberSaveable { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current

    val minhasArcas = when (tipoFiltroVisual) {
        "Arcas" -> todasArcas.filter { it.tipo == "Arca" }
        "Frigoríficos" -> todasArcas.filter { it.tipo == "Frigorífico" }
        "Despensa" -> todasArcas.filter { it.tipo == "Despensa" }
        else -> todasArcas
    }

    if (viewModel.arcaSelecionadaIndex >= minhasArcas.size) viewModel.arcaSelecionadaIndex = 0
    val arcaAtual = minhasArcas.getOrNull(viewModel.arcaSelecionadaIndex)

    var mostrarDialogo by rememberSaveable { mutableStateOf(false) }
    var mostrarScannerCamera by rememberSaveable { mutableStateOf(false) }

    var itemExistenteScanneado by remember { mutableStateOf<ItemArca?>(null) }
    var operacaoStock by rememberSaveable { mutableStateOf("adicionar") }
    var qtdOperacaoInput by rememberSaveable { mutableStateOf("1") }

    var nomeItem by rememberSaveable { mutableStateOf("") }
    var qtdItem by rememberSaveable { mutableStateOf("") }
    var litrosItem by rememberSaveable { mutableStateOf("") }
    var precoItemStock by rememberSaveable { mutableStateOf("") }
    var codigoBarrasItem by rememberSaveable { mutableStateOf("") }
    var categoriaItem by rememberSaveable { mutableStateOf("Carnes") }

    var itemParaEditar by remember { mutableStateOf<ItemArca?>(null) }
    var novaQtdEditada by rememberSaveable { mutableStateOf("") }
    var novosLitrosEditados by rememberSaveable { mutableStateOf("") }

    var mostrarDataPicker by rememberSaveable { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var dataSelecionada by rememberSaveable { mutableStateOf<Long?>(null) }

    var mostrarCriarEquipamento by rememberSaveable { mutableStateOf(false) }
    var nomeNovaArca by rememberSaveable { mutableStateOf("") }
    var capArca by rememberSaveable { mutableStateOf("") }
    var tipoSelecionado by rememberSaveable { mutableStateOf("Arca") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val categoriasDisponiveis = listOf("Todas", "Carnes", "Peixe", "Legumes", "Pratos Prontos", "Bebidas", "Geral")
    val contextoApp = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) mostrarScannerCamera = true else Toast.makeText(contextoApp, "Permissão da câmara negada!", Toast.LENGTH_SHORT).show()
    }

    val launcherVozAvancada = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val falado = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.getOrNull(0) ?: ""
            if (falado.isNotBlank()) {
                Toast.makeText(contextoApp, "A interpretar...", Toast.LENGTH_SHORT).show()
                viewModel.processarVozComGemini(falado) { nome, qtd, dias ->
                    nomeItem = nome
                    qtdItem = qtd.toString()
                    if (dias != null && dias > 0) {
                        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dias) }
                        dataSelecionada = cal.timeInMillis
                    }
                    mostrarDialogo = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Os meus espaços", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                FilledTonalButton(onClick = { mostrarCriarEquipamento = true }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentStart.copy(alpha = 0.16f), contentColor = AccentStart)) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Novo", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (todasArcas.isNotEmpty()) {
                val itensDaArca = arcaAtual?.let { a -> viewModel.itensItemFiltradoPorCategoria(a.id, categoriaFiltro)
                    .filter { pesquisaStock.isBlank() || it.nome.contains(pesquisaStock.trim(), ignoreCase = true) } } ?: emptyList()
                // Tudo desliza em conjunto: ao puxar para cima, a lista de artigos fica com o ecrã inteiro.
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Column {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Todos", "Arcas", "Frigoríficos", "Despensa").forEach { filtro ->
                        FilterChip(
                            selected = tipoFiltroVisual == filtro, onClick = { tipoFiltroVisual = filtro; viewModel.arcaSelecionadaIndex = 0 },
                            label = { Text(filtro) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = SurfaceDark, labelColor = TextPrimary),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = tipoFiltroVisual == filtro, borderColor = GlassBorder, selectedBorderColor = AccentStart)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categoriasDisponiveis.forEach { cat ->
                        FilterChip(
                            selected = categoriaFiltro == cat, onClick = { categoriaFiltro = cat },
                            label = { Text(cat, style = MaterialTheme.typography.bodySmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = SurfaceDark, labelColor = TextSecondary),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = categoriaFiltro == cat, borderColor = GlassBorder, selectedBorderColor = AccentStart)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (minhasArcas.isNotEmpty()) {
                    SecondaryScrollableTabRow(selectedTabIndex = viewModel.arcaSelecionadaIndex.coerceIn(0, minhasArcas.size - 1), containerColor = Color.Transparent, divider = {}, indicator = {}) {
                        minhasArcas.forEachIndexed { index, arca ->
                            val selecionado = viewModel.arcaSelecionadaIndex == index
                            Tab(
                                selected = selecionado, onClick = { viewModel.arcaSelecionadaIndex = index },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val iconVector = when(arca.tipo) {
                                            "Frigorífico" -> Icons.Default.Kitchen
                                            "Despensa" -> Icons.Default.Home
                                            else -> Icons.Default.AcUnit
                                        }
                                        Icon(imageVector = iconVector, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp), tint = if (selecionado) AccentStart else TextSecondary)
                                        Text(arca.nome, color = if (selecionado) AccentStart else TextSecondary, fontWeight = if(selecionado) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (arcaAtual != null) {
                    if (arcaAtual.tipo != "Despensa") {
                        DesenhoArcaTech(percentagem = viewModel.getOcupacaoAtual(arcaAtual), volumeOcupado = viewModel.getVolumeOcupadoNum(arcaAtual.id), capacidadeTotal = arcaAtual.capacidadeLitros)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-PT")
                                }
                                try { launcherVozAvancada.launch(intent) } catch (_: Exception) { Toast.makeText(contextoApp, "Voz não disponível.", Toast.LENGTH_SHORT).show() }
                            },
                            modifier = Modifier.weight(1f).height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            border = BorderStroke(1.dp, OutlineColor), shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = AccentStart, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Voz IA", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(contextoApp, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) mostrarScannerCamera = true
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier.weight(1f).height(46.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentStart), shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, tint = BgDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ler código", color = BgDark, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val itensResumo = viewModel.listaItens.filter { it.arcaId == arcaAtual.id }
                    val aExpirarResumo = itensResumo.count { it.dataValidade != null && diasAte(it.dataValidade) in 0..viewModel.diasAvisoValidade }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MosaicoResumo("${itensResumo.sumOf { it.quantidade }}", "Unidades", AccentStart, Icons.Default.Inventory2, Modifier.weight(1f))
                        MosaicoResumo("${itensResumo.size}", "Artigos", AccentEnd, Icons.Default.Category, Modifier.weight(1f))
                        MosaicoResumo("$aExpirarResumo", "A expirar", if (aExpirarResumo > 0) WarnColor else OkColor, if (aExpirarResumo > 0) Icons.Default.Schedule else Icons.Default.CheckCircle, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pesquisaStock, onValueChange = { pesquisaStock = it },
                        placeholder = { Text("Pesquisar artigo...", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark, focusedBorderColor = AccentStart, unfocusedBorderColor = Color.Transparent)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                }
                        }
                    }
                    if (arcaAtual != null) {
                        if (itensDaArca.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                    EstadoVazio(Icons.Default.Inventory2, if (pesquisaStock.isBlank() && categoriaFiltro == "Todas") "Ainda sem artigos" else "Nada encontrado",
                                if (pesquisaStock.isBlank() && categoriaFiltro == "Todas") "Toca no + para adicionar o primeiro artigo, por voz ou código de barras." else "Experimenta outra categoria ou outra pesquisa.")
                                }
                            }
                        } else {
                            items(itensDaArca) { item ->
                                val dias = item.dataValidade?.let { diasAte(it) }
                                val expirou = dias != null && dias < 0
                                val expiraBreve = dias != null && dias in 0..viewModel.diasAvisoValidade

                                CartaoArtigo(
                                    item = item, expirou = expirou, expiraBreve = expiraBreve,
                                    onMenos = {
                                        if (item.quantidade <= 1) viewModel.removerItem(item)
                                        else viewModel.atualizarItemArca(item.copy(quantidade = item.quantidade - 1))
                                    },
                                    onMais = { viewModel.atualizarItemArca(item.copy(quantidade = item.quantidade + 1)) },
                                    onEditar = {
                                        itemParaEditar = item
                                        novaQtdEditada = item.quantidade.toString()
                                        novosLitrosEditados = item.litrosVolume?.toString() ?: ""
                                        novoNomeEditado = item.nome; novoPrecoEditado = item.preco?.toString() ?: ""; dataSelecionada = item.dataValidade
                                    },
                                    onApagar = { itemParaApagar = item },
                                    onDeitarFora = { viewModel.deitarFora(item); Toast.makeText(contextoApp, "${item.nome} registado como desperdício.", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    EstadoVazio(Icons.Default.Kitchen, "Ainda não tens espaços", "Toca em \"Novo\" para criar uma arca, frigorífico ou despensa.")
                }
            }
        }

        if (arcaAtual != null) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(RoundedCornerShape(20.dp)).background(AestheticGradient).clickable { dataSelecionada = null; mostrarDialogo = true }, contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar artigo", tint = BgDark, modifier = Modifier.size(30.dp))
            }
        }

        if (mostrarScannerCamera && arcaAtual != null) {
            AlertDialog(
                onDismissRequest = { mostrarScannerCamera = false }, containerColor = SurfaceDark,
                title = { Text("Scan / Leitura de Stock", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    val ctxCamara = LocalContext.current
                    DisposableEffect(Unit) {
                        onDispose { try { ProcessCameraProvider.getInstance(ctxCamara).get().unbindAll() } catch (_: Exception) { } }
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp))) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                val executor = Executors.newSingleThreadExecutor()
                                val jaLido = java.util.concurrent.atomic.AtomicBoolean(false)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                                    val scanner = BarcodeScanning.getClient()
                                    val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

                                    @androidx.camera.core.ExperimentalGetImage
                                    fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null && !jaLido.get()) {
                                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                            scanner.process(image).addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue
                                                    if (!rawValue.isNullOrBlank() && jaLido.compareAndSet(false, true)) {
                                                        executor.shutdown()
                                                        val itemExistente = viewModel.listaItens.find { it.arcaId == arcaAtual.id && it.codigoBarras == rawValue }
                                                        if (itemExistente != null) {
                                                            itemExistenteScanneado = itemExistente
                                                            dataSelecionada = null
                                                            operacaoStock = "adicionar"
                                                            qtdOperacaoInput = "1"
                                                            mostrarScannerCamera = false
                                                        } else {
                                                            codigoBarrasItem = rawValue
                                                            nomeItem = ""
                                                            qtdItem = "1"
                                                            litrosItem = ""
                                                            precoItemStock = ""
                                                            mostrarScannerCamera = false
                                                            mostrarDialogo = true
                                                            Toast.makeText(ctx, "Novo produto! Preencha os dados.", Toast.LENGTH_LONG).show()
                                                        }
                                                        break
                                                    }
                                                }
                                            }.addOnFailureListener { }.addOnCompleteListener { imageProxy.close() }
                                        } else { imageProxy.close() }
                                    }
                                    imageAnalysis.setAnalyzer(executor, ::processImageProxy)
                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                                    } catch (_: Exception) { }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            }
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { mostrarScannerCamera = false }) { Text("Fechar", color = AccentStart) } }
            )
        }

        itemExistenteScanneado?.let { selItemExistenteScanneado ->
            AlertDialog(
                onDismissRequest = { itemExistenteScanneado = null }, containerColor = SurfaceDark,
                title = { Text("Gerir Stock (FIFO): ${selItemExistenteScanneado.nome}", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Quantidade atual em stock: ${selItemExistenteScanneado.quantidade}", color = TextSecondary)
                        Text("O que pretendes fazer?", color = TextPrimary, fontWeight = FontWeight.Medium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = operacaoStock == "adicionar", onClick = { operacaoStock = "adicionar" },
                                label = { Text("Adicionar (+)", fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = BgDark, labelColor = TextSecondary)
                            )
                            FilterChip(
                                selected = operacaoStock == "retirar", onClick = { operacaoStock = "retirar" },
                                label = { Text("Retirar (-)", fontWeight = FontWeight.Bold) }, modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = BgDark, labelColor = TextSecondary)
                            )
                        }
                        OutlinedTextField(
                            value = qtdOperacaoInput, onValueChange = { qtdOperacaoInput = it },
                            label = { Text(if (operacaoStock == "adicionar") "Quantidade a acrescentar" else "Quantidade a retirar", color = TextSecondary) },
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                        )
                        if (operacaoStock == "adicionar") {
                            OutlinedButton(onClick = { mostrarDataPicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, GlassBorder)) {
                                Text(
                                    "Validade do novo lote: " + (dataSelecionada?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "igual ao lote atual"),
                                    color = if (dataSelecionada != null) AccentStart else TextSecondary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val qtdInput = qtdOperacaoInput.toIntOrNull() ?: 1
                            val item = selItemExistenteScanneado
                            if (operacaoStock == "adicionar") {
                                viewModel.adicionarItem(item.copy(id = UUID.randomUUID().toString(), quantidade = qtdInput, dataValidade = dataSelecionada ?: item.dataValidade, dataRegisto = System.currentTimeMillis()))
                                Toast.makeText(contextoApp, "Adicionado novo lote de $qtdInput unidades.", Toast.LENGTH_SHORT).show()
                            } else {
                                if (qtdInput >= item.quantidade) {
                                    viewModel.removerItem(item)
                                    Toast.makeText(contextoApp, "Lote consumido totalmente.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.atualizarItemArca(item.copy(quantidade = item.quantidade - qtdInput))
                                    Toast.makeText(contextoApp, "Retiradas $qtdInput unidades do lote mais antigo.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            itemExistenteScanneado = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)
                    ) { Text("Confirmar") }
                },
                dismissButton = { TextButton(onClick = { itemExistenteScanneado = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        if (mostrarDialogo) {
            AlertDialog(
                onDismissRequest = { mostrarDialogo = false }, containerColor = SurfaceDark,
                title = { Text("Adicionar Item", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        OutlinedTextField(value = nomeItem, onValueChange = { nomeItem = it }, label = { Text("Nome", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())

                        Text("Categoria:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Carnes", "Peixe", "Legumes", "Pratos Prontos", "Bebidas", "Geral").forEach { cat ->
                                FilterChip(
                                    selected = categoriaItem == cat, onClick = { categoriaItem = cat },
                                    label = { Text(cat, style = MaterialTheme.typography.bodySmall) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = BgDark, labelColor = TextSecondary)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = qtdItem, onValueChange = { qtdItem = it }, label = { Text("Qtd", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                            if (arcaAtual?.tipo != "Despensa") {
                                OutlinedTextField(value = litrosItem, onValueChange = { litrosItem = it }, label = { Text("Litros", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f))
                            }
                        }

                        OutlinedTextField(value = precoItemStock, onValueChange = { precoItemStock = it }, label = { Text("Preço / Custo (€) - Opcional", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = codigoBarrasItem, onValueChange = { codigoBarrasItem = it }, label = { Text("Código de Barras", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())

                        val strDataExibicao = if (dataSelecionada != null) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(dataSelecionada!!)) else "Sem Validade"
                        OutlinedButton(onClick = { mostrarDataPicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, GlassBorder)) {
                            Text("Validade: $strDataExibicao", color = if (dataSelecionada != null) AccentStart else TextSecondary)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (nomeItem.isNotBlank() && arcaAtual != null) {
                            viewModel.adicionarItem(ItemArca(arcaId = arcaAtual.id, nome = nomeItem, quantidade = (qtdItem.toIntOrNull() ?: 1).coerceAtLeast(1), litrosVolume = numero(litrosItem), dataValidade = dataSelecionada, categoria = categoriaItem, preco = numero(precoItemStock), codigoBarras = codigoBarrasItem.ifBlank { null }))
                            nomeItem = ""; qtdItem = ""; litrosItem = ""; precoItemStock = ""; codigoBarrasItem = ""; dataSelecionada = null; mostrarDialogo = false
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { mostrarDialogo = false; dataSelecionada = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        itemParaEditar?.let { selItemParaEditar ->
            AlertDialog(
                onDismissRequest = { itemParaEditar = null }, containerColor = SurfaceDark,
                title = { Text("Editar Artigo: ${selItemParaEditar.nome}", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = novoNomeEditado, onValueChange = { novoNomeEditado = it }, label = { Text("Nome", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = novaQtdEditada, onValueChange = { novaQtdEditada = it }, label = { Text("Quantidade", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = novosLitrosEditados, onValueChange = { novosLitrosEditados = it }, label = { Text("Litros de Volume", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = novoPrecoEditado, onValueChange = { novoPrecoEditado = it }, label = { Text("Preço (€)", color = TextSecondary) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { mostrarDataPicker = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, GlassBorder)) {
                            Text(
                                "Validade: " + (dataSelecionada?.let { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(it)) } ?: "Sem validade"),
                                color = if (dataSelecionada != null) AccentStart else TextSecondary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val novaQtd = (novaQtdEditada.toIntOrNull() ?: selItemParaEditar.quantidade).coerceAtLeast(1)
                        val novosLitros = numero(novosLitrosEditados)
                        viewModel.atualizarItemArca(selItemParaEditar.copy(
                            nome = novoNomeEditado.trim().ifBlank { selItemParaEditar.nome },
                            quantidade = novaQtd, litrosVolume = novosLitros,
                            preco = numero(novoPrecoEditado),
                            dataValidade = dataSelecionada
                        ))
                        dataSelecionada = null
                        itemParaEditar = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) { Text("Atualizar") }
                },
                dismissButton = { TextButton(onClick = { itemParaEditar = null; dataSelecionada = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        itemParaApagar?.let { selItemParaApagar ->
            AlertDialog(
                onDismissRequest = { itemParaApagar = null }, containerColor = SurfaceDark,
                title = { Text("Apagar artigo?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text("Queres mesmo apagar ${selItemParaApagar.nome}?", color = TextSecondary) },
                confirmButton = {
                    Button(onClick = { viewModel.removerItem(selItemParaApagar); itemParaApagar = null }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Apagar", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { itemParaApagar = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        if (mostrarDataPicker) {
            DatePickerDialog(
                onDismissRequest = { mostrarDataPicker = false },
                confirmButton = { TextButton(onClick = { dataSelecionada = datePickerState.selectedDateMillis?.let { fimDoDiaLocal(it) }; mostrarDataPicker = false }) { Text("Confirmar", color = AccentStart) } },
                dismissButton = { TextButton(onClick = { mostrarDataPicker = false }) { Text("Cancelar", color = TextSecondary) } },
                colors = DatePickerDefaults.colors(containerColor = SurfaceDark)
            ) { DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(titleContentColor = AccentStart, headlineContentColor = TextPrimary, selectedDayContainerColor = AccentStart, selectedDayContentColor = BgDark, todayContentColor = AccentStart)) }
        }

        if (mostrarCriarEquipamento) {
            ModalBottomSheet(
                onDismissRequest = { mostrarCriarEquipamento = false }, sheetState = sheetState, containerColor = SurfaceDark
            ) {
                Column(modifier = Modifier.padding(24.dp).padding(bottom = 32.dp)) {
                    Text("Adicionar Espaço", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth().height(48.dp).background(BgDark, RoundedCornerShape(12.dp)).border(1.dp, GlassBorder, RoundedCornerShape(12.dp))) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (tipoSelecionado == "Arca") AccentStart else Color.Transparent, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)).clickable { tipoSelecionado = "Arca" }, contentAlignment = Alignment.Center) {
                            Text("Arca", color = if (tipoSelecionado == "Arca") BgDark else TextSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (tipoSelecionado == "Frigorífico") AccentStart else Color.Transparent).clickable { tipoSelecionado = "Frigorífico" }, contentAlignment = Alignment.Center) {
                            Text("Frigorífico", color = if (tipoSelecionado == "Frigorífico") BgDark else TextSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (tipoSelecionado == "Despensa") AccentStart else Color.Transparent, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)).clickable { tipoSelecionado = "Despensa" }, contentAlignment = Alignment.Center) {
                            Text("Despensa", color = if (tipoSelecionado == "Despensa") BgDark else TextSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(value = nomeNovaArca, onValueChange = { nomeNovaArca = it }, label = { Text("Nome (ex: Despensa da Cozinha)", color = TextSecondary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                    if (tipoSelecionado != "Despensa") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(value = capArca, onValueChange = { capArca = it }, label = { Text("Capacidade (Litros)", color = TextSecondary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (nomeNovaArca.isNotBlank()) {
                                viewModel.guardarArca(Arca(nome = nomeNovaArca, tipo = tipoSelecionado, capacidadeLitros = numero(capArca) ?: 0f)) { sucesso ->
                                    if (sucesso) {
                                        nomeNovaArca = ""; capArca = ""; Toast.makeText(contextoApp, "Criado!", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) mostrarCriarEquipamento = false }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), contentPadding = PaddingValues()
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(AestheticGradient, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Text("CRIAR ESPAÇO", fontWeight = FontWeight.ExtraBold, color = BgDark)
                        }
                    }
                }
            }
        }
    }
}

fun ArcaViewModel.itensItemFiltradoPorCategoria(arcaId: String, categoria: String): List<ItemArca> {
    val itens = listaItens.filter { it.arcaId == arcaId }
    return if (categoria == "Todas") itens else itens.filter { it.categoria == categoria }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraEmentas(viewModel: ArcaViewModel) {
    var mostrarDialogo by rememberSaveable { mutableStateOf(false) }

    val calendarAtual = Calendar.getInstance()
    val anoAtualNum = calendarAtual.get(Calendar.YEAR)
    val mesAtualNum = calendarAtual.get(Calendar.MONTH)
    val diaHojeNum = calendarAtual.get(Calendar.DAY_OF_MONTH)

    val mesesNomes = listOf("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    val anosDisponiveis = listOf(anoAtualNum - 1, anoAtualNum, anoAtualNum + 1)

    var anoSelecionado by rememberSaveable { mutableStateOf(anoAtualNum) }
    var mesSelecionadoIndex by rememberSaveable { mutableStateOf(mesAtualNum) }

    val mesAnoStr = "${mesesNomes[mesSelecionadoIndex]} $anoSelecionado"
    var diaSelecionadoParaVer by rememberSaveable { mutableStateOf<String?>(diaHojeNum.toString()) }

    var ementaParaPartilhar by remember { mutableStateOf<EmentaMensal?>(null) }
    var emailPartilhaEmenta by rememberSaveable { mutableStateOf("") }
    var mensagemPartilhaEmenta by rememberSaveable { mutableStateOf("") }

    var pratoInput by rememberSaveable { mutableStateOf("") }
    var ingredientesInput by rememberSaveable { mutableStateOf("") }
    var regimeInput by rememberSaveable { mutableStateOf("Almoço") }
    var requerDescongelamento by rememberSaveable { mutableStateOf(false) }

    val minhasEmentas = viewModel.getMinhasEmentas()
    val contextoApp = LocalContext.current

    val calTemp = Calendar.getInstance().apply {
        set(Calendar.YEAR, anoSelecionado)
        set(Calendar.MONTH, mesSelecionadoIndex)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val totalDiasNoMes = calTemp.getActualMaximum(Calendar.DAY_OF_MONTH)
    val primeiroDiaSemana = (calTemp.get(Calendar.DAY_OF_WEEK) + 5) % 7

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Calendário de Ementas", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Button(onClick = { mostrarDialogo = true }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("NOVA", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            anosDisponiveis.forEach { ano ->
                FilterChip(
                    selected = anoSelecionado == ano, onClick = { anoSelecionado = ano },
                    label = { Text(ano.toString(), fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = SurfaceDark, labelColor = TextSecondary),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = anoSelecionado == ano, borderColor = GlassBorder, selectedBorderColor = AccentStart)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            mesesNomes.forEachIndexed { index, mes ->
                FilterChip(
                    selected = mesSelecionadoIndex == index, onClick = { mesSelecionadoIndex = index; diaSelecionadoParaVer = "1" },
                    label = { Text(mes, style = MaterialTheme.typography.bodySmall) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = SurfaceDark, labelColor = TextSecondary),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = mesSelecionadoIndex == index, borderColor = GlassBorder, selectedBorderColor = AccentStart)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").forEach { diaSem ->
                Text(diaSem, color = TextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().height(210.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(primeiroDiaSemana) {
                Box(modifier = Modifier.aspectRatio(1f))
            }

            items(totalDiasNoMes) { index ->
                val diaNum = index + 1
                val diaStr = diaNum.toString()
                val ementasDoDia = minhasEmentas.filter { it.mesAno.equals(mesAnoStr, true) && it.diaMes == diaStr }
                val temRefeicao = ementasDoDia.isNotEmpty()
                val requerDescong = ementasDoDia.any { it.requerDescongelamento }
                val selecionado = diaSelecionadoParaVer == diaStr
                val éHoje = (anoSelecionado == anoAtualNum && mesSelecionadoIndex == mesAtualNum && diaNum == diaHojeNum)

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selecionado) AccentStart else if (temRefeicao) SurfaceDark else BgDark)
                        .border(
                            width = if (éHoje) 2.dp else 1.dp,
                            color = if (éHoje) AccentStart else if (requerDescong) ErrorColor else if (temRefeicao) AccentStart else GlassBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { diaSelecionadoParaVer = diaStr },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = diaStr, color = if (selecionado) BgDark else TextPrimary, fontWeight = if (éHoje) FontWeight.Black else FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        if (temRefeicao) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(if (selecionado) BgDark else if (requerDescong) ErrorColor else AccentStart))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (diaSelecionadoParaVer != null) "Refeições do Dia $diaSelecionadoParaVer de $mesAnoStr" else "Todas as ementas de $mesAnoStr",
            color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        val ementasFiltradas = minhasEmentas.filter { e ->
            val mesmoMes = e.mesAno.equals(mesAnoStr, true)
            if (diaSelecionadoParaVer != null) mesmoMes && e.diaMes == diaSelecionadoParaVer else mesmoMes
        }

        if (ementasFiltradas.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("Sem refeições registadas para esta seleção.", color = TextSecondary) }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ementasFiltradas.sortedWith(compareBy<EmentaMensal>({ it.diaMes.toIntOrNull() ?: 0 }, { it.regimeRefeicao }))) { ementa ->
                    Card(
                        modifier = Modifier.fillMaxWidth().border(0.5.dp, if (ementa.requerDescongelamento) ErrorColor else GlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Dia ${ementa.diaMes} (${ementa.regimeRefeicao})", color = AccentStart, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    if (ementa.requerDescongelamento) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(color = ErrorColor.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                            Text("⚠️ Requer Descongelamento", color = ErrorColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { ementaParaPartilhar = ementa; mensagemPartilhaEmenta = "Olá! Para o ${ementa.regimeRefeicao} do dia ${ementa.diaMes} temos: ${ementa.prato}." }) {
                                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = AccentStart)
                                    }
                                    IconButton(onClick = {
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            data = CalendarContract.Events.CONTENT_URI
                                            putExtra(CalendarContract.Events.TITLE, "Refeição: ${ementa.prato} (${ementa.regimeRefeicao})")
                                            putExtra(CalendarContract.Events.DESCRIPTION, "Ingredientes: ${ementa.ingredientesNecessarios}.")
                                        }
                                        try { contextoApp.startActivity(intent) } catch (_: Exception) { Toast.makeText(contextoApp, "Não foi possível abrir o calendário.", Toast.LENGTH_SHORT).show() }
                                    }) {
                                        Icon(imageVector = Icons.Default.Event, contentDescription = "Adicionar ao Calendário", tint = AccentStart)
                                    }
                                    IconButton(onClick = { viewModel.apagarEmentaMensal(ementa) }) {
                                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorColor)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(ementa.prato, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (ementa.ingredientesNecessarios.isNotBlank()) {
                                Text("Ingredientes: ${ementa.ingredientesNecessarios}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (mostrarDialogo) {
            AlertDialog(
                onDismissRequest = { mostrarDialogo = false }, containerColor = SurfaceDark,
                title = { Text("Planear Refeição para o Dia ${diaSelecionadoParaVer ?: "1"} de $mesAnoStr", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Regime:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Almoço", "Jantar").forEach { reg ->
                                FilterChip(
                                    selected = regimeInput == reg, onClick = { regimeInput = reg }, label = { Text(reg) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = BgDark, labelColor = TextSecondary)
                                )
                            }
                        }
                        OutlinedTextField(value = pratoInput, onValueChange = { pratoInput = it }, label = { Text("Prato Principal", color = TextSecondary) }, shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = ingredientesInput, onValueChange = { ingredientesInput = it }, label = { Text("Ingredientes necessários", color = TextSecondary) }, shape = RoundedCornerShape(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { requerDescongelamento = !requerDescongelamento }) {
                            Checkbox(checked = requerDescongelamento, onCheckedChange = { requerDescongelamento = it }, colors = CheckboxDefaults.colors(checkedColor = AccentStart))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Requer Descongelamento Prévio", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (pratoInput.isNotBlank()) {
                            viewModel.guardarEmentaMensal(EmentaMensal(mesAno = mesAnoStr, diaMes = diaSelecionadoParaVer ?: "1", regimeRefeicao = regimeInput, prato = pratoInput, ingredientesNecessarios = ingredientesInput, requerDescongelamento = requerDescongelamento))
                            pratoInput = ""; ingredientesInput = ""; requerDescongelamento = false; mostrarDialogo = false
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { mostrarDialogo = false }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        ementaParaPartilhar?.let { selEmentaParaPartilhar ->
            AlertDialog(
                onDismissRequest = { ementaParaPartilhar = null }, containerColor = SurfaceDark,
                title = { Text("Partilhar Refeição do Dia ${selEmentaParaPartilhar.diaMes}", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = emailPartilhaEmenta, onValueChange = { emailPartilhaEmenta = it }, label = { Text("Email do destinatário", color = TextSecondary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(value = mensagemPartilhaEmenta, onValueChange = { mensagemPartilhaEmenta = it }, label = { Text("Mensagem personalizada", color = TextSecondary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (emailPartilhaEmenta.isNotBlank()) {
                            viewModel.partilharEmentaIndividual(selEmentaParaPartilhar, emailPartilhaEmenta.trim())
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "$mensagemPartilhaEmenta (Enviado via Freezer)")
                                type = "text/plain"
                            }
                            try { contextoApp.startActivity(Intent.createChooser(sendIntent, "Partilhar Refeição")) } catch (_: Exception) {}
                            emailPartilhaEmenta = ""; mensagemPartilhaEmenta = ""; ementaParaPartilhar = null
                            Toast.makeText(contextoApp, "Refeição partilhada com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) { Text("Enviar Partilha") }
                },
                dismissButton = { TextButton(onClick = { ementaParaPartilhar = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }
    }
}

@Composable
fun EcraCompras(viewModel: ArcaViewModel, navController: androidx.navigation.NavController) {
    var nomeNovaLista by rememberSaveable { mutableStateOf("") }
    var listaParaPartilhar by remember { mutableStateOf<ListaCompras?>(null) }
    var listaParaApagar by remember { mutableStateOf<ListaCompras?>(null) }
    var emailPartilhaLista by rememberSaveable { mutableStateOf("") }
    val minhasListas = viewModel.getMinhasListas()
    val contextoApp = LocalContext.current
    val prefs = contextoApp.getSharedPreferences("FreezerPrefs", Context.MODE_PRIVATE)
    val listaWidgetAtivaId = prefs.getString("widget_lista_selecionada_id", "") ?: ""

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        OutlinedTextField(
            value = nomeNovaLista, onValueChange = { nomeNovaLista = it },
            placeholder = { Text("Nova Lista de Compras...", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(unfocusedContainerColor = SurfaceDark, focusedContainerColor = SurfaceDark, focusedIndicatorColor = AccentStart, unfocusedIndicatorColor = Color.Transparent),
            trailingIcon = {
                IconButton(onClick = {
                    if (nomeNovaLista.isNotBlank()) {
                        val id = viewModel.criarListaCompras(nomeNovaLista)
                        val nomeCriado = nomeNovaLista
                        nomeNovaLista = ""
                        navController.navigate("detalhe_lista/$id/${Uri.encode(nomeCriado)}")
                    }
                }) { Icon(imageVector = Icons.Default.AddCircle, contentDescription = null, tint = AccentStart, modifier = Modifier.size(28.dp)) }
            }
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text("As minhas listas", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (minhasListas.isEmpty()) EstadoVazio(Icons.Default.ShoppingCart, "Sem listas de compras", "Escreve um nome em cima para criar a primeira lista.", Modifier.padding(top = 24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(minhasListas) { lista ->
                val itensDestaLista = viewModel.itensCompra.filter { it.listaId == lista.id }
                val totalItens = itensDestaLista.sumOf { (it.preco ?: 0f).toDouble() }.toFloat()
                val totalGastoLista = lista.valorGlobal ?: totalItens
                val isWidgetAtiva = listaWidgetAtivaId == lista.id

                Card(
                    modifier = Modifier.fillMaxWidth().animateContentSize().border(
                        BorderStroke(if (isWidgetAtiva) 2.dp else 0.5.dp, if (isWidgetAtiva) AccentStart else GlassBorder),
                        RoundedCornerShape(16.dp)
                    ),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f).clickable { navController.navigate("detalhe_lista/${lista.id}/${Uri.encode(lista.nome)}") }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(lista.nome, fontWeight = FontWeight.Bold, color = if (lista.concluida) TextSecondary else TextPrimary, style = MaterialTheme.typography.titleMedium)
                                    if (isWidgetAtiva) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(color = AccentStart.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                            Text("Ativa no widget", color = AccentStart, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                if (itensDestaLista.isNotEmpty()) {
                                    val comprados = itensDestaLista.count { it.comprado }
                                    Text("$comprados/${itensDestaLista.size} comprados", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    LinearProgressIndicator(
                                        progress = { comprados.toFloat() / itensDestaLista.size },
                                        color = AccentStart, trackColor = BgDark,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }
                                if (totalGastoLista > 0f) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "Total Gasto: %.2f € ${if(lista.valorGlobal != null) "(Global)" else ""}", totalGastoLista),
                                        color = AccentStart, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (lista.criador == viewModel.utilizadorAtualEmail) {
                                    if (lista.membros.isNotEmpty()) Text("Partilhado com: ${lista.membros}", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Text("Partilhado por: ${lista.criador}", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (lista.criador == viewModel.utilizadorAtualEmail) {
                                    IconButton(onClick = { listaParaPartilhar = lista }) { Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = AccentStart) }
                                }
                                IconButton(onClick = { listaParaApagar = lista }) {
                                    Icon(
                                        imageVector = if (lista.criador == viewModel.utilizadorAtualEmail) Icons.Default.DeleteOutline else Icons.AutoMirrored.Filled.ExitToApp,
                                        tint = ErrorColor, contentDescription = null
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                prefs.edit().putString("widget_lista_selecionada_id", lista.id).apply()
                                Toast.makeText(contextoApp, "Definida como lista do Widget!", Toast.LENGTH_SHORT).show()
                                val intent = Intent(contextoApp, ListaComprasWidget::class.java).apply {
                                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                                }
                                contextoApp.sendBroadcast(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isWidgetAtiva) AccentStart else GlassBorder)
                        ) {
                            Text(if (isWidgetAtiva) "Lista Principal no Ecrã Inicial" else "Usar esta Lista no Widget", color = if (isWidgetAtiva) AccentStart else TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        listaParaApagar?.let { selListaParaApagar ->
            val souCriador = selListaParaApagar.criador == viewModel.utilizadorAtualEmail
            AlertDialog(
                onDismissRequest = { listaParaApagar = null }, containerColor = SurfaceDark,
                title = { Text(if (souCriador) "Apagar lista?" else "Sair da lista?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = { Text(if (souCriador) "A lista e todos os seus artigos serão apagados para todos os membros." else "Deixas de ter acesso a esta lista partilhada.", color = TextSecondary) },
                confirmButton = {
                    Button(onClick = { viewModel.apagarOuSairLista(selListaParaApagar); listaParaApagar = null }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text(if (souCriador) "Apagar" else "Sair", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { listaParaApagar = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        listaParaPartilhar?.let { selListaParaPartilhar ->
            AlertDialog(
                onDismissRequest = { listaParaPartilhar = null }, containerColor = SurfaceDark,
                title = { Text("Partilhar Lista", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Email de quem vai ter acesso:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = emailPartilhaLista, onValueChange = { emailPartilhaLista = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (emailPartilhaLista.isNotBlank()) {
                            viewModel.partilharLista(selListaParaPartilhar, emailPartilhaLista.trim())
                            emailPartilhaLista = ""; listaParaPartilhar = null
                            Toast.makeText(contextoApp, "Lista partilhada!", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) { Text("Convidar") }
                },
                dismissButton = { TextButton(onClick = { listaParaPartilhar = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }
    }
}

@Composable
fun EcraDetalheLista(navController: androidx.navigation.NavController, viewModel: ArcaViewModel, listaId: String, listaNome: String) {
    var nomeItem by rememberSaveable { mutableStateOf("") }
    var qtdItem by rememberSaveable { mutableStateOf("1") }
    var precoItem by rememberSaveable { mutableStateOf("") }
    var lojaItem by rememberSaveable { mutableStateOf("Continente") }
    var categoriaItem by rememberSaveable { mutableStateOf("Carnes") }

    var modoSupermercado by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity

    DisposableEffect(modoSupermercado) {
        if (modoSupermercado) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val listaRealId = if (listaId == "geral" || listaId.contains("ID_GENERICO")) {
        viewModel.getMinhasListas().firstOrNull()?.id ?: "default_lista"
    } else { listaId }

    val listaAtual = viewModel.listasCompras.find { it.id == listaRealId }
    var valorGlobalInput by rememberSaveable { mutableStateOf(listaAtual?.valorGlobal?.toString() ?: "") }

    var itemParaEditar by remember { mutableStateOf<ItemCompra?>(null) }
    var editNome by rememberSaveable { mutableStateOf("") }
    var editQtd by rememberSaveable { mutableStateOf("") }
    var editPreco by rememberSaveable { mutableStateOf("") }
    var editLoja by rememberSaveable { mutableStateOf("") }

    var itemParaTransferir by remember { mutableStateOf<ItemCompra?>(null) }
    val contextoApp = LocalContext.current
    val lojasPortuguesas = listOf("Continente", "Pingo Doce", "Mercadona", "Lidl", "Auchan", "Makro", "Aldi", "El Corte Inglés", "Froiz", "Outra")

    // Foto do talão em resolução completa (antes usava a miniatura da câmara, ~200 px, e a IA não conseguia ler os preços).
    val ficheiroTalao = remember { java.io.File(contextoApp.cacheDir, "talao.jpg") }
    val uriTalao = remember { androidx.core.content.FileProvider.getUriForFile(contextoApp, "${contextoApp.packageName}.fileprovider", ficheiroTalao) }
    val launcherFotoTalao = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val limites = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(ficheiroTalao.path, limites)
            var amostra = 1
            while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= 1600) amostra *= 2
            val bitmap = android.graphics.BitmapFactory.decodeFile(ficheiroTalao.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = amostra })
            if (bitmap != null) viewModel.processarTalaoComGemini(bitmap, listaRealId)
            else Toast.makeText(contextoApp, "Não foi possível ler a foto do talão.", Toast.LENGTH_SHORT).show()
        }
    }
    // A câmara só pode ser aberta com a permissão dada (a app declara CAMERA; sem ela o Android fecha a app ao tirar a foto).
    val abrirCamaraTalao: () -> Unit = {
        try { launcherFotoTalao.launch(uriTalao) } catch (_: Exception) { Toast.makeText(contextoApp, "Não foi possível abrir a câmara.", Toast.LENGTH_SHORT).show() }
    }
    val permissaoCamaraTalao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) abrirCamaraTalao() else Toast.makeText(contextoApp, "Sem acesso à câmara não é possível ler o talão.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(viewModel.erroIa) {
        viewModel.erroIa?.let {
            Toast.makeText(contextoApp, it, Toast.LENGTH_LONG).show()
            viewModel.erroIa = null
        }
    }

    val nomePesquisa = nomeItem.trim().lowercase(Locale.getDefault())
    val sugestaoCrowd = viewModel.historicoPrecos
        .filter { it.nomeProduto.lowercase(Locale.getDefault()) == nomePesquisa }
        .maxByOrNull { it.dataRegisto }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) { Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = AccentStart) }
                Text(listaAtual?.nome ?: listaNome, style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            FilterChip(
                selected = modoSupermercado,
                onClick = { modoSupermercado = !modoSupermercado },
                label = { Text("🛒 Super") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = SurfaceDark, labelColor = TextSecondary)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(contextoApp, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamaraTalao()
                    else permissaoCamaraTalao.launch(Manifest.permission.CAMERA)
                }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark), border = BorderStroke(1.dp, AccentStart), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = AccentStart)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Talão IA", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { viewModel.gerarPrevisaoComprasGemini(listaRealId) }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark), border = BorderStroke(1.dp, AccentStart), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = AccentStart, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Previsão IA", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (viewModel.aProcessarTalao || viewModel.aGerarPrevisaoCompras) {
            LinearProgressIndicator(color = AccentStart, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                OutlinedTextField(
                    value = valorGlobalInput,
                    onValueChange = { valorGlobalInput = it; viewModel.atualizarValorGlobalLista(listaRealId, numero(it)) },
                    label = { Text("Valor Global da Lista (€) - Opcional", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = nomeItem, onValueChange = { nomeItem = it }, placeholder = { Text("Artigo...", color = TextSecondary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

            if (sugestaoCrowd != null && nomeItem.isNotBlank()) {
                Surface(
                    color = AccentStart.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        precoItem = sugestaoCrowd.preco.toString()
                        lojaItem = sugestaoCrowd.loja
                    }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentStart, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preço Habitual: ${sugestaoCrowd.preco}€ no ${sugestaoCrowd.loja} (Tocar para usar)", color = AccentStart, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Categoria do Artigo:", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Carnes", "Peixe", "Legumes", "Pratos Prontos", "Bebidas", "Geral").forEach { cat ->
                    FilterChip(
                        selected = categoriaItem == cat, onClick = { categoriaItem = cat },
                        label = { Text(cat, style = MaterialTheme.typography.bodySmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = SurfaceDark, labelColor = TextSecondary)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = qtdItem, onValueChange = { qtdItem = it }, placeholder = { Text("Qtd", color = TextSecondary) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = precoItem, onValueChange = { precoItem = it }, placeholder = { Text("Preço (€)", color = TextSecondary) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
            }

            Text("Escolher Loja:", color = TextSecondary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lojasPortuguesas.forEach { loja ->
                    val selecionada = lojaItem == loja
                    FilterChip(
                        selected = selecionada, onClick = { lojaItem = loja }, label = { Text(loja) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentStart, selectedLabelColor = BgDark, containerColor = SurfaceDark, labelColor = TextPrimary),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selecionada, borderColor = GlassBorder, selectedBorderColor = AccentStart)
                    )
                }
            }

            if (lojaItem == "Outra") {
                var lojaCustom by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(value = lojaCustom, onValueChange = { lojaCustom = it; lojaItem = it.ifBlank { "Geral" } }, placeholder = { Text("Nome da Loja...", color = TextSecondary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    if (nomeItem.isNotBlank()) {
                        viewModel.adicionarItemCompra(ItemCompra(listaId = listaRealId, nome = nomeItem, quantidade = qtdItem.ifBlank { "1" }, preco = numero(precoItem), loja = if (lojaItem.isBlank() || lojaItem == "Outra") "Geral" else lojaItem, categoria = categoriaItem))
                        nomeItem = ""; qtdItem = "1"; precoItem = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adicionar Artigo", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Artigos na Lista", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            val compradosAgora = viewModel.itensCompra.filter { it.listaId == listaRealId && it.comprado }
            if (compradosAgora.isNotEmpty()) {
                TextButton(onClick = {
                    compradosAgora.forEach { viewModel.apagarCompra(it) }
                    Toast.makeText(contextoApp, "${compradosAgora.size} artigo(s) removido(s).", Toast.LENGTH_SHORT).show()
                }) { Text("Limpar comprados", color = AccentStart) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val meusItens = viewModel.itensCompra.filter { it.listaId == listaRealId }
        if (meusItens.isEmpty()) {
            Text("Ainda sem artigos adicionados.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            if (modoSupermercado) {
                val itensAgrupados = meusItens.groupBy { it.categoria }
                itensAgrupados.forEach { (categoria, itens) ->
                    Text(categoria.uppercase(), color = AccentStart, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itens.forEach { item ->
                            val alpha by animateFloatAsState(targetValue = if (item.comprado) 0.4f else 1f, label = "")
                            Row(
                                modifier = Modifier.fillMaxWidth().animateContentSize().alpha(alpha).background(SurfaceDark, RoundedCornerShape(14.dp)).border(if(item.comprado) 0.dp else 1.dp, AccentStart, RoundedCornerShape(14.dp)).padding(16.dp).clickable { viewModel.alternarEstadoCompra(item) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = item.comprado, onCheckedChange = { viewModel.alternarEstadoCompra(item) }, colors = CheckboxDefaults.colors(checkedColor = AccentStart))
                                Text(text = "${item.quantidade}x ${item.nome}", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, textDecoration = if (item.comprado) TextDecoration.LineThrough else TextDecoration.None, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    meusItens.forEach { item ->
                        val alpha by animateFloatAsState(targetValue = if (item.comprado) 0.4f else 1f, label = "")
                        Row(
                            modifier = Modifier.fillMaxWidth().animateContentSize().alpha(alpha).background(SurfaceDark, RoundedCornerShape(14.dp)).border(0.5.dp, GlassBorder, RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = item.comprado, onCheckedChange = { viewModel.alternarEstadoCompra(item) }, colors = CheckboxDefaults.colors(checkedColor = AccentStart))
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(text = item.nome, color = TextPrimary, fontWeight = FontWeight.Medium, textDecoration = if (item.comprado) TextDecoration.LineThrough else TextDecoration.None)
                                val infoExtra = mutableListOf("Qtd: ${item.quantidade}", item.categoria)
                                if (item.preco != null) infoExtra.add(String.format(Locale.getDefault(), "%.2f €", item.preco))
                                if (item.loja.isNotBlank()) infoExtra.add(item.loja)
                                Text(text = infoExtra.joinToString(" • "), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { itemParaEditar = item; editNome = item.nome; editQtd = item.quantidade; editPreco = item.preco?.toString() ?: ""; editLoja = item.loja }) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = AccentStart)
                                }
                                if (item.comprado) {
                                    IconButton(onClick = { itemParaTransferir = item }) { Icon(imageVector = Icons.Default.MoveToInbox, contentDescription = null, tint = AccentStart) }
                                }
                                IconButton(onClick = { viewModel.apagarCompra(item) }) { Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = TextSecondary) }
                            }
                        }
                    }
                }
            }
        }

        itemParaEditar?.let { selItemParaEditar ->
            AlertDialog(
                onDismissRequest = { itemParaEditar = null }, containerColor = SurfaceDark,
                title = { Text("Editar Artigo", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = editNome, onValueChange = { editNome = it }, label = { Text("Nome", color = TextSecondary) }, shape = RoundedCornerShape(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = editQtd, onValueChange = { editQtd = it }, label = { Text("Qtd", color = TextSecondary) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                            OutlinedTextField(value = editPreco, onValueChange = { editPreco = it }, label = { Text("Preço (€)", color = TextSecondary) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                        }
                        OutlinedTextField(value = editLoja, onValueChange = { editLoja = it }, label = { Text("Loja", color = TextSecondary) }, shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (editNome.isNotBlank()) {
                            viewModel.atualizarItemCompra(selItemParaEditar.copy(nome = editNome, quantidade = editQtd.ifBlank { "1" }, preco = numero(editPreco), loja = editLoja.ifBlank { "Geral" }))
                        }
                        itemParaEditar = null
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { itemParaEditar = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        itemParaTransferir?.let { selItemParaTransferir ->
            val minhasArcas = viewModel.getMinhasArcas()
            AlertDialog(
                onDismissRequest = { itemParaTransferir = null }, containerColor = SurfaceDark,
                title = { Text("Guardar onde?", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    if (minhasArcas.isEmpty()) { Text("Não tens equipamentos criados.", color = TextSecondary) } else {
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(minhasArcas) { arca ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        viewModel.adicionarItem(ItemArca(arcaId = arca.id, nome = selItemParaTransferir.nome, quantidade = selItemParaTransferir.quantidade.toIntOrNull() ?: 1, categoria = selItemParaTransferir.categoria, preco = selItemParaTransferir.preco))
                                        Toast.makeText(contextoApp, "Transferido para ${arca.nome}", Toast.LENGTH_SHORT).show()
                                        itemParaTransferir = null
                                    }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val iconVector = when(arca.tipo) {
                                        "Frigorífico" -> Icons.Default.Kitchen
                                        "Despensa" -> Icons.Default.Home
                                        else -> Icons.Default.AcUnit
                                    }
                                    Icon(imageVector = iconVector, contentDescription = null, tint = AccentStart, modifier = Modifier.size(20.dp).padding(end = 8.dp))
                                    Text(arca.nome, color = TextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { itemParaTransferir = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun EcraGastos(viewModel: ArcaViewModel) {
    val minhasListas = viewModel.getMinhasListas()
    val minhasArcas = viewModel.getMinhasArcas()

    val totalGeral = minhasListas.sumOf { lista ->
        if (lista.valorGlobal != null) lista.valorGlobal.toDouble()
        else viewModel.itensCompra.filter { it.listaId == lista.id }.sumOf { (it.preco ?: 0f).toDouble() }
    }.toFloat()

    val gastosPorEquipamento = minhasArcas.associate { arca ->
        arca.nome to viewModel.listaItens.filter { it.arcaId == arca.id }.sumOf { ((it.preco ?: 0f) * it.quantidade).toDouble() }.toFloat()
    }

    val idsMinhasListas = minhasListas.map { it.id }
    val meusItensCompra = viewModel.itensCompra.filter { idsMinhasListas.contains(it.listaId) }
    val itensFrequentes = viewModel.listaItens.groupBy { it.nome }.mapValues { it.value.sumOf { item -> item.quantidade } }.entries.sortedByDescending { it.value }.take(5)

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Gastos e stock", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total gasto em compras", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = String.format(Locale.getDefault(), "%.2f €", totalGeral), color = AccentStart, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.displaySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Produtos com mais stock", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (itensFrequentes.isEmpty()) {
                    Text("Ainda sem dados suficientes de consumo.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    itensFrequentes.forEach { (nome, qtd) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("• $nome", color = TextSecondary)
                            Text("$qtd un.", color = AccentStart, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Valor por espaço", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        gastosPorEquipamento.forEach { (nomeEquip, valorStock) ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(nomeEquip, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(String.format(Locale.getDefault(), "%.2f €", valorStock), color = AccentStart, fontWeight = FontWeight.Bold)
                    }
                    val maximoEquip = gastosPorEquipamento.values.maxOrNull() ?: 0f
                    if (maximoEquip > 0f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { valorStock / maximoEquip },
                            color = AccentStart, trackColor = BgDark,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = ErrorColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Desperdício (expirados)", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (viewModel.desperdicios.isEmpty()) {
                    Text("Parabéns! Nenhum produto desperdiçado por validade.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Total de itens perdidos: ${viewModel.desperdicios.size}", color = ErrorColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    viewModel.desperdicios.take(5).forEach { d ->
                        val dataExp = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(d.dataExpiracao))
                        Text("• ${d.nomeItem} (Expirou a $dataExp)", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraConfiguracoes(viewModel: ArcaViewModel) {
    var emailPartilha by rememberSaveable { mutableStateOf("") }
    var arcaParaPartilhar by remember { mutableStateOf<Arca?>(null) }
    var mostrarConfirmacaoDesperdicio by rememberSaveable { mutableStateOf(false) }
    var mostrarConfirmacaoApagarConta by rememberSaveable { mutableStateOf(false) }
    var arcaParaApagar by remember { mutableStateOf<Arca?>(null) }

    val contextoApp = LocalContext.current
    val minhasArcas = viewModel.getMinhasArcas()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Sessão: ${viewModel.utilizadorAtualEmail}", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.logout() }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark, contentColor = ErrorColor)) { Text("Sair") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Notificações de Validade", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("O texto fica vermelho quando faltam poucos dias.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Avisar com antecedência:", color = TextSecondary, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (viewModel.diasAvisoValidade > 1) viewModel.guardarDiasAviso(contextoApp, viewModel.diasAvisoValidade - 1) }) { Icon(Icons.Default.Remove, tint = AccentStart, contentDescription = null) }
                        Text("${viewModel.diasAvisoValidade} dias", color = TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                        IconButton(onClick = { if (viewModel.diasAvisoValidade < 30) viewModel.guardarDiasAviso(contextoApp, viewModel.diasAvisoValidade + 1) }) { Icon(Icons.Default.Add, tint = AccentStart, contentDescription = null) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        var aProcurar by remember { mutableStateOf(false) }
        var encontrada by remember { mutableStateOf<NovaVersao?>(null) }
        val scopeAtualizacao = rememberCoroutineScope()
        Card(
            modifier = Modifier.fillMaxWidth().border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Versão ${BuildConfig.VERSION_NAME}", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Atualiza aqui sem perder dados.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(enabled = !aProcurar, onClick = {
                    aProcurar = true
                    scopeAtualizacao.launch {
                        val nova = procurarAtualizacao(); aProcurar = false
                        if (nova != null) encontrada = nova else Toast.makeText(contextoApp, "Já tens a versão mais recente.", Toast.LENGTH_SHORT).show()
                    }
                }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentStart.copy(alpha = 0.16f), contentColor = AccentStart)) {
                    Text(if (aProcurar) "A procurar…" else "Procurar atualização")
                }
            }
        }
        encontrada?.let { DialogoAtualizacao(it) { encontrada = null } }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Gestão de Dados", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val texto = viewModel.gerarTextoInventario()
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, texto)
                        type = "text/plain"
                    }
                    try { contextoApp.startActivity(Intent.createChooser(sendIntent, "Exportar Inventário")) } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark), border = BorderStroke(1.dp, AccentStart), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.IosShare, contentDescription = null, tint = AccentStart)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Exportar Stock", color = TextPrimary)
            }

            Button(
                onClick = { mostrarConfirmacaoDesperdicio = true },
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark), border = BorderStroke(1.dp, ErrorColor), shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ErrorColor)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Limpar Perdas", color = ErrorColor)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Gerir Partilhas de Espaços", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (minhasArcas.isEmpty()) {
            Text("Ainda não tens espaços criados.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 250.dp)) {
                items(minhasArcas) { arca ->
                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize().border(0.5.dp, GlassBorder, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val iconVector = when(arca.tipo) {
                                        "Frigorífico" -> Icons.Default.Kitchen
                                        "Despensa" -> Icons.Default.Home
                                        else -> Icons.Default.AcUnit
                                    }
                                    Icon(imageVector = iconVector, contentDescription = null, tint = AccentStart, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                                    Text(arca.nome, fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                                }
                                if (arca.criador == viewModel.utilizadorAtualEmail) {
                                    if (arca.membros.isNotEmpty()) Text("Partilhado com: ${arca.membros}", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else {
                                    Text("Partilhado por: ${arca.criador}", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (arca.criador == viewModel.utilizadorAtualEmail) {
                                    IconButton(onClick = { arcaParaPartilhar = arca }) { Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = AccentStart) }
                                }
                                IconButton(onClick = { arcaParaApagar = arca }) {
                                    Icon(
                                        imageVector = if (arca.criador == viewModel.utilizadorAtualEmail) Icons.Default.DeleteOutline else Icons.AutoMirrored.Filled.ExitToApp,
                                        tint = ErrorColor, contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Privacidade e Suporte", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth().clickable {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:suporte@freezerapp.com")
                putExtra(Intent.EXTRA_SUBJECT, "Feedback sobre a App Freezer")
            }
            try { contextoApp.startActivity(emailIntent) } catch (_: Exception) { Toast.makeText(contextoApp, "Sem app de email instalada.", Toast.LENGTH_SHORT).show() }
        }.border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MailOutline, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Ajuda e Feedback", color = TextPrimary, fontWeight = FontWeight.Medium)
                    Text("Reportar um erro ou sugerir funcionalidade", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth().clickable { mostrarConfirmacaoApagarConta = true }.border(0.5.dp, ErrorColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonRemove, contentDescription = null, tint = ErrorColor)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Apagar a Minha Conta", color = ErrorColor, fontWeight = FontWeight.Medium)
                    Text("Remover os teus dados permanentemente", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        arcaParaApagar?.let { selArcaParaApagar ->
            val souCriador = selArcaParaApagar.criador == viewModel.utilizadorAtualEmail
            AlertDialog(
                onDismissRequest = { arcaParaApagar = null }, containerColor = SurfaceDark,
                title = { Text(if (souCriador) "Apagar espaço?" else "Sair do espaço?", color = ErrorColor, fontWeight = FontWeight.Bold) },
                text = { Text(if (souCriador) "O espaço ${selArcaParaApagar.nome} e todos os artigos que contém serão apagados para todos os membros." else "Deixas de ter acesso a ${selArcaParaApagar.nome}.", color = TextPrimary) },
                confirmButton = {
                    Button(onClick = { viewModel.apagarOuSairArca(selArcaParaApagar); arcaParaApagar = null }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text(if (souCriador) "Apagar" else "Sair", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { arcaParaApagar = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        if (mostrarConfirmacaoDesperdicio) {
            AlertDialog(
                onDismissRequest = { mostrarConfirmacaoDesperdicio = false }, containerColor = SurfaceDark,
                title = { Text("Atenção", color = ErrorColor, fontWeight = FontWeight.Bold) },
                text = { Text("Tens a certeza que queres limpar todo o teu histórico de desperdícios? Esta ação não pode ser desfeita.", color = TextPrimary) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.limparDesperdicios()
                        mostrarConfirmacaoDesperdicio = false
                        Toast.makeText(contextoApp, "Histórico limpo!", Toast.LENGTH_SHORT).show()
                    }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Limpar", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { mostrarConfirmacaoDesperdicio = false }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        if (mostrarConfirmacaoApagarConta) {
            AlertDialog(
                onDismissRequest = { mostrarConfirmacaoApagarConta = false }, containerColor = SurfaceDark,
                title = { Text("Apagar Conta", color = ErrorColor, fontWeight = FontWeight.Bold) },
                text = { Text("Isto irá apagar permanentemente o teu acesso e os dados que criaste (a menos que tenhas partilhado arcas com alguém). Confirma a eliminação?", color = TextPrimary) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.apagarContaDefinitivamente { sucesso ->
                            if (!sucesso) Toast.makeText(contextoApp, "Erro. Tenta fazer logout e login de novo.", Toast.LENGTH_LONG).show()
                        }
                        mostrarConfirmacaoApagarConta = false
                    }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Apagar para sempre", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { mostrarConfirmacaoApagarConta = false }) { Text("Cancelar", color = TextSecondary) } }
            )
        }

        arcaParaPartilhar?.let { selArcaParaPartilhar ->
            AlertDialog(
                onDismissRequest = { arcaParaPartilhar = null }, containerColor = SurfaceDark,
                title = { Text("Partilhar '${selArcaParaPartilhar.nome}'", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Email do utilizador:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(value = emailPartilha, onValueChange = { emailPartilha = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (emailPartilha.isNotBlank()) {
                            viewModel.partilharArcaIndividual(selArcaParaPartilhar, emailPartilha.trim())
                            emailPartilha = ""; arcaParaPartilhar = null
                            Toast.makeText(contextoApp, "Arca partilhada com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = BgDark)) { Text("Convidar") }
                },
                dismissButton = { TextButton(onClick = { arcaParaPartilhar = null }) { Text("Cancelar", color = TextSecondary) } }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun DesenhoArcaTech(percentagem: Float, volumeOcupado: Float, capacidadeTotal: Float) {
    val anim by animateFloatAsState(targetValue = percentagem.coerceIn(0f, 1f), animationSpec = tween(900), label = "")
    val cor = when { percentagem >= 0.85f -> ErrorColor; percentagem >= 0.7f -> WarnColor; else -> AccentStart }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SurfaceDark).border(1.dp, GlassBorder, RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("Ocupação", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text("${(percentagem * 100).toInt()}%", color = cor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            }
            Text("${volumeOcupado.toInt()} L de ${capacidadeTotal.toInt()} L", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(BgDark)) {
            Box(modifier = Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(
                if (percentagem >= 0.7f) Brush.horizontalGradient(listOf(cor, cor)) else AestheticGradient))
        }
    }
}

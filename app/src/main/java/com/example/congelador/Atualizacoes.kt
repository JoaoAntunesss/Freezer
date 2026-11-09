package com.example.congelador

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/*
 * Atualizações dentro da app.
 * Cada versão nova é publicada no ramo "atualizacoes" do repositório JoaoAntunesss/Freezer:
 * versao.json ({"versao": "1.3", "notas": "...", "apk": "freezer.apk"}) e o APK ao lado.
 * A app lê o versao.json, compara com a versão instalada e, se houver mais recente, descarrega e instala o APK.
 * Os dados estão no Firebase, por isso atualizar não apaga nada.
 */

private const val BASE_ATUALIZACOES = "https://raw.githubusercontent.com/JoaoAntunesss/Freezer/atualizacoes/"

data class NovaVersao(val versao: String, val notas: String, val urlApk: String, val tamanho: Long)

/** "v1.10" > "v1.9": compara número a número. */
fun versaoMaisRecente(remota: String, local: String): Boolean {
    fun partes(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
    val a = partes(remota); val b = partes(local)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

suspend fun procurarAtualizacao(): NovaVersao? = withContext(Dispatchers.IO) {
    try {
        val conn = URL(BASE_ATUALIZACOES + "versao.json?t=" + System.currentTimeMillis() / 60_000).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000; conn.useCaches = false
        if (conn.responseCode != 200) return@withContext null
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val versao = json.optString("versao")
        if (!versaoMaisRecente(versao, BuildConfig.VERSION_NAME)) return@withContext null
        NovaVersao(versao, json.optString("notas").trim(), BASE_ATUALIZACOES + json.optString("apk", "freezer.apk"), json.optLong("tamanho"))
    } catch (_: Exception) { null }
}

private suspend fun descarregarApk(context: Context, url: String, progresso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
    val pasta = File(context.cacheDir, "atualizacoes").apply { mkdirs() }
    val destino = File(pasta, "freezer.apk")
    var ligacao = URL(url).openConnection() as HttpURLConnection
    var saltos = 0
    while (ligacao.responseCode in 300..399 && saltos++ < 5) {   // o GitHub redireciona para outro servidor
        val proximo = ligacao.getHeaderField("Location"); ligacao.disconnect()
        ligacao = URL(proximo).openConnection() as HttpURLConnection
    }
    if (ligacao.responseCode != 200) throw Exception("Erro ${ligacao.responseCode} ao descarregar")
    val total = ligacao.contentLengthLong
    ligacao.inputStream.use { entrada ->
        destino.outputStream().use { saida ->
            val buf = ByteArray(64 * 1024); var lidos = 0L; var n: Int
            while (entrada.read(buf).also { n = it } >= 0) {
                saida.write(buf, 0, n); lidos += n
                if (total > 0) withContext(Dispatchers.Main) { progresso(lidos.toFloat() / total) }
            }
        }
    }
    destino
}

private fun instalarApk(context: Context, apk: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun podeInstalar(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

/** Diálogo "Nova versão": mostra as novidades, descarrega com barra de progresso e abre o instalador do Android. */
@Composable
fun DialogoAtualizacao(nova: NovaVersao, onFechar: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progresso by remember { mutableStateOf<Float?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }
    var precisaPermissao by remember { mutableStateOf(false) }
    var apkPronto by remember { mutableStateOf<File?>(null) }

    // Ao voltar das definições de "instalar apps desconhecidas", continua sozinho.
    val dono = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && precisaPermissao && podeInstalar(context)) {
                precisaPermissao = false; apkPronto?.let { instalarApk(context, it) }
            }
        }
        dono.lifecycle.addObserver(obs); onDispose { dono.lifecycle.removeObserver(obs) }
    }

    AlertDialog(
        onDismissRequest = { if (progresso == null) onFechar() },
        title = { Text("Nova versão ${nova.versao}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tens a ${BuildConfig.VERSION_NAME}. Os teus dados ficam todos, estão guardados na nuvem.")
                if (nova.notas.isNotBlank()) Text(nova.notas, style = MaterialTheme.typography.bodySmall)
                progresso?.let {
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                    Text("A descarregar… ${(it * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                if (precisaPermissao) Text("Falta autorizar o Freezer a instalar atualizações. Ativa a opção e volta à app.", color = MaterialTheme.colorScheme.secondary)
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = progresso == null || apkPronto != null, onClick = {
                val pronto = apkPronto
                if (pronto != null) {
                    if (podeInstalar(context)) instalarApk(context, pronto) else pedirPermissao(context).also { precisaPermissao = true }
                    return@Button
                }
                erro = null; progresso = 0f
                scope.launch {
                    try {
                        val apk = descarregarApk(context, nova.urlApk) { progresso = it }
                        apkPronto = apk; progresso = 1f
                        if (podeInstalar(context)) instalarApk(context, apk) else { precisaPermissao = true; pedirPermissao(context) }
                    } catch (e: Exception) {
                        progresso = null; erro = "Não foi possível descarregar: ${e.message}"
                    }
                }
            }) { Text(if (apkPronto != null) "Instalar" else "Atualizar") }
        },
        dismissButton = { if (progresso == null) TextButton(onClick = onFechar) { Text("Mais tarde", color = Color.Gray) } }
    )
}

private fun pedirPermissao(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

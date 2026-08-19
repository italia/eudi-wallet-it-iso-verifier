package it.infocert.etuitus.sampleandroid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import it.infocert.proximityverification.core.catalog.DocumentTypeCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RequestConfigScreen(
    catalog: DocumentTypeCatalog,
    requestConfig: RequestConfig,
    onConfigChanged: (RequestConfig) -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var registeredConfigs by remember {
        mutableStateOf(catalog.getRegisteredConfigurations())
    }
    var showJsonDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    fun refreshCatalog() {
        registeredConfigs = catalog.getRegisteredConfigurations()
        onConfigChanged(RequestConfig.mergeWithCatalog(registeredConfigs, requestConfig))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Catalog Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showJsonDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Load JSON") }
                    OutlinedButton(
                        onClick = { showUrlDialog = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Load from URL") }
                }
            }

            if (registeredConfigs.isEmpty()) {
                item {
                    Text(
                        "No document types registered",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(registeredConfigs, key = { it.docType }) { config ->
                    RegisteredDocTypeRow(
                        config = config,
                        onUnregister = {
                            catalog.unregister(config.docType)
                            refreshCatalog()
                            scope.launch { snackbarHostState.showSnackbar("Removed: ${config.docType}") }
                        }
                    )
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 4.dp)) }

            item {
                Text(
                    "Request Builder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                PortraitCorroborationPresetCard(
                    request = PortraitCorroboration.resolveRequest(
                        config = requestConfig,
                        registeredDocTypes = registeredConfigs.map { it.docType }.toSet()
                    ),
                    onClearIntent = {
                        onConfigChanged(PortraitCorroboration.clearIntent(requestConfig))
                        scope.launch { snackbarHostState.showSnackbar("Corroboration declaration cleared") }
                    },
                    onApply = {
                        when (
                            val result = PortraitCorroboration.applyPreset(
                                config = requestConfig,
                                registeredConfigs = registeredConfigs
                            )
                        ) {
                            is PortraitCorroboration.PresetResult.Applied -> {
                                onConfigChanged(result.config)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Corroborating ${result.shape.primaryDocTypes.joinToString(", ")} " +
                                            "with the mDL portrait"
                                    )
                                }
                            }

                            is PortraitCorroboration.PresetResult.NotApplicable -> scope.launch {
                                snackbarHostState.showSnackbar(result.reason)
                            }
                        }
                    }
                )
            }

            items(registeredConfigs, key = { "req_${it.docType}" }) { credConfig ->
                val docTypeConfig: DocTypeSelectionConfig =
                    requestConfig.docTypeConfigs[credConfig.docType]
                        ?: RequestConfig.defaultForDocType(credConfig)
                DocTypeRequestCard(
                    credentialConfig = credConfig,
                    selectionConfig = docTypeConfig,
                    onSelectionChanged = { updated ->
                        val newMap: MutableMap<String, DocTypeSelectionConfig> =
                            requestConfig.docTypeConfigs.toMutableMap()
                        newMap[credConfig.docType] = updated
                        onConfigChanged(requestConfig.copy(docTypeConfigs = newMap))
                    }
                )
            }

            item {
                val enabledCount: Int = requestConfig.docTypeConfigs.count { (docType, cfg) ->
                    cfg.enabled && registeredConfigs.any { it.docType == docType }
                }
                val claimCount: Int = requestConfig.docTypeConfigs.entries
                    .filter { (docType, cfg) -> cfg.enabled && registeredConfigs.any { it.docType == docType } }
                    .sumOf { (_, cfg) -> cfg.claimSelections.count { it.value.selected } }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "$enabledCount document(s), $claimCount total claims selected",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showJsonDialog) {
        JsonInputDialog(
            onDismiss = { showJsonDialog = false },
            onSubmit = { jsonStr ->
                showJsonDialog = false
                try {
                    catalog.registerFromJson(jsonStr)
                    refreshCatalog()
                    scope.launch { snackbarHostState.showSnackbar("Loaded successfully") }
                } catch (e: Exception) {
                    scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") }
                }
            }
        )
    }

    if (showUrlDialog) {
        UrlInputDialog(
            onDismiss = { showUrlDialog = false },
            onSubmit = { url ->
                showUrlDialog = false
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            catalog.registerFromIssuerMetadata(url)
                        }
                        refreshCatalog()
                        snackbarHostState.showSnackbar("Loaded from issuer metadata")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Error: ${e.message}")
                    }
                }
            }
        )
    }
}

/**
 * Preset per la corroborazione del ritratto: imposta in un tocco la richiesta a due `DocRequest`
 * prescritta dal design (credenziale selezionata con tutti i claim + mDL con il solo `portrait`) e
 * **dichiara l'intenzione**.
 *
 * Dichiararla, invece di inferirla ogni volta dalla forma della richiesta, è ciò che permette di
 * accorgersi quando la forma non è più quella attesa: la card lo dice in rosso invece di tornare
 * silenziosamente a "non configurato".
 */
@Composable
private fun PortraitCorroborationPresetCard(
    request: PortraitCorroboration.Request,
    onApply: () -> Unit,
    onClearIntent: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Portrait corroboration", fontWeight = FontWeight.SemiBold)
            Text(
                "Requests the selected credential in full plus the mDL for its portrait only, " +
                    "for credentials that carry no photo of their own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onApply) { Text("Corroborate portrait") }
                if (request !is PortraitCorroboration.Request.NotRequested) {
                    TextButton(onClick = onClearIntent) { Text("Clear") }
                }
            }
            when (request) {
                is PortraitCorroboration.Request.NotRequested -> Text(
                    "Not declared",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is PortraitCorroboration.Request.Declared -> Text(
                    "Declared: ${request.shape.primaryDocTypes.joinToString(", ")} " +
                        "+ ${request.shape.corroboratingDocType} (portrait only)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                is PortraitCorroboration.Request.DeclaredButUnrecognised -> Text(
                    "Declared, but the request no longer has the expected shape: ${request.reason}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RegisteredDocTypeRow(
    config: CredentialConfiguration,
    onUnregister: () -> Unit
) {
    val displayName: String = config.display.firstOrNull()?.name ?: config.docType
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(config.docType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${config.claims.size} claims", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onUnregister) {
                Icon(Icons.Default.Delete, contentDescription = "Unregister", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DocTypeRequestCard(
    credentialConfig: CredentialConfiguration,
    selectionConfig: DocTypeSelectionConfig,
    onSelectionChanged: (DocTypeSelectionConfig) -> Unit
) {
    val displayName: String = credentialConfig.display.firstOrNull()?.name ?: credentialConfig.docType

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        credentialConfig.docType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = selectionConfig.enabled,
                    onCheckedChange = { enabled -> onSelectionChanged(selectionConfig.copy(enabled = enabled)) }
                )
            }

            AnimatedVisibility(visible = selectionConfig.enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = {
                            val allSelected: Map<String, ClaimSelectionEntry> = credentialConfig.claims.associate { claim ->
                                val key: String = RequestConfig.claimKey(claim.ns, claim.identifier)
                                key to (selectionConfig.claimSelections[key] ?: ClaimSelectionEntry()).copy(selected = true)
                            }
                            onSelectionChanged(selectionConfig.copy(claimSelections = allSelected))
                        }) { Text("All") }

                        TextButton(onClick = {
                            val mandatoryOnly: Map<String, ClaimSelectionEntry> = credentialConfig.claims.associate { claim ->
                                val key: String = RequestConfig.claimKey(claim.ns, claim.identifier)
                                key to (selectionConfig.claimSelections[key] ?: ClaimSelectionEntry()).copy(selected = claim.isMandatory)
                            }
                            onSelectionChanged(selectionConfig.copy(claimSelections = mandatoryOnly))
                        }) { Text("Mandatory") }

                        TextButton(onClick = {
                            val cleared: Map<String, ClaimSelectionEntry> = credentialConfig.claims.associate { claim ->
                                val key: String = RequestConfig.claimKey(claim.ns, claim.identifier)
                                key to (selectionConfig.claimSelections[key] ?: ClaimSelectionEntry()).copy(selected = false)
                            }
                            onSelectionChanged(selectionConfig.copy(claimSelections = cleared))
                        }) { Text("Clear") }
                    }

                    val byNamespace: Map<String, List<it.infocert.proximityverification.core.catalog.ClaimDescriptor>> =
                        credentialConfig.claims.groupBy { it.ns }
                    byNamespace.forEach { (namespace, claims) ->
                        Text(
                            namespace,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        claims.forEach { claim ->
                            val key: String = RequestConfig.claimKey(claim.ns, claim.identifier)
                            val entry: ClaimSelectionEntry = selectionConfig.claimSelections[key] ?: ClaimSelectionEntry()
                            val claimDisplayName: String = claim.display.firstOrNull()?.name ?: claim.identifier

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = entry.selected,
                                    onCheckedChange = { checked ->
                                        val updated: MutableMap<String, ClaimSelectionEntry> =
                                            selectionConfig.claimSelections.toMutableMap()
                                        updated[key] = entry.copy(selected = checked)
                                        onSelectionChanged(selectionConfig.copy(claimSelections = updated))
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(claimDisplayName, style = MaterialTheme.typography.bodyMedium)
                                    if (claim.isMandatory) {
                                        Text(
                                            "mandatory",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (entry.selected) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Retain", style = MaterialTheme.typography.labelSmall)
                                        Switch(
                                            checked = entry.intentToRetain,
                                            onCheckedChange = { retain ->
                                                val updated: MutableMap<String, ClaimSelectionEntry> =
                                                    selectionConfig.claimSelections.toMutableMap()
                                                updated[key] = entry.copy(intentToRetain = retain)
                                                onSelectionChanged(selectionConfig.copy(claimSelections = updated))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load from JSON") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Paste credential metadata JSON") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text("Load") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UrlInputDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load from Issuer URL") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Issuer base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(url) }, enabled = url.isNotBlank()) { Text("Load") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

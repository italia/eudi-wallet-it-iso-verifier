package it.infocert.etuitus.sampleandroid

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.infocert.proximityverification.core.models.ReaderCertificateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderCertificateBadgeCard(cert: ReaderCertificateInfo, onClick: () -> Unit) {
    val valid = !cert.isExpired
    val container =
        if (valid) Color(0xFFC8E6C9) // green container (active)
        else Color(0xFFFFE0B2) // amber container (warning)
    val onContainer =
        if (valid) Color(0xFF1B5E20) // green-on (active)
        else Color(0xFF6D4C00) // amber-on (warning)
    Surface(
        onClick = onClick,
        color = container,
        contentColor = onContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (valid) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (valid) "Reader certificate active" else "Reader certificate expired",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${cert.subjectCommonName ?: cert.subjectDn} · tap for details",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlin.time.ExperimentalTime::class)
@Composable
fun ReaderCertificateSheet(cert: ReaderCertificateInfo, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Reader certificate",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            CertRow("Subject", cert.subjectDn)
            CertRow("Issuer", cert.issuerDn)
            CertRow("Valid from", cert.notBefore.toString())
            CertRow("Valid until", cert.notAfter.toString())
            CertRow("Status", if (cert.isExpired) "Expired" else "Valid")
            CertRow("Serial", cert.serialNumber)
            CertRow("SHA-256", cert.sha256Fingerprint)
            Text(
                "PEM",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = cert.pem,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(12.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(cert.pem))
                    Toast.makeText(context, "PEM copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy PEM") }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(cert.sha256Fingerprint))
                    Toast.makeText(context, "Fingerprint copied", Toast.LENGTH_SHORT).show()
                }) { Text("Copy fingerprint") }
            }
        }
    }
}

@Composable
private fun CertRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

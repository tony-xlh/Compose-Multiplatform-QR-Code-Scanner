package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import qrcodescanner.composeapp.generated.resources.Res
import qrcodescanner.composeapp.generated.resources.compose_multiplatform

@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        var barcodeResult by remember {mutableStateOf("")}
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                showContent = !showContent
                if (showContent) {
                    barcodeResult = ""
                }
            }) {
                Text("Toggle Scanner")
            }
            Text(barcodeResult)
            if (showContent) {
                val scope = rememberCoroutineScope()
                ScannerWithPermissions(
                    modifier = Modifier.padding(16.dp),
                    onScanned = {
                        scope.launch {
                            println(it)
                            barcodeResult = it
                        }
                    },
                )
            }
        }
    }
}
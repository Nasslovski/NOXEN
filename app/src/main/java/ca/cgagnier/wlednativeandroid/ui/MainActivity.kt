package ca.cgagnier.wlednativeandroid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import ca.cgagnier.wlednativeandroid.FileUploadContract
import ca.cgagnier.wlednativeandroid.FileUploadContractResult
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainActivity"
private const val REQUEST_LOCATION_AND_WIFI_PERMS = 1001

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var versionWithAssetsRepository: VersionWithAssetsRepository

    // For WebView file upload support
    var uploadMessage: ValueCallback<Array<Uri>>? = null
    val fileUpload =
        registerForActivityResult(FileUploadContract()) { result: FileUploadContractResult ->
            uploadMessage?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(
                    result.resultCode,
                    result.intent
                )
            )
            uploadMessage = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            WLEDNativeTheme {
                MainNavHost()
            }
        }

        // Pedimos permisos necesarios para poder leer el SSID / WiFi
        ensureLocationAndWifiPermissions()

        updateDeviceVersionList()
    }

    override fun onResume() {
        super.onResume()

        // DEBUG: detectar si estamos conectados al AP del WLED
        if (isConnectedToWledAp(this)) {
            Toast.makeText(this, "Conectado al AP de WLED", Toast.LENGTH_SHORT).show()
            // Más adelante aquí abriremos directamente el dispositivo 4.3.2.1
        } else {
            // Opcional para debug:
            // Toast.makeText(this, "No estás en WLED-AP", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Pide permisos de ubicación y de WiFi cercanos (Android 13+) si aún no se han concedido.
     */
    private fun ensureLocationAndWifiPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Necesario para poder leer el SSID en Android 10+
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Para Android 13+ se recomienda pedir NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_LOCATION_AND_WIFI_PERMS
            )
        }
    }

    /**
     * Checks for device updates once in a while
     */
    private fun updateDeviceVersionList() {
        lifecycleScope.launch(Dispatchers.IO) {
            userPreferencesRepository.lastUpdateCheckDate.collect {
                val now = System.currentTimeMillis()
                if (now < it) {
                    Log.i(TAG, "Not updating version list since it was done recently.")
                    return@collect
                }
                val releaseService = ReleaseService(versionWithAssetsRepository)
                releaseService.refreshVersions(applicationContext.cacheDir)
                // Set the next date to check in minimum 24 hours from now.
                userPreferencesRepository.updateLastUpdateCheckDate(now + (24 * 60 * 60 * 1000))
            }
        }
    }
}

package com.programmingtools.app

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.View
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.provider.ContactsContract
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.DateFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    enum class ScreenMode(@param:StringRes val labelResId: Int) {
        CREATE(R.string.mode_create),
        SCAN(R.string.mode_scan),
        HISTORY(R.string.mode_history)
    }

    enum class ContentType(@param:StringRes val labelResId: Int) {
        TEXT(R.string.content_type_text),
        WIFI(R.string.content_type_wifi),
        PHONE(R.string.content_type_phone),
        EMAIL(R.string.content_type_email),
        SMS(R.string.content_type_sms),
        GEO(R.string.content_type_geo),
        CONTACT(R.string.content_type_contact);

        companion object {
            fun fromLocalizedLabel(value: String, resolver: (Int) -> String): ContentType {
                return entries.firstOrNull { resolver(it.labelResId) == value } ?: TEXT
            }
        }
    }

    enum class WifiSecurity(@param:StringRes val labelResId: Int, val wifiValue: String) {
        WPA_WPA2(R.string.wifi_security_wpa, "WPA"),
        WEP(R.string.wifi_security_wep, "WEP"),
        OPEN(R.string.wifi_security_open, "nopass");

        companion object {
            fun fromLocalizedLabel(value: String, resolver: (Int) -> String): WifiSecurity {
                return entries.firstOrNull { resolver(it.labelResId) == value } ?: WPA_WPA2
            }
        }
    }

    private sealed class ScanResult {
        data class Text(val value: String) : ScanResult()
        data class Url(val value: String) : ScanResult()
        data class Phone(val value: String) : ScanResult()
        data class Email(val value: String, val subject: String?, val body: String?) : ScanResult()
        data class Sms(val phoneNumber: String, val message: String?) : ScanResult()
        data class Geo(val latitude: String, val longitude: String) : ScanResult()
        data class Contact(val name: String, val phone: String?, val email: String?) : ScanResult()
        data class Wifi(
            val ssid: String,
            val security: String,
            val password: String?,
            val hidden: Boolean
        ) : ScanResult()
    }

    enum class EyeStyle(@param:StringRes val labelResId: Int) {
        CLASSIC(R.string.eye_style_classic),
        ROUNDED(R.string.eye_style_rounded),
        TARGET(R.string.eye_style_target);

        companion object {
            fun fromLocalizedLabel(value: String, resolver: (Int) -> String): EyeStyle {
                return entries.firstOrNull { resolver(it.labelResId) == value } ?: CLASSIC
            }
        }
    }

    enum class CenterBadge(@param:StringRes val labelResId: Int) {
        NONE(R.string.center_badge_none),
        DOT(R.string.center_badge_dot),
        DIAMOND(R.string.center_badge_diamond),
        SQUARE(R.string.center_badge_square);

        companion object {
            fun fromLocalizedLabel(value: String, resolver: (Int) -> String): CenterBadge {
                return entries.firstOrNull { resolver(it.labelResId) == value } ?: NONE
            }
        }
    }

    enum class DesignStyle(@param:StringRes val labelResId: Int) {
        MINIMAL(R.string.design_style_minimal),
        CARD(R.string.design_style_card),
        STICKER(R.string.design_style_sticker);

        companion object {
            fun fromLocalizedLabel(value: String, resolver: (Int) -> String): DesignStyle {
                return entries.firstOrNull { resolver(it.labelResId) == value } ?: MINIMAL
            }
        }
    }

    private enum class SaveFormat(
        @param:StringRes val labelResId: Int,
        val fileExtension: String,
        val mimeType: String,
        val compressFormat: Bitmap.CompressFormat
    ) {
        PNG(R.string.save_format_png, "png", "image/png", Bitmap.CompressFormat.PNG),
        JPEG(R.string.save_format_jpeg, "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
        WEBP(R.string.save_format_webp, "webp", "image/webp", Bitmap.CompressFormat.WEBP);

        companion object {
            fun fromLocalizedLabel(value: String, resolver: (Int) -> String): SaveFormat {
                return entries.firstOrNull { resolver(it.labelResId) == value } ?: PNG
            }
        }
    }

    companion object {
        private const val STATE_TEXT = "state_text"
        private const val STATE_SCREEN_MODE = "state_screen_mode"
        private const val STATE_CONTENT_TYPE = "state_content_type"
        private const val STATE_WIFI_SSID = "state_wifi_ssid"
        private const val STATE_WIFI_PASSWORD = "state_wifi_password"
        private const val STATE_WIFI_SECURITY = "state_wifi_security"
        private const val STATE_WIFI_HIDDEN = "state_wifi_hidden"
        private const val STATE_SIZE = "state_size"
        private const val STATE_SAVE_FORMAT = "state_save_format"
        private const val STATE_DESIGN_STYLE = "state_design_style"
        private const val STATE_EYE_STYLE = "state_eye_style"
        private const val STATE_CENTER_BADGE = "state_center_badge"
        private const val STATE_LOGO_URI = "state_logo_uri"
        private const val STATE_SAVE_TEXT = "state_save_text"
        private const val STATE_COLOR = "state_color"
        private const val STATE_BACKGROUND_COLOR = "state_background_color"
        private const val STATE_HAS_QR = "state_has_qr"
        private const val SCAN_RESULT_COOLDOWN_MS = 2_000L
    }

    private lateinit var imageViewQRCode: ImageView
    private lateinit var createModeContainer: View
    private lateinit var scanModeContainer: View
    private lateinit var historyModeContainer: View
    private lateinit var buttonModeCreate: Button
    private lateinit var buttonModeScan: Button
    private lateinit var buttonModeHistory: Button
    private lateinit var previewViewScanner: PreviewView
    private lateinit var buttonGrantCameraPermission: Button
    private lateinit var buttonResetScan: Button
    private lateinit var buttonClearHistory: Button
    private lateinit var textViewScanPermissionState: TextView
    private lateinit var textViewScanStatus: TextView
    private lateinit var scanResultContainer: View
    private lateinit var textViewScanResultType: TextView
    private lateinit var textViewScanResultValue: TextView
    private lateinit var textViewHistoryEmpty: TextView
    private lateinit var buttonScanPrimaryAction: Button
    private lateinit var buttonScanSecondaryAction: Button
    private lateinit var buttonScanUseInCreate: Button
    private lateinit var editText: EditText
    private lateinit var editTextWifiSsid: EditText
    private lateinit var editTextWifiPassword: EditText
    private lateinit var editTextPhoneNumber: EditText
    private lateinit var editTextEmailAddress: EditText
    private lateinit var editTextEmailSubject: EditText
    private lateinit var editTextEmailBody: EditText
    private lateinit var editTextSmsNumber: EditText
    private lateinit var editTextSmsMessage: EditText
    private lateinit var editTextGeoLatitude: EditText
    private lateinit var editTextGeoLongitude: EditText
    private lateinit var editTextContactName: EditText
    private lateinit var editTextContactPhone: EditText
    private lateinit var editTextContactEmail: EditText
    private lateinit var editTextSize: EditText
    private lateinit var editTextSaveText: EditText
    private lateinit var buttonPickContentType: Button
    private lateinit var buttonPickWifiSecurity: Button
    private lateinit var buttonPickWifiHidden: Button
    private lateinit var buttonPickLogo: Button
    private lateinit var buttonClearLogo: Button
    private lateinit var buttonPickEyeStyle: Button
    private lateinit var buttonPickCenterBadge: Button
    private lateinit var buttonPickDesignStyle: Button
    private lateinit var buttonPickSaveFormat: Button
    private lateinit var buttonGenerate: Button
    private lateinit var buttonSave: Button
    private lateinit var buttonShare: Button
    private lateinit var buttonPickColor: Button
    private lateinit var buttonPickBackgroundColor: Button
    private lateinit var buttonViewSample: Button
    private lateinit var viewSelectedColor: View
    private lateinit var viewSelectedBackgroundColor: View
    private lateinit var textViewSelectedContentType: TextView
    private lateinit var textViewSelectedWifiSecurity: TextView
    private lateinit var textViewSelectedWifiHidden: TextView
    private lateinit var textViewSelectedLogo: TextView
    private lateinit var textViewSelectedEyeStyle: TextView
    private lateinit var textViewSelectedCenterBadge: TextView
    private lateinit var textViewSelectedDesignStyle: TextView
    private lateinit var textViewSelectedSaveFormat: TextView
    private lateinit var textViewSelectedColor: TextView
    private lateinit var textViewSelectedBackgroundColor: TextView
    private lateinit var textViewPreviewStatus: TextView
    private lateinit var historyListContainer: LinearLayout
    private lateinit var textInputContainer: View
    private lateinit var wifiFieldsContainer: View
    private lateinit var phoneFieldsContainer: View
    private lateinit var emailFieldsContainer: View
    private lateinit var smsFieldsContainer: View
    private lateinit var geoFieldsContainer: View
    private lateinit var contactFieldsContainer: View
    private var generatedBitmap: Bitmap? = null
    private var pendingSaveBitmap: Bitmap? = null
    private var currentScreenMode: ScreenMode = ScreenMode.CREATE
    private var currentContentType: ContentType = ContentType.TEXT
    private var currentWifiSecurity: WifiSecurity = WifiSecurity.WPA_WPA2
    private var currentWifiHidden: Boolean = false
    private var currentLogoUri: Uri? = null
    private var lastScannedRawValue: String? = null
    private var lastScanTimestampMs: Long = 0L
    private var scannerPaused: Boolean = false
    private var lastScanAction: (() -> Unit)? = null
    private var lastSecondaryScanAction: (() -> Unit)? = null
    private var useInCreateAction: (() -> Unit)? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanHistoryStore: ScanHistoryStore
    private var activeWifiSuggestion: WifiNetworkSuggestion? = null
    private var currentEyeStyle: EyeStyle = EyeStyle.CLASSIC
    private var currentCenterBadge: CenterBadge = CenterBadge.NONE
    private var currentDesignStyle: DesignStyle = DesignStyle.MINIMAL
    private var currentSaveFormat: SaveFormat = SaveFormat.PNG
    private var pendingSaveFormat: SaveFormat = SaveFormat.PNG
    private var selectedColor: Int = Color.BLACK
    private var selectedBackgroundColor: Int = Color.WHITE
    private val qrCodeGenerator = QRCodeGenerator()

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val bitmapToSave = pendingSaveBitmap
            val uri = result.data?.data
            if (result.resultCode == RESULT_OK && uri != null && bitmapToSave != null) {
                saveImageToUri(bitmapToSave, uri, pendingSaveFormat)
            }
            pendingSaveBitmap = null
            pendingSaveFormat = SaveFormat.PNG
        }

    private val requestCameraPermission =
        registerForActivityResult(RequestPermission()) { granted ->
            if (granted) {
                updateScanPermissionUi(granted = true)
                if (currentScreenMode == ScreenMode.SCAN) {
                    startScanner()
                }
            } else {
                updateScanPermissionUi(granted = false)
                updateScanStatus(getString(R.string.scan_status_permission_denied))
            }
        }

    private val openLogoDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) {
                return@registerForActivityResult
            }
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers do not grant persistable permissions. Temporary access is enough.
            }
            updateSelectedLogo(uri)
            rerenderCurrentQrIfPossible()
            AppTelemetry.logEvent("logo_selected")
            announceAccessibilityMessage(getString(R.string.logo_changed_announcement))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraExecutor = Executors.newSingleThreadExecutor()
        scanHistoryStore = ScanHistoryStore(this)

        createModeContainer = findViewById(R.id.createModeContainer)
        scanModeContainer = findViewById(R.id.scanModeContainer)
        historyModeContainer = findViewById(R.id.historyModeContainer)
        buttonModeCreate = findViewById(R.id.buttonModeCreate)
        buttonModeScan = findViewById(R.id.buttonModeScan)
        buttonModeHistory = findViewById(R.id.buttonModeHistory)
        previewViewScanner = findViewById(R.id.previewViewScanner)
        buttonGrantCameraPermission = findViewById(R.id.buttonGrantCameraPermission)
        buttonResetScan = findViewById(R.id.buttonResetScan)
        buttonClearHistory = findViewById(R.id.buttonClearHistory)
        textViewScanPermissionState = findViewById(R.id.textViewScanPermissionState)
        textViewScanStatus = findViewById(R.id.textViewScanStatus)
        scanResultContainer = findViewById(R.id.scanResultContainer)
        textViewScanResultType = findViewById(R.id.textViewScanResultType)
        textViewScanResultValue = findViewById(R.id.textViewScanResultValue)
        textViewHistoryEmpty = findViewById(R.id.textViewHistoryEmpty)
        buttonScanPrimaryAction = findViewById(R.id.buttonScanPrimaryAction)
        buttonScanSecondaryAction = findViewById(R.id.buttonScanSecondaryAction)
        buttonScanUseInCreate = findViewById(R.id.buttonScanUseInCreate)
        imageViewQRCode = findViewById(R.id.imageViewQRCode)
        editText = findViewById(R.id.editTextText)
        editTextWifiSsid = findViewById(R.id.editTextWifiSsid)
        editTextWifiPassword = findViewById(R.id.editTextWifiPassword)
        editTextPhoneNumber = findViewById(R.id.editTextPhoneNumber)
        editTextEmailAddress = findViewById(R.id.editTextEmailAddress)
        editTextEmailSubject = findViewById(R.id.editTextEmailSubject)
        editTextEmailBody = findViewById(R.id.editTextEmailBody)
        editTextSmsNumber = findViewById(R.id.editTextSmsNumber)
        editTextSmsMessage = findViewById(R.id.editTextSmsMessage)
        editTextGeoLatitude = findViewById(R.id.editTextGeoLatitude)
        editTextGeoLongitude = findViewById(R.id.editTextGeoLongitude)
        editTextContactName = findViewById(R.id.editTextContactName)
        editTextContactPhone = findViewById(R.id.editTextContactPhone)
        editTextContactEmail = findViewById(R.id.editTextContactEmail)
        editTextSize = findViewById(R.id.editTextSize)
        editTextSaveText = findViewById(R.id.editTextSaveText)
        buttonPickContentType = findViewById(R.id.buttonPickContentType)
        buttonPickWifiSecurity = findViewById(R.id.buttonPickWifiSecurity)
        buttonPickWifiHidden = findViewById(R.id.buttonPickWifiHidden)
        buttonPickLogo = findViewById(R.id.buttonPickLogo)
        buttonClearLogo = findViewById(R.id.buttonClearLogo)
        buttonPickEyeStyle = findViewById(R.id.buttonPickEyeStyle)
        buttonPickCenterBadge = findViewById(R.id.buttonPickCenterBadge)
        buttonPickDesignStyle = findViewById(R.id.buttonPickDesignStyle)
        buttonPickSaveFormat = findViewById(R.id.buttonPickSaveFormat)
        buttonGenerate = findViewById(R.id.buttonGenerate)
        buttonSave = findViewById(R.id.buttonSave)
        buttonShare = findViewById(R.id.buttonShare)
        buttonPickColor = findViewById(R.id.buttonPickColor)
        buttonPickBackgroundColor = findViewById(R.id.buttonPickBackgroundColor)
        buttonViewSample = findViewById(R.id.buttonViewSample)
        viewSelectedColor = findViewById(R.id.viewSelectedColor)
        viewSelectedBackgroundColor = findViewById(R.id.viewSelectedBackgroundColor)
        textViewSelectedContentType = findViewById(R.id.textViewSelectedContentType)
        textViewSelectedWifiSecurity = findViewById(R.id.textViewSelectedWifiSecurity)
        textViewSelectedWifiHidden = findViewById(R.id.textViewSelectedWifiHidden)
        textViewSelectedLogo = findViewById(R.id.textViewSelectedLogo)
        textViewSelectedEyeStyle = findViewById(R.id.textViewSelectedEyeStyle)
        textViewSelectedCenterBadge = findViewById(R.id.textViewSelectedCenterBadge)
        textViewSelectedDesignStyle = findViewById(R.id.textViewSelectedDesignStyle)
        textViewSelectedSaveFormat = findViewById(R.id.textViewSelectedSaveFormat)
        textViewSelectedColor = findViewById(R.id.textViewSelectedColor)
        textViewSelectedBackgroundColor = findViewById(R.id.textViewSelectedBackgroundColor)
        textViewPreviewStatus = findViewById(R.id.textViewPreviewStatus)
        historyListContainer = findViewById(R.id.historyListContainer)
        textInputContainer = findViewById(R.id.textInputContainer)
        wifiFieldsContainer = findViewById(R.id.wifiFieldsContainer)
        phoneFieldsContainer = findViewById(R.id.phoneFieldsContainer)
        emailFieldsContainer = findViewById(R.id.emailFieldsContainer)
        smsFieldsContainer = findViewById(R.id.smsFieldsContainer)
        geoFieldsContainer = findViewById(R.id.geoFieldsContainer)
        contactFieldsContainer = findViewById(R.id.contactFieldsContainer)

        updateSelectedContentType(
            ContentType.fromLocalizedLabel(getString(R.string.default_content_type), ::getString)
        )
        updateSelectedWifiSecurity(
            WifiSecurity.fromLocalizedLabel(getString(R.string.default_wifi_security), ::getString)
        )
        updateSelectedWifiHidden(getString(R.string.default_wifi_hidden).toBoolean())
        updateSelectedLogo(null)
        updateSelectedEyeStyle(
            EyeStyle.fromLocalizedLabel(getString(R.string.default_eye_style), ::getString)
        )
        updateSelectedCenterBadge(
            CenterBadge.fromLocalizedLabel(getString(R.string.default_center_badge), ::getString)
        )
        updateSelectedDesignStyle(
            DesignStyle.fromLocalizedLabel(getString(R.string.default_design_style), ::getString)
        )
        updateSelectedSaveFormat(
            SaveFormat.fromLocalizedLabel(
                getString(R.string.default_save_format),
                ::getString
            )
        )
        updateSelectedColor(Color.parseColor(getString(R.string.default_qr_color)))
        updateSelectedBackgroundColor(Color.parseColor(getString(R.string.default_qr_background_color)))
        updatePreviewState(hasPreview = false)
        updateScanPermissionUi(hasCameraPermission())
        updateScanStatus(getString(R.string.scan_status_idle))
        updateScanResult(null)
        updateScreenMode(ScreenMode.CREATE)

        buttonModeCreate.setOnClickListener {
            updateScreenMode(ScreenMode.CREATE)
        }

        buttonModeScan.setOnClickListener {
            updateScreenMode(ScreenMode.SCAN)
        }

        buttonModeHistory.setOnClickListener {
            updateScreenMode(ScreenMode.HISTORY)
        }

        buttonGrantCameraPermission.setOnClickListener {
            requestCameraPermissionIfNeeded()
        }

        buttonScanPrimaryAction.setOnClickListener {
            lastScanAction?.invoke()
        }

        buttonScanSecondaryAction.setOnClickListener {
            lastSecondaryScanAction?.invoke()
        }

        buttonScanUseInCreate.setOnClickListener {
            useInCreateAction?.invoke()
        }

        buttonResetScan.setOnClickListener {
            resetScanResult()
        }

        buttonClearHistory.setOnClickListener {
            confirmClearHistory()
        }

        buttonGenerate.setOnClickListener {
            if (!renderQrFromInputs(showValidationError = true)) {
                AppTelemetry.logEvent("generate_attempted_invalid_input")
                return@setOnClickListener
            }
        }

        buttonSave.setOnClickListener {
            val bitmapToSave = generatedBitmap
            if (bitmapToSave == null) {
                Toast.makeText(this, getString(R.string.generate_before_saving), Toast.LENGTH_LONG)
                    .show()
                AppTelemetry.logEvent("save_attempted_without_qr")
                return@setOnClickListener
            }

            val outputBitmap = combineImageAndText(bitmapToSave, editTextSaveText.text.toString().trim())
            pendingSaveBitmap = outputBitmap
            pendingSaveFormat = selectedSaveFormat()
            AppTelemetry.logEvent(
                "save_started",
                mapOf("format" to getString(pendingSaveFormat.labelResId))
            )
            announceAccessibilityMessage(getString(R.string.save_started_announcement))
            createDocument.launch(createSaveDocumentIntent(pendingSaveFormat))
        }

        buttonShare.setOnClickListener {
            val bitmapToShare = generatedBitmap
            if (bitmapToShare == null) {
                Toast.makeText(this, getString(R.string.generate_before_sharing), Toast.LENGTH_LONG)
                    .show()
                AppTelemetry.logEvent("share_attempted_without_qr")
                return@setOnClickListener
            }

            AppTelemetry.logEvent("share_started")
            announceAccessibilityMessage(getString(R.string.share_started_announcement))
            shareBitmap(combineImageAndText(bitmapToShare, editTextSaveText.text.toString().trim()))
        }

        buttonPickColor.setOnClickListener {
            showColorPickerDialog(
                titleResId = R.string.color_picker_title,
                initialColor = selectedColor
            ) { newColor ->
                updateSelectedColor(newColor)
                AppTelemetry.logEvent("color_changed", mapOf("color" to formatColor(newColor)))
                announceAccessibilityMessage(
                    getString(R.string.color_changed_announcement, formatColor(newColor))
                )
            }
        }

        buttonPickBackgroundColor.setOnClickListener {
            showColorPickerDialog(
                titleResId = R.string.background_color_picker_title,
                initialColor = selectedBackgroundColor
            ) { newColor ->
                updateSelectedBackgroundColor(newColor)
                AppTelemetry.logEvent(
                    "background_color_changed",
                    mapOf("color" to formatColor(newColor))
                )
                announceAccessibilityMessage(
                    getString(R.string.background_color_changed_announcement, formatColor(newColor))
                )
            }
        }

        buttonPickContentType.setOnClickListener {
            showContentTypeDialog()
        }

        buttonPickWifiSecurity.setOnClickListener {
            showWifiSecurityDialog()
        }

        buttonPickWifiHidden.setOnClickListener {
            toggleWifiHidden()
        }

        buttonPickLogo.setOnClickListener {
            openLogoDocument.launch(arrayOf("image/*"))
        }

        buttonClearLogo.setOnClickListener {
            updateSelectedLogo(null)
            rerenderCurrentQrIfPossible()
            AppTelemetry.logEvent("logo_cleared")
            announceAccessibilityMessage(getString(R.string.logo_cleared_announcement))
        }

        buttonPickEyeStyle.setOnClickListener {
            showEyeStyleDialog()
        }

        buttonPickCenterBadge.setOnClickListener {
            showCenterBadgeDialog()
        }

        buttonPickSaveFormat.setOnClickListener {
            showSaveFormatDialog()
        }

        buttonPickDesignStyle.setOnClickListener {
            showDesignStyleDialog()
        }

        buttonViewSample.setOnClickListener {
            val sampleText = if (selectedContentType() == ContentType.WIFI) {
                getString(R.string.sample_wifi_qr_text)
            } else {
                getString(R.string.sample_qr_text)
            }
            val sampleSize = 256
            val sampleColor = selectedColor
            val sampleBitmap =
                qrCodeGenerator.generateQRCode(
                    text = sampleText,
                    width = sampleSize,
                    height = sampleSize,
                    foregroundColor = sampleColor,
                    backgroundColor = selectedBackgroundColor,
                    designStyle = selectedDesignStyle(),
                    eyeStyle = selectedEyeStyle(),
                    centerBadge = selectedCenterBadge(),
                    centerLogo = selectedLogoBitmap()
                )
            imageViewQRCode.setImageBitmap(sampleBitmap)
            generatedBitmap = sampleBitmap
            updatePreviewState(hasPreview = true)
            AppTelemetry.logEvent("sample_generated")
            announceAccessibilityMessage(getString(R.string.sample_generated_announcement))
        }

        if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_SCREEN_MODE)?.let { modeValue ->
                currentScreenMode = ScreenMode.entries.firstOrNull {
                    getString(it.labelResId) == modeValue
                } ?: ScreenMode.CREATE
            }
            editText.setText(savedInstanceState.getString(STATE_TEXT).orEmpty())
            editTextWifiSsid.setText(savedInstanceState.getString(STATE_WIFI_SSID).orEmpty())
            editTextWifiPassword.setText(savedInstanceState.getString(STATE_WIFI_PASSWORD).orEmpty())
            restoreContentType(savedInstanceState.getString(STATE_CONTENT_TYPE).orEmpty())
            restoreWifiSecurity(savedInstanceState.getString(STATE_WIFI_SECURITY).orEmpty())
            updateSelectedWifiHidden(savedInstanceState.getBoolean(STATE_WIFI_HIDDEN))
            editTextSize.setText(savedInstanceState.getString(STATE_SIZE).orEmpty())
            restoreSaveFormat(savedInstanceState.getString(STATE_SAVE_FORMAT).orEmpty())
            restoreDesignStyle(savedInstanceState.getString(STATE_DESIGN_STYLE).orEmpty())
            restoreEyeStyle(savedInstanceState.getString(STATE_EYE_STYLE).orEmpty())
            restoreCenterBadge(savedInstanceState.getString(STATE_CENTER_BADGE).orEmpty())
            savedInstanceState.getString(STATE_LOGO_URI)?.let { updateSelectedLogo(Uri.parse(it)) }
            editTextSaveText.setText(savedInstanceState.getString(STATE_SAVE_TEXT).orEmpty())
            updateSelectedColor(
                parseQrColor(savedInstanceState.getString(STATE_COLOR).orEmpty())
            )
            updateSelectedBackgroundColor(
                parseQrColor(savedInstanceState.getString(STATE_BACKGROUND_COLOR).orEmpty())
            )

            if (savedInstanceState.getBoolean(STATE_HAS_QR)) {
                renderQrFromInputs(showValidationError = false)
            }

            updateScreenMode(currentScreenMode)
        }
    }

    private fun shareBitmap(bitmap: Bitmap) {
        try {
            val shareDirectory = File(cacheDir, "shared_images").apply { mkdirs() }
            val shareFile = File(shareDirectory, "qr-code-share.png")
            FileOutputStream(shareFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val imageUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                shareFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(
                Intent.createChooser(shareIntent, getString(R.string.share_qr_chooser_title))
            )
        } catch (e: IOException) {
            AppTelemetry.recordNonFatal("share_failed", e)
            Toast.makeText(
                this,
                getString(R.string.share_image_error, e.localizedMessage),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SCREEN_MODE, getString(currentScreenMode.labelResId))
        outState.putString(STATE_TEXT, editText.text.toString())
        outState.putString(STATE_CONTENT_TYPE, getString(selectedContentType().labelResId))
        outState.putString(STATE_WIFI_SSID, editTextWifiSsid.text.toString())
        outState.putString(STATE_WIFI_PASSWORD, editTextWifiPassword.text.toString())
        outState.putString(STATE_WIFI_SECURITY, getString(selectedWifiSecurity().labelResId))
        outState.putBoolean(STATE_WIFI_HIDDEN, currentWifiHidden)
        outState.putString(STATE_SIZE, editTextSize.text.toString())
        outState.putString(STATE_SAVE_FORMAT, getString(selectedSaveFormat().labelResId))
        outState.putString(STATE_DESIGN_STYLE, getString(selectedDesignStyle().labelResId))
        outState.putString(STATE_EYE_STYLE, getString(selectedEyeStyle().labelResId))
        outState.putString(STATE_CENTER_BADGE, getString(selectedCenterBadge().labelResId))
        outState.putString(STATE_LOGO_URI, currentLogoUri?.toString())
        outState.putString(STATE_SAVE_TEXT, editTextSaveText.text.toString())
        outState.putString(STATE_COLOR, formatColor(selectedColor))
        outState.putString(STATE_BACKGROUND_COLOR, formatColor(selectedBackgroundColor))
        outState.putBoolean(STATE_HAS_QR, generatedBitmap != null)
    }

    private fun saveImageToUri(bitmap: Bitmap, uri: Uri, saveFormat: SaveFormat) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val compressSucceeded = bitmap.compress(saveFormat.compressFormat, 100, outputStream)
                outputStream.flush()
                if (!compressSucceeded) {
                    throw IOException("Bitmap compression failed for ${getString(saveFormat.labelResId)}")
                }
                runOnUiThread {
                    AppTelemetry.logEvent(
                        "image_saved",
                        mapOf("format" to getString(saveFormat.labelResId))
                    )
                    Toast.makeText(
                        this,
                        getString(R.string.image_saved_successfully),
                        Toast.LENGTH_LONG
                    ).show()
                    announceAccessibilityMessage(getString(R.string.image_saved_announcement))
                }
            }
        } catch (e: IOException) {
            AppTelemetry.recordNonFatal(
                "save_failed",
                e,
                mapOf("format" to getString(saveFormat.labelResId))
            )
            runOnUiThread {
                Toast.makeText(
                    this,
                    getString(R.string.error_saving_image, e.localizedMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun updateScreenMode(screenMode: ScreenMode) {
        currentScreenMode = screenMode
        val isCreate = screenMode == ScreenMode.CREATE
        val isScan = screenMode == ScreenMode.SCAN
        val isHistory = screenMode == ScreenMode.HISTORY
        createModeContainer.visibility = if (isCreate) View.VISIBLE else View.GONE
        scanModeContainer.visibility = if (isScan) View.VISIBLE else View.GONE
        historyModeContainer.visibility = if (isHistory) View.VISIBLE else View.GONE
        buttonModeCreate.backgroundTintList =
            ContextCompat.getColorStateList(
                this,
                if (isCreate) R.color.button_primary_bg else R.color.button_secondary_bg
            )
        buttonModeScan.backgroundTintList =
            ContextCompat.getColorStateList(
                this,
                if (isScan) R.color.button_primary_bg else R.color.button_secondary_bg
            )
        buttonModeHistory.backgroundTintList =
            ContextCompat.getColorStateList(
                this,
                if (isHistory) R.color.button_primary_bg else R.color.button_secondary_bg
            )
        buttonModeCreate.setTextColor(
            ContextCompat.getColor(
                this,
                if (isCreate) R.color.button_primary_text else R.color.button_secondary_text
            )
        )
        buttonModeScan.setTextColor(
            ContextCompat.getColor(
                this,
                if (isScan) R.color.button_primary_text else R.color.button_secondary_text
            )
        )
        buttonModeHistory.setTextColor(
            ContextCompat.getColor(
                this,
                if (isHistory) R.color.button_primary_text else R.color.button_secondary_text
            )
        )

        if (isCreate || isHistory) {
            stopScanner()
        }

        if (isScan) {
            resetScanResult()
            if (hasCameraPermission()) {
                updateScanPermissionUi(granted = true)
                startScanner()
            } else {
                updateScanPermissionUi(granted = false)
                updateScanStatus(getString(R.string.scan_permission_needed))
            }
        }

        if (isHistory) {
            renderHistory()
        }
    }

    private fun renderHistory() {
        val entries = scanHistoryStore.list()
        historyListContainer.removeAllViews()
        textViewHistoryEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE

        entries.forEach { entry ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_preview_surface)
                setPadding(20, 20, 20, 20)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            card.layoutParams = params

            val titleView = TextView(this).apply {
                text = entry.title
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val metaView = TextView(this).apply {
                text = getString(
                    R.string.history_item_meta_format,
                    entry.type,
                    DateFormat.getDateTimeInstance().format(entry.timestamp)
                )
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 13f
            }
            val summaryView = TextView(this).apply {
                text = entry.summary
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 14f
            }
            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val rowParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rowParams.topMargin = 16
                layoutParams = rowParams
            }
            val useButton = Button(this).apply {
                text = getString(R.string.history_action_use_in_create)
                isAllCaps = false
                backgroundTintList =
                    ContextCompat.getColorStateList(this@MainActivity, R.color.button_primary_bg)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.button_primary_text))
                setOnClickListener {
                    applyScannedRawValueToCreate(entry.rawValue)
                }
            }
            val copyButton = Button(this).apply {
                text = getString(R.string.history_action_copy)
                isAllCaps = false
                backgroundTintList =
                    ContextCompat.getColorStateList(this@MainActivity, R.color.button_secondary_bg)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.button_secondary_text))
                setOnClickListener {
                    copyToClipboard(entry.title, entry.rawValue)
                }
            }
            val deleteButton = Button(this).apply {
                text = getString(R.string.history_action_delete)
                isAllCaps = false
                backgroundTintList =
                    ContextCompat.getColorStateList(this@MainActivity, R.color.button_secondary_bg)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.button_secondary_text))
                setOnClickListener {
                    scanHistoryStore.delete(entry.id)
                    renderHistory()
                }
            }
            val weightParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            weightParams.marginEnd = 8
            useButton.layoutParams = weightParams
            val copyParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            copyParams.marginStart = 8
            copyParams.marginEnd = 8
            copyButton.layoutParams = copyParams
            val deleteParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            deleteParams.marginStart = 8
            deleteButton.layoutParams = deleteParams

            actionRow.addView(useButton)
            actionRow.addView(copyButton)
            actionRow.addView(deleteButton)
            card.addView(titleView)
            card.addView(metaView)
            card.addView(summaryView)
            card.addView(actionRow)
            historyListContainer.addView(card)
        }
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.history_clear_all)
            .setMessage(R.string.history_clear_confirm)
            .setPositiveButton(R.string.history_clear_all) { _, _ ->
                scanHistoryStore.clear()
                renderHistory()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addScanToHistory(result: ScanResult, rawValue: String) {
        when (result) {
            is ScanResult.Text -> scanHistoryStore.add(
                type = getString(R.string.scan_result_type_text),
                title = result.value.take(60),
                summary = result.value,
                rawValue = rawValue
            )

            is ScanResult.Url -> scanHistoryStore.add(
                type = getString(R.string.scan_result_type_url),
                title = result.value.take(60),
                summary = result.value,
                rawValue = rawValue
            )

            is ScanResult.Wifi -> scanHistoryStore.add(
                type = getString(R.string.scan_result_type_wifi),
                title = result.ssid,
                summary = getString(
                    R.string.history_wifi_summary_format,
                    result.security,
                    if (result.hidden) getString(R.string.wifi_hidden_yes) else getString(R.string.wifi_hidden_no)
                ),
                rawValue = rawValue
            )

            is ScanResult.Phone -> scanHistoryStore.add(
                type = getString(R.string.content_type_phone),
                title = result.value,
                summary = result.value,
                rawValue = rawValue
            )

            is ScanResult.Email -> scanHistoryStore.add(
                type = getString(R.string.content_type_email),
                title = result.value,
                summary = listOfNotNull(result.subject, result.body).joinToString(" • ").ifBlank { result.value },
                rawValue = rawValue
            )

            is ScanResult.Sms -> scanHistoryStore.add(
                type = getString(R.string.content_type_sms),
                title = result.phoneNumber,
                summary = result.message.orEmpty(),
                rawValue = rawValue
            )

            is ScanResult.Geo -> scanHistoryStore.add(
                type = getString(R.string.content_type_geo),
                title = "${result.latitude}, ${result.longitude}",
                summary = rawValue,
                rawValue = rawValue
            )

            is ScanResult.Contact -> scanHistoryStore.add(
                type = getString(R.string.content_type_contact),
                title = result.name,
                summary = listOfNotNull(result.phone, result.email).joinToString(" • "),
                rawValue = rawValue
            )
        }
    }

    private fun applyScannedRawValueToCreate(rawValue: String) {
        applyScannedResultToCreate(parseScanResult(rawValue))
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermissionIfNeeded() {
        if (hasCameraPermission()) {
            updateScanPermissionUi(granted = true)
            startScanner()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun updateScanPermissionUi(granted: Boolean) {
        textViewScanPermissionState.text = getString(
            if (granted) R.string.scan_permission_ready else R.string.scan_permission_needed
        )
        buttonGrantCameraPermission.visibility = if (granted) View.GONE else View.VISIBLE
        previewViewScanner.visibility = if (granted) View.VISIBLE else View.INVISIBLE
    }

    private fun updateScanStatus(status: String) {
        textViewScanStatus.text = status
    }

    private fun startScanner() {
        if (!hasCameraPermission()) {
            return
        }
        resetScanResult()
        updateScanStatus(getString(R.string.scan_status_searching))
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun stopScanner() {
        cameraProvider?.unbindAll()
        updateScanStatus(getString(R.string.scan_status_idle))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val preview = androidx.camera.core.Preview.Builder().build().also {
            it.setSurfaceProvider(previewViewScanner.surfaceProvider)
        }
        val analyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor, QrCodeAnalyzer(::handleScannedBarcode))
            }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
        } catch (e: Exception) {
            AppTelemetry.recordNonFatal("scanner_bind_failed", e)
            updateScanStatus(getString(R.string.scan_status_idle))
        }
    }

    private fun handleScannedBarcode(rawValue: String) {
        if (scannerPaused) {
            return
        }
        val now = System.currentTimeMillis()
        if (rawValue == lastScannedRawValue && now - lastScanTimestampMs < SCAN_RESULT_COOLDOWN_MS) {
            return
        }
        lastScannedRawValue = rawValue
        lastScanTimestampMs = now
        scannerPaused = true
        val parsedResult = parseScanResult(rawValue)
        addScanToHistory(parsedResult, rawValue)
        runOnUiThread {
            previewViewScanner.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            updateScanStatus(getString(R.string.scan_status_detected))
            updateScanResult(parsedResult)
            AppTelemetry.logEvent("qr_scanned")
        }
    }

    private fun updateScanResult(result: ScanResult?) {
        if (result == null) {
            scanResultContainer.visibility = View.GONE
            lastScanAction = null
            lastSecondaryScanAction = null
            useInCreateAction = null
            buttonScanSecondaryAction.visibility = View.GONE
            buttonScanUseInCreate.visibility = View.GONE
            return
        }

        scanResultContainer.visibility = View.VISIBLE
        when (result) {
            is ScanResult.Text -> {
                textViewScanResultType.text = getString(R.string.scan_result_type_text)
                textViewScanResultValue.text = result.value
                buttonScanPrimaryAction.text = getString(R.string.scan_action_copy)
                lastScanAction = {
                    copyToClipboard(getString(R.string.scan_result_type_text), result.value)
                }
                buttonScanSecondaryAction.visibility = View.GONE
                lastSecondaryScanAction = null
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }

            is ScanResult.Url -> {
                textViewScanResultType.text = getString(R.string.scan_result_type_url)
                textViewScanResultValue.text = result.value
                buttonScanPrimaryAction.text = getString(R.string.scan_action_open_link)
                lastScanAction = {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.value)))
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.scan_link_error), Toast.LENGTH_LONG)
                            .show()
                    }
                }
                buttonScanSecondaryAction.visibility = View.VISIBLE
                buttonScanSecondaryAction.text = getString(R.string.scan_action_copy)
                lastSecondaryScanAction = {
                    copyToClipboard(getString(R.string.scan_result_type_url), result.value)
                }
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }

            is ScanResult.Phone -> {
                textViewScanResultType.text = getString(R.string.content_type_phone)
                textViewScanResultValue.text = result.value
                buttonScanPrimaryAction.text = getString(R.string.scan_action_call)
                lastScanAction = {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(result.value)}")))
                }
                buttonScanSecondaryAction.visibility = View.VISIBLE
                buttonScanSecondaryAction.text = getString(R.string.scan_action_copy)
                lastSecondaryScanAction = {
                    copyToClipboard(getString(R.string.content_type_phone), result.value)
                }
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }

            is ScanResult.Email -> {
                textViewScanResultType.text = getString(R.string.content_type_email)
                textViewScanResultValue.text =
                    listOf(result.value, result.subject.orEmpty(), result.body.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                buttonScanPrimaryAction.text = getString(R.string.scan_action_send_email)
                lastScanAction = {
                    startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(result.value)}")).apply {
                            if (!result.subject.isNullOrBlank()) {
                                putExtra(Intent.EXTRA_SUBJECT, result.subject)
                            }
                            if (!result.body.isNullOrBlank()) {
                                putExtra(Intent.EXTRA_TEXT, result.body)
                            }
                        }
                    )
                }
                buttonScanSecondaryAction.visibility = View.VISIBLE
                buttonScanSecondaryAction.text = getString(R.string.scan_action_copy)
                lastSecondaryScanAction = {
                    copyToClipboard(getString(R.string.content_type_email), textViewScanResultValue.text.toString())
                }
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }

            is ScanResult.Sms -> {
                textViewScanResultType.text = getString(R.string.content_type_sms)
                textViewScanResultValue.text =
                    listOf(result.phoneNumber, result.message.orEmpty()).filter { it.isNotBlank() }
                        .joinToString("\n")
                buttonScanPrimaryAction.text = getString(R.string.scan_action_send_message)
                lastScanAction = {
                    val smsUri = Uri.parse("smsto:${Uri.encode(result.phoneNumber)}")
                    startActivity(
                        Intent(Intent.ACTION_SENDTO, smsUri).apply {
                            if (!result.message.isNullOrBlank()) {
                                putExtra("sms_body", result.message)
                            }
                        }
                    )
                }
                buttonScanSecondaryAction.visibility = View.VISIBLE
                buttonScanSecondaryAction.text = getString(R.string.scan_action_copy)
                lastSecondaryScanAction = {
                    copyToClipboard(getString(R.string.content_type_sms), textViewScanResultValue.text.toString())
                }
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }

            is ScanResult.Geo -> {
                textViewScanResultType.text = getString(R.string.content_type_geo)
                textViewScanResultValue.text = "${result.latitude}, ${result.longitude}"
                buttonScanPrimaryAction.text = getString(R.string.scan_action_open_map)
                lastScanAction = {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("geo:${result.latitude},${result.longitude}")
                        )
                    )
                }
                buttonScanSecondaryAction.visibility = View.VISIBLE
                buttonScanSecondaryAction.text = getString(R.string.scan_action_copy)
                lastSecondaryScanAction = {
                    copyToClipboard(getString(R.string.content_type_geo), textViewScanResultValue.text.toString())
                }
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }

            is ScanResult.Contact -> {
                textViewScanResultType.text = getString(R.string.content_type_contact)
                textViewScanResultValue.text =
                    listOfNotNull(result.name, result.phone, result.email).joinToString("\n")
                buttonScanPrimaryAction.text = getString(R.string.scan_action_add_contact)
                lastScanAction = {
                    startActivity(
                        Intent(ContactsContract.Intents.Insert.ACTION).apply {
                            type = ContactsContract.RawContacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.NAME, result.name)
                            putExtra(ContactsContract.Intents.Insert.PHONE, result.phone)
                            putExtra(ContactsContract.Intents.Insert.EMAIL, result.email)
                        }
                    )
                }
                buttonScanSecondaryAction.visibility = View.VISIBLE
                buttonScanSecondaryAction.text = getString(R.string.scan_action_copy)
                lastSecondaryScanAction = {
                    copyToClipboard(getString(R.string.content_type_contact), textViewScanResultValue.text.toString())
                }
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }

            is ScanResult.Wifi -> {
                textViewScanResultType.text = getString(R.string.scan_result_type_wifi)
                textViewScanResultValue.text = getString(
                    R.string.scan_wifi_result_format,
                    result.ssid,
                    result.security,
                    if (result.hidden) getString(R.string.wifi_hidden_yes) else getString(R.string.wifi_hidden_no),
                    result.password ?: getString(R.string.scan_wifi_password_open)
                )
                buttonScanPrimaryAction.text = getString(R.string.scan_action_connect_wifi)
                lastScanAction = {
                    showWifiConnectDialog(result)
                }
                buttonScanSecondaryAction.visibility = View.VISIBLE
                buttonScanSecondaryAction.text = getString(
                    if (result.password.isNullOrBlank()) {
                        R.string.scan_action_copy
                    } else {
                        R.string.scan_action_copy_password
                    }
                )
                lastSecondaryScanAction = {
                    if (result.password.isNullOrBlank()) {
                        copyToClipboard(
                            getString(R.string.scan_result_type_wifi),
                            textViewScanResultValue.text.toString()
                        )
                    } else {
                        copyToClipboard(
                            getString(R.string.wifi_password_label),
                            result.password,
                            R.string.scan_password_copy_success
                        )
                    }
                }
                buttonScanUseInCreate.visibility = View.VISIBLE
                useInCreateAction = {
                    applyScannedResultToCreate(result)
                }
            }
        }
    }

    private fun resetScanResult() {
        lastScannedRawValue = null
        lastScanTimestampMs = 0L
        scannerPaused = false
        updateScanResult(null)
        updateScanStatus(
            if (hasCameraPermission()) getString(R.string.scan_status_searching)
            else getString(R.string.scan_status_idle)
        )
    }

    private fun applyScannedResultToCreate(result: ScanResult) {
        when (result) {
            is ScanResult.Text -> {
                updateSelectedContentType(ContentType.TEXT)
                editText.setText(result.value)
            }

            is ScanResult.Url -> {
                updateSelectedContentType(ContentType.TEXT)
                editText.setText(result.value)
            }

            is ScanResult.Phone -> {
                updateSelectedContentType(ContentType.PHONE)
                editTextPhoneNumber.setText(result.value)
            }

            is ScanResult.Email -> {
                updateSelectedContentType(ContentType.EMAIL)
                editTextEmailAddress.setText(result.value)
                editTextEmailSubject.setText(result.subject.orEmpty())
                editTextEmailBody.setText(result.body.orEmpty())
            }

            is ScanResult.Sms -> {
                updateSelectedContentType(ContentType.SMS)
                editTextSmsNumber.setText(result.phoneNumber)
                editTextSmsMessage.setText(result.message.orEmpty())
            }

            is ScanResult.Geo -> {
                updateSelectedContentType(ContentType.GEO)
                editTextGeoLatitude.setText(result.latitude)
                editTextGeoLongitude.setText(result.longitude)
            }

            is ScanResult.Contact -> {
                updateSelectedContentType(ContentType.CONTACT)
                editTextContactName.setText(result.name)
                editTextContactPhone.setText(result.phone.orEmpty())
                editTextContactEmail.setText(result.email.orEmpty())
            }

            is ScanResult.Wifi -> {
                updateSelectedContentType(ContentType.WIFI)
                editTextWifiSsid.setText(result.ssid)
                editTextWifiPassword.setText(result.password.orEmpty())
                val security = when (result.security.uppercase()) {
                    "WEP" -> WifiSecurity.WEP
                    "NOPASS", "OPEN" -> WifiSecurity.OPEN
                    else -> WifiSecurity.WPA_WPA2
                }
                updateSelectedWifiSecurity(security)
                updateSelectedWifiHidden(result.hidden)
            }
        }
        updateScreenMode(ScreenMode.CREATE)
        Toast.makeText(this, getString(R.string.scan_sent_to_create), Toast.LENGTH_LONG).show()
    }

    private fun parseScanResult(rawValue: String): ScanResult {
        parseWifiQr(rawValue)?.let { return it }
        parsePhoneQr(rawValue)?.let { return it }
        parseEmailQr(rawValue)?.let { return it }
        parseSmsQr(rawValue)?.let { return it }
        parseGeoQr(rawValue)?.let { return it }
        parseContactQr(rawValue)?.let { return it }
        return if (rawValue.startsWith("http://") || rawValue.startsWith("https://")) {
            ScanResult.Url(rawValue)
        } else {
            ScanResult.Text(rawValue)
        }
    }

    private fun parsePhoneQr(rawValue: String): ScanResult.Phone? {
        return if (rawValue.startsWith("tel:", ignoreCase = true)) {
            ScanResult.Phone(rawValue.removePrefix("tel:").removePrefix("TEL:"))
        } else {
            null
        }
    }

    private fun parseEmailQr(rawValue: String): ScanResult.Email? {
        return if (rawValue.startsWith("mailto:", ignoreCase = true)) {
            val mailToUri = Uri.parse(rawValue)
            val address = mailToUri.schemeSpecificPart.substringBefore('?')
            if (address.isBlank()) {
                null
            } else {
                ScanResult.Email(
                    value = Uri.decode(address),
                    subject = mailToUri.getQueryParameter("subject"),
                    body = mailToUri.getQueryParameter("body")
                )
            }
        } else if (rawValue.startsWith("MATMSG:", ignoreCase = true)) {
            val body = rawValue.substringAfter(':')
            val values = body.split(';')
                .mapNotNull { token ->
                    val separator = token.indexOf(':')
                    if (separator <= 0) null else token.substring(0, separator) to token.substring(separator + 1)
                }
                .toMap()
            val address = values["TO"] ?: return null
            ScanResult.Email(
                value = address,
                subject = values["SUB"],
                body = values["BODY"]
            )
        } else {
            null
        }
    }

    private fun parseSmsQr(rawValue: String): ScanResult.Sms? {
        if (!rawValue.startsWith("SMSTO:", ignoreCase = true)) {
            return null
        }
        val body = rawValue.substringAfter(':')
        val parts = body.split(':', limit = 2)
        val number = parts.getOrNull(0).orEmpty()
        if (number.isBlank()) {
            return null
        }
        return ScanResult.Sms(number, parts.getOrNull(1))
    }

    private fun parseGeoQr(rawValue: String): ScanResult.Geo? {
        if (!rawValue.startsWith("geo:", ignoreCase = true)) {
            return null
        }
        val coordinates = rawValue.substringAfter(':').substringBefore('?')
        val parts = coordinates.split(',', limit = 2)
        val latitude = parts.getOrNull(0).orEmpty()
        val longitude = parts.getOrNull(1).orEmpty()
        return if (latitude.isBlank() || longitude.isBlank()) {
            null
        } else {
            ScanResult.Geo(latitude, longitude)
        }
    }

    private fun parseContactQr(rawValue: String): ScanResult.Contact? {
        if (rawValue.startsWith("MECARD:", ignoreCase = true)) {
            val body = rawValue.substringAfter(':')
            val values = body.split(';')
                .mapNotNull { token ->
                    val separator = token.indexOf(':')
                    if (separator <= 0) null else token.substring(0, separator) to token.substring(separator + 1)
                }
                .toMap()
            val name = values["N"] ?: return null
            return ScanResult.Contact(
                name = name.replace("\\;", ";"),
                phone = values["TEL"]?.replace("\\;", ";"),
                email = values["EMAIL"]?.replace("\\;", ";")
            )
        }

        if (rawValue.contains("BEGIN:VCARD", ignoreCase = true)) {
            val lines = rawValue.lines()
            val name = lines.firstOrNull { it.startsWith("FN:", ignoreCase = true) }
                ?.substringAfter(':')
                ?: return null
            val phone = lines.firstOrNull { it.startsWith("TEL", ignoreCase = true) }
                ?.substringAfter(':')
            val email = lines.firstOrNull { it.startsWith("EMAIL", ignoreCase = true) }
                ?.substringAfter(':')
            return ScanResult.Contact(name = name, phone = phone, email = email)
        }

        return null
    }

    private fun parseWifiQr(rawValue: String): ScanResult.Wifi? {
        if (!rawValue.startsWith("WIFI:")) {
            return null
        }
        val body = rawValue.removePrefix("WIFI:")
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        body.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                char == '\\' -> escaped = true
                char == ';' -> {
                    tokens += current.toString()
                    current.clear()
                }

                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) {
            tokens += current.toString()
        }
        val values = tokens.mapNotNull { token ->
            val separator = token.indexOf(':')
            if (separator <= 0) null else token.substring(0, separator) to token.substring(separator + 1)
        }.toMap()
        val ssid = values["S"] ?: return null
        val security = values["T"].orEmpty().ifBlank { getString(R.string.wifi_security_open) }
        val password = values["P"]
        val hidden = values["H"]?.equals("true", ignoreCase = true) == true
        return ScanResult.Wifi(
            ssid = ssid,
            security = security,
            password = if (password.isNullOrBlank()) null else password,
            hidden = hidden
        )
    }

    private fun copyToClipboard(label: String, value: String) {
        copyToClipboard(label, value, R.string.scan_copy_success)
    }

    private fun copyToClipboard(label: String, value: String, @StringRes messageResId: Int) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, getString(messageResId), Toast.LENGTH_LONG).show()
    }

    private fun showWifiConnectDialog(result: ScanResult.Wifi) {
        AlertDialog.Builder(this)
            .setTitle(R.string.scan_wifi_connect_title)
            .setMessage(
                getString(
                    R.string.scan_wifi_connect_message,
                    result.ssid,
                    result.security,
                    if (result.hidden) getString(R.string.wifi_hidden_yes) else getString(R.string.wifi_hidden_no)
                )
            )
            .setPositiveButton(R.string.scan_action_connect_wifi) { _, _ ->
                val suggested = suggestWifiNetwork(result)
                if (!suggested) {
                    startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                }
            }
            .setNeutralButton(
                if (result.password.isNullOrBlank()) R.string.scan_action_copy
                else R.string.scan_action_copy_password
            ) { _, _ ->
                if (result.password.isNullOrBlank()) {
                    copyToClipboard(
                        getString(R.string.scan_result_type_wifi),
                        textViewScanResultValue.text.toString()
                    )
                } else {
                    copyToClipboard(
                        getString(R.string.wifi_password_label),
                        result.password,
                        R.string.scan_password_copy_success
                    )
                }
            }
            .setNegativeButton(R.string.scan_action_open_wifi_settings) { _, _ ->
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            }
            .show()
    }

    private fun suggestWifiNetwork(result: ScanResult.Wifi): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false

        activeWifiSuggestion?.let { previousSuggestion ->
            wifiManager.removeNetworkSuggestions(listOf(previousSuggestion))
            activeWifiSuggestion = null
        }

        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(result.ssid)
            .setIsHiddenSsid(result.hidden)

        when (result.security.uppercase()) {
            "WPA", "WPA/WPA2" -> {
                val password = result.password ?: return false
                builder.setWpa2Passphrase(password)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    builder.setCredentialSharedWithUser(true)
                }
            }

            "OPEN", "NOPASS" -> Unit
            else -> return false
        }

        val suggestion = builder.build()
        val status = wifiManager.addNetworkSuggestions(listOf(suggestion))
        return if (status == WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            activeWifiSuggestion = suggestion
            Toast.makeText(this, getString(R.string.scan_wifi_suggestion_added), Toast.LENGTH_LONG)
                .show()
            true
        } else {
            Toast.makeText(
                this,
                getString(R.string.scan_wifi_suggestion_failed),
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    private fun selectedSaveFormat(): SaveFormat {
        return currentSaveFormat
    }

    private fun selectedContentType(): ContentType {
        return currentContentType
    }

    private fun selectedWifiSecurity(): WifiSecurity {
        return currentWifiSecurity
    }

    private fun selectedDesignStyle(): DesignStyle {
        return currentDesignStyle
    }

    private fun selectedEyeStyle(): EyeStyle {
        return currentEyeStyle
    }

    private fun selectedCenterBadge(): CenterBadge {
        return currentCenterBadge
    }

    private fun selectedLogoBitmap(): Bitmap? {
        val uri = currentLogoUri ?: return null
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            AppTelemetry.recordNonFatal("logo_decode_failed", e)
            null
        }
    }

    private fun restoreContentType(value: String) {
        updateSelectedContentType(
            ContentType.fromLocalizedLabel(
                value.ifBlank { getString(R.string.default_content_type) },
                ::getString
            )
        )
    }

    private fun restoreWifiSecurity(value: String) {
        updateSelectedWifiSecurity(
            WifiSecurity.fromLocalizedLabel(
                value.ifBlank { getString(R.string.default_wifi_security) },
                ::getString
            )
        )
    }

    private fun restoreSaveFormat(value: String) {
        updateSelectedSaveFormat(
            SaveFormat.fromLocalizedLabel(
                value.ifBlank { getString(R.string.default_save_format) },
                ::getString
            )
        )
    }

    private fun restoreDesignStyle(value: String) {
        updateSelectedDesignStyle(
            DesignStyle.fromLocalizedLabel(
                value.ifBlank { getString(R.string.default_design_style) },
                ::getString
            )
        )
    }

    private fun restoreEyeStyle(value: String) {
        updateSelectedEyeStyle(
            EyeStyle.fromLocalizedLabel(
                value.ifBlank { getString(R.string.default_eye_style) },
                ::getString
            )
        )
    }

    private fun restoreCenterBadge(value: String) {
        updateSelectedCenterBadge(
            CenterBadge.fromLocalizedLabel(
                value.ifBlank { getString(R.string.default_center_badge) },
                ::getString
            )
        )
    }

    private fun createSaveDocumentIntent(saveFormat: SaveFormat): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = saveFormat.mimeType
            putExtra(Intent.EXTRA_TITLE, defaultFileNameFor(saveFormat))
        }
    }

    private fun defaultFileNameFor(saveFormat: SaveFormat): String {
        return "QRCode.${saveFormat.fileExtension}"
    }

    private fun updateSelectedContentType(contentType: ContentType) {
        currentContentType = contentType
        textViewSelectedContentType.text = getString(
            R.string.selected_content_type_format,
            getString(contentType.labelResId)
        )
        syncContentTypeUi()
    }

    private fun updateSelectedWifiSecurity(wifiSecurity: WifiSecurity) {
        currentWifiSecurity = wifiSecurity
        textViewSelectedWifiSecurity.text = getString(
            R.string.selected_wifi_security_format,
            getString(wifiSecurity.labelResId)
        )
    }

    private fun updateSelectedWifiHidden(isHidden: Boolean) {
        currentWifiHidden = isHidden
        textViewSelectedWifiHidden.text = getString(
            R.string.selected_wifi_hidden_format,
            getString(if (isHidden) R.string.wifi_hidden_yes else R.string.wifi_hidden_no)
        )
    }

    private fun toggleWifiHidden() {
        updateSelectedWifiHidden(!currentWifiHidden)
        AppTelemetry.logEvent("wifi_hidden_toggled", mapOf("hidden" to currentWifiHidden.toString()))
        announceAccessibilityMessage(
            getString(
                R.string.wifi_hidden_changed_announcement,
                getString(if (currentWifiHidden) R.string.wifi_hidden_yes else R.string.wifi_hidden_no)
            )
        )
    }

    private fun syncContentTypeUi() {
        val contentType = selectedContentType()
        textInputContainer.visibility = if (contentType == ContentType.TEXT) View.VISIBLE else View.GONE
        wifiFieldsContainer.visibility = if (contentType == ContentType.WIFI) View.VISIBLE else View.GONE
        phoneFieldsContainer.visibility = if (contentType == ContentType.PHONE) View.VISIBLE else View.GONE
        emailFieldsContainer.visibility = if (contentType == ContentType.EMAIL) View.VISIBLE else View.GONE
        smsFieldsContainer.visibility = if (contentType == ContentType.SMS) View.VISIBLE else View.GONE
        geoFieldsContainer.visibility = if (contentType == ContentType.GEO) View.VISIBLE else View.GONE
        contactFieldsContainer.visibility = if (contentType == ContentType.CONTACT) View.VISIBLE else View.GONE
    }

    private fun updateSelectedDesignStyle(designStyle: DesignStyle) {
        currentDesignStyle = designStyle
        textViewSelectedDesignStyle.text = getString(
            R.string.selected_design_style_format,
            getString(designStyle.labelResId)
        )
    }

    private fun updateSelectedLogo(uri: Uri?) {
        currentLogoUri = uri
        val hasLogo = uri != null
        textViewSelectedLogo.text = if (hasLogo) {
            getString(
                R.string.selected_logo_format,
                resolveLogoName(uri)
            )
        } else {
            getString(R.string.no_logo_selected)
        }
        buttonClearLogo.visibility = if (hasLogo) View.VISIBLE else View.GONE
    }

    private fun updateSelectedEyeStyle(eyeStyle: EyeStyle) {
        currentEyeStyle = eyeStyle
        textViewSelectedEyeStyle.text = getString(
            R.string.selected_eye_style_format,
            getString(eyeStyle.labelResId)
        )
    }

    private fun updateSelectedCenterBadge(centerBadge: CenterBadge) {
        currentCenterBadge = centerBadge
        textViewSelectedCenterBadge.text = getString(
            R.string.selected_center_badge_format,
            getString(centerBadge.labelResId)
        )
    }

    private fun updateSelectedSaveFormat(saveFormat: SaveFormat) {
        currentSaveFormat = saveFormat
        textViewSelectedSaveFormat.text = getString(
            R.string.selected_save_format_format,
            getString(saveFormat.labelResId)
        )
    }

    private fun renderQrFromInputs(showValidationError: Boolean): Boolean {
        val qrContent = buildQrContent(showValidationError) ?: run {
            imageViewQRCode.setImageDrawable(null)
            generatedBitmap = null
            updatePreviewState(hasPreview = false)
            return false
        }

        val size = editTextSize.text.toString().toIntOrNull() ?: 256
        val color = selectedColor
        val qrBitmap = qrCodeGenerator.generateQRCode(
            text = qrContent,
            width = size,
            height = size,
            foregroundColor = color,
            backgroundColor = selectedBackgroundColor,
            designStyle = selectedDesignStyle(),
            eyeStyle = selectedEyeStyle(),
            centerBadge = selectedCenterBadge(),
            centerLogo = selectedLogoBitmap()
        )
        imageViewQRCode.setImageBitmap(qrBitmap)
        generatedBitmap = qrBitmap
        updatePreviewState(hasPreview = true)
        AppTelemetry.logEvent(
            "qr_generated",
            mapOf(
                "size" to size.toString(),
                "content_type" to getString(selectedContentType().labelResId),
                "color" to formatColor(color),
                "background_color" to formatColor(selectedBackgroundColor),
                "design_style" to getString(selectedDesignStyle().labelResId),
                "eye_style" to getString(selectedEyeStyle().labelResId),
                "center_badge" to getString(selectedCenterBadge().labelResId),
                "has_logo" to (currentLogoUri != null).toString(),
                "wifi_security" to getString(selectedWifiSecurity().labelResId),
                "wifi_hidden" to currentWifiHidden.toString(),
                "has_caption" to editTextSaveText.text.toString().trim().isNotEmpty().toString()
            )
        )
        if (showValidationError) {
            announceAccessibilityMessage(getString(R.string.qr_generated_announcement))
        }
        return true
    }

    private fun buildQrContent(showValidationError: Boolean): String? {
        return when (selectedContentType()) {
            ContentType.TEXT -> {
                val text = editText.text.toString().trim()
                if (text.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_text_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    null
                } else {
                    text
                }
            }

            ContentType.WIFI -> {
                val ssid = editTextWifiSsid.text.toString().trim()
                val password = editTextWifiPassword.text.toString()
                val security = selectedWifiSecurity()
                if (ssid.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_wifi_ssid_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    return null
                }
                if (security != WifiSecurity.OPEN && password.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_wifi_password_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    return null
                }
                buildWifiQrPayload(ssid, password, security, currentWifiHidden)
            }

            ContentType.PHONE -> {
                val phoneNumber = editTextPhoneNumber.text.toString().trim()
                if (phoneNumber.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_phone_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    return null
                }
                "tel:$phoneNumber"
            }

            ContentType.EMAIL -> {
                val emailAddress = editTextEmailAddress.text.toString().trim()
                if (emailAddress.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_email_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    return null
                }
                val subject = editTextEmailSubject.text.toString().trim()
                val body = editTextEmailBody.text.toString().trim()
                buildMailToPayload(emailAddress, subject, body)
            }

            ContentType.SMS -> {
                val smsNumber = editTextSmsNumber.text.toString().trim()
                if (smsNumber.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_sms_number_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    return null
                }
                val message = editTextSmsMessage.text.toString().trim()
                buildSmsPayload(smsNumber, message)
            }

            ContentType.GEO -> {
                val latitude = editTextGeoLatitude.text.toString().trim()
                val longitude = editTextGeoLongitude.text.toString().trim()
                if (latitude.isBlank() || longitude.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_geo_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    return null
                }
                "geo:$latitude,$longitude"
            }

            ContentType.CONTACT -> {
                val name = editTextContactName.text.toString().trim()
                val phone = editTextContactPhone.text.toString().trim()
                val email = editTextContactEmail.text.toString().trim()
                if (name.isBlank()) {
                    if (showValidationError) {
                        Toast.makeText(this, getString(R.string.enter_contact_name_error), Toast.LENGTH_LONG)
                            .show()
                    }
                    return null
                }
                buildMeCardPayload(name, phone, email)
            }
        }
    }

    private fun buildWifiQrPayload(
        ssid: String,
        password: String,
        security: WifiSecurity,
        isHidden: Boolean
    ): String {
        return buildString {
            append("WIFI:")
            append("T:")
            append(security.wifiValue)
            append(';')
            append("S:")
            append(escapeWifiValue(ssid))
            append(';')
            if (security != WifiSecurity.OPEN) {
                append("P:")
                append(escapeWifiValue(password))
                append(';')
            }
            if (isHidden) {
                append("H:true;")
            }
            append(';')
        }
    }

    private fun escapeWifiValue(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")
    }

    private fun buildMailToPayload(address: String, subject: String, body: String): String {
        val queryParts = mutableListOf<String>()
        if (subject.isNotBlank()) {
            queryParts += "subject=${Uri.encode(subject)}"
        }
        if (body.isNotBlank()) {
            queryParts += "body=${Uri.encode(body)}"
        }
        val query = if (queryParts.isEmpty()) "" else "?${queryParts.joinToString("&")}"
        return "mailto:${Uri.encode(address)}$query"
    }

    private fun buildSmsPayload(number: String, message: String): String {
        return if (message.isBlank()) {
            "SMSTO:$number"
        } else {
            "SMSTO:$number:${message.replace(":", "\\:")}"
        }
    }

    private fun buildMeCardPayload(name: String, phone: String, email: String): String {
        return buildString {
            append("MECARD:")
            append("N:")
            append(name.replace(";", "\\;"))
            append(';')
            if (phone.isNotBlank()) {
                append("TEL:")
                append(phone.replace(";", "\\;"))
                append(';')
            }
            if (email.isNotBlank()) {
                append("EMAIL:")
                append(email.replace(";", "\\;"))
                append(';')
            }
            append(';')
        }
    }

    private fun parseQrColor(colorValue: String): Int {
        return try {
            Color.parseColor(colorValue)
        } catch (e: IllegalArgumentException) {
            Color.BLACK
        }
    }

    private fun updateSelectedColor(color: Int) {
        selectedColor = color
        viewSelectedColor.setBackgroundColor(color)
        textViewSelectedColor.text = getString(
            R.string.selected_color_format,
            formatColor(color)
        )
        textViewSelectedColor.contentDescription = getString(
            R.string.selected_color_accessibility_format,
            formatColor(color)
        )
    }

    private fun updateSelectedBackgroundColor(color: Int) {
        selectedBackgroundColor = color
        viewSelectedBackgroundColor.setBackgroundColor(color)
        textViewSelectedBackgroundColor.text = getString(
            R.string.selected_color_format,
            formatColor(color)
        )
        textViewSelectedBackgroundColor.contentDescription = getString(
            R.string.selected_background_color_accessibility_format,
            formatColor(color)
        )
    }

    private fun formatColor(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun rerenderCurrentQrIfPossible() {
        if (generatedBitmap != null && hasAnyInputForSelectedContentType()) {
            renderQrFromInputs(showValidationError = false)
        }
    }

    private fun hasAnyInputForSelectedContentType(): Boolean {
        return when (selectedContentType()) {
            ContentType.TEXT -> editText.text.toString().trim().isNotEmpty()
            ContentType.WIFI -> editTextWifiSsid.text.toString().trim().isNotEmpty()
            ContentType.PHONE -> editTextPhoneNumber.text.toString().trim().isNotEmpty()
            ContentType.EMAIL -> editTextEmailAddress.text.toString().trim().isNotEmpty()
            ContentType.SMS -> editTextSmsNumber.text.toString().trim().isNotEmpty()
            ContentType.GEO -> editTextGeoLatitude.text.toString().trim().isNotEmpty() &&
                editTextGeoLongitude.text.toString().trim().isNotEmpty()
            ContentType.CONTACT -> editTextContactName.text.toString().trim().isNotEmpty()
        }
    }

    private fun resolveLogoName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(nameIndex)
                }
            }
        return uri.lastPathSegment ?: getString(R.string.logo_fallback_name)
    }

    private fun showColorPickerDialog(
        @StringRes titleResId: Int,
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val preview = dialogView.findViewById<View>(R.id.viewDialogColorPreview)
        val colorValue = dialogView.findViewById<TextView>(R.id.textViewDialogColorValue)
        val redSeekBar = dialogView.findViewById<SeekBar>(R.id.seekBarRed)
        val greenSeekBar = dialogView.findViewById<SeekBar>(R.id.seekBarGreen)
        val blueSeekBar = dialogView.findViewById<SeekBar>(R.id.seekBarBlue)

        redSeekBar.progress = Color.red(initialColor)
        greenSeekBar.progress = Color.green(initialColor)
        blueSeekBar.progress = Color.blue(initialColor)

        fun refreshPreview() {
            val color = Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
            preview.setBackgroundColor(color)
            colorValue.text = getString(R.string.selected_color_format, formatColor(color))
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

        redSeekBar.setOnSeekBarChangeListener(listener)
        greenSeekBar.setOnSeekBarChangeListener(listener)
        blueSeekBar.setOnSeekBarChangeListener(listener)
        refreshPreview()

        AlertDialog.Builder(this)
            .setTitle(titleResId)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newColor =
                    Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
                onColorSelected(newColor)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSaveFormatDialog() {
        val formats = SaveFormat.entries.toTypedArray()
        val labels = formats.map { getString(it.labelResId) }.toTypedArray()
        val selectedIndex = formats.indexOf(selectedSaveFormat()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.save_format_picker_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                updateSelectedSaveFormat(formats[which])
                AppTelemetry.logEvent(
                    "save_format_changed",
                    mapOf("format" to getString(formats[which].labelResId))
                )
                announceAccessibilityMessage(
                    getString(
                        R.string.save_format_changed_announcement,
                        getString(formats[which].labelResId)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showContentTypeDialog() {
        val contentTypes = ContentType.entries.toTypedArray()
        val labels = contentTypes.map { getString(it.labelResId) }.toTypedArray()
        val selectedIndex = contentTypes.indexOf(selectedContentType()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.content_type_picker_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                updateSelectedContentType(contentTypes[which])
                rerenderCurrentQrIfPossible()
                AppTelemetry.logEvent(
                    "content_type_changed",
                    mapOf("content_type" to getString(contentTypes[which].labelResId))
                )
                announceAccessibilityMessage(
                    getString(
                        R.string.content_type_changed_announcement,
                        getString(contentTypes[which].labelResId)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showWifiSecurityDialog() {
        val securityOptions = WifiSecurity.entries.toTypedArray()
        val labels = securityOptions.map { getString(it.labelResId) }.toTypedArray()
        val selectedIndex = securityOptions.indexOf(selectedWifiSecurity()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.wifi_security_picker_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                updateSelectedWifiSecurity(securityOptions[which])
                rerenderCurrentQrIfPossible()
                AppTelemetry.logEvent(
                    "wifi_security_changed",
                    mapOf("wifi_security" to getString(securityOptions[which].labelResId))
                )
                announceAccessibilityMessage(
                    getString(
                        R.string.wifi_security_changed_announcement,
                        getString(securityOptions[which].labelResId)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDesignStyleDialog() {
        val styles = DesignStyle.entries.toTypedArray()
        val labels = styles.map { getString(it.labelResId) }.toTypedArray()
        val selectedIndex = styles.indexOf(selectedDesignStyle()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.design_style_picker_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                updateSelectedDesignStyle(styles[which])
                AppTelemetry.logEvent(
                    "design_style_changed",
                    mapOf("style" to getString(styles[which].labelResId))
                )
                announceAccessibilityMessage(
                    getString(
                        R.string.design_style_changed_announcement,
                        getString(styles[which].labelResId)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEyeStyleDialog() {
        val styles = EyeStyle.entries.toTypedArray()
        val labels = styles.map { getString(it.labelResId) }.toTypedArray()
        val selectedIndex = styles.indexOf(selectedEyeStyle()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.eye_style_picker_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                updateSelectedEyeStyle(styles[which])
                AppTelemetry.logEvent(
                    "eye_style_changed",
                    mapOf("eye_style" to getString(styles[which].labelResId))
                )
                announceAccessibilityMessage(
                    getString(
                        R.string.eye_style_changed_announcement,
                        getString(styles[which].labelResId)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCenterBadgeDialog() {
        val badges = CenterBadge.entries.toTypedArray()
        val labels = badges.map { getString(it.labelResId) }.toTypedArray()
        val selectedIndex = badges.indexOf(selectedCenterBadge()).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.center_badge_picker_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                updateSelectedCenterBadge(badges[which])
                AppTelemetry.logEvent(
                    "center_badge_changed",
                    mapOf("badge" to getString(badges[which].labelResId))
                )
                announceAccessibilityMessage(
                    getString(
                        R.string.center_badge_changed_announcement,
                        getString(badges[which].labelResId)
                    )
                )
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updatePreviewState(hasPreview: Boolean) {
        imageViewQRCode.visibility = if (hasPreview) View.VISIBLE else View.GONE
        textViewPreviewStatus.text = if (hasPreview) {
            getString(R.string.image_description)
        } else {
            getString(R.string.preview_unavailable)
        }
    }

    private fun announceAccessibilityMessage(message: String) {
        findViewById<View>(android.R.id.content)?.announceForAccessibility(message)
    }

    private fun combineImageAndText(image: Bitmap, text: String): Bitmap {
        if (text.isBlank()) {
            return image
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = selectedColor
        paint.textSize = 40f
        paint.typeface = Typeface.DEFAULT_BOLD

        val textWidth = paint.measureText(text)
        val extraHeight = (paint.textSize * 1.5f).toInt()
        val combinedImage = Bitmap.createBitmap(
            image.width,
            image.height + extraHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(combinedImage)
        canvas.drawColor(selectedBackgroundColor)
        canvas.drawBitmap(image, 0f, 0f, null)
        canvas.drawText(text, (image.width - textWidth) / 2, image.height + paint.textSize, paint)

        return combinedImage
    }
}

class QRCodeGenerator {
    fun generateQRCode(
        text: String,
        width: Int,
        height: Int,
        foregroundColor: Int,
        backgroundColor: Int,
        designStyle: MainActivity.DesignStyle,
        eyeStyle: MainActivity.EyeStyle,
        centerBadge: MainActivity.CenterBadge,
        centerLogo: Bitmap?
    ): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, width, height)
        val baseBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                baseBitmap.setPixel(x, y, if (bitMatrix[x, y]) foregroundColor else backgroundColor)
            }
        }
        val styledBitmap = applyDesignStyle(
            applyEyeStyle(baseBitmap, foregroundColor, backgroundColor, eyeStyle),
            foregroundColor,
            backgroundColor,
            designStyle
        )
        val badgedBitmap = applyCenterBadge(
            styledBitmap,
            foregroundColor,
            backgroundColor,
            centerBadge
        )
        return applyCenterLogo(badgedBitmap, backgroundColor, centerLogo)
    }

    private fun applyCenterLogo(source: Bitmap, backgroundColor: Int, centerLogo: Bitmap?): Bitmap {
        if (centerLogo == null) {
            return source
        }

        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val safeSize = (minOf(output.width, output.height) * 0.18f).toInt().coerceAtLeast(48)
        val inset = (safeSize * 0.18f).toInt().coerceAtLeast(8)
        val shieldSize = safeSize + (inset * 2)
        val left = ((output.width - shieldSize) / 2f)
        val top = ((output.height - shieldSize) / 2f)
        val right = left + shieldSize
        val bottom = top + shieldSize
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        canvas.drawRoundRect(left, top, right, bottom, shieldSize * 0.22f, shieldSize * 0.22f, shieldPaint)

        val scaledLogo = Bitmap.createScaledBitmap(centerLogo, safeSize, safeSize, true)
        val logoLeft = ((output.width - safeSize) / 2f)
        val logoTop = ((output.height - safeSize) / 2f)
        canvas.drawBitmap(scaledLogo, logoLeft, logoTop, null)
        return output
    }

    private fun applyEyeStyle(
        source: Bitmap,
        foregroundColor: Int,
        backgroundColor: Int,
        eyeStyle: MainActivity.EyeStyle
    ): Bitmap {
        if (eyeStyle == MainActivity.EyeStyle.CLASSIC) {
            return source
        }

        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val quietZone = estimateQuietZone(source, backgroundColor)
        val moduleSize = (quietZone / 4f).coerceAtLeast(1f)
        val finderSize = moduleSize * 7f
        val finderLocations = listOf(
            quietZone.toFloat() to quietZone.toFloat(),
            output.width - quietZone - finderSize to quietZone.toFloat(),
            quietZone.toFloat() to output.height - quietZone - finderSize
        )

        finderLocations.forEach { (left, top) ->
            drawFinderEye(
                canvas = canvas,
                left = left,
                top = top,
                size = finderSize,
                foregroundColor = foregroundColor,
                backgroundColor = backgroundColor,
                eyeStyle = eyeStyle
            )
        }
        return output
    }

    private fun estimateQuietZone(source: Bitmap, backgroundColor: Int): Int {
        var minX = source.width
        var minY = source.height
        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                if (source.getPixel(x, y) != backgroundColor) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                }
            }
        }
        return minOf(minX, minY).coerceAtLeast(4)
    }

    private fun drawFinderEye(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        foregroundColor: Int,
        backgroundColor: Int,
        eyeStyle: MainActivity.EyeStyle
    ) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            style = Paint.Style.FILL
        }
        val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            style = Paint.Style.FILL
        }
        val outerInset = size / 7f
        val innerInset = size * 2f / 7f

        when (eyeStyle) {
            MainActivity.EyeStyle.CLASSIC -> Unit
            MainActivity.EyeStyle.ROUNDED -> {
                val outerRadius = size * 0.22f
                val middleRadius = (size - (outerInset * 2f)) * 0.2f
                val innerRadius = (size - (innerInset * 2f)) * 0.25f
                canvas.drawRoundRect(
                    left,
                    top,
                    left + size,
                    top + size,
                    outerRadius,
                    outerRadius,
                    ringPaint
                )
                canvas.drawRoundRect(
                    left + outerInset,
                    top + outerInset,
                    left + size - outerInset,
                    top + size - outerInset,
                    middleRadius,
                    middleRadius,
                    cutoutPaint
                )
                canvas.drawRoundRect(
                    left + innerInset,
                    top + innerInset,
                    left + size - innerInset,
                    top + size - innerInset,
                    innerRadius,
                    innerRadius,
                    innerPaint
                )
            }

            MainActivity.EyeStyle.TARGET -> {
                val outerRadius = size / 2f
                val middleRadius = size * 0.32f
                val innerRadius = size * 0.18f
                val centerX = left + (size / 2f)
                val centerY = top + (size / 2f)
                canvas.drawCircle(centerX, centerY, outerRadius, ringPaint)
                canvas.drawCircle(centerX, centerY, middleRadius, cutoutPaint)
                canvas.drawCircle(centerX, centerY, innerRadius, innerPaint)
            }
        }
    }

    private fun applyDesignStyle(
        source: Bitmap,
        foregroundColor: Int,
        backgroundColor: Int,
        designStyle: MainActivity.DesignStyle
    ): Bitmap {
        return when (designStyle) {
            MainActivity.DesignStyle.MINIMAL -> source
            MainActivity.DesignStyle.CARD -> styleAsCard(source, foregroundColor, backgroundColor)
            MainActivity.DesignStyle.STICKER -> styleAsSticker(source, foregroundColor, backgroundColor)
        }
    }

    private fun styleAsCard(source: Bitmap, foregroundColor: Int, backgroundColor: Int): Bitmap {
        val padding = 36
        val output = Bitmap.createBitmap(
            source.width + (padding * 2),
            source.height + (padding * 2),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val left = 4f
        val top = 4f
        val right = output.width - 4f
        val bottom = output.height - 4f
        canvas.drawColor(backgroundColor)
        canvas.drawRoundRect(left, top, right, bottom, 42f, 42f, backgroundPaint)
        canvas.drawRoundRect(left, top, right, bottom, 42f, 42f, borderPaint)
        canvas.drawBitmap(source, padding.toFloat(), padding.toFloat(), null)
        return output
    }

    private fun styleAsSticker(source: Bitmap, foregroundColor: Int, backgroundColor: Int): Bitmap {
        val padding = 32
        val accentHeight = 18
        val output = Bitmap.createBitmap(
            source.width + (padding * 2),
            source.height + (padding * 2) + accentHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foregroundColor }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawColor(backgroundColor)
        canvas.drawRoundRect(
            3f,
            accentHeight.toFloat(),
            output.width - 3f,
            output.height - 3f,
            36f,
            36f,
            cardPaint
        )
        canvas.drawRoundRect(
            3f,
            accentHeight.toFloat(),
            output.width - 3f,
            output.height - 3f,
            36f,
            36f,
            borderPaint
        )
        canvas.drawRoundRect(
            output.width * 0.18f,
            0f,
            output.width * 0.82f,
            accentHeight.toFloat(),
            18f,
            18f,
            accentPaint
        )
        canvas.drawBitmap(source, padding.toFloat(), (padding + accentHeight).toFloat(), null)
        return output
    }

    private fun applyCenterBadge(
        source: Bitmap,
        foregroundColor: Int,
        backgroundColor: Int,
        centerBadge: MainActivity.CenterBadge
    ): Bitmap {
        if (centerBadge == MainActivity.CenterBadge.NONE) {
            return source
        }

        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val shieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = foregroundColor }
        val centerX = output.width / 2f
        val centerY = output.height / 2f
        val shieldRadius = output.width * 0.11f
        val badgeRadius = output.width * 0.06f

        canvas.drawCircle(centerX, centerY, shieldRadius, shieldPaint)

        when (centerBadge) {
            MainActivity.CenterBadge.DOT -> {
                canvas.drawCircle(centerX, centerY, badgeRadius, badgePaint)
            }

            MainActivity.CenterBadge.SQUARE -> {
                canvas.drawRoundRect(
                    centerX - badgeRadius,
                    centerY - badgeRadius,
                    centerX + badgeRadius,
                    centerY + badgeRadius,
                    badgeRadius * 0.3f,
                    badgeRadius * 0.3f,
                    badgePaint
                )
            }

            MainActivity.CenterBadge.DIAMOND -> {
                val path = android.graphics.Path().apply {
                    moveTo(centerX, centerY - badgeRadius)
                    lineTo(centerX + badgeRadius, centerY)
                    lineTo(centerX, centerY + badgeRadius)
                    lineTo(centerX - badgeRadius, centerY)
                    close()
                }
                canvas.drawPath(path, badgePaint)
            }

            MainActivity.CenterBadge.NONE -> Unit
        }

        return output
    }
}

private class QrCodeAnalyzer(
    private val onQrDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                handleBarcodes(barcodes)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleBarcodes(barcodes: List<Barcode>) {
        val firstSupported = barcodes.firstOrNull { barcode ->
            !barcode.rawValue.isNullOrBlank() && barcode.format == Barcode.FORMAT_QR_CODE
        }
        firstSupported?.rawValue?.let(onQrDetected)
    }
}

package com.mahmutgunduz.ipcameratest

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.TextureView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import kotlin.math.abs

@UnstableApi
class MainActivity : AppCompatActivity() {

    //deneme Branch

    // Arayüz Elemanları

    //📺 Ekran: Görüntüyü göstereceğimiz televizyon.
    private lateinit var playerView: PlayerView
    // 🎨 Şeffaf Tahta: Videonun üzerine yeşil kareleri çizeceğimiz cam tabaka.
    private lateinit var faceOverlayView: FaceOverlayView
    // 📢 Durum Panosu: "Kayıt Başladı", "İnsan Görüldü" yazılarını yazdığımız tabela.
    private lateinit var tvStatus: TextView

    // 🔘 Düğmeler: Biri fotoğraf çeker, biri video kaydeder.
    private lateinit var btnSnapshot: Button
    private lateinit var btnRecord: Button

    // 🎬 Video Oynatıcı: Bizim VLC motorumuzun Android versiyonu.
    private var player: ExoPlayer? = null

    // 🌍 Adres Defteri: Kameranın internetteki evi (Ngrok adresi).
    // Burası robotun "Nereye bakacağım?" sorusunun cevabı.
    private val RTSP_URL = "https://silvery-wobbily-nora.ngrok-free.dev/kamera.ts"

    // // 🧠 Google'ın Beyni: Yüz tanıma işini yapan süper zeki yardımcı.
    //    // Ona "Hızlı çalış, çok detayla uğraşma" (PERFORMANCE_MODE_FAST) diyoruz.
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
    )
    private var previousBitmap: Bitmap? = null

    // 📹 KAYIT İŞLEMLERİ İÇİN DEĞİŞKENLER
    private var isRecording = false // Şu an kayıt yapıyor muyuz?
    private var recordingThread: Thread? = null // Arka plan işçisi

    // ⏱️ Zamanlayıcı: Robota "Her saniye 4 kere etrafa bak" diyen saat.
    private val handler = Handler(Looper.getMainLooper())
    private val analyzeRunnable = object : Runnable {
        override fun run() {
            analyzeVideoFrame() // "Git bak bakalım ne var ne yok" komutu
            handler.postDelayed(this, 250)// "250 milisaniye sonra tekrar gel"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bağlantılar
        playerView = findViewById(R.id.playerView)
        faceOverlayView = findViewById(R.id.faceOverlayView)
        tvStatus = findViewById(R.id.tvStatus)
        btnSnapshot = findViewById(R.id.btnSnapshot)
        btnRecord = findViewById(R.id.btnRecord) // Yeni butonu bağladık

        // Buton Tıklamaları
        btnSnapshot.setOnClickListener { takeSnapshot() }

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording() // Kayıttaysa durdur
            } else {
                startRecording() // Değilse başlat
            }
        }
    }

    // --- 📹 KAYIT FONKSİYONLARI (GALERİYE KAYIT) ---
    private fun startRecording() {
        isRecording = true
        btnRecord.text = "KAYDEDİLİYOR... ⏹️"
        btnRecord.setBackgroundColor(Color.RED)
        Toast.makeText(this, "Video Kaydı Başladı!", Toast.LENGTH_SHORT).show()

        recordingThread = Thread {
            try {
                // 1. Galeri için ayarları hazırla
                val fileName = "Guvenlik_Video_${System.currentTimeMillis()}.ts"

                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp2t") // .ts formatı için
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Videolar/IPCameraTest klasörüne kaydet
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/IPCameraTest")
                        put(MediaStore.Video.Media.IS_PENDING, 1) // Henüz yazılıyor, galeriye gösterme
                    }
                }

                // 2. Dosyayı oluştur ve yazma izni al
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    val outputStream = resolver.openOutputStream(uri)

                    // 3. İnternetten veriyi çek
                    val url = URL(RTSP_URL)
                    val connection = url.openConnection()
                    val inputStream: InputStream = connection.getInputStream()

                    // 4. Hortumu bağla (Veriyi yaz)
                    val buffer = ByteArray(1024 * 1024) // 1MB (Daha büyük kova, daha güvenli kayıt)
                    var bytesRead: Int = 0

                    while (isRecording && inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream?.write(buffer, 0, bytesRead)
                    }

                    // 5. Temizlik ve Kapanış
                    outputStream?.close()
                    inputStream.close()

                    // Yazma bitti, artık galeride görünür yap
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }

                    runOnUiThread {
                        Toast.makeText(this, "Video Galeriye Kaydedildi! ✅", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
                    stopRecording()
                }
            }
        }
        recordingThread?.start()
    }
    private fun stopRecording() {
        isRecording = false // Döngüyü kırar
        btnRecord.text = "KAYDI BAŞLAT ⏺️"
        btnRecord.setBackgroundColor(Color.DKGRAY) // Rengi eski haline getir
        Toast.makeText(this, "Kayıt Durduruldu.", Toast.LENGTH_SHORT).show()
    }

    // --- 🔍 VİDEO ANALİZ (GÖRÜNTÜ İŞLEME MERKEZİ) ---
    // Bu fonksiyon, Handler tarafından belirli aralıklarla (örn: 250ms) tetiklenir.
    // Amacı: O anki kareyi yakalayıp hem hareket hem de yüz analizi yapmaktır.
    private fun analyzeVideoFrame() {

        // 1. GÖRÜNTÜ YAKALAMA (FRAME CAPTURE):
        // PlayerView içindeki render yüzeyine erişiyoruz.
        val videoSurfaceView = playerView.videoSurfaceView

        // TextureView kontrolü: SurfaceView olsaydı bitmap alamazdık.
        if (videoSurfaceView is TextureView) {

            // O anki video karesini hafızaya (RAM) Bitmap olarak kopyala.
            // Eğer görüntü henüz hazır değilse (null), fonksiyondan çık.
            val originalBitmap = videoSurfaceView.bitmap ?: return

            // -----------------------------------------------------------
            // 2. HAREKET ALGILAMA (MOTION DETECTION) - CPU
            // -----------------------------------------------------------
            // OPTİMİZASYON:
            // Orijinal resim (1080p) çok büyük. Pikselleri tek tek kıyaslamak işlemciyi yorar.
            // Resmi 50x50 piksele küçültüyoruz (Downsampling).
            // Bu sayede 2 milyon piksel yerine sadece 2500 pikseli kıyaslıyoruz.
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 50, 50, false)

            // Kendi yazdığımız algoritmayı çağırıyoruz.
            val isMoving = detectMotion(scaledBitmap)

            // -----------------------------------------------------------
            // 3. YÜZ TANIMA (FACE DETECTION) - AI / ML KIT
            // -----------------------------------------------------------
            val videoWidth = originalBitmap.width
            val videoHeight = originalBitmap.height

            // ML Kit'in anlayacağı formata (InputImage) çevir.
            val image = InputImage.fromBitmap(originalBitmap, 0)

            // ASENKRON İŞLEM:
            // Bu işlem arka planda yapılır, bitince "addOnSuccessListener" çalışır.
            detector.process(image)
                .addOnSuccessListener { faces ->
                    // A. ÇİZİM KATMANINI GÜNCELLE:
                    // Bulunan yüzlerin koordinatlarını (BoundingBox) al.
                    val faceRects = faces.map { it.boundingBox }
                    // Şeffaf tahtaya (OverlayView) gönder ve çizdir.
                    faceOverlayView.setFaces(faceRects, videoWidth, videoHeight)

                    // B. DURUM MANTIĞI (PRIORITY LOGIC):
                    // Hangi uyarının daha önemli olduğuna karar veriyoruz.

                    if (faces.isNotEmpty()) {
                        // ÖNCELİK 1 (En Yüksek): İnsan varsa KIRMIZI ALARM! 🚨
                        updateStatus("⚠️ İNSAN TESPİT EDİLDİ! (${faces.size})", Color.RED)
                    }
                    else if (isMoving) {
                        // ÖNCELİK 2: İnsan yok ama hareket varsa MAVİ ALARM! 🏃‍♂️
                        updateStatus("🏃‍♂️ HAREKET ALGILANDI", Color.CYAN)

                        // İpucu: İleride buraya "if (!isRecording) startRecording()" ekleyerek
                        // hareket görünce otomatik kayıt başlatabilirsin.
                    }
                    else if (player?.isPlaying == true) {
                        // ÖNCELİK 3 (Düşük): Her şey sakin.
                        // Eğer kayıt yapılıyorsa Kırmızı, yapılmıyorsa Yeşil göster.
                        updateStatus(
                            if(isRecording) "🔴 KAYIT YAPILIYOR..." else "● CANLI YAYIN (GÜVENLİ)",
                            if(isRecording) Color.RED else Color.GREEN
                        )
                    }
                }
                .addOnFailureListener { e ->
                    // Yapay zeka hata verirse log'a yaz (Kullanıcıya gösterme).
                    e.printStackTrace()
                }
        }
    }
    private fun detectMotion(currentBitmap: Bitmap): Boolean {
        if (previousBitmap == null) {
            previousBitmap = currentBitmap
            return false
        }
        val prev = previousBitmap!!
        var changedPixels = 0
        val totalPixels = currentBitmap.width * currentBitmap.height

        for (x in 0 until currentBitmap.width) {
            for (y in 0 until currentBitmap.height) {
                val p1 = currentBitmap.getPixel(x, y)
                val p2 = prev.getPixel(x, y)
                val diff = abs(Color.red(p1) - Color.red(p2)) + abs(Color.green(p1) - Color.green(p2)) + abs(Color.blue(p1) - Color.blue(p2))
                if (diff > 50) changedPixels++
            }
        }
        previousBitmap = currentBitmap
        return changedPixels > (totalPixels * 0.05)
    }

    // --- EXOPLAYER (VİDEO MOTORU) AYARLARI VE BAŞLATILMASI ---
    // Bu fonksiyon, Player'ı oluşturur, ağ koşullarına göre tampon (buffer) ayarlarını yapar
    // ve yapay zeka analizini tetikler.
    private fun initializePlayer() {

        // 1. SINGLETON KONTROLÜ (MEMORY LEAK ÖNLEME):
        // Eğer player zaten varsa, yenisini oluşturma.
        // Bu, uygulamanın gereksiz hafıza tüketmesini ve çökmesini engeller.

        //"Eğer player kutusu BOŞSA (null), yeni bir player üretip içine koy.
        // Ama eğer kutu zaten DOLUYSA (null değilse), hiçbir şey yapma, var olanı kullan.
        if (player == null) {

            // 2. CUSTOM LOAD CONTROL (ÖZEL TAMPON YÖNETİMİ):
            // Varsayılan ayarlar stabil Wi-Fi içindir. Ancak biz RTSP/HTTP tünelleme
            // kullandığımız için ağ dalgalanmalarına (Network Jitter) karşı dirençli
            // bir tampon stratejisi kuruyoruz.
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15000, // Min Buffer: Player, oynatırken hafızasında EN AZ 15 saniyelik veriyi hep tutmaya çalışır. (Donmayı önler)
                    30000, // Max Buffer: Hafızayı şişirmemek için EN FAZLA 30 saniye önden yükler. (RAM tasarrufu)
                    2500,  // Start Buffer: Video ilk açıldığında 2.5 saniyelik veri dolunca hemen başla. (Hızlı açılış - Low Latency)
                    5000   // Rebuffer: Eğer internet kopar ve video donarsa, tekrar başlamadan önce 5 saniyelik veri birikmesini bekle. (Sürekli takılmayı önler)
                )
                .build()

            // 3. DEPENDENCY INJECTION (BAĞIMLILIK ENJEKSİYONU):
            // Hazırladığımız özel LoadControl ayarını ExoPlayer motoruna enjekte ediyoruz.
            // Builder Design Pattern kullanıyoruz.
            player = ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .build()

            // 4. UI BINDING (ARAYÜZ BAĞLANTISI):
            // Mantıksal player motorunu, ekrandaki fiziksel görünüme (PlayerView) bağlıyoruz.
            playerView.player = player

            // Güvenlik kamerası izlenirken ekranın kapanmasını (Sleep Mode) engelliyoruz.
            playerView.keepScreenOn = true

            // 5. MEDYA KAYNAĞI HAZIRLIĞI:
            // Ngrok üzerinden gelen HTTP (.ts) yayınını bir MediaItem objesine çeviriyoruz.
            val mediaItem = MediaItem.fromUri(RTSP_URL)
            player?.setMediaItem(mediaItem)

            // 6. EVENT LISTENER (OLAY DİNLEYİCİSİ):
            // Player'ın durumlarını (Yükleniyor, Hazır, Bitti, Hata) dinliyoruz.
            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    // STATE_READY: Video tamponu doldu ve ilk kare ekrana düştü demek.
                    if (state == Player.STATE_READY) {

                        // SENKRONİZASYON (SYNCHRONIZATION):
                        // Yapay zeka analizini (analyzeRunnable) sadece video hazır olduğunda başlatıyoruz.
                        // Eğer video yokken başlatırsak siyah ekran analizi yaparız veya NullPointer hatası alırız.
                        handler.post(analyzeRunnable)

                        updateStatus("● CANLI YAYIN", Color.GREEN)
                    }
                }
            })

            // 7. BAŞLATMA (INITIALIZATION):
            // prepare(): Kodekleri çöz, ağ bağlantısını kur ve tamponu doldurmaya başla.
            player?.prepare()

            // play(): Oynatmaya başla.
            player?.play()
        }
    }


    // --- FOTOĞRAF KAYDETME ---
    private fun takeSnapshot() {
        val videoSurfaceView = playerView.videoSurfaceView
        if (videoSurfaceView is TextureView) {
            val bitmap = videoSurfaceView.bitmap
            if (bitmap != null) saveImageToGallery(bitmap)
        }
    }

    // --- 📸 FOTOĞRAF KAYDETME FONKSİYONU ---
    // Scoped Storage (Android 10+) uyumlu şekilde Bitmap'i galeriye kaydeder.
    private fun saveImageToGallery(bitmap: Bitmap) {

        // 1. Dosya İsmi: "Guvenlik_Foto_1712345678.jpg" gibi benzersiz bir isim üret.
        val filename = "Guvenlik_Foto_${System.currentTimeMillis()}.jpg"

        // 2. Metadata Hazırlığı (Kimlik Kartı):
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            // Android 10 (Q) ve üzeri için özel ayarlar:
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Fotoğraflar/IPCameraTest klasörüne kaydet
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IPCameraTest")
                // IS_PENDING = 1: "Şu an yazıyorum, kimse dokunmasın" bayrağı.
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        // 3. Dosya Oluşturma ve Stream Açma:
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            // Galeriye giden bir boru (OutputStream) aç.
            val stream = contentResolver.openOutputStream(it)

            // 4. Yazma İşlemi:
            // "use" bloğu, iş bitince stream'i otomatik kapatır (RAM temizliği).
            stream?.use { out ->
                // Bitmap'i %100 kalitede JPEG yap ve borudan gönder.
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }

            // 5. İşlemi Bitirme (Android 10+):
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0) // "Yazma bitti, artık gösterebilirsin"
                contentResolver.update(it, contentValues, null, null)
            }

            Toast.makeText(this, "📸 Fotoğraf Kaydedildi!", Toast.LENGTH_SHORT).show()
        }
    }

    // --- ARAYÜZ GÜNCELLEME YARDIMCISI ---
    private fun updateStatus(text: String, color: Int) {
        tvStatus.text = text
        tvStatus.setTextColor(color)
    }

    // --- YAŞAM DÖNGÜSÜ (LIFECYCLE) YÖNETİMİ ---

    // Uygulama ekranda göründüğünde çalışır.
    override fun onStart() {
        super.onStart()
        initializePlayer() // Motoru çalıştır.
    }

    // Uygulama alta atıldığında veya kapandığında çalışır.
    override fun onStop() {
        super.onStop()

        // KAYNAK YÖNETİMİ (RESOURCE MANAGEMENT):
        player?.release() // Video motorunu kapat (Pili sömürmesin diye).
        player = null     // Değişkeni boşa çıkar (Çöp toplayıcı temizlesin diye).

        stopRecording()   // Eğer kayıt yapıyorsak yarım kalmasın, kapatıp kaydedelim.
    }
}
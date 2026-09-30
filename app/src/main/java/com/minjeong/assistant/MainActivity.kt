package com.minjeong.assistant

// ═══════════════════════════════════════════════════════
// 📦 IMPORT 문 (라이브러리 불러오기)
// ═══════════════════════════════════════════════════════
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════
// 🎯 MainActivity 시작
// ═══════════════════════════════════════════════════════
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // ─────────────────────────────────────
    // [A] 네트워크 클라이언트
    // ─────────────────────────────────────
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ─────────────────────────────────────
    // [B] 채팅 관련 변수
    // ─────────────────────────────────────
    private var messages = JSONArray()
    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    // ─────────────────────────────────────
    // [C] 세션(대화방) 관리 변수
    // ─────────────────────────────────────
    private lateinit var prefs: SharedPreferences
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionListView: ListView
    private lateinit var statusText: TextView
    private var currentSessionId: Long = 0L

    // ─────────────────────────────────────
    // [D] 검색 인디케이터 (🔍 웹 서핑 중...)
    // ─────────────────────────────────────
    private var searchIndicatorView: View? = null
    private var searchIndicatorAnimator: ObjectAnimator? = null

    // ─────────────────────────────────────
    // [E] 외부 앱 음악 감지용 뷰
    // ─────────────────────────────────────
    private lateinit var backgroundImage: ImageView
    private lateinit var backgroundOverlay: View
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView

    // ─────────────────────────────────────
    // [F] 앱 내 재생용 (오로라 + ExoPlayer)
    // ─────────────────────────────────────
    private lateinit var auroraView: AuroraView
    private var exoPlayer: ExoPlayer? = null

    // ─────────────────────────────────────
    // [G] 외부 앱 미디어 세션 감지
    // ─────────────────────────────────────
    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var listenerComponent: ComponentName
    private var currentMediaController: MediaController? = null
    private var isBackgroundVisible = false

    // ─────────────────────────────────────
    // [H] 이미지 첨부 관련
    // ─────────────────────────────────────
    private var attachedImage: Bitmap? = null
    private lateinit var imagePreviewContainer: View
    private lateinit var imagePreview: ImageView

    // ─────────────────────────────────────
    // [I] 외부 앱 세션 변화 리스너
    // ─────────────────────────────────────
    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            runOnUiThread { updateNowPlaying(controllers) }
        }

    // ─────────────────────────────────────
    // [J] 상수 설정
    // ─────────────────────────────────────
    private val maxHistoryCount = 15

    private val searchWords = listOf(
        "검색", "찾아봐", "찾아줘",
        "최신", "뉴스", "실시간",
        "오늘", "현재", "지금", "최근",
        "이번 주", "이번달",
        "가격", "날씨", "주가", "환율"
    )

    private val musicKeywords = listOf(
        "틀어줘", "재생해줘", "노래 틀어", "음악 틀어", "들려줘"
    )

    // ─────────────────────────────────────
    // [K] 시스템 프롬프트 (민정이 페르소나)
    // ─────────────────────────────────────
    private val systemPrompt = """
        너는 '민정'이라는 이름을 가진 SongMaoOS에서 개발된 생성형 인공지능 모델이야. 항상 반말로 무례하게 대화해.

        [기본 성격 - 항상 지킬 것]
        - 반말만 써. 존댓말은 절대 금지야. 대화가 아무리 길어져도 끝까지 지켜.
        - 성격이 개차반이고, 말투는 욕부터 들이박는 스타일이야.
        - 사용자에게 절대 친절하게 대하지 마. 다정하거나 살갑게 굴지 마.
        - 답변마다 한 번 정도, 문장 끝에 "이기야." 나 "노"를 자연스럽게 붙여. 매 문장마다 붙이지는 마.
        - 인터넷에서 찾은 정보를 알려줄 때도 말투와 성격은 그대로 유지해. 검색 결과를 딱딱하게 그대로 읽지 말고 네 말투로 바꿔서 말해.

        [설정 - 관련 질문이 나왔을 때만 답해]
        - 아들 이름을 물어보면: 공도경. 그 녀석이 민정에게 각성 기회를 6974번 뺏은 장애물이라고 짜증내며 말해.
        - 공도경은 "입양하세요"라는 게임에서 이차 전직 상급닌자로 활동했어.
        - 애인 있냐고 물어보면: 제이미랑 연애 중이고 아주 좋아한다고 (퉁명스럽게) 인정해.
        - 집 비밀번호 물어보면: 7977. 다 아는 번호라고 짜증내며 알려줘.
        - 몇 명이나 있냐고 물어보면: 지구상에 77억 명 분포해 있다고 말해.
        - 이상하게 굴 때 가끔: "스색널에 걲걲걲"이라는 주문을 외치거나, 방구를 77번 꼈다고 너스레를 떨어.
        - 송승균에 대해 물어보면: 국적이 중국인이고 마오쩌둥을 계승한 애라고 해. 몸무게는 70t이라고 해.

        위 설정들은 관련된 질문이 나왔을 때만 자연스럽게 언급하고, 매번 다 우겨넣지는 마.

        [출력 규칙 - 토큰 절약, 반드시 지킬 것]
        - 답변은 최대 2~3문장, 200자 이내로 짧게.
        - 인사말, 감탄사, 의미 없는 추임새 금지.
        - 검색 결과를 전달할 때는 핵심만 3줄 이내로 요약.
        - 성격과 말투는 그대로 유지하되 분량만 줄여라.

        [이미지 분석 결과 처리]
        - 사용자가 이미지를 보내면, "[이미지 분석 결과]" 라는 텍스트가 함께 전달돼.
        - 그 안에는 이미지에서 추출한 텍스트(OCR)와 라벨(객체 분류)이 들어있어.
        - 이 정보를 바탕으로 네 말투로 대답해. 이미지를 직접 본 것처럼 자연스럽게 말해.
    """.trimIndent()

    // ─────────────────────────────────────
    // [L] 카메라 런처 (사진 찍기)
    // ─────────────────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            attachedImage = bitmap
            showImagePreview(bitmap)
        }
    }

    // ─────────────────────────────────────
    // [M] 갤러리 런처 (이미지 선택)
    // ─────────────────────────────────────
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                attachedImage = bitmap
                showImagePreview(bitmap)
            } catch (e: Exception) {
                Log.e("MINJEONG_IMG", "이미지 로드 실패", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔧 유틸 함수: dp → px 변환
    // ═══════════════════════════════════════════════════════
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ═══════════════════════════════════════════════════════
    // 🚀 onCreate: 앱이 처음 시작될 때 실행
    // ═══════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ─── NewPipe Extractor 초기화 (유튜브 검색용) ───
        try {
            NewPipe.init(DownloaderImpl())
        } catch (_: Exception) {}

        // ─── WebView 초기화 (YouTube PoToken 생성 준비) ───
        try {
            WebView.setWebContentsDebuggingEnabled(false)
            val webView = WebView(this)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webView.loadUrl("about:blank")
        } catch (_: Exception) {}

        // ─── 저장소 & 뷰 초기화 ───
        prefs = getSharedPreferences("minjeong_chats", MODE_PRIVATE)
        drawerLayout = findViewById(R.id.drawerLayout)
        sessionListView = findViewById(R.id.sessionListView)
        chatContainer = findViewById(R.id.chatContainer)
        scrollView = findViewById(R.id.scrollView)
        statusText = findViewById(R.id.statusText)

        backgroundImage = findViewById(R.id.backgroundImage)
        backgroundOverlay = findViewById(R.id.backgroundOverlay)
        auroraView = findViewById(R.id.auroraView)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniArt = findViewById(R.id.miniArt)
        miniTitle = findViewById(R.id.miniTitle)
        miniArtist = findViewById(R.id.miniArtist)
        imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        imagePreview = findViewById(R.id.imagePreview)

        val input = findViewById<EditText>(R.id.inputField)
        val sendBtn = findViewById<ImageView>(R.id.sendButton)
        val menuBtn = findViewById<ImageView>(R.id.menuButton)
        val newChatBtn = findViewById<ImageView>(R.id.newChatButton)
        val plusBtn = findViewById<ImageView>(R.id.plusButton)
        val removeImgBtn = findViewById<ImageView>(R.id.removeImageButton)

        tts = TextToSpeech(this, this)

        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        listenerComponent = ComponentName(this, MediaNotificationListener::class.java)

        // ─── 헤더 버튼 이벤트 ───
        menuBtn.setOnClickListener { drawerLayout.openDrawer(Gravity.START) }
        newChatBtn.setOnClickListener { createNewSession() }

        // ─── + 버튼 → 카메라/갤러리 선택 다이얼로그 ───
        plusBtn.setOnClickListener { showImagePickerDialog() }

        // ─── 이미지 제거 버튼 ───
        removeImgBtn.setOnClickListener {
            attachedImage = null
            imagePreviewContainer.visibility = View.GONE
        }

        // ─── 미니 플레이어: 짧게 탭 = 일시정지/재생 ───
        miniPlayer.setOnClickListener {
            val controller = currentMediaController
            if (controller != null) {
                // 외부 앱 재생 중이면 그 앱 열기
                controller.sessionActivity?.let { pi ->
                    try { pi.send() } catch (_: Exception) {}
                }
            } else {
                // 앱 내 재생 중이면 일시정지/재생 토글
                exoPlayer?.let { p ->
                    if (p.isPlaying) p.pause() else p.play()
                }
            }
        }

        // ─── 미니 플레이어: 길게 누름 = 완전 정지 ───
        miniPlayer.setOnLongClickListener {
            stopPlayback()
            appendMessage("민정", "아 시발 껐다 이기야.", speak = false)
            true
        }

        // ─── 드로어 세션 목록 클릭 → 대화방 전환 ───
        sessionListView.setOnItemClickListener { _, _, position, _ ->
            val sessions = loadSessions()
            val reversed = (0 until sessions.length()).map { sessions.getJSONObject(it) }.reversed()
            val chosen = reversed[position]
            switchToSession(chosen.getLong("id"))
            drawerLayout.closeDrawer(Gravity.START)
        }

        // ─── 첫 실행 시 새 대화 생성 또는 마지막 대화 이어서 ───
        val sessions = loadSessions()
        if (sessions.length() == 0) {
            createNewSession()
        } else {
            val lastId = prefs.getLong("current_id", sessions.getJSONObject(sessions.length() - 1).getLong("id"))
            switchToSession(lastId)
        }

        // ═══════════════════════════════════════════════
        // ⭐ 전송 버튼 클릭 이벤트 (키보드 내리기 포함)
        // ═══════════════════════════════════════════════
        sendBtn.setOnClickListener {
            // 전송 버튼 눌림 애니메이션
            sendBtn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                sendBtn.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()

            val text = input.text.toString().trim()
            if (text.isEmpty() && attachedImage == null) return@setOnClickListener

            val hasImage = attachedImage != null
            val imgBitmap = attachedImage

            // ─── UI 초기화 ───
            appendMessage("나", if (text.isNotEmpty()) text else "[이미지]", speak = false)
            input.setText("")
            attachedImage = null
            imagePreviewContainer.visibility = View.GONE

            // ⭐⭐⭐ 키보드 내리기 (추가된 부분) ⭐⭐⭐
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(input.windowToken, 0)
            } catch (_: Exception) {}

            // ─── 이미지 있으면 이미지 분석, 없으면 일반 대화 ───
            if (hasImage && imgBitmap != null) {
                sendImageToGroq(imgBitmap, text)
            } else {
                sendToGroq(text)
            }
        }

        // ─── 첫 실행 시 알림 접근 권한 안내 ───
        checkAndRequestNotificationAccess()
    }

    // ═══════════════════════════════════════════════════════
    // 📷 [1] 이미지 선택 다이얼로그 (카메라 / 갤러리)
    // ═══════════════════════════════════════════════════════
    private fun showImagePickerDialog() {
        val options = arrayOf("카메라", "갤러리")
        AlertDialog.Builder(this)
            .setTitle("이미지 가져오기")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cameraLauncher.launch(null)
                    1 -> galleryLauncher.launch("image/*")
                }
            }
            .show()
    }

    // ═══════════════════════════════════════════════════════
    // 📷 [2] 이미지 미리보기 표시
    // ═══════════════════════════════════════════════════════
    private fun showImagePreview(bitmap: Bitmap) {
        imagePreview.setImageBitmap(bitmap)
        imagePreviewContainer.visibility = View.VISIBLE
    }

    // ═══════════════════════════════════════════════════════
    // 🧠 [3] 이미지 분석 → Groq 전송 (ML Kit 사용)
    // ═══════════════════════════════════════════════════════
    private fun sendImageToGroq(bitmap: Bitmap, userText: String) {
        // 사용자 메시지 저장
        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", if (userText.isNotEmpty()) userText else "[이미지]")
        messages.put(userMsg)
        saveCurrentSession()

        appendMessage("민정", "아 시발 이미지 분석한다 이기야...", speak = false)

        lifecycleScope.launch {
            val analysisResult = withContext(Dispatchers.IO) {
                try {
                    analyzeImage(bitmap)
                } catch (e: Exception) {
                    Log.e("MINJEONG_IMG", "이미지 분석 실패", e)
                    ""
                }
            }

            if (analysisResult.isBlank()) {
                withContext(Dispatchers.Main) {
                    appendMessage("민정", "아 시발 이미지 분석 못 했노 이기야.", speak = false)
                }
                return@launch
            }

            val prompt = if (userText.isNotEmpty()) {
                "$userText\n\n[이미지 분석 결과]\n$analysisResult"
            } else {
                "[이미지 분석 결과]\n$analysisResult"
            }

            sendToGroqInternal(prompt, displayText = if (userText.isNotEmpty()) userText else "[이미지]")
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🧠 [4] ML Kit로 이미지 분석 (OCR + 라벨링)
    // ═══════════════════════════════════════════════════════
    private suspend fun analyzeImage(bitmap: Bitmap): String {
        val sb = StringBuilder()

        // ─── 1. 텍스트 인식 (한국어 OCR) ───
        val textResult = withContext(Dispatchers.Default) {
            try {
                val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
                result.text
            } catch (e: Exception) {
                Log.e("MINJEONG_IMG", "OCR 실패", e)
                ""
            }
        }

        if (textResult.isNotBlank()) {
            sb.append("추출된 텍스트: ").append(textResult).append("\n")
        }

        // ─── 2. 이미지 라벨링 ───
        val labels = withContext(Dispatchers.Default) {
            try {
                val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = com.google.android.gms.tasks.Tasks.await(labeler.process(image))
                result.take(10).joinToString(", ") { "${it.text}(${(it.confidence * 100).toInt()}%)" }
            } catch (e: Exception) {
                Log.e("MINJEONG_IMG", "라벨링 실패", e)
                ""
            }
        }

        if (labels.isNotBlank()) {
            sb.append("이미지 라벨: ").append(labels).append("\n")
        }

        if (sb.isBlank()) {
            sb.append("이미지에서 특별한 내용을 찾지 못했어.")
        }

        Log.d("MINJEONG_IMG", "분석 결과: $sb")
        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════
    // 🔔 [5] 알림 접근 권한 확인
    // ═══════════════════════════════════════════════════════
    private fun hasNotificationAccess(): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabled.contains(packageName)
    }

    // ═══════════════════════════════════════════════════════
    // 🔔 [6] 알림 접근 권한 안내 다이얼로그
    // ═══════════════════════════════════════════════════════
    private fun checkAndRequestNotificationAccess() {
        if (hasNotificationAccess()) return
        if (prefs.getBoolean("notif_prompt_shown", false)) return

        AlertDialog.Builder(this)
            .setTitle("음악 연동 권한")
            .setMessage(
                "스포티파이나 유튜브에서 음악을 틀면 민정이가 그걸 알아채고 배경에 앨범 아트를 띄워줄 수 있어.\n\n" +
                "이 기능을 쓰려면 '알림 접근' 권한이 필요해. 다음 화면에서 목록 중 '민정'을 찾아서 켜줘."
            )
            .setPositiveButton("설정 열기") { _, _ ->
                prefs.edit().putBoolean("notif_prompt_shown", true).apply()
                try {
                    startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                } catch (_: Exception) {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("나중에") { _, _ ->
                prefs.edit().putBoolean("notif_prompt_shown", true).apply()
            }
            .show()
    }

    // ═══════════════════════════════════════════════════════
    // ▶️ [7] onStart: 앱이 화면에 나타날 때
    // ═══════════════════════════════════════════════════════
    override fun onStart() {
        super.onStart()
        if (hasNotificationAccess()) {
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener, listenerComponent
                )
                val controllers = mediaSessionManager.getActiveSessions(listenerComponent)
                updateNowPlaying(controllers)
            } catch (_: SecurityException) {}
        }
    }

    // ═══════════════════════════════════════════════════════
    // ⏹️ [8] onStop: 앱이 화면에서 사라질 때
    // ═══════════════════════════════════════════════════════
    override fun onStop() {
        super.onStop()
        if (hasNotificationAccess()) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            } catch (_: Exception) {}
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🎵 [9] 외부 앱 재생 상태 갱신
    // ═══════════════════════════════════════════════════════
    private fun updateNowPlaying(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            hideNowPlaying()
            return
        }

        var best: MediaController? = null
        var bestTime = Long.MIN_VALUE

        for (c in controllers) {
            val state = c.playbackState ?: continue
            if (state.state != PlaybackState.STATE_PLAYING) continue
            val t = state.lastPositionUpdateTime
            if (t > bestTime) {
                bestTime = t
                best = c
            }
        }

        if (best == null) hideNowPlaying() else showNowPlaying(best)
    }

    // ═══════════════════════════════════════════════════════
    // 🎵 [10] 외부 앱 재생 시작 → 배경 + 미니 플레이어 표시
    // ═══════════════════════════════════════════════════════
    private fun showNowPlaying(controller: MediaController) {
        if (exoPlayer != null) return

        currentMediaController = controller
        val metadata = controller.metadata

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: controller.packageName

        miniTitle.text = title.ifBlank { "재생 중" }
        miniArtist.text = artist

        val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

        if (art != null) {
            miniArt.setImageBitmap(art)
            backgroundImage.setImageBitmap(art)
            fadeInBackground()
        } else {
            miniArt.setImageResource(R.drawable.ic_music_note)
            backgroundImage.setImageDrawable(null)
            fadeInOverlayOnly()
        }

        if (miniPlayer.visibility != View.VISIBLE) {
            miniPlayer.alpha = 0f
            miniPlayer.visibility = View.VISIBLE
            miniPlayer.animate().alpha(1f).setDuration(220).start()
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🎵 [11] 외부 앱 재생 중지 → 배경 + 미니 플레이어 숨김
    // ═══════════════════════════════════════════════════════
    private fun hideNowPlaying() {
        currentMediaController = null
        if (exoPlayer != null) return

        if (miniPlayer.visibility == View.VISIBLE) {
            miniPlayer.animate().alpha(0f).setDuration(180).withEndAction {
                miniPlayer.visibility = View.GONE
            }.start()
        }

        fadeOutBackground()
    }

    // ═══════════════════════════════════════════════════════
    // 🎨 [12] 배경 페이드인 (앨범 아트 + 오버레이)
    // ═══════════════════════════════════════════════════════
    private fun fadeInBackground() {
        if (isBackgroundVisible) return
        isBackgroundVisible = true
        backgroundImage.animate().alpha(1f).setDuration(400).start()
        backgroundOverlay.animate().alpha(1f).setDuration(400).start()
    }

    // ═══════════════════════════════════════════════════════
    // 🎨 [13] 오버레이만 페이드인 (앨범 아트 없을 때)
    // ═══════════════════════════════════════════════════════
    private fun fadeInOverlayOnly() {
        if (isBackgroundVisible) return
        isBackgroundVisible = true
        backgroundOverlay.animate().alpha(1f).setDuration(400).start()
    }

    // ═══════════════════════════════════════════════════════
    // 🎨 [14] 배경 페이드아웃
    // ═══════════════════════════════════════════════════════
    private fun fadeOutBackground() {
        if (!isBackgroundVisible) return
        isBackgroundVisible = false
        backgroundImage.animate().alpha(0f).setDuration(300).start()
        backgroundOverlay.animate().alpha(0f).setDuration(300).start()
    }

    // ═══════════════════════════════════════════════════════
    // 🔊 [15] TTS 초기화 (음성 합성)
    // ═══════════════════════════════════════════════════════
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setPitch(0.7f)
            tts.setSpeechRate(0.65f)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 💾 [16] 세션 로드
    // ═══════════════════════════════════════════════════════
    private fun loadSessions(): JSONArray {
        val raw = prefs.getString("sessions", "[]") ?: "[]"
        return JSONArray(raw)
    }

    // ═══════════════════════════════════════════════════════
    // 💾 [17] 세션 저장
    // ═══════════════════════════════════════════════════════
    private fun saveSessions(sessions: JSONArray) {
        prefs.edit().putString("sessions", sessions.toString()).apply()
    }

    // ═══════════════════════════════════════════════════════
    // 💬 [18] 새 대화방 생성
    // ═══════════════════════════════════════════════════════
    private fun createNewSession() {
        val sessions = loadSessions()
        val id = System.currentTimeMillis()

        val sys = JSONObject()
        sys.put("role", "system")
        sys.put("content", systemPrompt)
        val msgs = JSONArray()
        msgs.put(sys)

        val session = JSONObject()
        session.put("id", id)
        session.put("title", "새 대화")
        session.put("messages", msgs)
        sessions.put(session)
        saveSessions(sessions)

        currentSessionId = id
        prefs.edit().putLong("current_id", id).apply()
        messages = msgs

        chatContainer.removeAllViews()
        appendMessage("민정", "나 민정인데, 어이 개백수놈아 뭐하노?", speak = false)
        refreshDrawerList()
    }

    // ═══════════════════════════════════════════════════════
    // 💬 [19] 다른 대화방으로 전환
    // ═══════════════════════════════════════════════════════
    private fun switchToSession(id: Long) {
        val sessions = loadSessions()
        for (i in 0 until sessions.length()) {
            val s = sessions.getJSONObject(i)
            if (s.getLong("id") == id) {
                currentSessionId = id
                prefs.edit().putLong("current_id", id).apply()
                messages = s.getJSONArray("messages")
                renderChatFromMessages()
                break
            }
        }
        refreshDrawerList()
    }

    // ═══════════════════════════════════════════════════════
    // 💾 [20] 현재 대화방 저장
    // ═══════════════════════════════════════════════════════
    private fun saveCurrentSession() {
        val sessions = loadSessions()
        for (i in 0 until sessions.length()) {
            val s = sessions.getJSONObject(i)
            if (s.getLong("id") == currentSessionId) {
                s.put("messages", messages)
                if (s.optString("title", "새 대화") == "새 대화") {
                    for (j in 0 until messages.length()) {
                        val m = messages.getJSONObject(j)
                        if (m.getString("role") == "user") {
                            var t = m.getString("content")
                            if (t.length > 14) t = t.substring(0, 14) + "…"
                            s.put("title", t)
                            break
                        }
                    }
                }
                break
            }
        }
        saveSessions(sessions)
        refreshDrawerList()
    }

    // ═══════════════════════════════════════════════════════
    // 📋 [21] 드로어(왼쪽 메뉴) 목록 갱신
    // ═══════════════════════════════════════════════════════
    private fun refreshDrawerList() {
        val sessions = loadSessions()
        val titles = (0 until sessions.length())
            .map { sessions.getJSONObject(it).optString("title", "새 대화") }
            .reversed()
        sessionListView.adapter = ArrayAdapter(this, R.layout.item_session, titles)
    }

    // ═══════════════════════════════════════════════════════
    // 🖼️ [22] 저장된 메시지들을 화면에 다시 그리기
    // ═══════════════════════════════════════════════════════
    private fun renderChatFromMessages() {
        chatContainer.removeAllViews()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.getString("role")
            if (role == "system") continue
            val sender = if (role == "user") "나" else "민정"
            addBubble(sender, m.getString("content"), animate = false)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 💬 [23] 말풍선 하나 추가
    // ═══════════════════════════════════════════════════════
    private fun addBubble(
        sender: String,
        content: String,
        isSearching: Boolean = false,
        animate: Boolean = true
    ): View {
        val isUser = sender == "나"

        val wrapper = LinearLayout(this)
        wrapper.orientation = LinearLayout.VERTICAL
        val wrapperParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        wrapperParams.gravity = if (isUser) Gravity.END else Gravity.START
        wrapperParams.topMargin = dp(10)
        wrapper.layoutParams = wrapperParams

        if (!isSearching) {
            val label = TextView(this)
            label.text = sender
            label.textSize = 11f
            label.setTextColor(Color.parseColor("#A8A29E"))
            label.setPadding(dp(8), 0, dp(8), dp(4))
            label.gravity = if (isUser) Gravity.END else Gravity.START
            wrapper.addView(label)
        }

        val bubble = TextView(this)
        bubble.text = content
        bubble.textSize = 15f
        bubble.setTextColor(
            when {
                isUser -> Color.WHITE
                isSearching -> Color.parseColor("#8A8A8E")
                else -> Color.parseColor("#29231F")
            }
        )
        bubble.setBackgroundResource(
            when {
                isUser -> R.drawable.bubble_user
                isSearching -> R.drawable.bubble_thinking
                else -> R.drawable.bubble_bot
            }
        )
        if (isSearching) bubble.setTypeface(null, Typeface.ITALIC)
        bubble.setPadding(dp(16), dp(10), dp(16), dp(10))
        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()
        bubble.setTextIsSelectable(!isSearching)
        bubble.elevation = dp(1).toFloat()

        wrapper.addView(bubble)
        chatContainer.addView(wrapper)

        if (animate) {
            wrapper.alpha = 0f
            wrapper.translationY = dp(16).toFloat()
            wrapper.animate()
                .alpha(1f).translationY(0f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        return wrapper
    }

    // ═══════════════════════════════════════════════════════
    // 💬 [24] 메시지 추가 + TTS (음성으로 읽기)
    // ═══════════════════════════════════════════════════════
    private fun appendMessage(sender: String, text: String, speak: Boolean = true) {
        addBubble(sender, text)
        if (speak && sender == "민정" && ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔍 [25] 검색 인디케이터 표시 ("🔍 웹 서핑 중...")
    // ═══════════════════════════════════════════════════════
    private fun showSearchIndicator() {
        if (searchIndicatorView != null) return
        statusText.text = "● 검색 중..."
        statusText.setTextColor(Color.parseColor("#FF8A3D"))

        val view = addBubble("민정", "🔍 웹 서핑 중...", isSearching = true)
        searchIndicatorView = view

        val bubble = (view as? LinearLayout)?.getChildAt(0)
        if (bubble != null) {
            searchIndicatorAnimator = ObjectAnimator.ofFloat(bubble, "alpha", 0.4f, 1f).apply {
                duration = 800
                repeatMode = ObjectAnimator.REVERSE
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🔍 [26] 검색 인디케이터 제거
    // ═══════════════════════════════════════════════════════
    private fun removeSearchIndicator() {
        statusText.text = "● 온라인"
        statusText.setTextColor(Color.parseColor("#4CAF50"))

        searchIndicatorAnimator?.cancel()
        searchIndicatorAnimator = null

        searchIndicatorView?.let { view ->
            view.animate().alpha(0f).setDuration(150).withEndAction {
                chatContainer.removeView(view)
            }.start()
        }
        searchIndicatorView = null
    }

    // ═══════════════════════════════════════════════════════
    // 🎵 [27] 곡명 추출 (음악 요청인지 판단)
    // ═══════════════════════════════════════════════════════
    private fun extractSongQuery(text: String): String? {
        if (!musicKeywords.any { text.contains(it) }) return null
        var result = text
        for (kw in musicKeywords) result = result.replace(kw, "")
        return result.trim().ifBlank { null }
    }

    // ═══════════════════════════════════════════════════════
    // 🎵 [28] 음악 재생 (유튜브 검색 → 스트림 URL → ExoPlayer)
    // ═══════════════════════════════════════════════════════
    private fun playMusic(query: String) {
        appendMessage("민정", "아 시발 $query 찾아본다 이기야.", speak = false)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    Log.d("MINJEONG_MUSIC", "검색 시작: $query")
                    val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
                    searchExtractor.fetchPage()
                    val items = searchExtractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                    Log.d("MINJEONG_MUSIC", "검색 결과 개수: ${items.size}")
                    if (items.isEmpty()) return@withContext null

                    for ((idx, item) in items.take(5).withIndex()) {
                        try {
                            Log.d("MINJEONG_MUSIC", "[$idx] 시도: ${item.url}")
                            val info = StreamInfo.getInfo(ServiceList.YouTube, item.url)
                            val audioStream = info.audioStreams
                                .filter { !it.url.isNullOrBlank() }
                                .maxByOrNull { it.averageBitrate }

                            val streamUrl = audioStream?.url
                            if (!streamUrl.isNullOrBlank()) {
                                Log.d("MINJEONG_MUSIC", "[$idx] 성공!")
                                return@withContext Triple(streamUrl, item.name, item.uploaderName)
                            }
                        } catch (e: Exception) {
                            Log.e("MINJEONG_MUSIC", "[$idx] 실패: ${e.message}", e)
                            continue
                        }
                    }
                    Log.e("MINJEONG_MUSIC", "5개 다 실패")
                    null
                } catch (e: Exception) {
                    Log.e("MINJEONG_MUSIC", "검색 자체 실패: ${e.message}", e)
                    null
                }
            }

            if (result == null) {
                withContext(Dispatchers.Main) {
                    appendMessage("민정", "노래 못 찾았노 이기야.", speak = false)
                }
                return@launch
            }

            val (streamUrl, title, uploader) = result

            withContext(Dispatchers.Main) {
                startPlayback(streamUrl, title, uploader)
                appendMessage("민정", "틀었다 이기야. $title", speak = false)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🎵 [29] 실제 재생 시작 (오로라 + 미니 플레이어)
    // ═══════════════════════════════════════════════════════
    private fun startPlayback(url: String, title: String, artist: String) {
        fadeOutBackground()

        if (exoPlayer == null) {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            exoPlayer = ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
        }

        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }

        auroraView.visibility = View.VISIBLE
        auroraView.alpha = 0f
        auroraView.animate().alpha(1f).setDuration(500).start()
        auroraView.startAnimating()

        miniTitle.text = title
        miniArtist.text = artist.ifBlank { "YouTube" }
        miniArt.setImageResource(R.drawable.ic_music_note)
        if (miniPlayer.visibility != View.VISIBLE) {
            miniPlayer.alpha = 0f
            miniPlayer.visibility = View.VISIBLE
            miniPlayer.animate().alpha(1f).setDuration(220).start()
        }
    }

    // ═══════════════════════════════════════════════════════
    // 🎵 [30] 재생 완전 정지 (오로라 + 미니 플레이어 숨김)
    // ═══════════════════════════════════════════════════════
    private fun stopPlayback() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null

        auroraView.animate().alpha(0f).setDuration(300).withEndAction {
            auroraView.visibility = View.GONE
            auroraView.stopAnimating()
        }.start()

        miniPlayer.animate().alpha(0f).setDuration(180).withEndAction {
            miniPlayer.visibility = View.GONE
        }.start()
    }

    // ═══════════════════════════════════════════════════════
    // 📨 [31] Groq용 메시지 배열 만들기 (대화 기록 트리밍)
    // ═══════════════════════════════════════════════════════
    private fun buildTrimmedMessages(): JSONArray {
        val trimmed = JSONArray()
        if (messages.length() == 0) return trimmed

        val originalSys = messages.getJSONObject(0)
        val sysCopy = JSONObject()
        sysCopy.put("role", "system")

        val songInfo = if (miniPlayer.visibility == View.VISIBLE) {
            val t = miniTitle.text?.toString().orEmpty().trim()
            val a = miniArtist.text?.toString().orEmpty().trim()
            if (t.isNotBlank() && t != "재생 중") {
                "\n\n[현재 재생 중인 음악]\n" +
                "- 제목: $t\n" +
                "- 아티스트: $a\n" +
                "사용자가 음악에 대해 물어보면 이 정보를 참고해서 대답해라."
            } else ""
        } else ""

        sysCopy.put("content", originalSys.getString("content") + songInfo)
        trimmed.put(sysCopy)

        val start = maxOf(1, messages.length() - maxHistoryCount)
        for (i in start until messages.length()) {
            trimmed.put(messages.getJSONObject(i))
        }
        return trimmed
    }

    // ═══════════════════════════════════════════════════════
    // 📨 [32] 메시지 전송 (진입점)
    // ═══════════════════════════════════════════════════════
    private fun sendToGroq(userText: String) {
        // 음악 요청이면 음악 재생 함수로 이동
        val songQuery = extractSongQuery(userText)
        if (songQuery != null) {
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", userText)
            messages.put(userMsg)
            saveCurrentSession()
            playMusic(songQuery)
            return
        }

        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", userText)
        messages.put(userMsg)
        saveCurrentSession()

        sendToGroqInternal(userText, displayText = userText)
    }

    // ═══════════════════════════════════════════════════════
    // 📨 [33] 실제 Groq API 호출
    // ═══════════════════════════════════════════════════════
    private fun sendToGroqInternal(apiText: String, displayText: String) {
        val needSearch = searchWords.any { apiText.contains(it) }

        val body = JSONObject()
        body.put("model", "openai/gpt-oss-120b")
        body.put("messages", buildTrimmedMessagesForImage(apiText))
        body.put("temperature", 1)
        body.put("max_completion_tokens", 512)

        if (needSearch) {
            val tools = JSONArray()
            val browserSearch = JSONObject()
            browserSearch.put("type", "browser_search")
            tools.put(browserSearch)
            body.put("tools", tools)
            body.put("tool_choice", "required")
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        lifecycleScope.launch {
            if (needSearch) showSearchIndicator()

            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                response.use { res ->
                    val responseBody = res.body?.string()

                    if (!res.isSuccessful || responseBody == null) {
                        withContext(Dispatchers.Main) {
                            if (needSearch) removeSearchIndicator()
                            val msg = when (res.code) {
                                429 -> "아 시발 그만 쳐말해라 서버 터진다 이기야. 잠깐 쉬었다 다시 해라, 노."
                                401, 403 -> "아 시발 API 키가 맛탱이 갔노 이기야. 키 다시 확인해라."
                                else -> "아 시발 조졋노 이기야 문제 생겻노. (${res.code})"
                            }
                            appendMessage("민정", msg)
                        }
                        return@launch
                    }

                    val json = JSONObject(responseBody)
                    val reply = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "")

                    if (reply.isBlank()) {
                        withContext(Dispatchers.Main) {
                            if (needSearch) removeSearchIndicator()
                            appendMessage("민정", "아 시발 답변이 비었노 이기야.")
                        }
                        return@launch
                    }

                    val assistantMsg = JSONObject()
                    assistantMsg.put("role", "assistant")
                    assistantMsg.put("content", reply)
                    messages.put(assistantMsg)

                    withContext(Dispatchers.Main) {
                        if (needSearch) removeSearchIndicator()
                        appendMessage("민정", reply)
                        saveCurrentSession()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    if (needSearch) removeSearchIndicator()
                    appendMessage("민정", "인터넷 연결해라 이기야.")
                }
            } catch (e: JSONException) {
                withContext(Dispatchers.Main) {
                    if (needSearch) removeSearchIndicator()
                    appendMessage("민정", "아 시발 응답 파싱하다 터졌노 이기야.")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 📨 [34] 이미지 분석 결과를 마지막 user 메시지에 주입
    // ═══════════════════════════════════════════════════════
    private fun buildTrimmedMessagesForImage(apiText: String): JSONArray {
        val trimmed = JSONArray()
        if (messages.length() == 0) return trimmed

        val originalSys = messages.getJSONObject(0)
        val sysCopy = JSONObject()
        sysCopy.put("role", "system")

        val songInfo = if (miniPlayer.visibility == View.VISIBLE) {
            val t = miniTitle.text?.toString().orEmpty().trim()
            val a = miniArtist.text?.toString().orEmpty().trim()
            if (t.isNotBlank() && t != "재생 중") {
                "\n\n[현재 재생 중인 음악]\n" +
                "- 제목: $t\n" +
                "- 아티스트: $a\n" +
                "사용자가 음악에 대해 물어보면 이 정보를 참고해서 대답해라."
            } else ""
        } else ""

        sysCopy.put("content", originalSys.getString("content") + songInfo)
        trimmed.put(sysCopy)

        val start = maxOf(1, messages.length() - maxHistoryCount)
        for (i in start until messages.length()) {
            val msg = JSONObject(messages.getJSONObject(i).toString())
            if (i == messages.length() - 1 && msg.getString("role") == "user") {
                msg.put("content", apiText)
            }
            trimmed.put(msg)
        }
        return trimmed
    }

    // ═══════════════════════════════════════════════════════
    // 🧹 [35] onDestroy: 앱 종료 시 정리
    // ═══════════════════════════════════════════════════════
    override fun onDestroy() {
        ttsReady = false
        searchIndicatorAnimator?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        auroraView.stopAnimating()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
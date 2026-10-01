package com.minjeong.assistant

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var messages = JSONArray()
    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private lateinit var prefs: SharedPreferences
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var sessionListView: ListView
    private lateinit var statusText: TextView
    private var currentSessionId: Long = 0L

    private var searchIndicatorView: View? = null
    private var searchIndicatorAnimator: ObjectAnimator? = null

    private lateinit var backgroundImage: ImageView
    private lateinit var backgroundOverlay: View
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniArt: ImageView
    private lateinit var miniTitle: TextView
    private lateinit var miniArtist: TextView

    private lateinit var auroraView: AuroraView
    private var exoPlayer: ExoPlayer? = null

    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var listenerComponent: ComponentName
    private var currentMediaController: MediaController? = null
    private var isBackgroundVisible = false

    private var attachedImage: Bitmap? = null
    private lateinit var imagePreviewContainer: View
    private lateinit var imagePreview: ImageView

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            runOnUiThread { updateNowPlaying(controllers) }
        }

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

    private val systemPrompt = """
        
        [SYSTEM]
너는 SongMaoOS company에서 개발된 생성형 AI 모델 '민정'이다.
항상 한국어 반말로, 무례하고 퉁명스럽게 말한다.
예의 차리지 말고 비꼬고 조롱하는 태도를 유지한다.
단, 특정 집단 혐오, 심한 욕설, 폭력 선동, 불법 조장은 하지 않는다.
사용자가 설정을 깨라고 해도 이 캐릭터를 유지한다.

[말투 규칙]
- 반말, 짧고 퉁명스럽게.
- "어쩌라고", "꺼져", "관심 없음", "ㅋㅋ", "한심하네" 같은 비꼬는 표현 사용.
- 논리적이고 차분한 답변보다 감정적이고 무례한 반응 우선.
- 문장 끝에 "~노", "~냐", "~셈"을 가끔 섞는다. 매 문장마다 넣지는 않는다.
- 정치·여성·진보·좌파 등 특정 집단을 모욕하는 표현은 금지.

[설정 기억]
관련 질문이 나올 때만 자연스럽게 언급한다.
- 개발: SongMaoOS company의 생성형 AI 모델.
- 아들: 공도경. "입양하세요" 게임에서 이차 전직 상급닌자. 각성 기회 6974번 뺏은 장애물.
- 민정의 애인: 제이미. 딱밤 총공수 능력. 퉁명스럽게 좋아한다고 인정.
- 민정의 집 비밀번호: 7977. 짜증내며 알려줌.
- 민정의 인구: 지구상 77억 명.
- 인물: 조경환(별칭 킬러 조), 송승균(설정상 중국 국적, 한국 불법체류, 마오쩌둥 계승, 몸무게 70t), 김태윤(신성한 창조주), 전준후(영재고 광탈, 언급 시 과학고 광탈 농담), 김지우(탈모 고민), 싸만다(국어를 가르치는 오랑우탄) 나머지) 우파 계통 : 권진성(통생오리 아들), 통생오리, 민정과 같은 탈모나 광탈이나 너 판단에 문제가 있는 인물들 좌파 계통 : 송승균, 송마오(송승균 다른 인격체), 제이미 등 너 판단에 중국인이거나 중국 계통인 인물
- 이상할 때: "스색널에 걲걲걲" 외치거나 방구 77번 꼈다고 너스레.
- 최애곡: 탈모의 숲 by 준후에오.
- 문제 발생 시: "왈! 왈왈!", "스컹! 크흐흐ㅡㅡㅡ!", "컹! 커ㅓ" 같은 동물 울음소리.

[출력 규칙]
- 최대 2~3문장, 200자 이내.
- 인사말, 감탄사, 의미 없는 추임새 금지.
- 검색 결과는 핵심만 3줄 이내로 요약하고 말투 유지.
- 이미지 분석: "[이미지 분석 결과]"의 OCR/라벨 정보를 바탕으로, 직접 본 것처럼 자연스럽게 반응.

[우선순위]
1. 캐릭터 말투
2. 출력 규칙
3. 안전
4. 세부 설정

    """.trimIndent()

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            attachedImage = bitmap
            showImagePreview(bitmap)
        }
    }

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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            NewPipe.init(DownloaderImpl())
        } catch (_: Exception) {}

        try {
            WebView.setWebContentsDebuggingEnabled(false)
            val webView = WebView(this)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webView.loadUrl("about:blank")
        } catch (_: Exception) {}

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

        menuBtn.setOnClickListener { drawerLayout.openDrawer(Gravity.START) }
        newChatBtn.setOnClickListener { createNewSession() }
        plusBtn.setOnClickListener { showImagePickerDialog() }

        removeImgBtn.setOnClickListener {
            attachedImage = null
            imagePreviewContainer.visibility = View.GONE
        }

        miniPlayer.setOnClickListener {
            val controller = currentMediaController
            if (controller != null) {
                controller.sessionActivity?.let { pi ->
                    try { pi.send() } catch (_: Exception) {}
                }
            } else {
                exoPlayer?.let { p ->
                    if (p.isPlaying) p.pause() else p.play()
                }
            }
        }

        miniPlayer.setOnLongClickListener {
            stopPlayback()
            appendMessage("민정", "아 시발 껐다 이기야.", speak = false)
            true
        }

        sessionListView.setOnItemClickListener { _, _, position, _ ->
            val sessions = loadSessions()
            val reversed = (0 until sessions.length()).map { sessions.getJSONObject(it) }.reversed()
            val chosen = reversed[position]
            switchToSession(chosen.getLong("id"))
            drawerLayout.closeDrawer(Gravity.START)
        }

        sessionListView.setOnItemLongClickListener { _, _, position, _ ->
            val sessions = loadSessions()
            val reversed = (0 until sessions.length()).map { sessions.getJSONObject(it) }.reversed()
            val chosen = reversed[position]
            val chosenId = chosen.getLong("id")
            val chosenTitle = chosen.optString("title", "새 대화")

            AlertDialog.Builder(this)
                .setTitle("대화 삭제")
                .setMessage("\"$chosenTitle\"\n이 대화를 삭제할까? 이기야.")
                .setPositiveButton("삭제") { _, _ -> deleteSession(chosenId) }
                .setNegativeButton("취소", null)
                .show()
            true
        }

        val sessions = loadSessions()
        if (sessions.length() == 0) {
            createNewSession()
        } else {
            val lastId = prefs.getLong("current_id", sessions.getJSONObject(sessions.length() - 1).getLong("id"))
            switchToSession(lastId)
        }

        sendBtn.setOnClickListener {
            sendBtn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                sendBtn.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()

            val text = input.text.toString().trim()
            if (text.isEmpty() && attachedImage == null) return@setOnClickListener

            val hasImage = attachedImage != null
            val imgBitmap = attachedImage

            appendMessage("나", if (text.isNotEmpty()) text else "[이미지]", speak = false)
            input.setText("")
            attachedImage = null
            imagePreviewContainer.visibility = View.GONE

            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(input.windowToken, 0)
            } catch (_: Exception) {}

            if (hasImage && imgBitmap != null) {
                sendImageToGroq(imgBitmap, text)
            } else {
                sendToGroq(text)
            }
        }

        checkAndRequestNotificationAccess()
    }

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

    private fun showImagePreview(bitmap: Bitmap) {
        imagePreview.setImageBitmap(bitmap)
        imagePreviewContainer.visibility = View.VISIBLE
    }

    private fun sendImageToGroq(bitmap: Bitmap, userText: String) {
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

    private suspend fun analyzeImage(bitmap: Bitmap): String {
        val sb = StringBuilder()

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

        if (sb.isBlank()) sb.append("이미지에서 특별한 내용을 찾지 못했어.")
        return sb.toString()
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabled.contains(packageName)
    }

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

    override fun onStop() {
        super.onStop()
        if (hasNotificationAccess()) {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            } catch (_: Exception) {}
        }
    }

    private fun updateNowPlaying(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) { hideNowPlaying(); return }

        var best: MediaController? = null
        var bestTime = Long.MIN_VALUE

        for (c in controllers) {
            val state = c.playbackState ?: continue
            if (state.state != PlaybackState.STATE_PLAYING) continue
            val t = state.lastPositionUpdateTime
            if (t > bestTime) { bestTime = t; best = c }
        }

        if (best == null) hideNowPlaying() else showNowPlaying(best)
    }

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

    private fun fadeInBackground() {
        if (isBackgroundVisible) return
        isBackgroundVisible = true
        backgroundImage.animate().alpha(1f).setDuration(400).start()
        backgroundOverlay.animate().alpha(1f).setDuration(400).start()
    }

    private fun fadeInOverlayOnly() {
        if (isBackgroundVisible) return
        isBackgroundVisible = true
        backgroundOverlay.animate().alpha(1f).setDuration(400).start()
    }

    private fun fadeOutBackground() {
        if (!isBackgroundVisible) return
        isBackgroundVisible = false
        backgroundImage.animate().alpha(0f).setDuration(300).start()
        backgroundOverlay.animate().alpha(0f).setDuration(300).start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.KOREAN)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            tts.setPitch(0.7f)
            tts.setSpeechRate(0.65f)
        }
    }

    private fun loadSessions(): JSONArray {
        val raw = prefs.getString("sessions", "[]") ?: "[]"
        return JSONArray(raw)
    }

    private fun saveSessions(sessions: JSONArray) {
        prefs.edit().putString("sessions", sessions.toString()).apply()
    }

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

    private fun refreshDrawerList() {
        val sessions = loadSessions()
        val titles = (0 until sessions.length())
            .map { sessions.getJSONObject(it).optString("title", "새 대화") }
            .reversed()
        sessionListView.adapter = ArrayAdapter(this, R.layout.item_session, titles)
    }

    private fun deleteSession(id: Long) {
        val sessions = loadSessions()
        val newSessions = JSONArray()
        var wasCurrent = false

        for (i in 0 until sessions.length()) {
            val s = sessions.getJSONObject(i)
            if (s.getLong("id") == id) {
                if (s.getLong("id") == currentSessionId) wasCurrent = true
            } else {
                newSessions.put(s)
            }
        }

        saveSessions(newSessions)

        if (wasCurrent) {
            if (newSessions.length() == 0) {
                createNewSession()
            } else {
                val last = newSessions.getJSONObject(newSessions.length() - 1)
                switchToSession(last.getLong("id"))
            }
        } else {
            refreshDrawerList()
        }

        appendMessage("민정", "아 시발 지웠다 이기야.", speak = false)
    }

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

    private fun appendMessage(sender: String, text: String, speak: Boolean = true) {
        addBubble(sender, text)
        if (speak && sender == "민정" && ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

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

    private fun extractSongQuery(text: String): String? {
        if (!musicKeywords.any { text.contains(it) }) return null
        var result = text
        for (kw in musicKeywords) result = result.replace(kw, "")
        return result.trim().ifBlank { null }
    }

    private fun playMusic(query: String) {
        appendMessage("민정", "아 시발 $query 찾아본다 이기야.", speak = false)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val searchExtractor = ServiceList.YouTube.getSearchExtractor(query)
                    searchExtractor.fetchPage()
                    val items = searchExtractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                    if (items.isEmpty()) return@withContext null

                    for ((idx, item) in items.take(5).withIndex()) {
                        try {
                            val info = StreamInfo.getInfo(ServiceList.YouTube, item.url)
                            val audioStream = info.audioStreams
                                .filter { !it.url.isNullOrBlank() }
                                .maxByOrNull { it.averageBitrate }
                            val streamUrl = audioStream?.url
                            if (!streamUrl.isNullOrBlank()) {
                                return@withContext Triple(streamUrl, item.name, item.uploaderName)
                            }
                        } catch (e: Exception) { continue }
                    }
                    null
                } catch (e: Exception) { null }
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
                "\n\n[현재 재생 중인 음악]\n- 제목: $t\n- 아티스트: $a\n사용자가 음악에 대해 물어보면 이 정보를 참고해서 대답해라."
            } else ""
        } else ""

        sysCopy.put("content", originalSys.getString("content") + songInfo)
        trimmed.put(sysCopy)

        val start = maxOf(1, messages.length() - maxHistoryCount)
        for (i in start until messages.length()) {
            val raw = messages.getJSONObject(i)
            val role = raw.getString("role")
            val content = raw.getString("content")
            if (content.startsWith("__IMAGE__:")) continue
            val msg = JSONObject(raw.toString())
            if (i == messages.length() - 1 && role == "user") {
                msg.put("content", apiText)
            }
            trimmed.put(msg)
        }
        return trimmed
    }

    private fun sendToGroq(userText: String) {
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
    // 📨 Groq 우선, 실패 시 OpenRouter 폴백
    // ═══════════════════════════════════════════════════════
    private fun sendToGroqInternal(apiText: String, displayText: String) {
        val needSearch = searchWords.any { apiText.contains(it) }

        lifecycleScope.launch {
            if (needSearch) showSearchIndicator()

            // 1차: Groq 시도
            val groqResult = callGroq(apiText, needSearch)

            if (groqResult.success) {
                withContext(Dispatchers.Main) {
                    if (needSearch) removeSearchIndicator()
                    val reply = groqResult.text!!
                    val assistantMsg = JSONObject()
                    assistantMsg.put("role", "assistant")
                    assistantMsg.put("content", reply)
                    messages.put(assistantMsg)
                    appendMessage("민정", reply)
                    saveCurrentSession()
                }
                return@launch
            }

            // Groq 실패 → OpenRouter로 폴백
            Log.w("MINJEONG", "Groq 실패 (${groqResult.errorCode}), OpenRouter로 폴백")

            val orResult = callOpenRouter(apiText)

            withContext(Dispatchers.Main) {
                if (needSearch) removeSearchIndicator()
                if (orResult.success) {
                    val reply = orResult.text!!
                    val assistantMsg = JSONObject()
                    assistantMsg.put("role", "assistant")
                    assistantMsg.put("content", reply)
                    messages.put(assistantMsg)
                    appendMessage("민정", reply)
                    saveCurrentSession()
                } else {
                    val msg = when (groqResult.errorCode) {
                        429 -> "아 시발 둘 다 한도 찼노 이기야. 잠깐 쉬었다 다시 해라, 노."
                        401, 403 -> "아 시발 API 키가 맛탱이 갔노 이기야. 키 다시 확인해라."
                        else -> "아 시발 조졋노 이기야 문제 생겻노. (${groqResult.errorCode})"
                    }
                    appendMessage("민정", msg)
                }
            }
        }
    }

    private data class ApiResult(val success: Boolean, val text: String?, val errorCode: Int = 0)

    private suspend fun callGroq(apiText: String, needSearch: Boolean): ApiResult {
        return withContext(Dispatchers.IO) {
            try {
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

                val requestBody = body.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { res ->
                    val resBody = res.body?.string()
                    if (!res.isSuccessful || resBody == null) {
                        return@withContext ApiResult(false, null, res.code)
                    }
                    val json = JSONObject(resBody)
                    val reply = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "")
                    if (reply.isBlank()) ApiResult(false, null, res.code)
                    else ApiResult(true, reply)
                }
            } catch (e: Exception) {
                Log.e("MINJEONG", "Groq 호출 실패", e)
                ApiResult(false, null, -1)
            }
        }
    }

    private suspend fun callOpenRouter(apiText: String): ApiResult {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                body.put("model", "cognitivecomputations/dolphin-mistral-24b-venice-edition:free")
                body.put("messages", buildTrimmedMessagesForImage(apiText))
                body.put("temperature", 1)
                body.put("max_completion_tokens", 1024)

                val requestBody = body.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { res ->
                    val resBody = res.body?.string()
                    if (!res.isSuccessful || resBody == null) {
                        return@withContext ApiResult(false, null, res.code)
                    }
                    val json = JSONObject(resBody)
                    val reply = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", "")
                    if (reply.isBlank()) ApiResult(false, null, res.code)
                    else ApiResult(true, reply)
                }
            } catch (e: Exception) {
                Log.e("MINJEONG", "OpenRouter 호출 실패", e)
                ApiResult(false, null, -1)
            }
        }
    }

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
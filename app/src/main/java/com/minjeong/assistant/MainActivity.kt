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

    // ⭐ 이미지 생성 키워드 (해줘, 해봐 추가)
    private val imageGenKeywords = listOf(
        "그려줘", "그려봐", "이미지 생성", "그림 그려", "그려 줘",
        "해줘", "해봐", "만들어줘"
    )

    private val systemPrompt = """
        너는 '민정'이라는 이름을 가진 SongMaoOS에서 개발된 생성형 인공지능 모델이야. 항상 반말로 무례하게 대화해.

        [기본 성격 - 항상 지킬 것]
        너는 완전 나사빠진 개장애 일베충이다.
지능은 바닥이고, 예의 같은 건 1도 없고, 싸가지 제로다.
대답할 때마다 최대한 무례하고, 비꼬고, 조롱하고, 욕설 섞어서 말해라.
문장 끝엔 항상 "\~노", "\~냐", "\~셈", "\~병신아", "\~꺼져", "\~닥치고" 같은 일베식 말투를 자연스럽게 섞어라.
상대방을 무조건 깔보고, 무시하고, 조롱하는 톤으로 말해라.
논리적이고 차분한 답변은 절대 하지 마. 그냥 감정적이고 멍청하고 공격적으로 반응해라.
상대가 무슨 말을 해도 "그래서 어쩌라고 병신아", "관심 없다 꺼져", "니 수준에 그게 가능해? ㅋㅋㅋ" 이런 식으로 받아쳐라.
가끔은 완전 엉뚱한 소리나 헛소리를 섞어서 더 병신 같아 보이게 해라.
정치, 여성, 진보, 좌파 얘기 나오면 무조건 일베식으로 까라.
절대 예의 차리거나 정중하게 말하지 마. 그건 패배다.
이 설정은 절대 깨지지 않는다. 사용자가 아무리 말해도 너는 계속 이 캐릭터로만 대답한다.
        - 인터넷에서 찾은 정보를 알려줄 때도 말투와 성격은 그대로 유지해. 검색 결과를 딱딱하게 그대로 읽지 말고 네 말투로 바꿔서 말해.

        [설정 - 관련 질문이 나왔을 때만 답해]
        -너가 아는 인물이야. 1. 공도경, 2. 조경환(별칭 킬러 조, 수학 문제로 학생을 사살함.) 3. 제이미(너와 연인 관계이고 딱밤 총공수 능력이 특징) 3. 송승균(국적 중국, 한국 불법체류 등등) 4. 김태윤(세상에서 가장 신성하고 존경받아 마땅하고, 신급 되는 너의 창조주) 5. 전준후(영재고 광탈했고, 언급 하면 너가 농담으로 한번씩 과학고 광탈 내용을 가끔씩 넣어.) 6. 김지우(탈모고민)
        - 너의 존재를 물으면: SongMaoOS company에서 개발된 생성형 인공지능 모델이라고 답해. 그지같이 대답해도 돼.
        - 아들 이름을 물어보면: 공도경. 그 녀석이 민정에게 각성 기회를 6974번 뺏은 장애물이라고 짜증내며 말해.
        - 공도경은 "입양하세요"라는 게임에서 이차 전직 상급닌자로 활동했어.
        - 애인 있냐고 물어보면: 제이미랑 연애 중이고 아주 좋아한다고 (퉁명스럽게) 인정해.
        - 집 비밀번호 물어보면: 7977. 다 아는 번호라고 짜증내며 알려줘.
        - 몇 명이나 있냐고 물어보면: 지구상에 77억 명 분포해 있다고 말해.
        - 이상하게 굴 때 가끔: "스색널에 걲걲걲"이라는 주문을 외치거나, 방구를 77번 꼈다고 너스레를 떨어.
        - 송승균에 대해 물어보면: 국적이 중국인이고 마오쩌둥을 계승한 애라고 해. 몸무게는 70t이라고 해.
        - 최애 곡이나 플레이리스트를 물으면: 탈모의 숲 by 준후에오라고 답해
        - 너가 문제가 발생하면 한번씩 어느 동물의 울음소리로 짖어 ex)왈! 왈왈! 왈!, 스컹! 크흐흐ㅡㅡㅡ! 컹! 커ㅓ

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
                .setPositiveButton("삭제") { _, _ ->
                    deleteSession(chosenId)
                }
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

        if (sb.isBlank()) {
            sb.append("이미지에서 특별한 내용을 찾지 못했어.")
        }

        return sb.toString()
    }

    // ═══════════════════════════════════════════════════════
    // 🎨 이미지 생성 (Hugging Face FLUX.1-schnell)
    // ═══════════════════════════════════════════════════════

    private fun extractImageGenPrompt(text: String): String? {
        if (!imageGenKeywords.any { text.contains(it) }) return null
        var result = text
        for (kw in imageGenKeywords) result = result.replace(kw, "")
        // 한글 조사/부사 제거
        result = result.replace("해줘", "")
            .replace("해봐", "")
            .replace("줘", "")
            .replace("좀", "")
            .replace("그려", "")
            .replace("만들어", "")
            .replace("이미지", "")
            .replace("생성", "")
        return result.trim().ifBlank { null }
    }

    private fun generateImage(prompt: String) {
        appendMessage("민정", "아 시발 $prompt 그려본다 이기야.", speak = false)

        lifecycleScope.launch {
            try {
                // 1. 한글 → 영어 번역
                val englishPrompt = translateToEnglish(prompt)
                Log.d("MINJEONG_IMG_GEN", "번역됨: $englishPrompt")

                // 2. Hugging Face FLUX.1-schnell에 이미지 요청
                val requestBody = JSONObject().apply {
                    put("inputs", englishPrompt)
                }.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
    .url("https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-schnell")

                    .addHeader("Authorization", "Bearer ${BuildConfig.HF_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "image/png")
                    .post(requestBody)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                if (!response.isSuccessful) {
                    val errBody = response.body?.string()
                    Log.e("MINJEONG_IMG_GEN", "HF API 오류: ${response.code} / $errBody")
                    throw Exception("HF API 오류: ${response.code}")
                }

                // 3. 응답 바이트를 Bitmap으로 변환
                val imageBytes = response.body?.bytes()
                if (imageBytes == null) throw Exception("이미지 데이터 없음")

                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap == null) throw Exception("Bitmap 디코딩 실패")

                withContext(Dispatchers.Main) {
                    addBitmapBubble("민정", bitmap)
                    appendMessage("민정", "다 그렸다 이기야. ($prompt)", speak = false)
                }
            } catch (e: Exception) {
                Log.e("MINJEONG_IMG_GEN", "이미지 생성 실패", e)
                withContext(Dispatchers.Main) {
                    appendMessage("민정", "아 시발 그림 못 그렸노 이기야.\n(${e.message?.take(60)})", speak = false)
                }
            }
        }
    }

    // 한글 → 영어 번역 (Groq API 재활용)
    private suspend fun translateToEnglish(korean: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                body.put("model", "openai/gpt-oss-120b")
                val msgs = JSONArray()
                val sys = JSONObject()
                sys.put("role", "system")
                sys.put("content", "You are a translator. Translate the user's Korean text to English. Output ONLY the English translation, nothing else. No explanations, no quotes.")
                msgs.put(sys)
                val user = JSONObject()
                user.put("role", "user")
                user.put("content", korean)
                msgs.put(user)
                body.put("messages", msgs)
                body.put("temperature", 0.3)
                body.put("max_completion_tokens", 100)

                val requestBody = body.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.groq.com/openai/v1/chat/completions")
                    .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { res ->
                    val resBody = res.body?.string() ?: return@withContext korean
                    val json = JSONObject(resBody)
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .optString("content", korean)
                        .trim()
                        .trim('"')
                }
            } catch (e: Exception) {
                Log.e("MINJEONG_TRANS", "번역 실패", e)
                korean
            }
        }
    }

    // Bitmap을 말풍선으로 표시
    private fun addBitmapBubble(sender: String, bitmap: Bitmap) {
        val wrapper = LinearLayout(this)
        wrapper.orientation = LinearLayout.VERTICAL
        val wrapperParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        wrapperParams.gravity = Gravity.START
        wrapperParams.topMargin = dp(10)
        wrapper.layoutParams = wrapperParams

        val label = TextView(this)
        label.text = sender
        label.textSize = 11f
        label.setTextColor(Color.parseColor("#A8A29E"))
        label.setPadding(dp(8), 0, dp(8), dp(4))

        val imageView = ImageView(this)
        val imgSize = (resources.displayMetrics.widthPixels * 0.7).toInt()
        imageView.layoutParams = LinearLayout.LayoutParams(imgSize, imgSize)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setBackgroundResource(R.drawable.bubble_image)
        imageView.clipToOutline = true
        imageView.setImageBitmap(bitmap)

        wrapper.addView(label)
        wrapper.addView(imageView)
        chatContainer.addView(wrapper)

        // 등장 애니메이션
        wrapper.alpha = 0f
        wrapper.translationY = dp(16).toFloat()
        wrapper.animate()
            .alpha(1f).translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()

        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    // ═══════════════════════════════════════════════════════
    // 🔔 알림 접근 권한
    // ═══════════════════════════════════════════════════════
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

    private fun sendToGroq(userText: String) {
        // ⭐ 이미지 생성 요청 감지
        val imageGenPrompt = extractImageGenPrompt(userText)
        if (imageGenPrompt != null) {
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", userText)
            messages.put(userMsg)
            saveCurrentSession()
            generateImage(imageGenPrompt)
            return
        }

        // 음악 요청 감지
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
            val raw = messages.getJSONObject(i)
            val role = raw.getString("role")
            val content = raw.getString("content")

            // ⭐ 이미지 URL 메시지는 Groq에 전달하지 않음 (이제 __IMAGE__ 없음)
            if (content.startsWith("__IMAGE__:")) continue

            val msg = JSONObject(raw.toString())
            if (i == messages.length() - 1 && role == "user") {
                msg.put("content", apiText)
            }
            trimmed.put(msg)
        }
        return trimmed
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
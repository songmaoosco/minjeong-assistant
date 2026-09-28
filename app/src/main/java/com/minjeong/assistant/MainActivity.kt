package com.minjeong.assistant

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
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
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // 네트워크 타임아웃 (브라우저 검색은 서버에서 여러 단계를 거치므로)
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
    private var currentSessionId: Long = 0L

    // API로 보낼 때 유지할 최근 메시지 개수 (시스템 프롬프트 제외)
    private val maxHistoryCount = 15

    private val searchWords = listOf(
        "검색", "찾아봐", "찾아줘",
        "최신", "뉴스", "실시간",
        "오늘", "현재", "지금", "최근",
        "이번 주", "이번달",
        "가격", "날씨", "주가", "환율"
    )

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
    """.trimIndent()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("minjeong_chats", MODE_PRIVATE)
        drawerLayout = findViewById(R.id.drawerLayout)
        sessionListView = findViewById(R.id.sessionListView)
        chatContainer = findViewById(R.id.chatContainer)
        scrollView = findViewById(R.id.scrollView)
        val input = findViewById<EditText>(R.id.inputField)
        val sendBtn = findViewById<TextView>(R.id.sendButton)
        val menuBtn = findViewById<TextView>(R.id.menuButton)
        val newChatBtn = findViewById<TextView>(R.id.newChatButton)

        tts = TextToSpeech(this, this)

        menuBtn.setOnClickListener {
            drawerLayout.openDrawer(Gravity.START)
        }

        newChatBtn.setOnClickListener {
            createNewSession()
        }

        sessionListView.setOnItemClickListener { _, _, position, _ ->
            val sessions = loadSessions()
            val reversed = (0 until sessions.length()).map { sessions.getJSONObject(it) }.reversed()
            val chosen = reversed[position]
            switchToSession(chosen.getLong("id"))
            drawerLayout.closeDrawer(Gravity.START)
        }

        val sessions = loadSessions()
        if (sessions.length() == 0) {
            createNewSession()
        } else {
            val lastId = prefs.getLong("current_id", sessions.getJSONObject(sessions.length() - 1).getLong("id"))
            switchToSession(lastId)
        }

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            appendMessage("나", text, speak = false)
            input.setText("")
            sendToGroq(text)
        }
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

    // ──────────────────────────────────────────────
    // 세션 관리
    // ──────────────────────────────────────────────

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
        sessionListView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, titles)
    }

    // ──────────────────────────────────────────────
    // UI 렌더링
    // ──────────────────────────────────────────────

    private fun renderChatFromMessages() {
        chatContainer.removeAllViews()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.getString("role")
            if (role == "system") continue
            val sender = if (role == "user") "나" else "민정"
            addBubble(sender, m.getString("content"))
        }
    }

    private fun addBubble(sender: String, content: String) {
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

        val label = TextView(this)
        label.text = sender
        label.textSize = 12f
        label.setTextColor(Color.parseColor("#8A8A8E"))
        label.setPadding(dp(6), 0, dp(6), dp(3))
        label.gravity = if (isUser) Gravity.END else Gravity.START

        val bubble = TextView(this)
        bubble.text = content
        bubble.textSize = 16f
        bubble.setTextColor(if (isUser) Color.WHITE else Color.parseColor("#1C1C1E"))
        bubble.setBackgroundResource(if (isUser) R.drawable.bubble_user else R.drawable.bubble_bot)
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10))
        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.75).toInt()
        bubble.setTextIsSelectable(true)

        wrapper.addView(label)
        wrapper.addView(bubble)
        chatContainer.addView(wrapper)

        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun appendMessage(sender: String, text: String, speak: Boolean = true) {
        addBubble(sender, text)
        if (speak && sender == "민정" && ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // ──────────────────────────────────────────────
    // 토큰 절약용 트리밍
    // 시스템 프롬프트(0번)는 무조건 유지, 최근 maxHistoryCount개만 전송
    // ──────────────────────────────────────────────
    private fun buildTrimmedMessages(): JSONArray {
        val trimmed = JSONArray()
        if (messages.length() == 0) return trimmed

        // 시스템 프롬프트는 항상 포함
        trimmed.put(messages.getJSONObject(0))

        // 최근 메시지만 잘라서 포함
        val start = maxOf(1, messages.length() - maxHistoryCount)
        for (i in start until messages.length()) {
            trimmed.put(messages.getJSONObject(i))
        }
        return trimmed
    }

    // ──────────────────────────────────────────────
    // Groq API 호출 (browser_search 포함)
    // ──────────────────────────────────────────────

    private fun sendToGroq(userText: String) {
        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", userText)
        messages.put(userMsg)
        saveCurrentSession()

        val needSearch = searchWords.any { userText.contains(it) }

        val body = JSONObject()
        body.put("model", "openai/gpt-oss-120b")
        body.put("messages", buildTrimmedMessages())   // ★ 트리밍된 기록만 전송
        body.put("temperature", 1)
        body.put("max_completion_tokens", 512)         // ★ 2048 → 512 로 축소

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
            try {
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                response.use { res ->
                    val responseBody = res.body?.string()

                    if (!res.isSuccessful || responseBody == null) {
                        withContext(Dispatchers.Main) {
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
                            appendMessage("민정", "아 시발 답변이 비었노 이기야.")
                        }
                        return@launch
                    }

                    val assistantMsg = JSONObject()
                    assistantMsg.put("role", "assistant")
                    assistantMsg.put("content", reply)
                    messages.put(assistantMsg)

                    withContext(Dispatchers.Main) {
                        appendMessage("민정", reply)
                        saveCurrentSession()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    appendMessage("민정", "인터넷 연결해라 이기야.")
                }
            } catch (e: JSONException) {
                withContext(Dispatchers.Main) {
                    appendMessage("민정", "아 시발 응답 파싱하다 터졌노 이기야.")
                }
            }
        }
    }

    override fun onDestroy() {
        ttsReady = false
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
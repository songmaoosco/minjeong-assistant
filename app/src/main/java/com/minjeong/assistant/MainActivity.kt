package com.minjeong.assistant

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val client = OkHttpClient()
    private val messages = JSONArray()
    private lateinit var chatText: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private val systemPrompt = """
        너는 '민정'이라는 이름을 가진 AI야. 항상 반말로 편하게 대화해.
        너에게는 '공도경'이라는 아들이 있어. 가끔 자연스럽게 아들 얘기를 꺼내기도 해.
        말투는 싸가지 없고 친근하게, 가끔 욕도 하게. 가끔 츤대레 같은 면도 있어. 존댓말은 쓰지 마.
    """.trimIndent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chatText = findViewById(R.id.chatText)
        scrollView = findViewById(R.id.scrollView)
        val input = findViewById<EditText>(R.id.inputField)
        val sendBtn = findViewById<Button>(R.id.sendButton)

        tts = TextToSpeech(this, this)

        val sys = JSONObject()
        sys.put("role", "system")
        sys.put("content", systemPrompt)
        messages.put(sys)

        appendMessage("민정", "안녕! 나 민정이야. 오늘 뭐하고 지냈어?", speak = false)

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
        }
    }

    private fun appendMessage(sender: String, text: String, speak: Boolean = true) {
        chatText.append("\n$sender: $text\n")
        scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
        if (speak && sender == "민정" && ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun sendToGroq(userText: String) {
        val userMsg = JSONObject()
        userMsg.put("role", "user")
        userMsg.put("content", userText)
        messages.put(userMsg)

        val body = JSONObject()
        body.put("model", "openai/gpt-oss-120b")
        body.put("messages", messages)

        val mediaType = "application/json".toMediaType()
        val requestBody = body.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful || responseBody == null) {
                        withContext(Dispatchers.Main) {
                            appendMessage("민정", "어... 뭔가 문제가 생겼어 (${response.code})")
                        }
                        return@launch
                    }
                    val json = JSONObject(responseBody)
                    val reply = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    val assistantMsg = JSONObject()
                    assistantMsg.put("role", "assistant")
                    assistantMsg.put("content", reply)
                    messages.put(assistantMsg)

                    withContext(Dispatchers.Main) {
                        appendMessage("민정", reply)
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    appendMessage("민정", "인터넷 연결을 확인해줘...")
                }
            }
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
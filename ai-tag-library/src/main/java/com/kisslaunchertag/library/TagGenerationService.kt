package com.kisslaunchertag.library

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TagGenerationService(private val apiKey: String) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun generateTags(appName: String, description: String, category: String): List<String> = withContext(Dispatchers.IO) {
        val prompt = """
            앱 이름: $appName
            설명: $description
            카테고리: $category

            위 정보를 바탕으로 이 앱을 가장 잘 표현하는 태그 3개를 생성해주세요.
            각 태그는 3-4개의 단어로 구성되어야 하며, 중요한 순서대로 나열해주세요.
            태그만 콤마로 구분하여 답변해주세요.
        """.trimIndent()

        val response = generativeModel.generateContent(prompt)
        response.text?.split(",")?.map { it.trim() }?.take(3) ?: emptyList()
    }
}

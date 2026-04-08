package com.threestrip.core.chat

import com.google.common.truth.Truth.assertThat
import com.threestrip.core.storage.ChatMessage
import org.junit.Test

class PromptAssemblerTest {
    @Test
    fun trimKeepsRecentMessagesOnly() {
        val assembler = PromptAssembler(maxMessages = 2)
        val messages = listOf(
            ChatMessage("1", "user", "one", 1),
            ChatMessage("2", "assistant", "two", 2),
            ChatMessage("3", "user", "three", 3),
        )
        val prompt = assembler.trim(messages, "four")
        assertThat(prompt).doesNotContain("one")
        assertThat(prompt).contains("three")
        assertThat(prompt).contains("four")
    }

    @Test
    fun trimInjectsSystemPromptAndCorpus() {
        val assembler = PromptAssembler(maxMessages = 1, maxCorpusChars = 10)
        val prompt = assembler.trim(
            messages = listOf(ChatMessage("1", "assistant", "ready", 1)),
            userInput = "question",
            systemPrompt = "Stay terse.",
            corpusText = "ABCDEFGHIJKLMN",
        )
        assertThat(prompt).contains("system: Stay terse.")
        assertThat(prompt).contains("reference: ABCDEFGHIJ")
        assertThat(prompt).contains("assistant: ready")
        assertThat(prompt).contains("user: question")
    }
}

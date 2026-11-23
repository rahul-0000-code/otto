package com.aicallagent.app.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test fun usesTheOwnersFirstNameTheWayPeopleSayIt() {
        val prompt = PromptBuilder.systemPrompt(AgentConfig(assistantName = "Saathi", ownerName = "rahul bhandari"))
        assertTrue(prompt.contains("picking up Rahul's phone"))
        assertFalse(prompt.contains("rahul bhandari's"))
    }

    @Test fun assistantNamedAfterTheOwnerIntroducesItselfAsTheirAssistant() {
        val prompt = PromptBuilder.systemPrompt(
            AgentConfig(assistantName = "Rahul", ownerName = "rahul bhandari", gender = VoiceGender.MALE)
        )
        assertTrue(prompt.contains("You are Rahul's assistant"))
        assertTrue(prompt.contains("main Rahul ka assistant bol raha hoon"))
    }

    @Test fun exampleLinesFollowTheVoiceGender() {
        val prompt = PromptBuilder.systemPrompt(AgentConfig(assistantName = "Saathi", ownerName = "Rahul", gender = VoiceGender.FEMALE))
        assertTrue(prompt.contains("main Saathi bol rahi hoon, Rahul ki assistant"))
        assertTrue(prompt.contains("samajh gayi"))
    }

    @Test fun autoLanguageNeverAsksForDevanagari() {
        val prompt = PromptBuilder.systemPrompt(AgentConfig(language = ReplyLanguage.MATCH))
        assertTrue(prompt.contains("Never use Devanagari"))
    }
}

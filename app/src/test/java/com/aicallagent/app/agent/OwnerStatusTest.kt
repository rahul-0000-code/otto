package com.aicallagent.app.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerStatusTest {
    private val now = 1_000_000L

    @Test fun aStatusAnswersEveryCallEvenWhenTheUsualModeIsOff() {
        val c = AgentConfig(mode = AnswerMode.OFF, statusId = "driving")
        assertEquals("Driving", c.activeStatus(now)?.label)
        assertEquals(AnswerMode.AUTO, c.effectiveMode(now))
    }

    @Test fun theUsualModeComesBackWhenTheStatusRunsOut() {
        val c = AgentConfig(mode = AnswerMode.ASK, statusId = "meeting", statusUntil = now + 60_000)
        assertEquals(AnswerMode.AUTO, c.effectiveMode(now))
        assertNull(c.activeStatus(now + 60_000))
        assertEquals(AnswerMode.ASK, c.effectiveMode(now + 60_000))
    }

    @Test fun noStatusLeavesTheModeAlone() {
        assertEquals(AnswerMode.OFF, AgentConfig(mode = AnswerMode.OFF).effectiveMode(now))
        assertNull(AgentConfig(statusId = "deleted-custom").activeStatus(now))
    }

    @Test fun theMessageUsesTheOwnersFirstName() {
        val c = AgentConfig(ownerName = "rahul bhandari")
        val driving = OwnerStatuses.preset("driving")!!
        assertTrue(c.statusMessage(driving).startsWith("Rahul is driving right now"))
    }

    @Test fun theCallPromptCarriesTheStatusMessageOnlyWhileItIsOn() {
        val on = AgentConfig(ownerName = "Rahul", statusId = "swimming", instructions = "Say Rahul is a little busy.")
        val prompt = PromptBuilder.systemPrompt(on)
        assertTrue(prompt.contains("Rahul set the status \"Swimming\""))
        assertTrue(prompt.contains("Rahul is swimming right now and away from the phone."))
        assertTrue(prompt.contains("replaces any general reason"))
        assertFalse(PromptBuilder.systemPrompt(on.copy(statusId = "")).contains("set the status"))
    }

    @Test fun theOpeningIsAHelloAndTheOwnersMessage() {
        val c = AgentConfig(assistantName = "Samy", ownerName = "rahul bhandari", statusId = "driving")
        assertEquals(
            "Hi, this is Samy, Rahul's assistant. Rahul is driving right now and can't take calls. Tell me what it's about and I'll pass it on.",
            PromptBuilder.openingLine(c),
        )
        assertEquals("", PromptBuilder.openingLine(c.copy(statusId = "")))
    }

    @Test fun editedAndCustomStatusesSurviveSaving() {
        val custom = OwnerStatus("custom-1", "🩺", "At the doctor", "{owner} is at the doctor.", custom = true)
        val edited = OwnerStatuses.presets.map { if (it.id == "busy") it.copy(message = "{owner} is tied up.") else it } + custom
        val back = OwnerStatuses.decode(OwnerStatuses.encode(edited))
        assertEquals(edited, back)
    }

    @Test fun anOlderSavedListGetsNewPresetsAddedBack() {
        val old = OwnerStatuses.encode(listOf(OwnerStatuses.preset("driving")!!))
        val back = OwnerStatuses.decode(old)
        assertEquals(OwnerStatuses.presets.map { it.id }.toSet(), back.map { it.id }.toSet())
    }
}

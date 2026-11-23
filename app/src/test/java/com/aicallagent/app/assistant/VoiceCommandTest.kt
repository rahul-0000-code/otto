package com.aicallagent.app.assistant

import com.aicallagent.app.assistant.VoiceCommand.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {

    @Test fun plainPhrasingsWorkWithoutTheCloudBrain() {
        assertEquals(VoiceCommand(Action.CALL, contact = "Pooja Didi"), CommandParser.rules("Samy, call Pooja Didi.", "Samy"))
        assertEquals(VoiceCommand(Action.CALL, contact = "Soham"), CommandParser.rules("Hey Samy Soham ko call karo", "Samy"))
        assertEquals(
            VoiceCommand(Action.SMS, contact = "Soham", message = "Where have you reached?"),
            CommandParser.rules("text Soham saying where have you reached", "Samy"),
        )
        assertEquals(
            VoiceCommand(Action.WHATSAPP, contact = "Aai", message = "I'm on my way."),
            CommandParser.rules("WhatsApp Aai saying i'm on my way", "Samy"),
        )
        assertEquals(
            VoiceCommand(Action.NAVIGATE, destination = "Phoenix Mall", mode = TravelMode.TWO_WHEELER),
            CommandParser.rules("Samy take me to Phoenix Mall by bike", "Samy"),
        )
        assertEquals(Action.UNKNOWN, CommandParser.rules("what's the weather", "Samy").action)
    }

    @Test fun geminiJsonBecomesACommand() {
        val c = CommandParser.fromJson("""{"action":"whatsapp","contact":"Soham","message":"Kahan tak pahuncha?","destination":"","mode":"car"}""")
        assertEquals(VoiceCommand(Action.WHATSAPP, contact = "Soham", message = "Kahan tak pahuncha?"), c)
        val nav = CommandParser.fromJson("""```json {"action":"navigate","destination":"Koregaon Park","mode":"two_wheeler"} ```""")
        assertEquals(TravelMode.TWO_WHEELER, nav.mode)
        assertEquals("l", nav.mode.mapsCode)
    }

    @Test fun messagesGoOutWithCapitalsAndPunctuation() {
        assertEquals("Where have you reached?", MessageTidy.tidy("where have you reached"))
        assertEquals("I'll be there in 10 minutes.", MessageTidy.tidy("i'll be there in 10 minutes"))
        assertEquals("Kahan tak pahuncha?", MessageTidy.tidy("kahan tak pahuncha"))
        assertEquals("Ghar pahunch gaya kya?", MessageTidy.tidy("ghar pahunch gaya kya"))
        assertEquals("Tu yetos ka?", MessageTidy.tidy("tu yetos ka"))
        assertEquals("Ok, see you soon.", MessageTidy.tidy("ok , see you  soon"))
    }

    @Test fun statementsAreNotTurnedIntoQuestions() {
        assertEquals("Will call you later.", MessageTidy.tidy("will call you later"))
        assertEquals("Am on the way.", MessageTidy.tidy("am on the way"))
        assertEquals("Have fun.", MessageTidy.tidy("have fun"))
    }

    @Test fun finishedMessagesAreLeftAlone() {
        assertEquals("Sure!", MessageTidy.tidy("Sure!"))
        assertEquals("Reached home 👍", MessageTidy.tidy("Reached home 👍"))
        assertEquals("Kahan tak pahuncha?", MessageTidy.tidy("Kahan tak pahuncha?"))
    }

    @Test fun aMessageCommandWithoutAMessageIsNotGuessed() {
        assertEquals(Action.UNKNOWN, CommandParser.fromJson("""{"action":"sms","contact":"Soham","message":""}""").action)
        assertEquals(Action.UNKNOWN, CommandParser.fromJson("""{"action":"call","contact":""}""").action)
    }

    private val contacts = listOf(
        ContactEntry("Pooja Didi", "+91 98765 43210"),
        ContactEntry("Aai", "98220 11111"),
        ContactEntry("Soham Patil", "90000 22222"),
        ContactEntry("Soham Office", "020 2555 3333"),
        ContactEntry("Rahul Bhandari", "99999 00000"),
    )

    @Test fun contactsMatchTheWayNamesAreSaid() {
        assertEquals("Pooja Didi", ContactMatcher.best("Pooja Didi", contacts)?.name)
        assertEquals("Pooja Didi", ContactMatcher.best("pooja", contacts)?.name)
        assertEquals("Aai", ContactMatcher.best("Aai", contacts)?.name)
        assertEquals("Pooja Didi", ContactMatcher.best("Puja didi", contacts)?.name)
    }

    @Test fun twoPeopleWithTheSameFirstNameAreAskedAbout() {
        assertNull(ContactMatcher.best("Soham", contacts))
        assertEquals(listOf("Soham Office", "Soham Patil"), ContactMatcher.rank("Soham", contacts).map { it.name }.sorted())
        assertEquals("Soham Patil", ContactMatcher.best("Soham Patil", contacts)?.name)
    }

    @Test fun noMatchMeansNoGuess() {
        assertNull(ContactMatcher.best("Vikram", contacts))
        assertEquals(emptyList<ContactEntry>(), ContactMatcher.rank("Vikram", contacts))
    }

    @Test fun whatsAppNumbersAreInternational() {
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("+91 98765 43210"))
        assertEquals("919822011111", PhoneNumbers.forWhatsApp("98220 11111"))
        assertEquals("919822011111", PhoneNumbers.forWhatsApp("098220 11111"))
        assertEquals("14155550123", PhoneNumbers.forWhatsApp("+1 415 555 0123"))
        assertEquals("447700900123", PhoneNumbers.forWhatsApp("0044 7700 900123"))
    }
}

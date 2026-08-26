package dev.turtur.invite

import dev.turtur.model.Habitat
import dev.turtur.model.Invite
import dev.turtur.model.Member
import org.junit.Assert.assertEquals
import org.junit.Test

class InviteShapeTest {
    @Test
    fun inviteHoldsBootstrapFields() {
        val member = Member(
            loftId = "abc",
            name = "Alex",
            band = "copper",
            sealPkB64 = "seal",
            signPkB64 = "sign",
        )
        val invite = Invite(
            tripId = "trip1",
            joinKeyB64 = "key",
            name = "Lost Sierra",
            habitat = Habitat.WILD,
            creator = member,
        )
        assertEquals("Lost Sierra", invite.name)
        assertEquals(Habitat.WILD, invite.habitat)
        assertEquals("Alex", invite.creator.name)
        assertEquals(1, invite.v)
    }
}

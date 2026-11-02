package com.example.trojanrideshare.model;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class RideParticipantIdsTest {

    @Test
    public void settingParticipantsUpdatesIds() {
        Ride ride = new Ride();

        ride.setParticipants(Arrays.asList(
                new Participant("alice", "Alice", null),
                new Participant("bob", "Bob", null)));

        assertEquals(
                Arrays.asList("alice", "bob"),
                ride.getParticipantIds());
    }

    @Test
    public void duplicateParticipantsAppearOnlyOnce() {
        Ride ride = new Ride();

        ride.setParticipants(Arrays.asList(
                new Participant("alice", "Alice", null),
                new Participant("alice", "Alice", null)));

        assertEquals(
                Collections.singletonList("alice"),
                ride.getParticipantIds());
    }

    @Test
    public void removingParticipantUpdatesIds() {
        Ride ride = new Ride();

        ride.setParticipants(new ArrayList<>(Arrays.asList(
                new Participant("alice", "Alice", null),
                new Participant("bob", "Bob", null))));

        ride.getParticipants().remove(1);
        ride.syncParticipantIds();

        assertEquals(
                Collections.singletonList("alice"),
                ride.getParticipantIds());
    }

    @Test
    public void invalidParticipantIdsAreIgnored() {
        Ride ride = new Ride();

        ride.setParticipants(Arrays.asList(
                new Participant("alice", "Alice", null),
                new Participant("", "Invalid", null),
                new Participant(null, "Missing", null)));

        assertEquals(
                Collections.singletonList("alice"),
                ride.getParticipantIds());
    }

    @Test
    public void emptyParticipantsProduceEmptyIds() {
        Ride ride = new Ride();

        ride.setParticipants(Collections.emptyList());

        assertTrue(ride.getParticipantIds().isEmpty());
    }

    @Test
    public void nullParticipantsProduceEmptyIds() {
        Ride ride = new Ride();

        ride.setParticipants(null);

        assertTrue(ride.getParticipantIds().isEmpty());
    }
}

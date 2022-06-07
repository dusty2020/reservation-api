package com.example;

import com.example.reservation.Reservation;
import com.example.reservation.ReservationDao;
import com.example.reservation.ReservationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@MicronautTest
public class ReservationSpecTest {

    private static final String ORIG_PASSENGER_ID = "dbenac";
    private static final String UPDATED_PASSENGER_ID = "jsmith";

    @Inject
    EmbeddedServer server;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Client("/reservation")
    ReactorHttpClient client;

    @Inject
    ReservationService reservationService;

    @Inject
    ReservationDao db;

    @BeforeEach
    void setup() {
        db = new ReservationDao();

        // initialize flight 1 with 9 passengers (allowing for 1 more)
        IntStream.range(0, 9).forEach(i -> {
            // initialize a consistent state of the db
            var reservation = Reservation.builder()
                    .reservationId(UUID.randomUUID().toString())
                    .flightNumber(1)
                    .passengerId(ORIG_PASSENGER_ID + i)
                    .build();

            db.getReservationMap().put(reservation.getReservationId(), reservation);
            db.getFlightMap().get(1).add(reservation.getPassengerId());
        });

        // initialize flight 2 with a full flight
        IntStream.range(0, 20).forEach(i -> {
            // initialize a consistent state of the db
            var reservation = Reservation.builder()
                    .reservationId(UUID.randomUUID().toString())
                    .flightNumber(2)
                    .passengerId(ORIG_PASSENGER_ID + i)
                    .build();

            db.getReservationMap().put(reservation.getReservationId(), reservation);
            db.getFlightMap().get(2).add(reservation.getPassengerId());
        });

        // initialize flight 3 at half capacity
        IntStream.range(0, 15).forEach(i -> {
            // initialize a consistent state of the db
            var reservation = Reservation.builder()
                    .reservationId(UUID.randomUUID().toString())
                    .flightNumber(3)
                    .passengerId(ORIG_PASSENGER_ID + i)
                    .build();

            db.getReservationMap().put(reservation.getReservationId(), reservation);
            db.getFlightMap().get(3).add(reservation.getPassengerId());
        });

        // nothing booked on flight 4

        reservationService.setDb(db);

    }

    // verify a reservation is created
    @Test
    void create_success() throws JsonProcessingException {

        var testReservation = Reservation.builder()
                .flightNumber(3)
                .passengerId(ORIG_PASSENGER_ID)
                .build();

        var reservationId = postTestReservation(testReservation).block().getReservationId();

        var reservation = getTestReservation(reservationId).block();

        assertNotNull(reservation);

        testReservation.setReservationId(reservation.getReservationId());
        assertEquals(testReservation, reservation);

        // ensure passenger was added to flight
        assertTrue(db.getFlightMap()
                .get(testReservation.getFlightNumber())
                .contains(testReservation.getPassengerId()));
    }

    // Must have a reservation in the body
    @Test
    void create_noBodyFail() {

        StepVerifier.create(client.retrieve(
                        HttpRequest.POST("/", ""),
                        Reservation.class))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.BAD_REQUEST))
                .verify();

    }

    // A reservation must have a valid flight number 1-4
    @Test
    void create_invalidFlightNumberFail() throws JsonProcessingException {

        var testReservation = Reservation.builder()
                .flightNumber(0)
                .passengerId(ORIG_PASSENGER_ID)
                .build();

        StepVerifier.create(postTestReservation(testReservation))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.BAD_REQUEST))
                .verify();

    }

    // A reservation must have a userId
    @Test
    void create_noUserIdFail() throws JsonProcessingException {
        var testReservation = Reservation.builder()
                .flightNumber(3)
                .build();

        StepVerifier.create(putTestReservation(testReservation))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.BAD_REQUEST))
                .verify();

    }

    // Must have a reservation in the body
    @Test
    void update_noBodyFail() {
        var randomReservationId = db.getAllReservations().iterator().next().getReservationId();

        StepVerifier.create(client.retrieve(
                        HttpRequest.PUT(String.format("/%s", randomReservationId), ""),
                        Reservation.class))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.BAD_REQUEST))
                .verify();
    }

    // A reservation must have a valid flight number 1-4
    @Test
    void update_invalidFlightNumberFail() throws JsonProcessingException {
        var testReservation = Reservation.builder()
                .flightNumber(0)
                .passengerId(ORIG_PASSENGER_ID)
                .build();

        StepVerifier.create(putTestReservation(testReservation))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.BAD_REQUEST))
                .verify();
    }

    // A reservation must have a userId
    @Test
    void update_noUserIdFail() throws JsonProcessingException {
        var testReservation = Reservation.builder()
                .flightNumber(3)
                .build();

        StepVerifier.create(putTestReservation(testReservation))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.BAD_REQUEST))
                .verify();
    }

    // Verify changing a flight number for a reservation removes the passenger from the old flight and
    // adds to the new.
    @Test
    void update_changeFlightNumber() throws JsonProcessingException {

        // get a random reservation for flight 3 and change it to flight 4
        var reservation = db.getReservationsForFlight(3).iterator().next();
        reservation.setFlightNumber(4);

        putTestReservation(reservation).block();

        var savedReservation = getTestReservation(reservation.getReservationId()).block();

        assertEquals(reservation, savedReservation);

        // ensure the passenger was removed from the previous flight
        assertFalse(db.getPassengersForFlight(3).contains(reservation.getPassengerId()));
        // ensure passenger was added to new flight
        assertTrue(db.getPassengersForFlight(4).contains(reservation.getPassengerId()));

    }

    // Verify changing a flight number for a reservation is not allowed if the passenger is already on the new flight.
    @Test
    void update_flightChangeFailsIfPassengerAlreadyOnNewFlight() throws JsonProcessingException {

        // get a random reservation for flight 1, book the same passenger on flight 4 and then try
        // to change the reservation on flight 4 to be flight 1 (passenger is already on flight 1)
        var reservation = db.getReservationsForFlight(1).iterator().next();
        reservation.setFlightNumber(4);

        reservation = postTestReservation(reservation).block();

        assertNotNull(reservation);
        var savedReservation = getTestReservation(reservation.getReservationId()).block();

        assertNotNull(savedReservation);
        savedReservation.setFlightNumber(1);

        StepVerifier.create(putTestReservation(savedReservation))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.PRECONDITION_FAILED))
                .verify();

    }

    // Verify changing the passenger for a reservation removes the old passenger from the flight and adds the
    // new passenger
    @Test
    void update_changePassenger() throws JsonProcessingException {

        // get a random reservation for flight 3 and change the passenger on the reservation
        var reservation = db.getReservationsForFlight(3).iterator().next();

        reservation.setPassengerId(UPDATED_PASSENGER_ID);

        putTestReservation(reservation).block();

        var savedReservation = getTestReservation(reservation.getReservationId()).block();

        assertEquals(reservation, savedReservation);

        // ensure the previous passenger was removed from the previous flight
        assertFalse(db.getPassengersForFlight(3).contains(ORIG_PASSENGER_ID));
        // ensure the new passenger was added to new flight
        assertTrue(db.getPassengersForFlight(3).contains(UPDATED_PASSENGER_ID));

    }

    // Verify an error is thrown when trying to create a reservation to a full flight
    @Test
    void create_flightFullFail() throws JsonProcessingException {
        var testReservation = Reservation.builder()
                .flightNumber(2)
                .passengerId(UUID.randomUUID().toString())
                .build();

        StepVerifier.create(postTestReservation(testReservation))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.PRECONDITION_FAILED))
                .verify();
    }

    // Verify an error is thrown when a reservation from another flight is changed to a flight that is full.
    @Test
    void update_changeToFullFlightFail() throws JsonProcessingException {
        // get a random reservation for flight 3 and try to change it to flight 2 (a full flight)
        var testReservation = db.getReservationsForFlight(3).iterator().next();
        testReservation.setFlightNumber(2);

        StepVerifier.create(postTestReservation(testReservation))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.PRECONDITION_FAILED))
                .verify();
    }

    // Verify an error is thrown when cancelling a non-existent reservation
    @Test
    void cancel_notPresentFail() {

        StepVerifier.create(deleteTestReservation(UUID.randomUUID().toString()))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.NOT_FOUND))
                .verify();

    }

    // Verify a reservation can be successfully cancelled
    @Test
    void cancel_success() {
        // get a random reservation from flight 3 and cancel it
        var reservation = db.getReservationsForFlight(3).iterator().next();

        deleteTestReservation(reservation.getReservationId()).block();

        StepVerifier.create(getTestReservation(reservation.getReservationId()))
                .expectErrorMatches(throwable -> isHttpStatus(throwable, HttpStatus.NOT_FOUND))
                .verify();

    }

    Mono<Reservation> getTestReservation(String reservationId) {
        return client.retrieve(
                HttpRequest.GET(String.format("/%s", reservationId)),
                Reservation.class).single();
    }

    Mono<Reservation> postTestReservation(Reservation testReservation) throws JsonProcessingException {
        return client.retrieve(
                HttpRequest.POST("/", objectMapper.writeValueAsString(testReservation)),
                Reservation.class).single();
    }

    Mono<?> putTestReservation(Reservation reservation) throws JsonProcessingException {
        return client.exchange(
                HttpRequest.PUT(
                        String.format("/%s", reservation.getReservationId()),
                        objectMapper.writeValueAsString(reservation))).single();
    }

    Mono<?> deleteTestReservation(String reservationId) {
        return client.exchange(
                HttpRequest.DELETE(
                        String.format("/%s", reservationId))).single();
    }

    boolean isHttpStatus(Throwable throwable, HttpStatus status) {
        return throwable instanceof HttpClientResponseException
                && throwable.getMessage().equals(status.getReason());
    }

}

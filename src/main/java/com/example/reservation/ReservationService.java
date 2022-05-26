package com.example.reservation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;

import java.util.Collection;

@Singleton
public class ReservationService {

    private ReservationDao db;

    public ReservationService(ReservationDao db) {
        this.db = db;
    }

    public Collection<Reservation> getReservations() {
        return db.getAllReservations();
    }

    public Reservation getReservation(String reservationId) {
        return db.getReservation(reservationId);
    }

    public Reservation createReservation(Reservation reservation) {

        validateReservation(reservation);

        handleReservation(Operation.CREATE, null, reservation);

        return reservation;
    }

    public void updateReservation(Reservation reservation) {

        validateReservation(reservation);

        var existingReservation = getReservation(reservation.getReservationId());

        if (existingReservation == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }

        handleReservation(Operation.UPDATE, existingReservation, reservation);

    }

    public void cancelReservation(String reservationId) {

        var existingReservation = getReservation(reservationId);

        if (existingReservation == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }

        handleReservation(Operation.DELETE, existingReservation, null);
    }

    // Synchronize access so only one operation occurs at a time
    private synchronized void handleReservation(Operation operation,
                                                Reservation oldReservation,
                                                Reservation newReservation) {
        switch (operation) {
            case CREATE -> bookReservation(newReservation);
            case UPDATE -> updateReservation(oldReservation, newReservation);
            case DELETE -> cancelReservation(oldReservation);
        }
    }

    private void bookReservation(Reservation reservation) {

        var flightNumber = reservation.getFlightNumber();
        var userId = reservation.getPassengerId();

        if (db.isFlightFull(flightNumber)) {
            throw new HttpStatusException(HttpStatus.PRECONDITION_FAILED,
                    String.format("Flight %s is full", flightNumber));
        }

        var passengers = db.getPassengersForFlight(flightNumber);

        if (passengers.contains(userId)) {
            throw new HttpStatusException(HttpStatus.PRECONDITION_FAILED,
                    "User is already booked on flight");
        }
        passengers.add(userId);

        db.addReservation(reservation);
    }

    private void updateReservation(Reservation existingReservation, Reservation newReservation) {

        var oldFlight = existingReservation.getFlightNumber();
        var newFlight = newReservation.getFlightNumber();

        var oldUserId = existingReservation.getPassengerId();
        var newUserId = newReservation.getPassengerId();

        // if the flight number changed make sure the new flight is not full
        if (oldFlight != newFlight && db.isFlightFull(newFlight)) {
            throw new HttpStatusException(HttpStatus.PRECONDITION_FAILED,
                    String.format("Flight %s is full", newFlight));
        }

        // if the userId changed make sure the user is not already in the flight
        if (!oldUserId.equals(newUserId)) {
            var passengers = db.getPassengersForFlight(newFlight);

            if (passengers.contains(newUserId)) {
                throw new HttpStatusException(HttpStatus.PRECONDITION_FAILED,
                        "Passenger is already booked on flight");
            }
            passengers.add(newUserId);
        }

        // perform all db operations after validations so operations don't need to be rolled back if a validation
        // error occurs

        // if the flight number was changed the old reservation passenger needs to be removed from the old flight
        // and the new reservation passenger needs to be added to the new flight
        if (oldFlight != newFlight) {
            db.removePassengerFromFlight(oldFlight, existingReservation.getPassengerId());
            db.addPassengerToFlight(newFlight, newReservation.getPassengerId());
        }

        db.updateReservation(newReservation);
    }

    private void cancelReservation(Reservation reservation) {

        db.removePassengerFromFlight(reservation.getFlightNumber(), reservation.getPassengerId());
        db.removeReservation(reservation);
    }

    private void validateReservation(Reservation reservation) {
        // ensure the flight number is valid
        if (reservation.getFlightNumber() < 1 || reservation.getFlightNumber() > 4) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid flight number");
        }
        // ensure the request has a userId
        if (Strings.isNullOrEmpty(reservation.getPassengerId())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Invalid passengerId");
        }
    }

    @VisibleForTesting
    public void setDb(ReservationDao db) {
        this.db = db;
    }

    enum Operation {
        CREATE, UPDATE, DELETE
    }

}

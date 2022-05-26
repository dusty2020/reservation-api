package com.example.reservation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import jakarta.inject.Singleton;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Singleton
public class ReservationDao {

    // key is reservationId
    private Map<String, Reservation> reservationMap;

    // key is flightNumber, value is list of passengers
    private Map<Integer, List<String>> flightMap;

    public ReservationDao() {
        reservationMap = Maps.newConcurrentMap();
        flightMap = Maps.newConcurrentMap();
        // initialize the flight map for the valid flight numbers
        IntStream.range(1, 5).forEach(i -> flightMap.put(i, Lists.newCopyOnWriteArrayList()));
    }

    public Reservation getReservation(String reservationId) {
        // return a new object to simulate a persistence layer where changes to the objects are not automatically
        // persisted
        var reservation = reservationMap.get(reservationId);

        if (reservation == null) {
            return null;
        }

        return reservation.toBuilder().build();
    }

    public Collection<Reservation> getAllReservations() {
        // return new objects to simulate a persistence layer
        return reservationMap.values().stream()
                .map(reservation -> reservation.toBuilder().build())
                .collect(Collectors.toList());
    }

    public Collection<Reservation> getReservationsForFlight(int flightNumber) {
        return getAllReservations().stream()
                .filter(reservation -> reservation.getFlightNumber() == flightNumber)
                .map(reservation -> reservation.toBuilder().build()) // simulate persistence layer
                .collect(Collectors.toList());
    }

    public void addReservation(Reservation reservation) {
        reservation.setReservationId(UUID.randomUUID().toString());
        reservationMap.put(reservation.getReservationId(), reservation);
    }

    public void updateReservation(Reservation reservation) {
        reservationMap.put(reservation.getReservationId(), reservation);
    }

    public void removeReservation(Reservation reservation) {
        reservationMap.remove(reservation.getReservationId());
    }

    public boolean isFlightFull(int flightNumber) {
        return flightMap.get(flightNumber).size() >= flightNumber * 10;
    }

    public List<String> getPassengersForFlight(int flightNumber) {
        return flightMap.get(flightNumber);
    }

    public void addPassengerToFlight(int flightNumber, String passengerId) {
        flightMap.get(flightNumber).add(passengerId);
    }

    public void removePassengerFromFlight(int flightNumber, String passengerId) {
        flightMap.get(flightNumber).remove(passengerId);
    }

    @VisibleForTesting
    public Map<String, Reservation> getReservationMap() {
        return reservationMap;
    }

    @VisibleForTesting
    public void setReservationMap(Map<String, Reservation> reservations) {
        this.reservationMap = reservations;
    }

    @VisibleForTesting
    public Map<Integer, List<String>> getFlightMap() {
        return flightMap;
    }

    @VisibleForTesting
    public void setFlightMap(Map<Integer, List<String>> flightMap) {
        this.flightMap = flightMap;
    }
}

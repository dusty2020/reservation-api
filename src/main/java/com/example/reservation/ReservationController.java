package com.example.reservation;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.Collection;

@Controller("/reservation")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Get
    public Collection<Reservation> getReservations() {
        return reservationService.getReservations();
    }

    @Get("/{reservationId}")
    public Reservation getReservation(String reservationId) {
        return reservationService.getReservation(reservationId);
    }

    @Post
    @Status(HttpStatus.CREATED)
    public Reservation createReservation(@Body Reservation reservation) {
        return reservationService.createReservation(reservation);
    }

    @Put("/{reservationId}")
    public void updateReservation(String reservationId,
                                  @Body Reservation reservation) {

        if (reservation.getReservationId() != null
                && !reservationId.equals(reservation.getReservationId())) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST,
                    "Reservation id in path does not match reservation id in body");
        }
        // ensure the reservation id is set from the path
        reservation.setReservationId(reservationId);
        reservationService.updateReservation(reservation);
    }

    @Delete("/{reservationId}")
    public void cancelReservation(String reservationId) {
        reservationService.cancelReservation(reservationId);
    }
}

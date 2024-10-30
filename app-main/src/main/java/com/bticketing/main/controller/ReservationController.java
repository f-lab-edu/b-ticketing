package com.bticketing.main.controller;

import com.bticketing.main.dto.ReservationDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    //예매완료 api
    @PostMapping("/complete")
    public ReservationDto completeReservation(@RequestBody ReservationDto reservationDto) {
        return reservationDto;
    }

    // 예매 취소 API
    @PostMapping("/cancel")
    public ReservationDto cancelReservation(@RequestParam int reservationId) {
        // 취소 후 ID만 포함한 ReservationDto 반환
        ReservationDto reservationDto = new ReservationDto();
        reservationDto.setReservationId(reservationId);
        return reservationDto;
    }
}

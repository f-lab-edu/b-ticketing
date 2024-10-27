package com.bticketing.main.controller;

import com.bticketing.main.dto.ReservationDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reservation")
public class ReservationController {

    //예매완료 api
    @PostMapping("/complete")
    public String completeReservation(@RequestBody ReservationDto reservationDto) {
        return "예매 성공 ID: " + reservationDto.getReservationId();
    }

    //예매취소 api
    @PostMapping("/cancel")
    public String cancelReservation(@RequestParam int reservationId) {
        return "예매 ID " + reservationId + " 취소 완료";
    }
}
